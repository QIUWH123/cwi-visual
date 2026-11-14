package net.quiwh.cwivisuals.weather;

import java.lang.reflect.Constructor;

/** Pure-Java event-envelope checks; opens no game window or Minecraft world. */
public final class AshIntensityFixture {
    private static int assertions;

    private static void check(boolean value, String reason) {
        assertions++;
        if (!value) throw new AssertionError(reason);
    }

    private static void close(float actual, float expected, String reason) {
        check(Math.abs(actual - expected) < 0.000001f, reason + ": " + actual + " != " + expected);
    }

    private static void naturalEvent(int duration, long seed) {
        AshIntensity event = new AshIntensity(duration, seed);
        check(!event.finished(), "New event is active");
        close(event.value(), 0, "New event starts invisible");
        float previous = 0, interiorMin = 1, interiorMax = 0, largestStep = 0;
        for (int tick = 1; tick <= duration; tick++) {
            event.tick();
            float value = event.value();
            check(Float.isFinite(value) && value >= 0 && value <= 1, "Intensity stays finite and bounded");
            check(event.finished() == (tick == duration), "Only the last requested tick finishes the event");
            largestStep = Math.max(largestStep, Math.abs(value - previous));
            if (duration >= 1200 && tick >= 120 && tick <= duration - 160) {
                interiorMin = Math.min(interiorMin, value);
                interiorMax = Math.max(interiorMax, value);
            }
            previous = value;
        }
        close(event.value(), 0, "Natural event ends invisible");
        for (int tick = 0; tick < 10; tick++) event.tick();
        close(event.value(), 0, "Finished event remains invisible");
        if (duration >= 1200) {
            check(largestStep < .025f, "Normal-duration intensity cannot jump between neighboring ticks");
            check(interiorMin < .43f && interiorMax > .80f, "Event contains both calm and strong interior weather");
        } else {
            check(largestStep < .35f, "Short events still ease instead of appearing at full strength");
        }
    }

    private static void replayAndPause() {
        AshIntensity a = new AshIntensity(1200, 7141);
        AshIntensity b = new AshIntensity(1200, 7141);
        AshIntensity other = new AshIntensity(1200, 7142);
        int different = 0;
        for (int tick = 0; tick < 1200; tick++) {
            close(a.value(), b.value(), "Same seed reproduces the same weather");
            float paused = a.value();
            close(a.value(), paused, "Reading intensity never advances the event");
            if (Math.abs(a.value() - other.value()) > .01) different++;
            a.tick(); b.tick(); other.tick();
        }
        check(different > 500, "Different seeds vary weather rather than replaying a fixed wave");
    }

    private static void stopDuringEvent() {
        AshIntensity event = new AshIntensity(1200, 87);
        for (int tick = 0; tick < 400; tick++) event.tick();
        float before = event.value();
        event.fadeOut(100);
        close(event.value(), before, "Manual stop preserves the current intensity");
        float previous = before;
        for (int tick = 1; tick <= 100; tick++) {
            if (tick == 30) event.fadeOut(400);
            event.tick();
            check(event.value() <= previous, "Manual fade is monotonic even if a gust would strengthen");
            check(previous - event.value() < .025, "Manual fade has no abrupt clearing");
            check(event.finished() == (tick == 100), "Repeating stop cannot extend the fade");
            previous = event.value();
        }
        close(event.value(), 0, "Manual stop ends at zero");
        event.fadeOut(20);
        check(event.finished(), "Stopping a finished event cannot restart it");

        AshIntensity ending = new AshIntensity(1200, 6);
        for (int tick = 0; tick < 1180; tick++) ending.tick();
        ending.fadeOut(100);
        for (int tick = 1; tick <= 20; tick++) ending.tick();
        check(ending.finished(), "Manual stop cannot extend the natural end time");

        AshIntensity justStarted = new AshIntensity(1200, 6);
        justStarted.fadeOut(100);
        check(justStarted.finished(), "Stopping invisible initial weather finishes immediately");
    }

    private static void rejectsInvalidDuration() {
        for (int duration : new int[]{0, -1}) {
            boolean rejected = false;
            try { new AshIntensity(duration, 0); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "Duration must be positive");
        }
    }

    private static AshIntensity indefinite(long seed) throws Exception {
        Constructor<AshIntensity> constructor;
        try {
            constructor = AshIntensity.class.getDeclaredConstructor(long.class);
            constructor.setAccessible(true);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("Missing true indefinite ash envelope: AshIntensity(long)", missing);
        }
        return constructor.newInstance(seed);
    }

    private static void indefiniteEvent(long seed) throws Exception {
        AshIntensity event = indefinite(seed);
        AshIntensity matchingTimed = new AshIntensity(100000, seed);
        close(event.value(), 0, "Indefinite ash starts with the existing invisible entry");
        float previous = 0;
        for (int tick = 1; tick <= 36000; tick++) {
            event.tick(); matchingTimed.tick();
            float value = event.value();
            check(!event.finished(), "Indefinite ash never expires, including beyond the former 60-second and 600-second limits");
            check(Float.isFinite(value) && value >= 0 && value <= 1, "Indefinite intensity stays finite and bounded");
            close(value, matchingTimed.value(), "Indefinite ash preserves the existing entry and seeded gust tuning");
            check(Math.abs(value - previous) < .025f, "Indefinite entry and gusts stay smooth between ticks");
            if (tick >= 120) check(value >= .25f, "Established indefinite ash has no scheduled exit fade");
            previous = value;
        }
        for (int read = 0; read < 20; read++) close(event.value(), previous, "Reading an indefinite envelope never advances it");
        event.fadeOut(80);
        close(event.value(), previous, "Explicit fade preserves the current indefinite intensity");
        for (int tick = 1; tick <= 80; tick++) {
            event.tick();
            check(event.finished() == (tick == 80), "Only an explicit fade can finish indefinite ash");
        }
        close(event.value(), 0, "Explicit indefinite fade ends invisible");
    }

    private static void indefiniteLongTimeline() throws Exception {
        AshIntensity event = indefinite(4281);
        AshIntensity reference = indefinite(4281);
        for (int tick = 0; tick < 400; tick++) { event.tick(); reference.tick(); }
        // Exercise the real relative timeline across the native int boundary without replaying years.
        long offset = Integer.MAX_VALUE - 450L;
        for (String name : new String[]{"elapsed", "gustStart", "gustEnd"}) {
            var field = AshIntensity.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setLong(event, field.getLong(event) + offset);
        }
        for (int tick = 0; tick < 800; tick++) {
            event.tick(); reference.tick();
            check(!event.finished(), "Indefinite ash stays active across the native int boundary");
            close(event.value(), reference.value(), "Gust arithmetic stays stable across the native int boundary");
        }
        event.fadeOut(3);
        event.tick(); event.tick();
        check(!event.finished(), "A long indefinite timeline retains its explicit fade ticks");
        event.tick();
        check(event.finished(), "A long indefinite timeline honors an explicit fade");
    }

    public static void main(String[] args) throws Exception {
        indefiniteLongTimeline();
        rejectsInvalidDuration();
        for (long seed : new long[]{0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE, 4281}) {
            indefiniteEvent(seed);
            naturalEvent(1200, seed);
            naturalEvent(12000, seed);
            naturalEvent(20, seed);
        }
        replayAndPause();
        stopDuringEvent();
        System.out.println("AshIntensityFixture PASS: " + assertions
                + " assertions; indefinite authority, original timed envelope, smooth gusts, long timelines, pause, replay and explicit fade.");
    }
}
