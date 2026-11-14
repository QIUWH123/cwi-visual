/* Adapted from Firelight 1.0.3 by comedy01 (MIT, 2026).
 * Source: https://github.com/comedy01/Firelight
 * Full upstream notice: META-INF/LICENSE-FIRELIGHT.txt.
 */
package net.quiwh.cwivisuals.firelight.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import net.quiwh.cwivisuals.firelight.RawUniforms;
import net.quiwh.cwivisuals.firelight.ShaderPatches;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {GlStateManager.class},
   remap = false
)
abstract class GlStateManagerMixin {
   @ModifyVariable(
      method = {"glShaderSource"},
      at = @At("HEAD"),
      argsOnly = true,
      remap = false
   )
   private static List<String> cwivisuals$source(List<String> source) {
      return List.of(ShaderPatches.patch("vanilla", String.join("", source)));
   }

   @Inject(
      method = {"_glUseProgram"},
      at = {@At("TAIL")},
      remap = false
   )
   private static void cwivisuals$use(int program, CallbackInfo ci) {
      RawUniforms.onUse(program);
   }

   @Inject(
      method = {"glCreateProgram"},
      at = {@At("RETURN")},
      remap = false
   )
   private static void cwivisuals$create(CallbackInfoReturnable<Integer> cir) {
      RawUniforms.forget(cir.getReturnValueI());
   }

   @Inject(
      method = {"glLinkProgram", "glDeleteProgram"},
      at = {@At("TAIL")},
      remap = false
   )
   private static void cwivisuals$forget(int program, CallbackInfo ci) {
      RawUniforms.forget(program);
   }
}
