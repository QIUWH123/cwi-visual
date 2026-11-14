package net.quiwh.cwivisuals.weather;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import pigcart.particlerain.ParticleLoader;
import pigcart.particlerain.config.ParticleData;

/** Same particles and retirement masks through the former and current tracking passes. */
final class AshTrackingFixture {
    private static final String[] IDS = {"dust", "skittering_dust", "dust_haze"};
    private static int checks;
    private static volatile long sink;

    private static final class TrackedAsh extends AshParticle {
        boolean alive = true;
        TrackedAsh(ClientLevel level, ParticleData data) { super(level, 0, 96, 0, data, true); }
        @Override public boolean isAlive() { return alive; }
    }

    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }

    // Exact previous tracking algorithm, retained only as a comparison oracle.
    private static void previous(List<Particle> particles, int[] counts, float strength) {
        Arrays.fill(counts, 0);
        particles.removeIf(particle -> !particle.isAlive());
        for (Particle particle : particles) {
            if (particle instanceof AshParticle ash) {
                ash.setVisibility(strength);
                for (int kind = 0; kind < IDS.length; kind++) {
                    if (IDS[kind].equals(ash.profileId())) counts[kind]++;
                }
            }
        }
    }

    private static void refill(List<Particle> particles, List<TrackedAsh> pool) {
        particles.clear();
        for (TrackedAsh ash : pool) particles.add(ash);
    }

    static void verify(ClientLevel level, boolean measure) throws Exception {
        var update = AshPreview.class.getDeclaredMethod("updateParticles", float.class);
        update.setAccessible(true);
        Field trackedField = AshPreview.class.getDeclaredField("PARTICLES");
        trackedField.setAccessible(true);
        @SuppressWarnings("unchecked") var tracked = (List<Particle>) trackedField.get(null);
        Field countsField = AshPreview.class.getDeclaredField("COUNTS");
        countsField.setAccessible(true);
        var actualCounts = (int[]) countsField.get(null);
        Field visibility = AshParticle.class.getDeclaredField("visibility");
        visibility.setAccessible(true);
        var pool = new ArrayList<TrackedAsh>(600);
        for (int i = 0; i < 600; i++) {
            pool.add(new TrackedAsh(level, AshParticleProfiles.create(ParticleLoader.particles.get(IDS[i % 3]))));
        }
        var reference = new ArrayList<Particle>(600);
        var boundary = new ArrayList<TrackedAsh>(3);
        int[] expectedCounts = new int[3];
        var random = new Random(4492106);
        try {
            for (int iteration = 0; iteration < 240; iteration++) {
                int size = switch (iteration % 8) { case 0 -> 0; case 1 -> 1; case 2 -> 147; default -> 600; };
                tracked.clear(); reference.clear();
                for (int i = 0; i < size; i++) {
                    TrackedAsh particle = pool.get(i);
                    particle.alive = switch (iteration % 6) {
                        case 0 -> true;
                        case 1 -> false;
                        case 2 -> i != 0;
                        case 3 -> i != size - 1;
                        case 4 -> (i & 1) == 0;
                        default -> random.nextBoolean();
                    };
                    particle.setVisibility(.37f);
                    tracked.add(particle); reference.add(particle);
                }
                float strength = switch (iteration % 5) {
                    case 0 -> 0; case 1 -> 1; case 2 -> .47f; case 3 -> Float.NaN; default -> -.2f;
                };
                previous(reference, expectedCounts, strength);
                for (TrackedAsh ash : pool) ash.setVisibility(.37f);
                update.invoke(null, strength);
                check(tracked.equals(reference), "Stable survivor identity/order at case " + iteration);
                check(Arrays.equals(actualCounts, expectedCounts), "All three population limits at case " + iteration);
                float expectedVisibility = Float.isFinite(strength) ? Math.clamp(strength, 0, 1) : 0;
                for (int i = 0; i < size; i++) {
                    TrackedAsh particle = pool.get(i);
                    check(visibility.getFloat(particle) == (particle.alive ? expectedVisibility : .37f),
                            "Only surviving particles receive event visibility at case " + iteration);
                }
            }
            String[] initialIds = {null, "unrecognized_ash_profile", "dust"};
            var descriptors = new ArrayList<ParticleData>(3);
            for (String id : initialIds) {
                ParticleData descriptor = AshParticleProfiles.create(ParticleLoader.particles.get("dust"));
                descriptor.id = id;
                descriptors.add(descriptor);
                boundary.add(new TrackedAsh(level, descriptor));
            }
            for (int phase = 0; phase < 2; phase++) {
                tracked.clear(); reference.clear();
                if (phase == 1) for (ParticleData descriptor : descriptors) descriptor.id = "dust_haze";
                for (TrackedAsh particle : boundary) {
                    tracked.add(particle); reference.add(particle);
                }
                previous(reference, expectedCounts, .61f);
                for (TrackedAsh particle : boundary) particle.setVisibility(0);
                update.invoke(null, .61f);
                check(tracked.equals(reference), "Null/unknown profile particles keep stable ownership in phase " + phase);
                check(Arrays.equals(actualCounts, expectedCounts) && Arrays.equals(actualCounts, new int[] {1, 0, 0}),
                        "Null/unknown profiles do not enroll and descriptor edits cannot reclassify existing ash");
                for (int i = 0; i < boundary.size(); i++) {
                    TrackedAsh particle = boundary.get(i);
                    check(java.util.Objects.equals(particle.profileId(), initialIds[i]),
                            "Profile identity remains the construction snapshot after descriptor edits");
                    check(visibility.getFloat(particle) == .61f,
                            "Null/unknown profile particles still receive event visibility");
                }
            }
            // A foreign tracked item was tolerated by the former code; preserve its identity too.
            var foreign = new Particle(level, 0, 96, 0) {
                @Override public void render(com.mojang.blaze3d.vertex.VertexConsumer buffer,
                                             net.minecraft.client.Camera camera, float partialTick) {}
                @Override public net.minecraft.client.particle.ParticleRenderType getRenderType() {
                    return net.minecraft.client.particle.ParticleRenderType.NO_RENDER;
                }
            };
            tracked.clear(); tracked.add(foreign);
            update.invoke(null, 1f);
            check(tracked.size() == 1 && tracked.getFirst() == foreign && Arrays.equals(actualCounts, new int[3]),
                    "A foreign tracked particle survives without enrolling in ash population counts");
            foreign.remove();
            tracked.clear();
            if (measure) {
                MethodHandle candidate = MethodHandles.lookup().unreflect(update);
                for (int size : new int[] {147, 600}) {
                    for (int retirement : new int[] {0, 20, 2, 1}) {
                        benchmark(candidate, tracked, actualCounts, pool.subList(0, size), retirement);
                    }
                }
            }
        } catch (Throwable error) {
            if (error instanceof Exception exception) throw exception;
            throw new AssertionError(error);
        } finally {
            tracked.clear();
            Arrays.fill(actualCounts, 0);
            for (TrackedAsh ash : pool) ash.remove();
            for (TrackedAsh ash : boundary) ash.remove();
        }
        System.out.println("AshTrackingFixture PASS: " + checks + " parity checks; stable retirement/order, population and visibility.");
    }

    private static void benchmark(MethodHandle update, List<Particle> tracked, int[] counts,
                                  List<TrackedAsh> pool, int retirement) throws Throwable {
        var reference = new ArrayList<Particle>(600);
        int[] expectedCounts = new int[3];
        long[] previousTimes = new long[7], currentTimes = new long[7];
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (bean.isThreadAllocatedMemorySupported() && !bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        long previousBytes = 0, currentBytes = 0;
        for (int i = 0; i < pool.size(); i++) pool.get(i).alive = retirement == 0 || i % retirement != 0;
        int repetitions = 9_000_000 / pool.size();
        for (int pass = -3; pass < 7; pass++) {
            for (int order = 0; order < 2; order++) {
                boolean candidate = ((pass + order) & 1) == 0;
                long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
                long started = System.nanoTime();
                for (int iteration = 0; iteration < repetitions; iteration++) {
                    if (candidate) {
                        refill(tracked, pool);
                        update.invokeExact(.63f);
                        sink = counts[0] + tracked.size();
                    } else {
                        refill(reference, pool);
                        previous(reference, expectedCounts, .63f);
                        sink = expectedCounts[0] + reference.size();
                    }
                }
                long nanos = System.nanoTime() - started;
                long bytes = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocated;
                if (pass >= 0) {
                    (candidate ? currentTimes : previousTimes)[pass] = nanos;
                    if (candidate) currentBytes += bytes; else previousBytes += bytes;
                }
            }
        }
        Arrays.sort(previousTimes); Arrays.sort(currentTimes);
        System.out.printf(java.util.Locale.ROOT,
                "Ash tracking A/B: %d particles, %d%% retired, %,d updates per sample; previous median %.1f ns/update, current %.1f ns/update; allocation %.1f -> %.1f bytes/update (7 interleaved measured samples, 3 warmups).%n",
                pool.size(), retirement == 0 ? 0 : 100 / retirement, repetitions,
                previousTimes[3] / (double) repetitions, currentTimes[3] / (double) repetitions,
                previousBytes / (double) (7 * repetitions), currentBytes / (double) (7 * repetitions));
    }
}
