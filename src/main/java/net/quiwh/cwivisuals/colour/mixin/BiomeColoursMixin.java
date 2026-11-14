package net.quiwh.cwivisuals.colour.mixin;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.quiwh.cwivisuals.colour.WorldColours;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shared vanilla/Sodium entry points preserve upstream biome blending and colour providers. */
@Mixin(BiomeColors.class)
public abstract class BiomeColoursMixin {
    @Inject(method = "getAverageGrassColor", at = @At("RETURN"), cancellable = true)
    private static void cwi$grass(BlockAndTintGetter view, BlockPos position,
                                  CallbackInfoReturnable<Integer> result) {
        int original = result.getReturnValueI();
        int colour = WorldColours.grass(original);
        if (colour != original) result.setReturnValue(colour);
    }

    @Inject(method = "getAverageFoliageColor", at = @At("RETURN"), cancellable = true)
    private static void cwi$foliage(BlockAndTintGetter view, BlockPos position,
                                    CallbackInfoReturnable<Integer> result) {
        int original = result.getReturnValueI();
        int colour = WorldColours.foliage(original);
        if (colour != original) result.setReturnValue(colour);
    }

    @Inject(method = "getAverageWaterColor", at = @At("RETURN"), cancellable = true)
    private static void cwi$water(BlockAndTintGetter view, BlockPos position,
                                  CallbackInfoReturnable<Integer> result) {
        int original = result.getReturnValueI();
        int colour = WorldColours.water(original);
        if (colour != original) result.setReturnValue(colour);
    }
}
