/* Adapted from Firelight 1.0.3 by comedy01 (MIT, 2026).
 * Source: https://github.com/comedy01/Firelight
 * Full upstream notice: META-INF/LICENSE-FIRELIGHT.txt.
 */
package net.quiwh.cwivisuals.firelight;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.neoforged.fml.ModList;

final class Compat {
    private static boolean looked;
    private static MethodHandle shaderPackInUse;

    private Compat() {}

    static boolean upstreamPresent() {
        ModList mods = ModList.get();
        return mods != null && mods.isLoaded("firelight");
    }

    static boolean shaderPackInUse() {
        if (!looked) {
            looked = true;
            for (String name : new String[]{"net.irisshaders.iris.api.v0.IrisApi", "net.coderbot.iris.api.v0.IrisApi"}) {
                try {
                    Class<?> api = Class.forName(name);
                    Object instance = api.getMethod("getInstance").invoke(null);
                    shaderPackInUse = MethodHandles.publicLookup().unreflect(api.getMethod("isShaderPackInUse"))
                            .bindTo(instance).asType(MethodType.methodType(boolean.class));
                    break;
                } catch (ReflectiveOperationException | LinkageError | RuntimeException absent) {
                    shaderPackInUse = null;
                }
            }
        }
        if (shaderPackInUse == null) return false;
        try {
            return (boolean) shaderPackInUse.invokeExact();
        } catch (Throwable unavailable) {
            // An installed shader API that stopped responding is treated as active.
            return true;
        }
    }
}
