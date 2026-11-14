package net.quiwh.cwivisuals.weather;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import neoforge.fun.qu_an.minecraft.asyncparticles.client.compat.particlerain.ParticleRainCompat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.FogRenderer.FogMode;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import pigcart.particlerain.ParticleLoader;
import pigcart.particlerain.ParticleSpawner;
import pigcart.particlerain.config.ConfigData;
import pigcart.particlerain.config.ConfigManager;
import pigcart.particlerain.config.ParticleData;
import pigcart.particlerain.mixin.access.ParticleAccessor;
import pigcart.particlerain.mixin.access.ParticleEngineAccessor;
import pigcart.particlerain.particle.CustomParticle;

/** Native descriptor/particle contracts. No game window, mixin loader, GPU, or pack paths. */
public final class NativeAshParticleFixture {
    private static sun.misc.Unsafe unsafe;
    private static int assertions;

    private static void check(boolean condition, String reason) {
        assertions++;
        if (!condition) throw new AssertionError(reason);
    }

    private static void close(double actual, double expected, String reason) {
        check(Math.abs(actual - expected) < 0.00001, reason + ": " + actual + " != " + expected);
    }

    private static float particleFloat(Particle particle, String name) throws Exception {
        Field field = Particle.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getFloat(particle);
    }

    private static JsonObject appearanceAndMotion(ParticleData data) {
        JsonObject result = ParticleLoader.GSON.toJsonTree(data).getAsJsonObject();
        for (String changed : List.of("tintType", "customTint", "precipitation", "renderType", "spriteLocations",
                "animateSprites", "rotationType", "distanceScaling", "size", "opacity", "gravity",
                "windStrength", "stormWindStrength", "bounciness", "rotationAmount")) result.remove(changed);
        return result;
    }

    // Only the interfaces normally added by Particle Rain's accessor mixins are supplied here.
    // The native CustomParticle constructor, tick, tint, fading, gravity and WindManager remain real.
    private static final class NativeParticle extends CustomParticle implements ParticleAccessor {
        NativeParticle(ClientLevel level, ParticleData data) { super(level, 0, 96, 0, data); }
        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getZ() { return z; }
        @Override public double getXd() { return xd; }
        @Override public void setXd(double value) { xd = value; }
        @Override public double getZd() { return zd; }
        @Override public void setZd(double value) { zd = value; }
    }

    // Empty, loaded air volume. Construction is skipped because network/chunk/GPU setup is unrelated.
    private static final class AirLevel extends ClientLevel {
        RandomSource fixtureRandom;
        Holder<Biome> fixtureBiome;
        boolean storm;
        int heightReads;
        private AirLevel() { super(null, null, null, null, 0, 0, null, null, false, 0); }
        @Override public RandomSource getRandom() { return fixtureRandom; }
        @Override public Holder<Biome> getBiome(BlockPos pos) { return fixtureBiome; }
        @Override public boolean isThundering() { return storm; }
        @Override public float getRainLevel(float partialTick) { return 0; }
        @Override public float getThunderLevel(float partialTick) { return 0; }
        @Override public long getGameTime() { return 0; }
        @Override public boolean isLoaded(BlockPos pos) { return true; }
        @Override public int getHeight(net.minecraft.world.level.levelgen.Heightmap.Types type, int x, int z) {
            heightReads++;
            return 80;
        }
        @Override public int getMinBuildHeight() { return -64; }
        @Override public int getMaxBuildHeight() { return 320; }
        @Override public BlockState getBlockState(BlockPos pos) { return Blocks.AIR.defaultBlockState(); }
        @Override public FluidState getFluidState(BlockPos pos) { return Fluids.EMPTY.defaultFluidState(); }
        @Override public BlockHitResult clip(ClipContext context) {
            return BlockHitResult.miss(context.getTo(), Direction.UP, BlockPos.containing(context.getTo()));
        }
    }

    private static final class Atlas extends TextureAtlas {
        private Atlas() { super(null); }
        @Override public TextureAtlasSprite getSprite(ResourceLocation id) {
            try {
                var contents = (SpriteContents) unsafe.allocateInstance(SpriteContents.class);
                MinimalNativeBootstrap.set(SpriteContents.class, contents, "name", id);
                var sprite = (TextureAtlasSprite) unsafe.allocateInstance(TextureAtlasSprite.class);
                MinimalNativeBootstrap.set(TextureAtlasSprite.class, sprite, "contents", contents);
                return sprite;
            } catch (Exception error) { throw new AssertionError(error); }
        }
    }

    private static final class Engine extends ParticleEngine implements ParticleEngineAccessor {
        TextureAtlas atlas;
        List<Particle> emitted;
        private Engine() { super(null, null); }
        @Override public TextureAtlas getTextureAtlas() { return atlas; }
        @Override public void add(Particle particle) {
            emitted.add(particle);
            mirrorAsyncCount();
        }
        @Override public void callClearParticles() { throw new AssertionError("Unexpected engine clear"); }
    }

    private static final class ViewCamera extends Camera {
        @Override public Vec3 getPosition() { return new Vec3(0, 96, 0); }
    }

    private static final class ViewPlayer extends LocalPlayer {
        String lastMessage;
        private ViewPlayer() { super(null, null, null, null, null, false, false); }
        @Override public float getFieldOfViewModifier() { return 1; }
        @Override public Vec3 getDeltaMovement() { return Vec3.ZERO; }
        @Override public void displayClientMessage(Component message, boolean actionBar) {
            lastMessage = message.getString();
        }
    }

    // Keep the real veil's ownership and thread selection; replace only the GPU deletion.
    private static final class DepthTarget extends TextureTarget {
        boolean destroyed;
        private DepthTarget() { super(1, 1, true, false); }
        @Override public void destroyBuffers() { destroyed = true; }
    }

    private static AirLevel initializeClient() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (sun.misc.Unsafe) field.get(null);
        var level = (AirLevel) unsafe.allocateInstance(AirLevel.class);
        level.fixtureRandom = RandomSource.create(7151);
        MinimalNativeBootstrap.set(Level.class, level, "dimension", Level.OVERWORLD);
        level.fixtureBiome = Holder.direct(new Biome.BiomeBuilder().hasPrecipitation(false)
                .temperature(2).downfall(0).mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .specialEffects(new BiomeSpecialEffects.Builder().fogColor(0).waterColor(0)
                        .waterFogColor(0).skyColor(0).build()).build());
        var client = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        var engine = (Engine) unsafe.allocateInstance(Engine.class);
        engine.atlas = (Atlas) unsafe.allocateInstance(Atlas.class);
        engine.emitted = new ArrayList<>();
        var renderer = (GameRenderer) unsafe.allocateInstance(GameRenderer.class);
        MinimalNativeBootstrap.set(GameRenderer.class, renderer, "mainCamera", new ViewCamera());
        var options = (Options) unsafe.allocateInstance(Options.class);
        var fov = new OptionInstance<Integer>("options.fov", OptionInstance.noTooltip(),
                (caption, value) -> Component.literal(value.toString()), new OptionInstance.IntRange(30, 110), 70, value -> {});
        MinimalNativeBootstrap.set(Options.class, options, "fov", fov);
        MinimalNativeBootstrap.set(Minecraft.class, client, "particleEngine", engine);
        MinimalNativeBootstrap.set(Minecraft.class, client, "gameRenderer", renderer);
        MinimalNativeBootstrap.set(Minecraft.class, client, "options", options);
        var player = unsafe.allocateInstance(ViewPlayer.class);
        MinimalNativeBootstrap.set(Minecraft.class, client, "player", player);
        MinimalNativeBootstrap.set(Minecraft.class, client, "cameraEntity", player);
        MinimalNativeBootstrap.set(Minecraft.class, client, "level", level);
        MinimalNativeBootstrap.set(Minecraft.class, null, "instance", client);
        var config = new ConfigData();
        config.updateTransientVariables();
        MinimalNativeBootstrap.set(ConfigManager.class, null, "config", config);
        return level;
    }

    private static void actualParticle(AirLevel level, ParticleData source, ParticleData ash) throws Exception {
        int countBefore = ParticleSpawner.particleCount;
        level.storm = false;
        var particle = new NativeParticle(level, ash);
        for (String channel : List.of("rCol", "gCol", "bCol")) {
            close(particleFloat(particle, channel), 96 / 255.0, source.id + " actual native gray " + channel);
        }
        check(particle.getLifetime() == source.lifetime, source.id + " keeps native lifetime");
        check(particle.getRenderType() == ash.renderType.get(), source.id + " uses the ash render layer");
        close(particle.getXd(), -0.03f * ash.windStrength * 10, source.id + " reduced spawn wind x");
        close(particle.getZd(), -0.03f * ash.windStrength * 10, source.id + " reduced spawn wind z");
        double windBeforeTick = particle.getXd();
        tickParticle(particle);
        check(particle.getXd() < windBeforeTick, source.id + " native wind keeps accelerating the real particle");
        for (String channel : List.of("rCol", "gCol", "bCol")) {
            close(particleFloat(particle, channel), 96 / 255.0, source.id + " stays gray after native tick " + channel);
        }
        check(Float.isFinite(particleFloat(particle, "alpha")), source.id + " native alpha is finite");
        particle.remove();
        level.storm = true;
        var stormParticle = new NativeParticle(level, ash);
        close(stormParticle.getXd(), -0.03f * ash.stormWindStrength * 10, source.id + " storm uses reduced wind multiplier");
        stormParticle.remove();
        check(ParticleSpawner.particleCount == countBefore, source.id + " native removal balances particle count");
        level.storm = false;
    }

    private static boolean stormActive() throws Exception {
        Field field = AshPreview.class.getDeclaredField("stormActive");
        field.setAccessible(true);
        return field.getBoolean(null);
    }

    private static AshIntensity envelope() throws Exception {
        Field field = AshPreview.class.getDeclaredField("intensity");
        field.setAccessible(true);
        return (AshIntensity) field.get(null);
    }

    private static void synchronize(boolean active, long seed) throws Exception {
        Class<?> payload = Class.forName("net.quiwh.cwivisuals.weather.network.AshWeatherPayload");
        Object snapshot = payload.getConstructor(boolean.class, long.class).newInstance(active, seed);
        AshPreview.class.getMethod("applySync", payload).invoke(null, snapshot);
    }

    private static void start() throws Exception {
        Field field = AshPreview.class.getDeclaredField("RANDOM");
        field.setAccessible(true);
        synchronize(true, ((RandomSource) field.get(null)).nextLong());
    }

    // These fixtures omit loader mixins. Mirror the installed AsyncParticles bookkeeping
    // redirection at harness boundaries; native CustomParticle still owns increment/removal.
    private static void mirrorAsyncCount() {
        ParticleRainCompat.particleCount.set(ParticleSpawner.particleCount);
    }

    private static void tickPreview() {
        mirrorAsyncCount();
        AshPreview.tick(new ClientTickEvent.Post());
    }

    private static void tickParticle(Particle particle) {
        particle.tick();
        mirrorAsyncCount();
    }

    private static void clearWeather() throws Exception { synchronize(false, 0); }

    private static void disconnect() throws Exception {
        AshPreview.class.getMethod("disconnect").invoke(null);
    }

    private static void synchronizedLifecycle(AirLevel level) throws Exception {
        int count = ParticleSpawner.particleCount;
        ParticleSpawner.particleCount = ConfigManager.getConfig().perf.particleAmount;
        synchronize(true, 81);
        AshIntensity original = envelope();
        check(stormActive() && original != null && original.value() == 0,
                "Server selection creates one gentle local entry without a duration");
        for (int i = 0; i < 12500; i++) tickPreview();
        check(stormActive() && envelope() == original && !original.finished() && original.value() > 0 && AshFog.active(),
                "Ash and existing fog remain active beyond both former duration limits without a heartbeat");
        check(ParticleSpawner.particleCount == ConfigManager.getConfig().perf.particleAmount,
                "Occupied budget prevents extra particles without changing the counter during an indefinite storm");
        synchronize(true, 81);
        check(envelope() == original, "Repeated active identity preserves existing entry and gust state");
        synchronize(true, 82);
        check(envelope() != original && envelope().value() == 0 && !AshFog.active(),
                "A new server selection replaces presentation with a gentle fresh entry");

        var own = new NativeParticle(level, AshParticleProfiles.create(ParticleLoader.particles.get("dust")));
        var unrelated = new NativeParticle(level, AshParticleProfiles.create(ParticleLoader.particles.get("dust")));
        Field particles = AshPreview.class.getDeclaredField("PARTICLES");
        particles.setAccessible(true);
        @SuppressWarnings("unchecked") var tracked = (List<Particle>) particles.get(null);
        tracked.add(own);
        for (int i = 0; i < 200; i++) tickPreview();
        check(AshFog.active(), "Indefinite server weather drives the existing surface fog");
        clearWeather();
        check(!stormActive() && envelope() == null && tracked.isEmpty() && !AshFog.active(),
                "Native clear immediately drops authority, ash and fog");
        check(own.getLifetime() == 0, "Native clear retires owned particles");
        check(unrelated.getLifetime() != 0 && unrelated.isAlive(), "Native clear preserves unrelated weather particles");
        own.remove();
        unrelated.remove();
        check(ParticleSpawner.particleCount == ConfigManager.getConfig().perf.particleAmount,
                "Owned clear leaves counter changes to native particle removal");

        synchronize(true, 91);
        for (int i = 0; i < 150; i++) tickPreview();
        original = envelope();
        MinimalNativeBootstrap.set(Level.class, level, "dimension", Level.NETHER);
        tickPreview();
        check(stormActive() && envelope() == null && !AshFog.active(),
                "Other dimensions retain indefinite authority while clearing Overworld presentation");
        for (int i = 0; i < 12500; i++) tickPreview();
        check(stormActive() && envelope() == null, "Time away from the Overworld cannot expire selected ash");
        MinimalNativeBootstrap.set(Level.class, level, "dimension", Level.OVERWORLD);
        tickPreview();
        check(stormActive() && envelope() != null && envelope() != original && envelope().value() < .001f,
                "Returning to Overworld softly reenters the retained indefinite storm");
        Minecraft.getInstance().level = null;
        tickPreview();
        check(stormActive() && envelope() == null && !AshFog.active(),
                "Transient world replacement clears presentation while retaining selection");
        Minecraft.getInstance().level = level;
        tickPreview();
        check(envelope() != null && envelope().value() < .001f, "A replacement world gets a gentle local entry");
        disconnect();
        check(!stormActive() && envelope() == null && !AshFog.active(), "Logout drops all authority and effects");
        tickPreview();
        check(envelope() == null, "A later client tick cannot restore disconnected authority");

        Minecraft.getInstance().level = null;
        synchronize(true, 101);
        check(stormActive() && envelope() == null, "A login snapshot can precede the client world");
        Minecraft.getInstance().level = level;
        tickPreview();
        check(envelope() != null && envelope().value() < .001f, "Deferred login selection starts gently once its world exists");
        var profiles = ParticleLoader.particles;
        ParticleLoader.particles = null;
        tickPreview();
        check(stormActive() && envelope() == null && !AshFog.active(),
                "Missing profiles clear effects while retaining server selection");
        ParticleLoader.particles = profiles;
        tickPreview();
        check(envelope() != null && envelope().value() < .001f,
                "Restored profiles resume the selected storm with a gentle entry");
        clearWeather();
        for (int i = 0; i < 200; i++) tickPreview();
        check(!stormActive() && envelope() == null && !AshFog.active(), "Explicit clear stays clear on following ticks");

        start();
        var noDust = AshParticleProfiles.create(ParticleLoader.particles.get("skittering_dust"));
        noDust.density = 0f;
        Field limitsField = AshPreview.class.getDeclaredField("LIMITS");
        limitsField.setAccessible(true);
        java.util.Arrays.fill((int[]) limitsField.get(null), 100);
        Method surface = AshPreview.class.getDeclaredMethod("emitSurface", Minecraft.class, Vec3.class,
                int.class, int.class, ParticleData.class);
        surface.setAccessible(true);
        int readsBefore = level.heightReads;
        surface.invoke(null, Minecraft.getInstance(), new Vec3(0, 96, 0), 32, 64, noDust);
        check(level.heightReads == readsBefore, "Rejected density candidates never sample terrain");
        disconnect();
        ParticleSpawner.particleCount = count;
    }

    private static void actualEmitter(AirLevel level) throws Exception {
        var config = ConfigManager.getConfig().perf;
        config.particleAmount = 160;
        config.particleDistance = 32;
        config.surfaceRange = 32;
        ParticleSpawner.particleCount = 0;
        var engine = (Engine) Minecraft.getInstance().particleEngine;
        engine.emitted.clear();
        var randomField = AshPreview.class.getDeclaredField("RANDOM");
        randomField.setAccessible(true);
        ((RandomSource) randomField.get(null)).setSeed(4151);
        start();
        Field trackedField = AshPreview.class.getDeclaredField("PARTICLES");
        trackedField.setAccessible(true);
        Field surfacesField = AshPreview.class.getDeclaredField("SURFACES");
        surfacesField.setAccessible(true);
        Object surfaces = surfacesField.get(null);
        Field entriesField = AshSurfaceCache.class.getDeclaredField("entries");
        entriesField.setAccessible(true);
        int births = 0;
        int peak = 0;
        int initialBirths = -1;
        int firstTenPeak = 0;
        int fogFrames = 0;
        for (int tick = 0; tick < 1200; tick++) {
            int before = engine.emitted.size();
            tickPreview();
            var fog = new ViewportEvent.RenderFog(FogMode.FOG_TERRAIN, FogType.NONE,
                    Minecraft.getInstance().gameRenderer.getMainCamera(), 1, 230, 256, FogShape.CYLINDER);
            AshFog.render(fog);
            check(fog.getFarPlaneDistance() == 256, "Actual event never lowers terrain fog-culling distance");
            if (fog.getNearPlaneDistance() < 230) fogFrames++;
            births += engine.emitted.size() - before;
            if (tick == 9) initialBirths = births;
            peak = Math.max(peak, engine.emitted.size());
            if (tick < 10) firstTenPeak = Math.max(firstTenPeak, engine.emitted.size());
            check(ParticleSpawner.particleCount <= config.particleAmount, "Real emitter honors shared live cap");
            check(WeatherParticleBudget.count() == ParticleSpawner.particleCount,
                    "The installed AsyncParticles budget sees every headless native constructor");
            check(((List<?>) trackedField.get(null)).size() <= config.particleAmount,
                    "Indefinite emission retains only a bounded live particle population");
            check(((Map<?, ?>) entriesField.get(surfaces)).size() <= 512,
                    "Indefinite surface sampling retains only its bounded terrain cache");
            for (Particle particle : engine.emitted) {
                check(particle instanceof AshParticle, "Emitter constructs the optimized production class");
                tickParticle(particle);
            }
            engine.emitted.removeIf(p -> !p.isAlive());
        }
        check(births > 20 && peak > firstTenPeak, "Emission builds gradually after the initially sparse entry");
        check(initialBirths < 10, "Preview onset does not immediately burst a full particle population");
        check(stormActive() && envelope() != null && !envelope().finished() && !engine.emitted.isEmpty(),
                "The real emitter remains active at the former 60-second deadline");
        clearWeather();
        for (Particle particle : engine.emitted) {
            close(particleFloat(particle, "alpha"), 0, "Explicit clear immediately hides every owned emitted particle");
            tickParticle(particle);
        }
        engine.emitted.removeIf(p -> !p.isAlive());
        check(!stormActive() && envelope() == null && engine.emitted.isEmpty(),
                "Explicit clear leaves no visible or tracked particle tail");
        check(ParticleSpawner.particleCount == 0, "Full real-particle lifecycle balances the native weather counter");
        check(WeatherParticleBudget.count() == 0, "The installed AsyncParticles budget balances after native removals");
        check(fogFrames > 600, "Actual preview tick drives visible surface fog through the active event");
        var clearedFog = new ViewportEvent.RenderFog(FogMode.FOG_TERRAIN, FogType.NONE,
                Minecraft.getInstance().gameRenderer.getMainCamera(), 1, 230, 256, FogShape.CYLINDER);
        AshFog.render(clearedFog);
        check(!clearedFog.isCanceled() && clearedFog.getFarPlaneDistance() == 256,
                "Explicit ash clear restores native world fog");
        System.out.println("Actual emitter: births=" + births + ", peak=" + peak + ", first10=" + initialBirths);
    }

    private static void disablingPreview(AirLevel level) throws Exception {
        Method configure;
        try {
            configure = AshPreview.class.getMethod("configure", boolean.class);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("Missing client config gate: AshPreview.configure(boolean)", missing);
        }
        start();
        check(stormActive() && envelope() != null, "Enabled ash receives the indefinite selected storm");
        int countBefore = ParticleSpawner.particleCount;
        var own = new AshParticle(level, 0, 96, 0,
                AshParticleProfiles.create(ParticleLoader.particles.get("dust")), true);
        var unrelated = new NativeParticle(level, AshParticleProfiles.create(ParticleLoader.particles.get("dust")));
        MinimalNativeBootstrap.set(Particle.class, own, "age", 30);
        own.tickFading();
        check(particleFloat(own, "alpha") > 0, "Owned ash is visible before disabling");
        Field particles = AshPreview.class.getDeclaredField("PARTICLES");
        particles.setAccessible(true);
        @SuppressWarnings("unchecked") var tracked = (List<Particle>) particles.get(null);
        tracked.add(own);
        var engine = (Engine) Minecraft.getInstance().particleEngine;
        engine.emitted.add(own);
        engine.emitted.add(unrelated);
        AshFog.tick(level, new Vec3(0, 84, 0), .8f);
        check(AshFog.active(), "Fog is active before disabling");
        Field surfaceField = AshPreview.class.getDeclaredField("SURFACES");
        surfaceField.setAccessible(true);
        var surfaces = (AshSurfaceCache) surfaceField.get(null);
        surfaces.sample(level, 3, 7, 1);
        var target = (DepthTarget) unsafe.allocateInstance(DepthTarget.class);
        MinimalNativeBootstrap.set(AshVeil.class, null, "depthCopy", target);
        MinimalNativeBootstrap.set(AshVeil.class, null, "bufferLevel", level);
        MinimalNativeBootstrap.set(AshVeil.class, null, "elapsedTicks", 40);
        Field renderThread = RenderSystem.class.getDeclaredField("renderThread");
        renderThread.setAccessible(true);
        Object previousRenderThread = renderThread.get(null);
        renderThread.set(null, Thread.currentThread());
        try {
            configure.invoke(null, false);
        } finally {
            renderThread.set(null, previousRenderThread);
        }
        check(stormActive() && envelope() == null && tracked.isEmpty(), "Disabling clears presentation while retaining indefinite authority");
        check(own.getLifetime() == 0, "Disabling expires owned ash on its next engine tick");
        close(particleFloat(own, "alpha"), 0, "Disabling immediately hides owned ash before the next particle tick");
        check(!AshFog.active() && !Float.isFinite(AshFog.groundHeight()), "Disabling clears active fog and terrain state");
        check(target.destroyed, "Disabling releases the owned veil framebuffer on the render thread");
        Field depth = AshVeil.class.getDeclaredField("depthCopy");
        depth.setAccessible(true);
        check(depth.get(null) == null, "Disabling releases the veil framebuffer reference");
        Field surfaceLevel = AshSurfaceCache.class.getDeclaredField("level");
        surfaceLevel.setAccessible(true);
        check(surfaceLevel.get(surfaces) == null, "Disabling clears the surface cache's world reference");
        for (String cache : List.of("SOURCES", "ASH")) {
            Field field = AshPreview.class.getDeclaredField(cache);
            field.setAccessible(true);
            for (Object descriptor : (Object[]) field.get(null)) check(descriptor == null, "Disabling releases " + cache + " descriptors");
        }
        check(unrelated.getLifetime() > 0 && unrelated.isAlive() && engine.emitted.contains(unrelated),
                "Disabling preserves unrelated native weather particles");
        check(ParticleSpawner.particleCount == countBefore + 2,
                "Disabling leaves native counters to their owning particle lifecycle");
        synchronize(true, 501);
        check(stormActive() && envelope() == null && tracked.isEmpty(), "Disabled clients accept new authority without restarting effects");
        int emittedBefore = engine.emitted.size();
        int readsBefore = level.heightReads;
        for (int i = 0; i < 12500; i++) tickPreview();
        check(engine.emitted.size() == emittedBefore && level.heightReads == readsBefore && !AshFog.active(),
                "Disabled ticks emit nothing and never sample terrain or restore fog");
        configure.invoke(null, false);
        configure.invoke(null, true);
        check(stormActive(), "Disabled presentation cannot expire indefinite server authority");
        tickPreview();
        check(stormActive() && envelope() != null && envelope().value() < .001f && tracked.isEmpty(),
                "Re-enabling gently enters the retained indefinite storm");
        configure.invoke(null, false);
        clearWeather();
        configure.invoke(null, true);
        tickPreview();
        check(!stormActive() && envelope() == null && !AshFog.active(),
                "A clear received while disabled stays clear when presentation is re-enabled");
        disconnect();
        own.remove();
        unrelated.remove();
        engine.emitted.clear();
        check(ParticleSpawner.particleCount == countBefore, "Config lifecycle balances native particle ownership");
    }

    public static void main(String[] args) throws Exception {
        // Reflection lets the first fixture run fail for the missing integration rather than fail compilation.
        Method create;
        try {
            create = Class.forName("net.quiwh.cwivisuals.weather.AshParticleProfiles")
                    .getDeclaredMethod("create", ParticleData.class);
            create.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchMethodException missing) {
            throw new AssertionError("Missing gray native sandstorm integration: AshParticleProfiles.create(ParticleData)", missing);
        }
        MinimalNativeBootstrap.initialize();
        AirLevel level = initializeClient();
        Map<String, ParticleData> defaults = new LinkedHashMap<>();
        try (var input = NativeAshParticleFixture.class.getResourceAsStream("/assets/particlerain/particles.json")) {
            check(input != null, "Particle Rain native descriptor resource is present");
            ParticleLoader.deserializeParticles(new InputStreamReader(input, StandardCharsets.UTF_8), defaults);
        }
        ParticleLoader.particles = defaults;
        for (String id : List.of("dust", "skittering_dust", "dust_haze")) {
            ParticleData source = defaults.get(id);
            check(source != null, "Native sandstorm descriptor " + id + " exists");
            source.updateTransientVariables();
            String before = ParticleLoader.GSON.toJson(source);
            ParticleData ash = (ParticleData) create.invoke(null, source);
            check(ash != source, id + " owns a separate descriptor");
            check(id.equals(ash.id), id + " preserves native sprite/wind identity");
            check(appearanceAndMotion(ash).equals(appearanceAndMotion(source)), id + " preserves untuned native settings");
            check(ash.renderType == ParticleData.RenderType.TRANSLUCENT, id + " supports thin translucent dust");
            check(ash.precipitation.containsAll(List.of(Biome.Precipitation.NONE, Biome.Precipitation.RAIN, Biome.Precipitation.SNOW)),
                    id + " preview can cross all precipitation climates");
            check(ash.registeredParticle != null && ash.biomeList.tags != null && ash.blockList.tags != null,
                    id + " rebuilds transient native lookup state");
            actualParticle(level, source, ash);
            check(before.equals(ParticleLoader.GSON.toJson(source)), id + " source stays untouched after native particle use");
            ash.spriteLocations.add("cwi_visual:test_only");
            ash.biomeList.entries.add("cwi_visual:test_only");
            ash.blockList.entries.clear();
            ash.precipitation.clear();
            check(before.equals(ParticleLoader.GSON.toJson(source)), id + " edits to cloned nested lists cannot mutate native weather");
            check(ParticleLoader.particles == defaults && ParticleLoader.particles.get(id) == source,
                    id + " never replaces Particle Rain's registered descriptor");
        }
        AshTrackingFixture.verify(level, java.util.Arrays.asList(args).contains("--benchmark-tracking"));
        synchronizedLifecycle(level);
        disablingPreview(level);
        actualEmitter(level);
        System.out.println("NativeAshParticleFixture PASS: " + assertions
                + " assertions; native gray particles, indefinite authority, config/dimension/login lifecycle, budget, existing fog and explicit cleanup.");
        System.out.println("Headless contract only; graphical appearance and installed LivingWind/AsyncParticles mixins require client acceptance.");
    }
}
