/* Adapted from Firelight 1.0.3 by comedy01 (MIT, 2026).
 * Source: https://github.com/comedy01/Firelight
 * Full upstream notice: META-INF/LICENSE-FIRELIGHT.txt.
 */
package net.quiwh.cwivisuals.firelight;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** The allowlist alone selects blocks; classes only select their flicker rhythm. */
final class Flames {
    private static final ConcurrentHashMap<BlockState, Integer> CODES = new ConcurrentHashMap<>();

    private Flames() {}
    static void clear() { CODES.clear(); }
    static boolean maybeFlame(BlockState state) { return code(state) != 0; }
    static FlameKind kind(BlockState state) {
        int code = code(state);
        return code == 0 ? null : FlameKind.byIndex(code - 1);
    }
    private static int code(BlockState state) {
        return CODES.computeIfAbsent(state, key -> {
            FlameKind kind = computeKind(key);
            return kind == null ? 0 : kind.ordinal() + 1;
        });
    }
    private static FlameKind computeKind(BlockState state) {
        Block block = state.getBlock();
        if (!FirelightClient.settings().sourceIds().contains(BuiltInRegistries.BLOCK.getKey(block))) return null;
        if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)) return null;
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) return null;
        if (state.getLightEmission() <= 0) return null;
        if (block == Blocks.SOUL_TORCH || block == Blocks.SOUL_WALL_TORCH || block == Blocks.SOUL_LANTERN
                || block == Blocks.SOUL_CAMPFIRE || block == Blocks.SOUL_FIRE) return FlameKind.SOUL;
        if (block instanceof LanternBlock || block == Blocks.JACK_O_LANTERN) return FlameKind.LANTERN;
        if (block instanceof AbstractCandleBlock) return FlameKind.CANDLE;
        if (block instanceof CampfireBlock) return FlameKind.CAMPFIRE;
        if (block instanceof AbstractFurnaceBlock) return FlameKind.FURNACE;
        if (block instanceof BaseFireBlock) return FlameKind.FIRE;
        return FlameKind.TORCH;
    }
}
