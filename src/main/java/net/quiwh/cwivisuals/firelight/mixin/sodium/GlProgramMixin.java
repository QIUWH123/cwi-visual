/* Adapted from Firelight 1.0.3 by comedy01 (MIT, 2026).
 * Source: https://github.com/comedy01/Firelight
 * Full upstream notice: META-INF/LICENSE-FIRELIGHT.txt.
 */
package net.quiwh.cwivisuals.firelight.mixin.sodium;

import net.quiwh.cwivisuals.firelight.RawUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   targets = {"net.caffeinemc.mods.sodium.client.gl.shader.GlProgram"},
   remap = false
)
abstract class GlProgramMixin {
   @Inject(
      method = {"bind()V"},
      at = {@At("TAIL")},
      remap = false,
      require = 1
   )
   private void cwivisuals$bind(CallbackInfo ci) {
      RawUniforms.onBind(this);
   }
}
