/* Adapted from Firelight 1.0.3 by comedy01 (MIT, 2026).
 * Source: https://github.com/comedy01/Firelight
 * Full upstream notice: META-INF/LICENSE-FIRELIGHT.txt.
 */
package net.quiwh.cwivisuals.firelight.mixin;

import net.quiwh.cwivisuals.firelight.FirelightClient;
import net.quiwh.cwivisuals.firelight.FlickerFrame;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {EntityRenderer.class},
   priority = 1100
)
abstract class EntityRendererMixin {
   @Inject(
      method = {"getPackedLightCoords"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void cwivisuals$light(Entity entity, float partialTick, CallbackInfoReturnable<Integer> cir) {
      if (FirelightClient.active()) {
         int packed = cir.getReturnValueI();
         int flickered = FlickerFrame.flicker(packed, entity.getX(), entity.getY() + (double)entity.getBbHeight() * 0.5, entity.getZ());
         if (flickered != packed) {
            cir.setReturnValue(flickered);
         }
      }
   }
}
