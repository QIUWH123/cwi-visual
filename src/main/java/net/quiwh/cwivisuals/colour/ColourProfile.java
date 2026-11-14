package net.quiwh.cwivisuals.colour;

import java.util.Objects;
import java.util.Set;
import net.minecraft.world.phys.Vec3;

/** Immutable settings which may be read directly by chunk compilation workers. */
public record ColourProfile(boolean enabled, Set<String> dimensions,
                            Tint grass, Tint foliage, Tint water, Tint sky) {
    public ColourProfile {
        dimensions = Set.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
        Objects.requireNonNull(grass, "grass");
        Objects.requireNonNull(foliage, "foliage");
        Objects.requireNonNull(water, "water");
        Objects.requireNonNull(sky, "sky");
    }

    boolean appliesTo(String dimension) {
        return enabled && dimension != null && dimensions.contains(dimension);
    }

    /** Packed colours blend toward RGB; sky uses the target hue at its existing peak brightness. */
    public record Tint(int target, double strength) {
        public Tint {
            target &= 0xFFFFFF;
            strength = Double.isFinite(strength) ? Math.clamp(strength, 0, 1) : 0;
        }

        int packed(int original) {
            if (strength == 0) return original;
            int red = channel(original >>> 16 & 255, target >>> 16 & 255);
            int green = channel(original >>> 8 & 255, target >>> 8 & 255);
            int blue = channel(original & 255, target & 255);
            return original & 0xFF000000 | red << 16 | green << 8 | blue;
        }

        Vec3 sky(Vec3 original) {
            if (strength == 0) return original;
            double red = finiteChannel(original.x);
            double green = finiteChannel(original.y);
            double blue = finiteChannel(original.z);
            double peak = Math.max(red, Math.max(green, blue));
            int targetPeak = Math.max(target >>> 16 & 255, Math.max(target >>> 8 & 255, target & 255));
            double scale = targetPeak == 0 ? 0 : peak / targetPeak;
            return new Vec3(blend(red, (target >>> 16 & 255) * scale),
                    blend(green, (target >>> 8 & 255) * scale),
                    blend(blue, (target & 255) * scale));
        }

        private int channel(int original, int targetChannel) {
            return (int) Math.round(original + (targetChannel - original) * strength);
        }

        private double blend(double original, double targetChannel) {
            return finiteChannel(original + (targetChannel - original) * strength);
        }

        private static double finiteChannel(double channel) {
            return Double.isFinite(channel) ? Math.clamp(channel, 0, 1) : 0;
        }
    }
}
