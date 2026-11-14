package net.quiwh.cwivisuals.weather;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import javax.imageio.ImageIO;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import pigcart.particlerain.ParticleLoader;
import pigcart.particlerain.ParticleSpawner;
import pigcart.particlerain.config.ConfigManager;
import pigcart.particlerain.config.ParticleData;
import pigcart.particlerain.mixin.access.ParticleAccessor;
import pigcart.particlerain.particle.CustomParticle;

/** Counts real native collision calls and verifies event alpha without a window or loader mixins. */
public final class LightAshParticleFixture {
    private static int checks;
    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
    private static void close(double actual, double expected, String reason) {
        check(Math.abs(actual - expected) < 1.0E-5, reason + ": " + actual + " != " + expected);
    }
    private static final class CountingLevel extends ClientLevel {
        RandomSource fixtureRandom;
        Holder<Biome> fixtureBiome;
        int casts;
        double barrier = Double.POSITIVE_INFINITY;
        Vec3 from;
        Vec3 to;
        private CountingLevel() { super(null, null, null, null, 0, 0, null, null, false, 0); }
        @Override public RandomSource getRandom() { return fixtureRandom; }
        @Override public Holder<Biome> getBiome(BlockPos pos) { return fixtureBiome; }
        @Override public boolean isThundering() { return false; }
        @Override public float getRainLevel(float partialTick) { return 0; }
        @Override public float getThunderLevel(float partialTick) { return 0; }
        @Override public long getGameTime() { return 0; }
        @Override public BlockState getBlockState(BlockPos pos) { return Blocks.AIR.defaultBlockState(); }
        @Override public FluidState getFluidState(BlockPos pos) { return Fluids.EMPTY.defaultFluidState(); }
        @Override public BlockHitResult clip(ClipContext context) {
            casts++;
            from = context.getFrom();
            to = context.getTo();
            if (from.x <= barrier && to.x >= barrier) {
                Vec3 hit = new Vec3(barrier, to.y, to.z);
                return new BlockHitResult(hit, Direction.WEST, BlockPos.containing(hit), false);
            }
            return BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to));
        }
    }

    // Only the accessor interface normally attached to Particle by mixins is supplied here.
    private static class Native extends CustomParticle implements ParticleAccessor {
        Native(ClientLevel level, double x, ParticleData data) { super(level, x, 96, 0, data); }
        void velocity(double dx, double dy, double dz) { xd = dx; yd = dy; zd = dz; }
        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getZ() { return z; }
        @Override public double getXd() { return xd; }
        @Override public void setXd(double value) { xd = value; }
        @Override public double getZd() { return zd; }
        @Override public void setZd(double value) { zd = value; }
    }
    private static final class Optimized extends AshParticle {
        int hits;
        Optimized(ClientLevel level, double x, ParticleData data, boolean light) { super(level, x, 96, 0, data, light); }
        void velocity(double dx, double dy, double dz) { xd = dx; yd = dy; zd = dz; }
        void age(int value) { age = value; }
        void position(double x) { setPos(x, 96, 0); }
        @Override public void onCollision(BlockHitResult hit) { hits++; super.onCollision(hit); }
    }
    private static float value(Particle particle, String name) throws Exception {
        Field f = Particle.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.getFloat(particle);
    }

    private static void softRendering(float opacity) throws Exception {
        String root = "/assets/cwi_visual/shaders/core/soft_ash";
        var json = com.google.gson.JsonParser.parseReader(new InputStreamReader(
                LightAshParticleFixture.class.getResourceAsStream(root + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
        var vanilla = com.google.gson.JsonParser.parseReader(new InputStreamReader(
                LightAshParticleFixture.class.getResourceAsStream("/assets/minecraft/shaders/core/particle.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        check(json.get("fragment").getAsString().equals("cwi_visual:soft_ash"), "Ash shader resolves its scoped fragment");
        json.remove("fragment"); vanilla.remove("fragment");
        check(json.equals(vanilla), "Soft ash preserves the native vertex format, light and fog uniforms");
        String fragment;
        try (var in = LightAshParticleFixture.class.getResourceAsStream(root + ".fsh")) {
            fragment = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        var threshold = java.util.regex.Pattern.compile("color\\.a\\s*<\\s*([0-9.]+)").matcher(fragment);
        check(threshold.find(), "Soft fragment declares its alpha cutoff");
        double cutoff = Double.parseDouble(threshold.group(1));
        String thickerAlpha = "color.a=1.0-(1.0-color.a)*(1.0-color.a);";
        String compactFragment = fragment.replaceAll("\\s+", "");
        boolean doubleThickness = compactFragment.contains(thickerAlpha);
        try (var in = LightAshParticleFixture.class.getResourceAsStream(
                "/assets/particlerain/textures/particle/fog_translucent.png")) {
            var sprite = ImageIO.read(in);
            int calmPixels = 0;
            int visiblePixels = 0;
            int featheredPixels = 0;
            double peakAlpha = 0;
            for (int y = 0; y < sprite.getHeight(); y++) for (int x = 0; x < sprite.getWidth(); x++) {
                double nativeAlpha = (sprite.getRGB(x, y) >>> 24) / 255.0 * opacity;
                double alpha = doubleThickness ? 1 - (1 - nativeAlpha) * (1 - nativeAlpha) : nativeAlpha;
                double calmAlpha = nativeAlpha * .25;
                if (doubleThickness) calmAlpha = 1 - (1 - calmAlpha) * (1 - calmAlpha);
                check(Double.isFinite(alpha) && alpha >= 0 && alpha < 1,
                        "Soft fog texture remains a finite translucent layer");
                peakAlpha = Math.max(peakAlpha, alpha);
                if (alpha >= .1) visiblePixels++;
                if (alpha > cutoff && alpha < .05) featheredPixels++;
                if (calmAlpha >= cutoff) calmPixels++;
            }
            check(peakAlpha > .55 && peakAlpha < .7,
                    "Airborne ash texture has a stronger translucent core without becoming a solid sheet: " + peakAlpha);
            check(visiblePixels > sprite.getWidth() * sprite.getHeight() / 100,
                    "Visible fog occupies a patch of the sprite rather than a single bright pixel");
            check(featheredPixels > 0, "Fog retains faint feathered edges alongside its more visible core");
            check(calmPixels > 0, "Actual soft texture remains visible through the ash shader in calm periods");
        }
        check(doubleThickness && compactFragment.indexOf(thickerAlpha) < compactFragment.indexOf("if(color.a<"),
                "Shader increases optical thickness before applying its soft-edge cutoff");
    }

    public static void main(String[] args) throws Exception {
        try { Class.forName("net.quiwh.cwivisuals.weather.AshParticle"); }
        catch (ClassNotFoundException missing) {
            throw new AssertionError("Missing ash particle with reduced distant collisions and event fading", missing);
        }
        MinimalNativeBootstrap.initialize();
        var setup = NativeAshParticleFixture.class.getDeclaredMethod("initializeClient");
        setup.setAccessible(true);
        ClientLevel base = (ClientLevel) setup.invoke(null);
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) uf.get(null);
        var level = (CountingLevel) unsafe.allocateInstance(CountingLevel.class);
        level.fixtureRandom = RandomSource.create(4151);
        level.fixtureBiome = base.getBiome(BlockPos.ZERO);
        level.barrier = Double.POSITIVE_INFINITY;
        ConfigManager.getConfig().perf.particleDistance = 128;
        ConfigManager.getConfig().perf.surfaceRange = 128;
        var defaults = new LinkedHashMap<String, ParticleData>();
        try (var in = LightAshParticleFixture.class.getResourceAsStream("/assets/particlerain/particles.json")) {
            ParticleLoader.deserializeParticles(new InputStreamReader(in, StandardCharsets.UTF_8), defaults);
        }
        for (ParticleData p : defaults.values()) p.updateTransientVariables();
        ParticleData dust = AshParticleProfiles.create(defaults.get("dust"));
        ParticleData grit = AshParticleProfiles.create(defaults.get("skittering_dust"));
        softRendering(dust.opacity);
        int countBefore = ParticleSpawner.particleCount;
        var softDust = new Optimized(level, 10, dust, true);
        for (int tick = 0; tick < 100; tick++) {
            double oldX = softDust.getXd(), oldZ = softDust.getZd();
            // WindLink can replace a descriptor's wind strength with a strong target velocity.
            softDust.setXd(tick < 50 ? 2 : -2);
            softDust.setZd(tick < 50 ? 2 : -2);
            check(Math.hypot(softDust.getXd(), softDust.getZd()) <= .05,
                    "Fine dust stays below one block per second under strong wind targets");
            check(Math.abs(softDust.getXd() - oldX) <= .00101
                            && Math.abs(softDust.getZd() - oldZ) <= .00101,
                    "Wind changes accelerate dust gently, including direction reversals");
        }
        softDust.age(20);
        softDust.tickFading();
        check(value(softDust, "alpha") >= .4 && value(softDust, "alpha") < 1,
                "Full-strength dust supports a visible fog patch while remaining translucent");
        softDust.remove();
        var nativeFar = new Native(level, 40, dust);
        nativeFar.velocity(.1, -.05, 0);
        for (int i = 0; i < 12; i++) nativeFar.tickCollisions();
        check(level.casts == 12, "Native far particle casts every tick");
        nativeFar.remove();
        level.casts = 0;
        var far = new Optimized(level, 40, dust, true);
        far.velocity(.1, -.05, 0);
        for (int i = 0; i < 12; i++) far.tickCollisions();
        check(level.casts == 3, "Distant ash makes three casts instead of twelve");
        far.remove();
        level.casts = 0;
        var tickingFar = new Optimized(level, 40, dust, true);
        for (int i = 0; i < 12; i++) tickingFar.tick();
        check(tickingFar.isAlive() && level.casts == 3,
                "Native tick dispatch uses reduced collision work while keeping distant ash alive");
        tickingFar.remove();
        level.casts = 0;
        var near = new Optimized(level, 10, dust, true);
        near.velocity(.1, -.05, 0);
        for (int i = 0; i < 12; i++) near.tickCollisions();
        check(level.casts == 12, "Nearby dust retains full native collision rate");
        near.remove();
        level.casts = 0;
        var skitter = new Optimized(level, 40, grit, false);
        skitter.velocity(.1, -.05, 0);
        for (int i = 0; i < 12; i++) skitter.tickCollisions();
        check(level.casts == 12, "Ground grit retains full native collision rate at distance");
        skitter.remove();

        level.casts = 0;
        var swept = new Optimized(level, 40, dust, true);
        swept.velocity(.1, 0, 0);
        swept.tickCollisions();
        level.barrier = 42;
        for (int i = 1; i <= 4; i++) { swept.position(40 + i); swept.tickCollisions(); }
        check(level.casts == 2 && swept.hits == 1, "Swept query finds a wall crossed between skipped checks");
        close(level.from.x, 40, "Swept query starts at previous checked position");
        check(level.to.x > 44, "Swept query includes current native lookahead");
        swept.remove();
        level.barrier = Double.POSITIVE_INFINITY;

        var appearance = new Optimized(level, 10, dust, true);
        var control = new Native(level, 10, dust);
        check(appearance.profileId().equals("dust"), "Profile identity is retained");
        check(appearance.getLifetime() == Math.min(dust.lifetime, 100), "Airborne ash has a bounded instance lifetime");
        check(dust.lifetime.equals(defaults.get("dust").lifetime), "Lifetime cap does not mutate the private descriptor");
        appearance.tickFading();
        close(value(appearance, "alpha"), 0, "New ash begins transparent");
        appearance.age(20);
        appearance.setVisibility(.25f);
        appearance.tickFading(); control.tickFading();
        close(value(appearance, "alpha"), value(control, "alpha") * .25, "Event envelope scales native opacity");
        appearance.tickFading();
        close(value(appearance, "alpha"), value(control, "alpha") * .25, "Repeated fades do not multiply opacity again");
        appearance.setVisibility(1); appearance.tickFading();
        close(value(appearance, "alpha"), value(control, "alpha"), "Strong period restores native opacity");
        appearance.setVisibility(Float.NaN); appearance.tickFading();
        close(value(appearance, "alpha"), 0, "Invalid visibility cannot produce nonfinite render alpha");
        appearance.setVisibility(1);
        appearance.age(appearance.getLifetime() - 15); appearance.tickFading();
        close(value(appearance, "alpha"), value(control, "alpha") * .5, "Particle's final thirty ticks ease out");
        appearance.age(appearance.getLifetime()); appearance.tickFading();
        close(value(appearance, "alpha"), 0, "Particle reaches transparent before retirement");
        appearance.age(20);
        double wind = appearance.getXd();
        appearance.tick();
        check(appearance.getXd() < wind, "Real native wind still accelerates optimized particle");
        for (String channel : new String[] {"rCol", "gCol", "bCol"}) {
            close(value(appearance, channel), 96 / 255., "Ash-grey native tint remains " + channel);
        }
        check(appearance.getRenderType() == SoftAshRenderType.INSTANCE,
                "Fine dust uses its soft alpha layer rather than the native ten-percent cutoff");
        check(control.getRenderType() == dust.renderType.get(), "Other Particle Rain particles retain their native layer");
        appearance.remove(); control.remove();
        var shortGrit = new Optimized(level, 10, grit, false);
        check(shortGrit.getLifetime() == Math.min(grit.lifetime, 100), "Native shorter grit lifetime remains shorter");
        shortGrit.remove();
        check(ParticleSpawner.particleCount == countBefore, "Native construction and removal counters remain balanced");
        System.out.println("LightAshParticleFixture PASS: " + checks
                + " checks; distant casts 12→3, near/grit collision parity, swept wall, native wind/tint/counters and event alpha.");
    }
}
