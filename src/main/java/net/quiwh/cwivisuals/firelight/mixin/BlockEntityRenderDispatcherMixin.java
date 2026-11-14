/* Adapted from Firelight 1.0.3 by comedy01 (MIT, 2026).
 * Source: https://github.com/comedy01/Firelight
 * Full upstream notice: META-INF/LICENSE-FIRELIGHT.txt.
 */
package net.quiwh.cwivisuals.firelight.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.quiwh.cwivisuals.firelight.FlickerFrame;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({BlockEntityRenderDispatcher.class})
abstract class BlockEntityRenderDispatcherMixin {
   @ModifyExpressionValue(
      method = {"setupAndRender"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I"
      )}
   )
   private static int cwivisuals$light(int light, @Local(argsOnly = true) BlockEntity blockEntity) {
      BlockPos pos = blockEntity.getBlockPos();
      return FlickerFrame.flicker(light, (double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5);
   }
}
