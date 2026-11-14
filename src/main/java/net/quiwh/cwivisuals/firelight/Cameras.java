/* Adapted from Firelight 1.0.3 by comedy01 (MIT, 2026).
 * Source: https://github.com/comedy01/Firelight
 * Full upstream notice: META-INF/LICENSE-FIRELIGHT.txt.
 */
package net.quiwh.cwivisuals.firelight;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class Cameras {
   private Cameras() {
   }

   public static Vec3 position(Minecraft mc) {
      return mc.gameRenderer.getMainCamera().getPosition();
   }
}
