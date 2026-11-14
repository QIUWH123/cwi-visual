package net.quiwh.cwivisuals.weather;

/** Tick-driven ash envelope. Reading it is allocation-free and never advances weather time. */
final class AshIntensity {
    private final boolean indefinite;
    private final long duration;
    private final int entryTicks;
    private final int exitTicks;
    private final long seed;
    private long elapsed;
    private long end;
    private long gustIndex;
    private long gustStart;
    private long gustEnd;
    private long stopStart = -1;
    private int stopTicks;
    private float stopIntensity;

    AshIntensity(int durationTicks, long seed) {
        this(false, durationTicks, seed);
    }

    /** Server-selected ash has no deadline; only an explicit clear ends its presentation. */
    AshIntensity(long seed) {
        this(true, 0, seed);
    }

    private AshIntensity(boolean indefinite, int durationTicks, long seed) {
        if (!indefinite && durationTicks < 1) throw new IllegalArgumentException("Ash duration must be positive");
        this.indefinite = indefinite;
        duration = durationTicks;
        end = durationTicks;
        entryTicks = indefinite ? 120 : Math.max(1, Math.min(120, durationTicks / 4));
        exitTicks = indefinite ? 0 : Math.max(1, Math.min(160, durationTicks / 3));
        this.seed = seed;
        int firstLength = gustLength(0);
        gustStart = -(int) (unit(seed ^ 0x5048415345L) * firstLength);
        gustEnd = gustStart + firstLength;
    }

    float value() {
        if (finished()) return 0;
        if (stopStart >= 0) {
            return stopIntensity * (1 - smooth((elapsed - stopStart) / (float) stopTicks));
        }
        float entry = smooth(elapsed / (float) entryTicks);
        float exit = indefinite ? 1 : smooth((duration - elapsed) / (float) exitTicks);
        float progress = smooth((elapsed - gustStart) / (float) (gustEnd - gustStart));
        float low = gustStrength(gustIndex);
        float gust = low + (gustStrength(gustIndex + 1) - low) * progress;
        return entry * exit * gust;
    }

    void tick() {
        if (finished()) return;
        elapsed++;
        if (elapsed >= gustEnd) {
            gustStart = gustEnd;
            gustEnd += gustLength(++gustIndex);
        }
    }

    boolean finished() { return (!indefinite || stopStart >= 0) && elapsed >= end; }

    /** Capture the current strength and ease it to zero; repeated stop requests never prolong it. */
    void fadeOut(int ticks) {
        if (finished()) return;
        float current = value();
        if (ticks <= 0 || current == 0) {
            stopStart = elapsed;
            end = elapsed;
            return;
        }
        int remaining = indefinite && stopStart < 0 ? ticks : (int) Math.min((long) ticks, end - elapsed);
        if (stopStart >= 0 && elapsed + remaining >= end) return;
        stopStart = elapsed;
        stopTicks = remaining;
        stopIntensity = current;
        end = elapsed + remaining;
    }

    private int gustLength(long index) {
        return 140 + (int) (unit(seed ^ index * 0x9E3779B97F4A7C15L ^ 0x4C454E475448L) * 100);
    }

    private float gustStrength(long index) {
        float variation = (float) unit(seed ^ index * 0x632BE59BD9B4E019L ^ 0x535452454E475448L);
        // Alternate broad calm/strong targets, with independently varied timing and amplitude.
        return ((index ^ seed) & 1) == 0 ? .25f + variation * .14f : .86f + variation * .14f;
    }

    private static float smooth(float value) {
        float t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }

    private static double unit(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return ((value ^ value >>> 31) >>> 11) * 0x1.0p-53;
    }
}
