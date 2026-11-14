package net.quiwh.cwivisuals.firelight;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Real vanilla state/registry checks without creating a game, level, window or GL context. */
public final class FirelightNativeFixture {
    private static int checks;

    public static void main(String[] args) throws Exception {
        minimalLoader();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        check(!FirelightClient.active(), "module starts inactive");
        FirelightClient.configure(true, .5f, Set.of("missing:lamp"));
        check(!FirelightClient.active(), "unknown block IDs are safely ignored");
        FirelightClient.configure(true, .5f, Set.of("minecraft:torch", "minecraft:campfire", "minecraft:lantern", "missing:lamp", "not a valid ID"));
        check(FirelightClient.active(), "valid configured sources activate module");
        check(Flames.kind(Blocks.TORCH.defaultBlockState()) == FlameKind.TORCH, "explicit torch accepted");
        check(Flames.kind(Blocks.SOUL_TORCH.defaultBlockState()) == null, "related class not allowlisted stays excluded");
        check(Flames.kind(Blocks.LANTERN.defaultBlockState()) == FlameKind.LANTERN, "allowlisted dry lantern accepted");
        check(Flames.kind(Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true)) == null, "waterlogged lantern excluded");
        check(Flames.kind(Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false)) == null, "unlit campfire excluded");
        check(Flames.kind(Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true)) == null, "waterlogged campfire excluded");
        FirelightClient.configure(true, .5f, Set.of("minecraft:glowstone", "minecraft:stone", "minecraft:candle"));
        check(Flames.kind(Blocks.TORCH.defaultBlockState()) == null, "allowlist reload invalidates prior source cache");
        check(Flames.kind(Blocks.GLOWSTONE.defaultBlockState()) != null, "other explicitly selected luminous blocks qualify");
        check(Flames.kind(Blocks.STONE.defaultBlockState()) == null, "nonemitting selected blocks excluded");
        check(Flames.kind(Blocks.CANDLE.defaultBlockState()) == null, "unlit selected candle excluded");
        check(Flames.kind(Blocks.CANDLE.defaultBlockState().setValue(BlockStateProperties.LIT, true)) == FlameKind.CANDLE, "lit selected candle qualifies");
        FirelightClient.configure(true, .5f, Set.of("minecraft:torch"));
        Field field = FlickerFrame.class.getDeclaredField("FRAME");
        field.setAccessible(true);
        float[] frame = (float[]) field.get(null);
        Arrays.fill(frame, 0);
        frame[2] = 1;
        frame[7] = Flicker.encode(14, -.5f);
        for (int sky = 0; sky <= 240; sky += 16) {
            int packed = sky << 16 | 224;
            int changed = FlickerFrame.flicker(packed, 0, 0, 0);
            check((changed & 0xffff0000) == (packed & 0xffff0000), "packed sky channel is preserved");
            check((changed & 255) == 112, "packed block channel follows local dip");
        }
        int skyOnly = 240 << 16;
        check(FlickerFrame.flicker(skyOnly, 0, 0, 0) == skyOnly, "pure sky light is untouched");
        FirelightClient.configure(true, 0, Set.of("minecraft:torch"));
        frame[2] = 1;
        frame[7] = Flicker.encode(14, -.5f);
        check(!FirelightClient.active() && FlickerFrame.flicker(224, 0, 0, 0) == 224, "zero strength is exact off even with stale data");
        FirelightClient.configure(true, Float.NaN, Set.of("minecraft:torch"));
        check(!FirelightClient.active(), "nonfinite strength is safely off");
        FirelightClient.configure(true, .5f, Set.of("minecraft:torch"));
        frame[2] = 1;
        frame[7] = Flicker.encode(14, -.5f);
        FirelightClient.reset();
        check(FlickerFrame.flicker(224, 0, 0, 0) == 224 && FlickerFrame.count() == 0, "reset clears rendered source data");
        check(FlameTracker.snapshot().count() == 0, "reset clears tracked sources");
        FlickerFrame.update(new Vec3(0, 0, 0));
        check(FlickerFrame.count() == 0, "old sources never reappear on the next frame");
        FirelightClient.configure(false, .5f, Set.of("minecraft:torch"));
        check(!FirelightClient.active(), "disabled config is off");
        checkShaderResources();
        checkCompatibilityGates(frame);
        checks += FirelightSelectionFixture.run();
        System.out.println("FirelightNativeFixture passed " + checks + " checks");
    }

    private static void minimalLoader() throws Exception {
        net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(java.nio.file.Path.of("build/firelight-fixture").toAbsolutePath());
        set(net.neoforged.fml.loading.FMLLoader.class, null, "loadingModList",
                net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.Map.of()));
        set(net.neoforged.fml.loading.FMLLoader.class, null, "dist", net.neoforged.api.distmarker.Dist.CLIENT);
        var mods = net.neoforged.fml.ModList.of(java.util.List.of(), java.util.List.of());
        set(net.neoforged.fml.ModList.class, mods, "indexedMods", java.util.Map.of());
        set(net.neoforged.fml.ModList.class, mods, "mods", java.util.List.of());
        set(net.neoforged.fml.ModList.class, mods, "sortedContainers", java.util.List.of());
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    public static boolean shaderPackActive() { return true; }

    private static void checkCompatibilityGates(float[] frame) throws Exception {
        FirelightClient.configure(true, .5f, Set.of("minecraft:torch"));
        String original = "#version 330 core\nvoid main() { vec3 position = vec3(0); v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord); }";
        java.util.Map<String, Object> upstream = new java.util.HashMap<>();
        upstream.put("firelight", null);
        set(net.neoforged.fml.ModList.class, net.neoforged.fml.ModList.get(), "indexedMods", upstream);
        frame[2] = 1;
        frame[7] = Flicker.encode(14, -.5f);
        check(!FirelightClient.active() && FlickerFrame.flicker(224, 0, 0, 0) == 224, "upstream Firelight suppresses own packed light correction");
        check(ShaderPatches.patch("duplicate", original).equals(original), "upstream Firelight keeps the unconsumed shader source");
        set(net.neoforged.fml.ModList.class, net.neoforged.fml.ModList.get(), "indexedMods", java.util.Map.of());
        var active = java.lang.invoke.MethodHandles.lookup().findStatic(FirelightNativeFixture.class, "shaderPackActive", java.lang.invoke.MethodType.methodType(boolean.class));
        set(Compat.class, null, "looked", true);
        set(Compat.class, null, "shaderPackInUse", active);
        check(!FirelightClient.active() && FlickerFrame.flicker(224, 0, 0, 0) == 224, "active shaderpack suppresses own packed light correction");
        check(ShaderPatches.patch("shaderpack", original).equals(original), "active shaderpack keeps untouched shader source");
        set(Compat.class, null, "shaderPackInUse", null);
        FirelightClient.reset();
    }

    private static void checkShaderResources() throws Exception {
        for (String layer : new String[]{"solid", "cutout", "cutout_mipped", "translucent", "tripwire"}) {
            String path = "/assets/minecraft/shaders/core/rendertype_" + layer + ".vsh";
            String shader;
            try (InputStream stream = FirelightNativeFixture.class.getResourceAsStream(path)) {
                check(stream != null, "actual vanilla shader resource exists: " + layer);
                shader = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            String patched = ShaderPatches.patch(layer, shader);
            check(!shader.equals(patched), "actual vanilla terrain shader is transformed: " + layer);
            check(patched.startsWith("#version"), "GLSL version remains first");
            check(patched.contains("cwi_firelight_sample(Sampler2, UV2, pos)"), "terrain uses local block light sample");
            check(ShaderPatches.patch(layer, patched).equals(patched), "shader transformation is idempotent");
        }
        String unknown = "#version 150\nvoid main() { gl_Position = vec4(0); }";
        check(ShaderPatches.patch("unknown", unknown).equals(unknown), "unknown shaders are unchanged");
        String sodium = "#version 330 core\nvoid main() { vec3 position = vec3(0); v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord); }";
        String patched = ShaderPatches.patch("sodium", sodium);
        check(patched.contains("cwi_firelight_coord(_vert_tex_light_coord, position)"), "installed Sodium pattern is transformed locally");
        check(ShaderPatches.patch("sodium", patched).equals(patched), "Sodium transformation is idempotent");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
