package net.quiwh.cwivisuals.weather;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.quiwh.cwivisuals.CwiVisuals;
import net.quiwh.cwivisuals.weather.network.AshWeatherPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pigcart.particlerain.ParticleLoader;
import pigcart.particlerain.config.ConfigManager;
import pigcart.particlerain.config.ParticleData;

/** Client presentation of the server-selected Overworld ash storm. */
@EventBusSubscriber(modid = CwiVisuals.ID, value = Dist.CLIENT)
public final class AshPreview {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final List<Particle> PARTICLES = new ArrayList<>();
    private static final String[] IDS = {"dust", "skittering_dust", "dust_haze"};
    private static final ParticleData[] SOURCES = new ParticleData[3];
    private static final ParticleData[] ASH = new ParticleData[3];
    private static final AshSurfaceCache SURFACES = new AshSurfaceCache();
    private static final int[] COUNTS = new int[3];
    private static final int[] LIMITS = new int[3];
    private static final float[] CREDITS = new float[3];
    private static ClientLevel presentationLevel;
    private static AshIntensity intensity;
    private static int elapsed;
    private static boolean enabled = true;
    private static boolean stormActive;
    private static long stormSeed;
    private static boolean profileWarning;

    private AshPreview() {}

    /** Apply the client config on its main thread; disabling releases only our presentation. */
    public static void configure(boolean value) {
        if (enabled == value) return;
        enabled = value;
        if (!enabled) {
            clearPresentation();
        }
    }

    /** Runs on the client main thread; authority survives hidden/disabled presentation. */
    public static void applySync(AshWeatherPayload snapshot) {
        boolean replacement = snapshot.active() && (!stormActive || snapshot.seed() != stormSeed);
        if (replacement || !snapshot.active()) clearPresentation();
        stormActive = snapshot.active();
        stormSeed = stormActive ? snapshot.seed() : 0;
        if (replacement) profileWarning = false;
        ensurePresentation(Minecraft.getInstance());
    }

    public static void disconnect() {
        stormActive = false;
        stormSeed = 0;
        profileWarning = false;
        clearPresentation();
    }

    private static boolean eligible(Minecraft client) {
        return enabled && client.level != null && client.level.dimension().equals(Level.OVERWORLD)
                && client.getCameraEntity() != null;
    }

    private static void ensurePresentation(Minecraft client) {
        if (!stormActive || !eligible(client) || intensity != null) return;
        if (!refreshProfiles()) {
            if (!profileWarning) {
                CwiVisuals.LOGGER.warn("Ash storm presentation awaits Particle Rain's three sandstorm profiles");
                profileWarning = true;
            }
            return;
        }
        profileWarning = false;
        presentationLevel = client.level;
        // Joining/re-entering fades gently into selected ash. No elapsed history is replayed.
        intensity = new AshIntensity(stormSeed);
    }

    private static void clearPresentation() {
        presentationLevel = null;
        intensity = null;
        AshFog.clear();
        AshVeil.clear();
        elapsed = 0;
        SURFACES.clear();
        Arrays.fill(SOURCES, null);
        Arrays.fill(ASH, null);
        Arrays.fill(CREDITS, 0);
        // Expire only our particles. Other weather integrations own their counters and particles.
        PARTICLES.forEach(particle -> {
            if (particle instanceof AshParticle ash) ash.hideImmediately();
            particle.setLifetime(0);
        });
        PARTICLES.clear();
    }

    private static boolean refreshProfiles() {
        Map<String, ParticleData> loaded = ParticleLoader.particles;
        if (loaded == null) return false;
        for (String id : IDS) {
            ParticleData source = loaded.get(id);
            if (source == null || source.particleStyle != ParticleData.ParticleStyle.CUSTOM) return false;
        }
        for (int i = 0; i < IDS.length; i++) {
            ParticleData source = loaded.get(IDS[i]);
            if (source != SOURCES[i]) {
                SOURCES[i] = source;
                ASH[i] = AshParticleProfiles.create(source);
            }
        }
        return true;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (presentationLevel != null && (client.level != presentationLevel || !eligible(client))) {
            clearPresentation();
        }
        if (client.level == null || client.isPaused()) return;
        ensurePresentation(client);
        if (intensity == null) return;
        if (!refreshProfiles()) {
            clearPresentation();
            return;
        }
        intensity.tick();
        elapsed++;
        float strength = intensity.value();
        AshFog.tick(client.level, client.gameRenderer.getMainCamera().getPosition(), strength);
        updateParticles(strength);
        if (hasBudget() && strength > 0) {
            emit(client, client.gameRenderer.getMainCamera().getPosition());
        }
    }

    private static void updateParticles(float strength) {
        Arrays.fill(COUNTS, 0);
        int live = 0;
        int size = PARTICLES.size();
        for (int read = 0; read < size; read++) {
            Particle particle = PARTICLES.get(read);
            if (!particle.isAlive()) continue;
            if (live != read) PARTICLES.set(live, particle);
            live++;
            if (particle instanceof AshParticle ash) {
                ash.setVisibility(strength);
                int kind = ash.profileKind();
                if (kind >= 0) COUNTS[kind]++;
            }
        }
        // Stable compaction preserves emission order and avoids removeIf's temporary death bitset.
        // Removing only from the tail never shifts the remaining particle references.
        for (int index = size - 1; index >= live; index--) PARTICLES.remove(index);
    }

    private static boolean hasBudget() {
        return WeatherParticleBudget.count() < ConfigManager.getConfig().perf.particleAmount;
    }

    private static void spawn(Minecraft client, double x, double y, double z, ParticleData data, int kind) {
        if (!hasBudget() || COUNTS[kind] >= LIMITS[kind]) return;
        // The registered provider looks up the original descriptor and would discard the tint.
        AshParticle particle = new AshParticle(client.level, x, y, z, data, kind != 1);
        particle.setVisibility(intensity.value());
        PARTICLES.add(particle);
        COUNTS[kind]++;
        client.particleEngine.add(particle);
    }

    private static boolean accepts(ParticleData data) {
        // Reject density before sampling terrain or collision shapes, especially 20%-density grit.
        return data.enabled && RANDOM.nextFloat() < data.density;
    }

    private static int attempts(int kind, int maximum, float strength) {
        CREDITS[kind] += maximum * strength;
        int count = (int) CREDITS[kind];
        CREDITS[kind] -= count;
        return count;
    }

    private static void emit(Minecraft client, Vec3 camera) {
        ClientLevel level = client.level;
        var performance = ConfigManager.getConfig().perf;
        float strength = intensity.value();
        int ownLimit = Mth.clamp(performance.particleAmount, 0, 600);
        LIMITS[0] = (int) (ownLimit * .92f);
        LIMITS[1] = (int) (ownLimit * .04f);
        LIMITS[2] = Math.min(16, ownLimit - LIMITS[0] - LIMITS[1]);
        int distance = Mth.clamp(performance.particleDistance, 1, 128);
        float speed = (float) client.getCameraEntity().getDeltaMovement().length();
        // Particle Rain's spherical dust sampling, density and storm multiplier.
        int baseAttempts = Mth.clamp((int) (performance.particleAmount / 50.0f
                * Mth.lerp(level.getThunderLevel(1), 1, performance.particleStormDensity)
                * (1 + (int) speed * 2)), 0, 128);
        int attempts = attempts(0, baseAttempts, strength);
        for (int i = 0; i < attempts && hasBudget(); i++) {
            if (COUNTS[0] >= LIMITS[0] || !accepts(ASH[0])) continue;
            float height = speed < 0.8f
                    ? 1 - Math.abs(Mth.square(RANDOM.nextFloat()) - Mth.square(RANDOM.nextFloat()))
                    : RANDOM.nextFloat();
            double theta = Math.PI * 2 * RANDOM.nextDouble();
            double vertical = 2 * height - 1;
            double horizontal = Math.sqrt(Math.max(0, 1 - vertical * vertical));
            // Fill the nearby volume instead of spending all particles on the outer shell.
            double radius = distance * (.25 + .75 * RANDOM.nextDouble());
            double far = radius / distance;
            if (RANDOM.nextFloat() > 1 - .45 * far * far) continue;
            double x = camera.x + radius * horizontal * Math.cos(theta);
            double y = camera.y + radius * vertical;
            double z = camera.z + radius * horizontal * Math.sin(theta);
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!level.isInWorldBounds(pos) || !level.isLoaded(pos)) continue;
            if (y <= level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ())) continue;
            spawn(client, x, y, z, ASH[0], 0);
        }

        // Sparse ground dust and soft haze, sampled on exposed loaded surfaces.
        emitSurface(client, camera, distance, attempts(1, Math.min(baseAttempts / 6, 8), strength), ASH[1]);
        emitSurface(client, camera, Mth.clamp(performance.surfaceRange, 1, 128),
                attempts(2, Mth.clamp(performance.particleAmount / 600, 0, 3), strength), ASH[2]);
    }

    private static void emitSurface(Minecraft client, Vec3 camera, int range, int attempts, ParticleData data) {
        ClientLevel level = client.level;
        int kind = "skittering_dust".equals(data.id) ? 1 : 2;
        for (int i = 0; i < attempts && hasBudget(); i++) {
            if (!accepts(data) || COUNTS[kind] >= LIMITS[kind]) continue;
            double x = RANDOM.triangle(camera.x, range);
            double z = RANDOM.triangle(camera.z, range);
            double dx = x - camera.x, dz = z - camera.z;
            double horizontalDistance = dx * dx + dz * dz;
            if (horizontalDistance > range * range
                    || RANDOM.nextFloat() > 1 - .45 * horizontalDistance / (range * range)) continue;
            int blockX = Mth.floor(x), blockZ = Mth.floor(z);
            var surface = SURFACES.sample(level, blockX, blockZ, elapsed);
            if (surface == null) continue;
            double top = surface.shape().max(Direction.Axis.Y, z - blockZ, x - blockX);
            if (!Double.isFinite(top)) continue;
            double y = surface.groundY() + top + 0.01;
            if (data.spawnPos == ParticleData.SpawnPos.WORLD_SURFACE) y += RANDOM.nextDouble() + 0.1;
            // Avoid filling distant roofs and valley floors when previewing high above them.
            double dy = y - camera.y;
            if (horizontalDistance + dy * dy <= range * range) spawn(client, x, y, z, data, kind);
        }
    }
}
