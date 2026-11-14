package net.quiwh.cwivisuals.weather;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Counts real client-level surface reads without constructing chunks or starting Minecraft. */
public final class AshSurfaceCacheFixture {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class GroundLevel extends ClientLevel {
        int heightReads;
        int blockReads;
        int loadedReads;
        int height;
        boolean loaded;
        BlockState ground;
        private GroundLevel() { super(null, null, null, null, 0, 0, null, null, false, 0); }
        @Override public boolean isLoaded(BlockPos pos) { loadedReads++; return loaded; }
        @Override public int getHeight(Heightmap.Types type, int x, int z) {
            if (type != Heightmap.Types.MOTION_BLOCKING) throw new AssertionError("Wrong surface heightmap");
            heightReads++;
            return height;
        }
        @Override public int getMinBuildHeight() { return -64; }
        @Override public int getMaxBuildHeight() { return 320; }
        @Override public BlockState getBlockState(BlockPos pos) { blockReads++; return ground; }
    }

    private static GroundLevel level(sun.misc.Unsafe unsafe) throws Exception {
        GroundLevel level = (GroundLevel) unsafe.allocateInstance(GroundLevel.class);
        level.height = 81;
        level.loaded = true;
        level.ground = Blocks.STONE.defaultBlockState();
        return level;
    }

    public static void main(String[] args) throws Exception {
        Class<?> type;
        try { type = Class.forName("net.quiwh.cwivisuals.weather.AshSurfaceCache"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Missing bounded ash surface cache", missing); }
        Constructor<?> constructor = type.getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true);
        Method sample = type.getDeclaredMethod("sample", ClientLevel.class, int.class, int.class, int.class);
        sample.setAccessible(true);
        Method clear = type.getDeclaredMethod("clear");
        clear.setAccessible(true);
        MinimalNativeBootstrap.initialize();
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        GroundLevel level = level(unsafe);
        Object cache = constructor.newInstance(2, 5);

        Object first = sample.invoke(cache, level, -17, 33, 10);
        check(first != null, "Dry solid surface is eligible");
        Method groundY = first.getClass().getDeclaredMethod("groundY");
        groundY.setAccessible(true);
        check((int) groundY.invoke(first) == 80, "Heightmap top becomes ground block Y");
        for (int tick = 10; tick < 15; tick++) {
            check(sample.invoke(cache, level, -17, 33, tick) == first, "Exact-column result reused before expiry");
        }
        check(level.heightReads == 1 && level.blockReads == 1, "Five repeated columns only read terrain once");
        check(level.loadedReads == 6, "Loaded status is checked even on cache hits");
        level.height = 90;
        Object renewed = sample.invoke(cache, level, -17, 33, 15);
        check(renewed != first && (int) groundY.invoke(renewed) == 89, "Surface edits become visible at expiry");
        check(level.heightReads == 2 && level.blockReads == 2, "Expiry performs one fresh sample");

        level.loaded = false;
        check(sample.invoke(cache, level, -17, 33, 16) == null, "Unloaded column never uses cached terrain");
        check(level.heightReads == 2, "Unloaded column does not request height or chunks");
        level.loaded = true;
        level.height = 91;
        Object reloaded = sample.invoke(cache, level, -17, 33, 17);
        check((int) groundY.invoke(reloaded) == 90, "Reload invalidates previous cached surface");

        GroundLevel other = level(unsafe);
        other.height = 101;
        Object changedWorld = sample.invoke(cache, other, -17, 33, 18);
        check((int) groundY.invoke(changedWorld) == 100, "New level cannot inherit old surface");
        check(other.heightReads == 1, "Level switch samples destination terrain");

        other.ground = Blocks.WATER.defaultBlockState();
        check(sample.invoke(cache, other, 1, 1, 20) == null, "Fluid surface is rejected");
        int invalidReads = other.heightReads;
        check(sample.invoke(cache, other, 1, 1, 21) == null && other.heightReads == invalidReads,
                "Rejected fluid surface is cached too");
        other.ground = Blocks.AIR.defaultBlockState();
        check(sample.invoke(cache, other, 2, 2, 21) == null, "Empty collision shape is rejected");
        other.ground = Blocks.STONE.defaultBlockState();
        int beforeEviction = other.heightReads;
        sample.invoke(cache, other, 3, 3, 22);
        sample.invoke(cache, other, 4, 4, 22);
        sample.invoke(cache, other, 1, 1, 22);
        check(other.heightReads == beforeEviction + 3, "Capacity bound evicts older columns");

        other.height = -64;
        int beforeBoundaryBlock = other.blockReads;
        check(sample.invoke(cache, other, 5, 5, 23) == null, "Void bottom is rejected");
        other.height = 320;
        check(sample.invoke(cache, other, 6, 6, 23) == null, "Build ceiling is rejected");
        check(other.blockReads == beforeBoundaryBlock, "Invalid height skips block/shape reads");
        other.height = 81;
        clear.invoke(cache);
        int beforeClear = other.heightReads;
        sample.invoke(cache, other, 1, 1, 24);
        check(other.heightReads == beforeClear + 1, "Explicit cleanup releases cached world data");
        sample.invoke(cache, other, 1, 1, 1);
        check(other.heightReads == beforeClear + 2, "A restarted tick clock cannot retain old entries");
        System.out.println("AshSurfaceCacheFixture PASS: " + checks
                + " checks; exact-column reuse, TTL, loading, level switches, invalid surfaces and bounded memory.");
    }
}
