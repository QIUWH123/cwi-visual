/* Adapted from Firelight 1.0.3 by comedy01 (MIT, 2026).
 * Source: https://github.com/comedy01/Firelight
 * Full upstream notice: META-INF/LICENSE-FIRELIGHT.txt.
 */
package net.quiwh.cwivisuals.firelight;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Client-thread settings and lifecycle bridge; configuration belongs to CWI Visual. */
public final class FirelightClient {
    private static final Logger LOGGER = LogManager.getLogger("cwi_visual.firelight");
    private static volatile Settings settings = new Settings(false, 0, Set.of());
    private static volatile boolean failed;

    private FirelightClient() {}

    public static void configure(boolean enabled, float strength, Set<String> sourceIds) {
        Set<ResourceLocation> sources = new HashSet<>();
        if (sourceIds != null) {
            for (String id : sourceIds) {
                if (id == null) continue;
                ResourceLocation parsed = ResourceLocation.tryParse(id.trim());
                if (parsed != null && BuiltInRegistries.BLOCK.containsKey(parsed)) sources.add(parsed);
            }
        }
        float bounded = Float.isFinite(strength) ? Math.max(0, Math.min(3, strength)) : 0;
        Settings next = new Settings(enabled, bounded, Set.copyOf(sources));
        if (!next.equals(settings)) {
            settings = next;
            reset();
        }
    }

    public static boolean active() {
        Settings current = settings;
        return !failed && current.enabled() && current.strength() > 0 && !current.sourceIds().isEmpty()
                && !Compat.upstreamPresent() && !Compat.shaderPackInUse();
    }

    static Settings settings() { return settings; }

    /** Clears world/frame/program data while retaining the configured preferences. */
    public static void reset() {
        Flames.clear();
        FlameTracker.forget();
        FlickerFrame.clear();
        RawUniforms.clear();
        failed = false;
    }

    public static void fail(Throwable error) {
        if (!failed) {
            failed = true;
            FlameTracker.forget();
            FlickerFrame.clear();
            LOGGER.error("CWI firelight stopped for this session after a rendering error", error);
        }
    }

    record Settings(boolean enabled, float strength, Set<ResourceLocation> sourceIds) {}
}
