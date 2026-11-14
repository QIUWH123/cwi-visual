/* Adapted from Firelight 1.0.3 by comedy01 (MIT, 2026).
 * Source: https://github.com/comedy01/Firelight
 * Full upstream notice: META-INF/LICENSE-FIRELIGHT.txt.
 */
package net.quiwh.cwivisuals.firelight;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

public final class RawUniforms {
   private static final Int2ObjectOpenHashMap<int[]> PROGRAMS = new Int2ObjectOpenHashMap();
   private static final ClassValue<MethodHandle> HANDLES = new ClassValue<MethodHandle>() {
      protected MethodHandle computeValue(Class<?> type) {
         try {
            Method method = type.getMethod("handle");
            return method.getReturnType() != int.class
               ? null
               : MethodHandles.publicLookup().unreflect(method).asType(MethodType.methodType(int.class, Object.class));
         } catch (RuntimeException | ReflectiveOperationException var3) {
            return null;
         }
      }
   };
   private static volatile boolean changed;
   private static volatile boolean any;
   private static int frame;
   private static int updatedFrame = -1;
   private static FloatBuffer info;
   private static FloatBuffer sources;
   private static long uploads;
   private static long programQueries;

   private RawUniforms() {
   }

   public static long uploads() {
      return uploads;
   }

   public static long programQueries() {
      return programQueries;
   }

   public static void shadersChanged() {
      changed = true;
      any = true;
   }

   public static void clear() {
      PROGRAMS.clear();
      updatedFrame = -1;
      info = null;
      sources = null;
   }

   public static void forget(int program) {
      if (any) {
         PROGRAMS.remove(program);
      }
   }

   public static void newFrame() {
      frame++;
   }

   public static void onBind(Object glProgram) {
      if (any) {
         MethodHandle handle = HANDLES.get(glProgram.getClass());
         if (handle != null) {
            int program;
            try {
               program = (int)handle.invokeExact((Object)glProgram);
            } catch (Throwable var4) {
               program = 0;
            }

            if (program != 0) {
               onUse(program);
               return;
            }
         }

         programQueries++;
         onUse(GL11.glGetInteger(35725));
      }
   }

   public static void onUse(int program) {
      if (any && program != 0) {
         try {
            if (changed) {
               PROGRAMS.clear();
               changed = false;
            }

            int[] entry = (int[])PROGRAMS.get(program);
            if (entry == null) {
               entry = new int[]{GL20.glGetUniformLocation(program, "CwiFirelightInfo"), GL20.glGetUniformLocation(program, "CwiFirelightSources"), -1};
               if (entry[0] >= 0 && entry[1] >= 0 && !matches(program)) {
                  entry[0] = -1;
               }

               PROGRAMS.put(program, entry);
            }

            if (entry[0] < 0 || entry[1] < 0 || entry[2] == frame) {
               return;
            }

            if (updatedFrame != frame) {
               updatedFrame = frame;
               FlickerFrame.update(Cameras.position(Minecraft.getInstance()));
               FloatBuffer all = FlickerFrame.bytes().asFloatBuffer();
               info = all.duplicate().position(0).limit(4).slice();
               sources = all.duplicate().position(4).limit(4 + FlickerFrame.count() * 4).slice();
            }

            GL20.glUniform4fv(entry[0], info);
            if (sources.hasRemaining()) {
               GL20.glUniform4fv(entry[1], sources);
            }

            entry[2] = frame;
            uploads++;
         } catch (RuntimeException var3) {
            FirelightClient.fail(var3);
         }
      }
   }

   private static boolean matches(int program) {
      int count = GL20.glGetProgrami(program, 35718);
      int found = 0;
      MemoryStack stack = MemoryStack.stackPush();

      boolean var11;
      label91: {
         label92: {
            try {
               IntBuffer size = stack.mallocInt(1);
               IntBuffer type = stack.mallocInt(1);

               for (int i = 0; i < count && found < 2; i++) {
                  String name = GL20.glGetActiveUniform(program, i, size, type);
                  if (name.equals("CwiFirelightInfo")) {
                     if (type.get(0) != 35666 || size.get(0) != 1) {
                        var11 = false;
                        break label91;
                     }

                     found++;
                  } else if (name.equals("CwiFirelightSources") || name.equals("CwiFirelightSources[0]")) {
                     if (type.get(0) != 35666 || size.get(0) != 64) {
                        var11 = false;
                        break label92;
                     }

                     found++;
                  }
               }
            } catch (Throwable var10) {
               if (stack != null) {
                  try {
                     stack.close();
                  } catch (Throwable var9) {
                     var10.addSuppressed(var9);
                  }
               }

               throw var10;
            }

            if (stack != null) {
               stack.close();
            }

            return found == 2;
         }

         if (stack != null) {
            stack.close();
         }

         return var11;
      }

      if (stack != null) {
         stack.close();
      }

      return var11;
   }
}
