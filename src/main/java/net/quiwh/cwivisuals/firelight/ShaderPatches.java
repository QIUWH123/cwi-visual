/* Adapted from Firelight 1.0.3 by comedy01 (MIT, 2026).
 * Source: https://github.com/comedy01/Firelight
 * Full upstream notice: META-INF/LICENSE-FIRELIGHT.txt.
 */
package net.quiwh.cwivisuals.firelight;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Patches only the inspected Minecraft 1.21.1 and Sodium terrain light samples. */
public final class ShaderPatches {
    private static final Logger LOGGER = LogManager.getLogger("cwi_visual.firelight");
    private static final String HEADER = "uniform vec4 CwiFirelightInfo;\nuniform vec4 CwiFirelightSources[64];\n#define CWI_FIRELIGHT_GLOBALS\n";
    private static final String VANILLA = "vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);";
    private static final String SODIUM = "v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);";
    private static volatile String code;

    private ShaderPatches() {}

    public static String patch(String name, String source) {
        if (source == null || source.contains("CWI_FIRELIGHT_FLICKER_GLSL")
                || Compat.upstreamPresent() || Compat.shaderPackInUse()) return source;
        String find;
        String replacement;
        if (source.contains("vec3 pos = ") && source.contains(VANILLA)) {
            find = VANILLA;
            replacement = "vertexColor = Color * cwi_firelight_sample(Sampler2, UV2, pos);";
        } else if (source.contains("vec3 position = ") && source.contains(SODIUM)) {
            find = SODIUM;
            replacement = "v_Color = _vert_color * texture(u_LightTex, cwi_firelight_coord(_vert_tex_light_coord, position));";
        } else {
            return source;
        }
        int main = source.indexOf("void main()");
        if (main < 0) return source;
        try {
            String patched = (source.substring(0, main) + HEADER + code() + "\n" + source.substring(main)).replace(find, replacement);
            RawUniforms.shadersChanged();
            return patched;
        } catch (IOException | RuntimeException error) {
            LOGGER.warn("CWI firelight could not patch terrain shader {}", name, error);
            return source;
        }
    }

    private static String code() throws IOException {
        if (code == null) {
            try (InputStream stream = ShaderPatches.class.getResourceAsStream("/assets/cwi_visual/glsl/flicker.glsl")) {
                if (stream == null) throw new IOException("CWI firelight GLSL is missing");
                code = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return code;
    }
}
