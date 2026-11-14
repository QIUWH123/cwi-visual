/* Adapted from Firelight 1.0.3 by comedy01 (MIT, 2026).
 * Source: https://github.com/comedy01/Firelight
 * Full upstream notice: META-INF/LICENSE-FIRELIGHT.txt.
 */
package net.quiwh.cwivisuals.firelight.mixin;

import net.quiwh.cwivisuals.firelight.FirelightClient;
import net.quiwh.cwivisuals.firelight.FlameTracker;
import net.quiwh.cwivisuals.firelight.RawUniforms;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Minecraft.class})
abstract class MinecraftTickMixin {
   @Inject(
      method = {"runTick"},
      at = {@At("HEAD")}
   )
   private void cwivisuals$frame(boolean advanceGameTime, CallbackInfo ci) {
      RawUniforms.newFrame();
   }

   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void cwivisuals$tick(CallbackInfo ci) {
      try {
         FlameTracker.tick((Minecraft)(Object)this);
      } catch (RuntimeException var3) {
         FirelightClient.fail(var3);
      }
   }
}
