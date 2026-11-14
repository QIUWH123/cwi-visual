/* Adapted from Firelight 1.0.3 by comedy01 (MIT, 2026).
 * Source: https://github.com/comedy01/Firelight
 * Full upstream notice: META-INF/LICENSE-FIRELIGHT.txt.
 */
package net.quiwh.cwivisuals.firelight;

public final class Flicker {
   public static final int PERIOD = 600;
   public static final int MAX_SOURCES = 64;
   public static final int FLOATS = 260;
   public static final float MARK = 1234.5F;
   private static final int[][] RATES = new int[][]{{5, 11, 23}, {3, 7, 13}, {4, 9, 17}, {6, 13, 29}, {4, 9, 19}, {7, 15, 31}, {1, 3, 7}};
   private static final float[] AMPLITUDES = new float[]{0.11F, 0.06F, 0.06F, 0.14F, 0.15F, 0.2F, 0.05F};
   private static final float[] DIPS = new float[]{1.4F, 0.8F, 1.0F, 1.6F, 1.2F, 1.0F, 0.6F};

   private Flicker() {
   }

   public static int hash(int x) {
      x ^= x >>> 16;
      x *= 2146121005;
      x ^= x >>> 15;
      x *= -2073254261;
      return x ^ x >>> 16;
   }

   public static int seed(int x, int y, int z) {
      return hash(x * 73856093 ^ y * 19349663 ^ z * 83492791) & 1023;
   }

   static float lattice(int seed, int i) {
      return (float)(hash(i * -1640531527 ^ seed) & 65535) / 65535.0F * 2.0F - 1.0F;
   }

   static float noise(int seed, float x, int period) {
      float cell = (float)Math.floor((double)x);
      float f = x - cell;
      int i = Math.floorMod((int)cell, period);
      int j = (i + 1) % period;
      float s = f * f * (3.0F - 2.0F * f);
      float a = lattice(seed, i);
      return a + (lattice(seed, j) - a) * s;
   }

   private static float smoothstep(float edge0, float edge1, float x) {
      float t = Math.max(0.0F, Math.min(1.0F, (x - edge0) / (edge1 - edge0)));
      return t * t * (3.0F - 2.0F * t);
   }

   public static float value(FlameKind kind, int seed, float time) {
      int k = kind.ordinal();
      int[] rates = RATES[k];
      float n = 0.5F * noise(seed, time * (float)rates[0], rates[0] * 600)
         + 0.3F * noise(seed + 101, time * (float)rates[1], rates[1] * 600)
         + 0.2F * noise(seed + 202, time * (float)rates[2], rates[2] * 600);
      float dip = smoothstep(0.35F, 0.9F, noise(seed + 303, time, 600));
      return AMPLITUDES[k] * (n - DIPS[k] * dip);
   }

   public static float encode(int level, float value) {
      return (float)(level * 8 + 4) + Math.max(-3.99F, Math.min(3.99F, value));
   }

   public static float factor(float[] frame, double x, double y, double z, float blockLevel) {
      int count = Math.min((int)frame[2], 64);
      if (count > 0 && !(blockLevel <= 0.5F)) {
         float weights = 0.0F;
         float sum = 0.0F;
         float best = 0.0F;

         for (int i = 0; i < count; i++) {
            int base = 4 + i * 4;
            float encoded = frame[base + 3];
            float level = (float)Math.floor((double)(encoded / 8.0F));
            float distance = (float)(Math.abs(x - (double)frame[base]) + Math.abs(y - (double)frame[base + 1]) + Math.abs(z - (double)frame[base + 2]));
            float reach = level - distance;
            if (!(reach <= 0.0F)) {
               float weight = reach * reach;
               weights += weight;
               sum += weight * (encoded - level * 8.0F - 4.0F);
               best = Math.max(best, reach);
            }
         }

         if (weights == 0.0F) {
            return 1.0F;
         } else {
            float share = Math.min(1.0F, Math.max(0.0F, (best + 3.5F - blockLevel) * 0.5F));
            return Math.max(0.0F, 1.0F + sum / weights * Math.min(1.0F, best / blockLevel) * share);
         }
      } else {
         return 1.0F;
      }
   }
}
