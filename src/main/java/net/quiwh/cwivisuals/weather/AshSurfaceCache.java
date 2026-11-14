package net.quiwh.cwivisuals.weather;

import java.util.LinkedHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Short-lived visual samples of exact surface columns; never requests missing chunks. */
final class AshSurfaceCache {
    private final int capacity;
    private final int lifetime;
    private final LinkedHashMap<Long, Entry> entries = new LinkedHashMap<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private ClientLevel level;
    private int lastTick = Integer.MIN_VALUE;

    AshSurfaceCache() { this(512, 5); }

    AshSurfaceCache(int capacity, int lifetime) {
        if (capacity < 1 || lifetime < 1) throw new IllegalArgumentException("Positive cache bounds required");
        this.capacity = capacity;
        this.lifetime = lifetime;
    }

    record Surface(int groundY, VoxelShape shape) {}
    private record Entry(int tick, Surface surface) {}

    Surface sample(ClientLevel currentLevel, int x, int z, int tick) {
        if (currentLevel != level || tick < lastTick) {
            clear();
            level = currentLevel;
        }
        lastTick = tick;
        long key = (long) x << 32 | z & 0xFFFFFFFFL;
        cursor.set(x, currentLevel.getMinBuildHeight(), z);
        // Recheck residency on hits: a cached roof must never make us request an unloaded chunk.
        if (!currentLevel.isLoaded(cursor)) {
            entries.remove(key);
            return null;
        }
        Entry previous = entries.get(key);
        if (previous != null && (long) tick - previous.tick < lifetime) return previous.surface;

        Surface surface = read(currentLevel, x, z);
        entries.put(key, new Entry(tick, surface));
        if (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
        return surface;
    }

    private Surface read(ClientLevel currentLevel, int x, int z) {
        int top = currentLevel.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        if (top <= currentLevel.getMinBuildHeight() || top >= currentLevel.getMaxBuildHeight()) return null;
        cursor.set(x, top - 1, z);
        var state = currentLevel.getBlockState(cursor);
        if (!state.getFluidState().isEmpty()) return null;
        VoxelShape shape = state.getCollisionShape(currentLevel, cursor);
        return shape.isEmpty() ? null : new Surface(top - 1, shape);
    }

    void clear() {
        entries.clear();
        level = null;
        lastTick = Integer.MIN_VALUE;
    }
}
