package net.quiwh.cwivisuals;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarInputStream;
import java.util.zip.ZipFile;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;
import net.quiwh.cwivisuals.firelight.ShaderPatches;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Exact installed binary contracts; no loader transformation, window, GPU or game launch. */
public final class PackRenderingFixture {
    private static final String ASYNC_COMPAT =
            "neoforge/fun/qu_an/minecraft/asyncparticles/client/compat/particlerain/ParticleRainCompat";
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path rain = Path.of("libs/particlerain-4.0.3-1.21.1-neoforge.jar");
        Path async = Path.of("libs/AsyncParticles-21.1.4.12-1.21.1.jar");
        Path sodium = Path.of("libs/sodium-neoforge-0.8.13+mc1.21.1.jar");
        hash(rain, "1e33e0e74fc8fcdb35f28c6142e63f7df1aede044bcd1eaf933ba0c0bf0b5435");
        hash(async, "6e35a69e7596667793e2b5c3f3564f79e8de5fb2cd18383ad7f27d14ec0f1402");
        hash(sodium, "575d187b15328d7bddd8a9b1bee64333c22c14735f5780ec49eac0cd5503c08d");

        checkCounterContract(async);
        checkSodiumContract(sodium);
        checkRequiredIntegration();
        System.out.println("PackRenderingFixture PASS: " + checks
                + " exact-pack binary, shader and client dependency contracts; no transformed startup or GPU check.");
    }

    private static void checkCounterContract(Path jar) throws Exception {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ClassNode compat = readClass(bytes(zip, ASYNC_COMPAT + ".class"));
            check(compat.fields.stream().anyMatch(f -> f.name.equals("particleCount")
                    && f.desc.equals("Ljava/util/concurrent/atomic/AtomicInteger;")
                    && (f.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL))
                    == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)),
                    "installed AsyncParticles exports its atomic weather counter");
            MethodNode init = method(compat, "<clinit>", "()V");
            check(calls(init, "java/util/concurrent/atomic/AtomicInteger", "<init>", "()V"),
                    "counter initializes independently");
            check(init.instructions.size() < 25, "counter initialization stays minimal");
            ClassNode redirects = readClass(bytes(zip,
                    "neoforge/fun/qu_an/minecraft/asyncparticles/client/mixin/compat/particlerain/MixinWeatherParticle.class"));
            check(calls(method(redirects, "modifyParticleCount", "()I"),
                    "java/util/concurrent/atomic/AtomicInteger", "getAndIncrement", "()I"),
                    "native weather constructor increments live atomic count");
            check(calls(method(redirects, "modifyParticleCount", "(I)V"),
                    "java/util/concurrent/atomic/AtomicInteger", "getAndDecrement", "()I"),
                    "native weather removal decrements live atomic count");
        }
        Class<?> compat = Class.forName(ASYNC_COMPAT.replace('/', '.'));
        AtomicInteger live = (AtomicInteger) compat.getField("particleCount").get(null);
        int previous = live.get();
        try {
            live.set(37);
            check(live.get() == 37, "actual .12 atomic counter is available without game startup");
        } finally {
            live.set(previous);
        }
    }

    private static void checkSodiumContract(Path jar) throws Exception {
        byte[] nested;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            nested = bytes(zip, "META-INF/jarjar/net.caffeinemc.sodium-neoforge-0.8.13+mc1.21.1-mod.jar");
        }
        check(sha256(nested).equals("d71acbffafd3d177add7624710114d8570cdd70d5553a2b5c8ab371852c59ce3"),
                "exact nested Sodium implementation");
        Map<String, byte[]> entries = new java.util.HashMap<>();
        try (JarInputStream stream = new JarInputStream(new ByteArrayInputStream(nested))) {
            java.util.jar.JarEntry entry;
            while ((entry = stream.getNextJarEntry()) != null) {
                if (!entry.isDirectory()) entries.put(entry.getName(), stream.readAllBytes());
            }
        }
        ClassNode loader = readClass(entries.get("net/caffeinemc/mods/sodium/client/gl/shader/ShaderLoader.class"));
        MethodNode load = method(loader, "getShaderSource", "(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;");
        check((load.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC))
                == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC), "exact Sodium shader source target is static/public");
        ClassNode program = readClass(entries.get("net/caffeinemc/mods/sodium/client/gl/shader/GlProgram.class"));
        MethodNode bind = method(program, "bind", "()V");
        check((bind.access & Opcodes.ACC_PUBLIC) != 0 && calls(bind, "org/lwjgl/opengl/GL20C", "glUseProgram", "(I)V"),
                "exact Sodium bind selects the raw GL program before callback");
        ClassNode object = readClass(entries.get("net/caffeinemc/mods/sodium/client/gl/GlObject.class"));
        check((method(object, "handle", "()I").access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL))
                == (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL), "Sodium exposes handle without GL current-program query");
        String shader = new String(entries.get("assets/sodium/shaders/blocks/block_layer_opaque.vsh"), StandardCharsets.UTF_8);
        check(shader.contains("vec3 position = ")
                && shader.contains("v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);"),
                "actual installed Sodium shader contains the selected light sample");
        minimalLoader();
        String patched = ShaderPatches.patch("installed-sodium", shader);
        check(!patched.equals(shader), "real installed Sodium shader is patched");
        check(patched.startsWith("#version 330 core"), "GLSL version remains first");
        check(patched.contains("cwi_firelight_coord(_vert_tex_light_coord, position)"),
                "real shader uses camera-relative firelight sample");
        check(ShaderPatches.patch("installed-sodium", patched).equals(patched), "installed shader patch is idempotent");
    }

    private static void checkRequiredIntegration() throws Exception {
        String metadata = new String(resource("META-INF/neoforge.mods.toml"), StandardCharsets.UTF_8);
        dependency(metadata, "particlerain", "[4.0.3,4.0.4)", "4.0.3", "4.0.4");
        dependency(metadata, "asyncparticles", "[21.1.4.12]", "21.1.4.12", "21.1.4.11", "21.1.4.13");
        dependency(metadata, "sodium", "[0.8.13+mc1.21.1]", "0.8.13+mc1.21.1", "0.8.12+mc1.21.1");
        hook("ShaderLoaderMixin", "getShaderSource(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;");
        hook("GlProgramMixin", "bind()V");
        ClassNode budget = readClass(resource("net/quiwh/cwivisuals/weather/WeatherParticleBudget.class"));
        MethodNode count = method(budget, "count", "()I");
        boolean direct = false;
        for (var insn : count.instructions) {
            if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && field.owner.equals(ASYNC_COMPAT) && field.name.equals("particleCount")) direct = true;
        }
        check(direct && calls(count, "java/util/concurrent/atomic/AtomicInteger", "get", "()I"),
                "CWI reads the exact installed live counter directly");
        check(budget.methods.stream().noneMatch(m -> m.name.equals("resolveCounter")),
                "no stale-native-counter fallback remains in the pack integration");
    }

    private static void dependency(String metadata, String id, String version, String installed, String... unsupported) throws Exception {
        String block = null;
        for (String candidate : metadata.split("\\[\\[")) {
            if (candidate.contains("modId=\"" + id + "\"")) block = candidate;
        }
        check(block != null, "client pack dependency declared: " + id);
        check(block.contains("type=\"required\"") && block.contains("side=\"CLIENT\"")
                && block.contains("versionRange=\"" + version + "\""),
                "required client version is pinned: " + id);
        VersionRange range = VersionRange.createFromVersionSpec(version);
        check(range.containsVersion(new DefaultArtifactVersion(installed)),
                "loader version range accepts installed dependency: " + id);
        for (String other : unsupported) {
            check(!range.containsVersion(new DefaultArtifactVersion(other)),
                    "loader version range rejects unverified dependency: " + id + " " + other);
        }
    }

    private static void hook(String name, String selector) throws Exception {
        ClassNode hook = readClass(resource("net/quiwh/cwivisuals/firelight/mixin/sodium/" + name + ".class"));
        check(annotation(hook.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Pseudo;") == null
                && annotation(hook.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Pseudo;") == null,
                "required Sodium target does not silently skip: " + name);
        AnnotationNode inject = null;
        for (MethodNode method : hook.methods) {
            AnnotationNode found = annotation(method.visibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Inject;");
            if (found == null) found = annotation(method.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Inject;");
            if (found != null) inject = found;
        }
        check(inject != null && Integer.valueOf(1).equals(value(inject, "require")),
                "Sodium callback is required: " + name);
        check(List.of(selector).equals(value(inject, "method")), "exact Sodium callback descriptor: " + name);
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        return annotations == null ? null : annotations.stream().filter(a -> a.desc.equals(descriptor)).findFirst().orElse(null);
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation == null || annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static boolean calls(MethodNode method, String owner, String name, String descriptor) {
        for (var insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.owner.equals(owner)
                    && call.name.equals(name) && call.desc.equals(descriptor)) return true;
        }
        return false;
    }

    private static MethodNode method(ClassNode type, String name, String descriptor) {
        MethodNode method = type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst().orElse(null);
        check(method != null, "native method exists: " + type.name + "." + name + descriptor);
        return method;
    }

    private static ClassNode readClass(byte[] bytes) {
        check(bytes != null, "class bytes exist");
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static byte[] bytes(ZipFile zip, String path) throws Exception {
        check(zip.getEntry(path) != null, "binary entry exists: " + path);
        try (InputStream stream = zip.getInputStream(zip.getEntry(path))) {
            return stream.readAllBytes();
        }
    }

    private static byte[] resource(String path) throws Exception {
        try (InputStream stream = PackRenderingFixture.class.getClassLoader().getResourceAsStream(path)) {
            check(stream != null, "project resource exists: " + path);
            return stream.readAllBytes();
        }
    }

    private static void hash(Path path, String expected) throws Exception {
        check(sha256(Files.readAllBytes(path)).equals(expected), "exact installed dependency hash: " + path.getFileName());
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static void minimalLoader() throws Exception {
        FMLPaths.loadAbsolutePaths(Path.of("build/pack-rendering-fixture").toAbsolutePath());
        set(FMLLoader.class, null, "loadingModList", LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of()));
        set(FMLLoader.class, null, "dist", net.neoforged.api.distmarker.Dist.CLIENT);
        ModList mods = ModList.of(List.of(), List.of());
        set(ModList.class, mods, "indexedMods", Map.of());
        set(ModList.class, mods, "mods", List.of());
        set(ModList.class, mods, "sortedContainers", List.of());
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
