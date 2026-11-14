package net.quiwh.cwivisuals.colour;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.phys.Vec3;

/** Exercises the public palette boundary without a window, level or Minecraft singleton. */
public final class ColourFixture {
    private static int checks;

    public static void main(String[] args) throws Exception {
        inactiveAndDimensionLifecycle();
        independentPalettes();
        blendBoundsAndAlpha();
        skyAndThreadVisibility();
        System.out.println("ColourFixture: " + checks + " checks passed");
    }

    private static void independentPalettes() {
        WorldColours.configure(new ColourProfile(true, Set.of("minecraft:overworld"),
                new ColourProfile.Tint(0xFF0000, 1), new ColourProfile.Tint(0x00FF00, 1),
                new ColourProfile.Tint(0x0000FF, 1), new ColourProfile.Tint(0xFFFFFF, 1)));
        WorldColours.enterDimension("minecraft:overworld");
        equal(0x7FFF0000, WorldColours.grass(0x7F204060), "grass uses its own palette");
        equal(0x7F00FF00, WorldColours.foliage(0x7F204060), "foliage uses its own palette");
        equal(0x7F0000FF, WorldColours.water(0x7F204060), "water uses its own palette");
        Vec3 nativeSky = new Vec3(.2, .4, .6);
        Vec3 excludedSky = WorldColours.sky(nativeSky, "minecraft:the_nether");
        check(excludedSky == nativeSky, "sky uses its own level dimension while levels transition");
        WorldColours.enterDimension("minecraft:the_nether");
        Vec3 includedSky = WorldColours.sky(nativeSky, "minecraft:overworld");
        near(.6, includedSky.x, "old included level does not inherit the new dimension snapshot");
        WorldColours.enterDimension("minecraft:overworld");
    }

    private static void inactiveAndDimensionLifecycle() throws Exception {
        clear();
        equal(0x7F204060, colour("grass", 0x7F204060), "no world is initially neutral");
        configure(true, Set.of("minecraft:overworld"), 0xA0B0C0, 0.5);
        equal(0x204060, colour("grass", 0x204060), "configuring cannot activate a missing world");
        enter("minecraft:the_nether");
        equal(0x204060, colour("grass", 0x204060), "excluded dimension is neutral");
        enter("minecraft:overworld");
        equal(0x607890, colour("grass", 0x204060), "included world activates the palette");
        for (String name : new String[]{"foliage", "water"}) {
            equal(0x607890, colour(name, 0x204060), name + " receives its palette");
        }
        clear();
        equal(0x204060, colour("water", 0x204060), "disconnect clears active world state");
        enter("minecraft:overworld");
        equal(0x607890, colour("water", 0x204060), "rejoining retains configuration");
        configure(false, Set.of("minecraft:overworld"), 0xFFFFFF, 1);
        equal(0x204060, colour("grass", 0x204060), "disabled profile takes effect immediately");
        configure(true, Set.of(), 0xFFFFFF, 1);
        equal(0x204060, colour("grass", 0x204060), "empty dimensions cannot act as wildcard");

        Set<String> mutable = new HashSet<>(Set.of("minecraft:overworld"));
        configure(true, mutable, 0xA0B0C0, 0.5);
        mutable.clear();
        equal(0x607890, colour("grass", 0x204060), "profile copies caller dimension sets");
        enter(null);
        equal(0x204060, colour("grass", 0x204060), "null dimension is inactive");
    }

    private static void blendBoundsAndAlpha() throws Exception {
        enter("minecraft:overworld");
        for (double strength : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            configure(true, Set.of("minecraft:overworld"), 0xFFFFFF, strength);
            equal(0x7F204060, colour("grass", 0x7F204060), "invalid or zero strength is exactly neutral");
        }
        configure(true, Set.of("minecraft:overworld"), 0xFFA0B0C0, 2);
        equal(0x12A0B0C0, colour("water", 0x12204060), "target clamps to RGB and keeps input alpha");
        configure(true, Set.of("minecraft:overworld"), 0xA0B0C0, 0.5);
        equal(0x7F607890, colour("foliage", 0x7F204060), "midpoint blends RGB without changing alpha");
        configure(true, Set.of("minecraft:overworld"), 0x000000, 0.5);
        equal(0x808080, colour("grass", 0xFFFFFF), "channel rounding is unbiased at half strength");
        for (int channel = 0; channel <= 255; channel++) {
            int input = channel * 0x010101;
            int output = colour("water", input);
            for (int shift : new int[]{0, 8, 16}) {
                int value = output >>> shift & 255;
                check(value >= 0 && value <= channel, "blending stays within its channel endpoints");
            }
        }
    }

    private static void skyAndThreadVisibility() throws Exception {
        Vec3 input = new Vec3(.2, .4, .6);
        configure(true, Set.of("minecraft:overworld"), 0x000000, 0);
        check(sky(input) == input, "neutral sky keeps the exact native Vec3");
        configure(true, Set.of("minecraft:overworld"), 0xFFFFFF, .5);
        Vec3 transformed = sky(input);
        near(.4, transformed.x, "sky red midpoint retains native brightness");
        near(.5, transformed.y, "sky green midpoint retains native brightness");
        near(.6, transformed.z, "sky blue midpoint retains native brightness");
        Vec3 night = sky(new Vec3(.006, .012, .018));
        near(.012, night.x, "night sky has no fixed red floor");
        near(.015, night.y, "night sky has no fixed green floor");
        near(.018, night.z, "night sky has no fixed blue floor");
        Vec3 black = sky(Vec3.ZERO);
        near(0, black.x + black.y + black.z, "black sky stays black");
        Vec3 invalid = sky(new Vec3(Double.NaN, Double.POSITIVE_INFINITY, -10));
        check(Double.isFinite(invalid.x) && Double.isFinite(invalid.y) && Double.isFinite(invalid.z),
                "nonfinite native channels cannot poison sky rendering");
        check(invalid.x >= 0 && invalid.x <= 1 && invalid.y >= 0 && invalid.y <= 1
                && invalid.z >= 0 && invalid.z <= 1, "active sky channels stay normalized");

        configure(true, Set.of("minecraft:overworld"), 0xA0B0C0, .5);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                for (int i = 0; i < 1000; i++) {
                    int result = colour("grass", 0x204060);
                    if (result != 0x607890) throw new AssertionError("render worker saw incomplete palette");
                }
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        }, "colour-fixture-worker");
        worker.start();
        worker.join();
        check(failure.get() == null, "render workers read a complete immutable profile: " + failure.get());
        clear();
        check(sky(input) == input, "disconnect restores exact native sky");
        check(WorldColours.sky(input, "minecraft:overworld") == input,
                "an old level sky callback cannot reactivate a disconnected palette");
    }

    static void configure(boolean enabled, Set<String> dimensions, int target, double strength) throws Exception {
        ColourProfile.Tint tint = new ColourProfile.Tint(target, strength);
        WorldColours.configure(new ColourProfile(enabled, dimensions, tint, tint, tint, tint));
    }

    static int colour(String name, int input) throws Exception {
        return switch (name) {
            case "grass" -> WorldColours.grass(input);
            case "foliage" -> WorldColours.foliage(input);
            case "water" -> WorldColours.water(input);
            default -> throw new IllegalArgumentException(name);
        };
    }

    static Vec3 sky(Vec3 input) throws Exception {
        return WorldColours.sky(input);
    }

    static void enter(String dimension) throws Exception {
        WorldColours.enterDimension(dimension);
    }

    static void clear() throws Exception {
        WorldColours.clear();
    }

    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }

    private static void equal(int expected, int actual, String reason) {
        check(expected == actual, reason + ": expected " + Integer.toHexString(expected)
                + ", got " + Integer.toHexString(actual));
    }

    private static void near(double expected, double actual, String reason) {
        check(Math.abs(expected - actual) < 1E-12, reason);
    }
}
