/* Adapted from Firelight 1.0.3 by comedy01 (MIT, 2026).
 * Source: https://github.com/comedy01/Firelight
 * Full upstream notice: META-INF/LICENSE-FIRELIGHT.txt.
 */
package net.quiwh.cwivisuals.firelight;

public enum FlameKind {
   TORCH,
   SOUL,
   LANTERN,
   CANDLE,
   CAMPFIRE,
   FIRE,
   FURNACE;

   private static final FlameKind[] VALUES = values();

   public static FlameKind byIndex(int index) {
      return index >= 0 && index < VALUES.length ? VALUES[index] : TORCH;
   }
}
