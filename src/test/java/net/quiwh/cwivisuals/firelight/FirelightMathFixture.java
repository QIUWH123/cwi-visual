package net.quiwh.cwivisuals.firelight;

import java.lang.reflect.Method;

/** Headless regression fixture for local light blending and bounded deterministic noise. */
public final class FirelightMathFixture {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Class<?> flicker;
        try {
            flicker = Class.forName("net.quiwh.cwivisuals.firelight.Flicker");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Local firelight math has not been implemented", missing);
        }
        Class<?> kind = Class.forName("net.quiwh.cwivisuals.firelight.FlameKind");
        Method factor = flicker.getMethod("factor", float[].class, double.class, double.class, double.class, float.class);
        Method encode = flicker.getMethod("encode", int.class, float.class);
        Method value = flicker.getMethod("value", kind, int.class, float.class);
        float[] frame = new float[260];
        equal(1, number(factor.invoke(null, frame, 0d, 0d, 0d, 14f)), "empty source list leaves light alone");
        frame[2] = 1;
        frame[7] = number(encode.invoke(null, 14, -.5f));
        equal(.5f, number(factor.invoke(null, frame, 0d, 0d, 0d, 14f)), "a nearby dipping source dims its own block light");
        equal(1, number(factor.invoke(null, frame, 14d, 0d, 0d, 14f)), "source does not reach outside its real light radius");
        equal(1, number(factor.invoke(null, frame, 0d, 0d, 0d, 0f)), "skylight-only vertices stay unchanged");
        equal(1, number(factor.invoke(null, frame, 0d, 0d, 0d, .5f)), "Sodium half-texel minimum represents zero block light");
        frame[2] = 2;
        frame[11] = number(encode.invoke(null, 14, .5f));
        equal(1, number(factor.invoke(null, frame, 0d, 0d, 0d, 14f)), "equal opposite flames blend without stacking");
        for (int level = 1; level <= 15; level++) {
            for (float extreme : new float[]{-100, -.1f, 0, .1f, 100}) {
                float encoded = number(encode.invoke(null, level, extreme));
                check((int) Math.floor(encoded / 8f) == level, "encoding preserves source light level");
            }
        }
        for (Object flameKind : kind.getEnumConstants()) {
            float min = Float.POSITIVE_INFINITY;
            float max = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < 1200; i++) {
                float t = i / 2f;
                float sample = number(value.invoke(null, flameKind, 145, t));
                check(Float.isFinite(sample) && Math.abs(sample) < 1, "noise remains finite and gentle");
                equal(sample, number(value.invoke(null, flameKind, 145, t + 600)), "noise loops continuously");
                min = Math.min(min, sample);
                max = Math.max(max, sample);
            }
            check(max - min > .01f, "each flame kind varies over time");
        }
        System.out.println("FirelightMathFixture passed " + checks + " checks");
    }

    private static float number(Object value) { return ((Number) value).floatValue(); }
    private static void equal(float expected, float actual, String message) {
        check(Math.abs(expected - actual) < .0005f, message + ": expected " + expected + ", got " + actual);
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
