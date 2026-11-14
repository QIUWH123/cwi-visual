/* Adapted from Firelight 1.0.3 by comedy01 (MIT, 2026).
 * Source: https://github.com/comedy01/Firelight
 * Full upstream notice: META-INF/LICENSE-FIRELIGHT.txt.
 */
package net.quiwh.cwivisuals.firelight;

import net.quiwh.cwivisuals.firelight.Flicker;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import net.minecraft.world.phys.Vec3;

public final class FlickerFrame {
   public static final int BYTES = 1040;
   private static final long START = System.nanoTime();
   private static final float[] FRAME = new float[260];
   private static final ByteBuffer BYTES_BUFFER = ByteBuffer.allocateDirect(1040).order(ByteOrder.nativeOrder());
   private static final FloatBuffer FLOATS_BUFFER = BYTES_BUFFER.asFloatBuffer();
   private static final float[] ZEROS = new float[260];
   private static double cameraX;
   private static double cameraY;
   private static double cameraZ;
   private static int sent;

   private FlickerFrame() {
   }

   public static float time() {
      double seconds = (double)(System.nanoTime() - START) / 1.0E9;
      return (float)(seconds % 600.0);
   }

   public static void update(Vec3 camera) {
      float[] frame = FRAME;
      float time = time();
      float strength = FirelightClient.settings().strength();
      frame[0] = time;
      frame[1] = strength;
      frame[3] = 1234.5F;
      FlameTracker.Snapshot snapshot = FlameTracker.snapshot();
      int count = FirelightClient.active() && camera != null ? snapshot.count() : 0;
      frame[2] = (float)count;
      sent = count;
      if (camera != null) {
         cameraX = camera.x;
         cameraY = camera.y;
         cameraZ = camera.z;
      }

      for (int i = 0; i < count; i++) {
         int base = 4 + i * 4;
         frame[base] = (float)((double)snapshot.x()[i] + 0.5 - cameraX);
         frame[base + 1] = (float)((double)snapshot.y()[i] + 0.5 - cameraY);
         frame[base + 2] = (float)((double)snapshot.z()[i] + 0.5 - cameraZ);
         frame[base + 3] = Flicker.encode(snapshot.levels()[i], Flicker.value(snapshot.kinds()[i], snapshot.seeds()[i], time) * strength);
      }

      FloatBuffer out = FLOATS_BUFFER;
      int used = 4 + sent * 4;
      out.clear();
      out.put(frame, 0, used);
      out.put(ZEROS, 0, 260 - used);
      out.put(2, (float)sent);
   }

   public static ByteBuffer bytes() {
      BYTES_BUFFER.clear();
      return BYTES_BUFFER;
   }

   public static void writeTo(ByteBuffer target, int offset) {
      ByteBuffer out = target.duplicate();
      out.clear().position(offset);
      out.put(BYTES_BUFFER.duplicate().clear());
   }

   public static int flicker(int packed, double x, double y, double z) {
      int block = packed & 0xFF;
      if (FirelightClient.active() && block != 0 && FRAME[2] != 0.0F) {
         float factor = Flicker.factor(FRAME, x - cameraX, y - cameraY, z - cameraZ, (float)block / 16.0F);
         int flickered = Math.max(0, Math.min(240, Math.round((float)block * factor)));
         return packed & -256 | flickered;
      } else {
         return packed;
      }
   }

   public static void clear() {
      java.util.Arrays.fill(FRAME, 0);
      FLOATS_BUFFER.clear();
      FLOATS_BUFFER.put(ZEROS);
      sent = 0;
      cameraX = cameraY = cameraZ = 0;
   }

   public static int count() {
      return sent;
   }
}
