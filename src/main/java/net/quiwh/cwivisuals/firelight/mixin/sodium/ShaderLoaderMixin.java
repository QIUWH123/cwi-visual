/* Adapted from Firelight 1.0.3 by comedy01 (MIT, 2026).
 * Source: https://github.com/comedy01/Firelight
 * Full upstream notice: META-INF/LICENSE-FIRELIGHT.txt.
 */
package net.quiwh.cwivisuals.firelight.mixin.sodium;

import net.quiwh.cwivisuals.firelight.ShaderPatches;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   targets = {"net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader"},
   remap = false
)
abstract class ShaderLoaderMixin {
   @Inject(
      method = {"getShaderSource(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;"},
      at = {@At("RETURN")},
      cancellable = true,
      remap = false,
      require = 1
   )
   private static void cwivisuals$source(CallbackInfoReturnable<String> cir) {
      cir.setReturnValue(ShaderPatches.patch("sodium", (String)cir.getReturnValue()));
   }
}
