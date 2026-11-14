package net.quiwh.cwivisuals.firelight;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Random;

/** Exact selection oracle and controlled old/new measurements; no world or renderer is created. */
final class FirelightSelectionFixture {
    private static int checks;
    private static volatile long consumed;

    static int run() throws Exception {
        int[] sizes = {0, 1, 7, 63, 64, 65, 129, 4096, 32768};
        for (int size : sizes) {
            for (int pattern = 0; pattern < 4; pattern++) {
                int[][] input = workload(size, pattern);
                FlameTracker.Candidates actual = new FlameTracker.Candidates();
                LegacyCandidates expected = new LegacyCandidates();
                add(actual, input);
                add(expected, input);
                equal(expected.nearest(64), actual.nearest(64));
                // Reusing the same scratch data must not retain sources after reset.
                actual.reset();
                equal(FlameTracker.Snapshot.EMPTY, actual.nearest(64));
                add(actual, input);
                equal(expected.nearest(64), actual.nearest(64));
            }
        }
        for (int iteration = 0; iteration < 100; iteration++) {
            int[][] input = workload(65 + iteration * 31, iteration + 4);
            FlameTracker.Candidates actual = new FlameTracker.Candidates();
            LegacyCandidates expected = new LegacyCandidates();
            add(actual, input);
            add(expected, input);
            equal(expected.nearest(64), actual.nearest(64));
        }
        FlameTracker.Candidates reused = new FlameTracker.Candidates();
        add(reused, workload(4096, 17));
        reused.nearest(64);
        reused.reset();
        LegacyCandidates sparse = new LegacyCandidates();
        int[][] sparseInput = workload(12, 1);
        add(reused, sparseInput);
        add(sparse, sparseInput);
        equal(sparse.nearest(64), reused.nearest(64));
        benchmark();
        FlameTracker.Candidates dense = new FlameTracker.Candidates();
        add(dense, workload(32768, 2));
        for (Field field : FlameTracker.Candidates.class.getDeclaredFields()) {
            if (field.getType().isArray()) {
                field.setAccessible(true);
                int length = java.lang.reflect.Array.getLength(field.get(dense));
                check(length <= 64, "selection scratch stays bounded at 64: " + field.getName() + "=" + length);
            }
        }
        System.out.println("FirelightSelectionFixture passed " + checks + " checks");
        return checks;
    }

    private static int[][] workload(int size, int pattern) {
        Random random = new Random(0xC71F1A4EL + pattern);
        int[][] values = new int[size][5];
        for (int i = 0; i < size; i++) {
            int distance = switch (pattern) {
                case 0 -> i % 1601;
                case 1 -> 1600 - i % 1601;
                case 2 -> 100; // Stable encounter order matters when many distances tie.
                default -> random.nextInt(1601);
            };
            values[i] = new int[]{random.nextInt(81) - 40, random.nextInt(81) - 40,
                    random.nextInt(81) - 40, (i % FlameKind.values().length) << 12 | (1 + i % 15) << 16, distance};
        }
        return values;
    }

    private static void add(FlameTracker.Candidates target, int[][] input) {
        for (int[] v : input) target.add(v[0], v[1], v[2], v[3], v[4]);
    }
    private static void add(LegacyCandidates target, int[][] input) {
        for (int[] v : input) target.add(v[0], v[1], v[2], v[3], v[4]);
    }
    private static void equal(FlameTracker.Snapshot expected, FlameTracker.Snapshot actual) {
        check(expected.count() == actual.count(), "selected count");
        check(Arrays.equals(expected.x(), actual.x()), "selected x and stable ordering");
        check(Arrays.equals(expected.y(), actual.y()), "selected y and stable ordering");
        check(Arrays.equals(expected.z(), actual.z()), "selected z and stable ordering");
        check(Arrays.equals(expected.levels(), actual.levels()), "selected light levels");
        check(Arrays.equals(expected.seeds(), actual.seeds()), "selected positional seeds");
        check(Arrays.equals(expected.kinds(), actual.kinds()), "selected flame kinds");
    }

    private static void benchmark() throws Exception {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        for (int size : new int[]{12, 64, 4096, 32768}) {
            int[][] input = workload(size, 17);
            FlameTracker.Candidates candidate = new FlameTracker.Candidates();
            LegacyCandidates legacy = new LegacyCandidates();
            int iterations = Math.max(30, 120000 / size);
            for (int i = 0; i < Math.max(100, iterations); i++) {
                run(candidate, input);
                run(legacy, input);
            }
            long[] oldNs = new long[7], newNs = new long[7], oldBytes = new long[7], newBytes = new long[7];
            for (int round = 0; round < 7; round++) {
                if ((round & 1) == 0) {
                    measure(legacy, input, iterations, bean, oldNs, oldBytes, round);
                    measure(candidate, input, iterations, bean, newNs, newBytes, round);
                } else {
                    measure(candidate, input, iterations, bean, newNs, newBytes, round);
                    measure(legacy, input, iterations, bean, oldNs, oldBytes, round);
                }
            }
            Arrays.sort(oldNs); Arrays.sort(newNs); Arrays.sort(oldBytes); Arrays.sort(newBytes);
            System.out.printf("Firelight selection A/B size=%d old=%dns/tick new=%dns/tick old=%dB/tick new=%dB/tick oldScratch=%dB newScratch=%dB%n",
                    size, oldNs[3] / iterations, newNs[3] / iterations, oldBytes[3] / iterations, newBytes[3] / iterations,
                    scratchBytes(legacy), scratchBytes(candidate));
        }
    }
    /** Primitive payload retained by scratch arrays, excluding array/object headers. */
    private static long scratchBytes(Object target) throws Exception {
        long result = 0;
        for (Field field : target.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            Object value = field.get(target);
            if (value instanceof int[] ints) result += ints.length * (long) Integer.BYTES;
            if (value instanceof long[] longs) result += longs.length * (long) Long.BYTES;
        }
        return result;
    }
    private static void measure(Object target, int[][] input, int iterations, ThreadMXBean bean,
                                long[] nanos, long[] bytes, int round) {
        long thread = Thread.currentThread().threadId();
        long beforeBytes = bean.getThreadAllocatedBytes(thread), before = System.nanoTime();
        if (target instanceof FlameTracker.Candidates candidate) {
            for (int i = 0; i < iterations; i++) run(candidate, input);
        } else {
            for (int i = 0; i < iterations; i++) run((LegacyCandidates) target, input);
        }
        nanos[round] = System.nanoTime() - before;
        bytes[round] = bean.getThreadAllocatedBytes(thread) - beforeBytes;
    }
    private static void run(FlameTracker.Candidates target, int[][] input) {
        target.reset(); add(target, input); consume(target.nearest(64));
    }
    private static void run(LegacyCandidates target, int[][] input) {
        target.reset(); add(target, input); consume(target.nearest(64));
    }
    private static void consume(FlameTracker.Snapshot snapshot) {
        long value = snapshot.count();
        for (int i = 0; i < snapshot.count(); i++) value += snapshot.x()[i] + snapshot.y()[i] + snapshot.z()[i]
                + snapshot.levels()[i] + snapshot.seeds()[i] + snapshot.kinds()[i].ordinal();
        consumed = value;
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    /* Previous released full-sort selection, retained only as the A/B oracle.
     * Adapted from Firelight 1.0.3 by comedy01 (MIT, 2026).
     * https://github.com/comedy01/Firelight; META-INF/LICENSE-FIRELIGHT.txt.
     */
    private static final class LegacyCandidates {
        private int size;
        private int[] x = new int[64], y = new int[64], z = new int[64], packed = new int[64], seed = new int[64];
        private long[] order = new long[64], sorted = new long[64];
        void reset() { size = 0; }
        void add(int px, int py, int pz, int flame, int distance) {
            int flameSeed = Flicker.seed(px, py, pz);
            if (size == x.length) {
                int grown = size * 2;
                x = Arrays.copyOf(x, grown); y = Arrays.copyOf(y, grown); z = Arrays.copyOf(z, grown);
                packed = Arrays.copyOf(packed, grown); seed = Arrays.copyOf(seed, grown); order = Arrays.copyOf(order, grown);
            }
            x[size] = px; y[size] = py; z[size] = pz; packed[size] = flame; seed[size] = flameSeed;
            order[size] = (long) distance << 32 | (long) size;
            size++;
        }
        FlameTracker.Snapshot nearest(int limit) {
            if (size == 0) return FlameTracker.Snapshot.EMPTY;
            if (sorted.length < size) sorted = new long[order.length];
            System.arraycopy(order, 0, sorted, 0, size);
            Arrays.sort(sorted, 0, size);
            int count = Math.min(limit, size);
            int[] xs = new int[count], ys = new int[count], zs = new int[count], levels = new int[count], seeds = new int[count];
            FlameKind[] kinds = new FlameKind[count];
            for (int i = 0; i < count; i++) {
                int c = (int) sorted[i];
                xs[i] = x[c]; ys[i] = y[c]; zs[i] = z[c];
                kinds[i] = FlameKind.byIndex(packed[c] >>> 12 & 15);
                levels[i] = packed[c] >>> 16 & 15; seeds[i] = seed[c];
            }
            return new FlameTracker.Snapshot(count, xs, ys, zs, levels, seeds, kinds);
        }
    }
}
