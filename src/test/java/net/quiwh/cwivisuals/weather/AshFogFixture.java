package net.quiwh.cwivisuals.weather;

import com.mojang.blaze3d.shaders.FogShape;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.regex.Pattern;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer.FogMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Real viewport events in an in-memory level; no render calls or graphics context. */
public final class AshFogFixture {
    private static int checks;
    private static Method tick, clear, render, color, amount, active, groundHeight, grey;

    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }

    private static void veilResources() throws Exception {
        String root = "/assets/cwi_visual/shaders/core/ash_veil";
        var metadata = JsonParser.parseReader(new InputStreamReader(
                AshFogFixture.class.getResourceAsStream(root + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
        var declared = new HashSet<String>();
        for (String stage : new String[] {"vertex", "fragment"}) {
            String name = metadata.get(stage).getAsString();
            check(name.startsWith("cwi_visual:"), "Veil shader stages remain owned resources");
            String path = "/assets/" + name.replace(":", "/shaders/core/")
                    + (stage.equals("vertex") ? ".vsh" : ".fsh");
            try (var stream = AshFogFixture.class.getResourceAsStream(path)) {
                check(stream != null, "Veil stage resolves: " + path);
                String shader = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                var uniforms = Pattern.compile("uniform\\s+\\w+\\s+(\\w+)\\s*;").matcher(shader);
                while (uniforms.find()) declared.add(uniforms.group(1));
            }
        }
        var supplied = new HashSet<String>();
        for (var sampler : metadata.getAsJsonArray("samplers")) supplied.add(sampler.getAsJsonObject().get("name").getAsString());
        for (var uniform : metadata.getAsJsonArray("uniforms")) {
            var definition = uniform.getAsJsonObject();
            supplied.add(definition.get("name").getAsString());
            check(definition.getAsJsonArray("values").size() == definition.get("count").getAsInt(),
                    "Veil uniform defaults match their declared size");
        }
        check(declared.equals(supplied), "All veil GLSL uniforms and depth sampler have native shader metadata");
    }

    private static final class Ground extends ClientLevel {
        boolean loaded = true;
        boolean liquid;
        int heightReads;
        private Ground() { super(null, null, null, null, 0, 0, null, null, false, 0); }
        @Override public boolean isLoaded(BlockPos pos) { return loaded; }
        @Override public FluidState getFluidState(BlockPos pos) {
            return liquid ? Fluids.WATER.defaultFluidState() : Fluids.EMPTY.defaultFluidState();
        }
        @Override public int getHeight(Heightmap.Types type, int x, int z) { heightReads++; return 80; }
    }

    private static final class View extends Camera {
        Vec3 position = new Vec3(0, 81.6, 0);
        FogType fluid = FogType.NONE;
        Entity entity;
        @Override public Vec3 getPosition() { return position; }
        @Override public FogType getFluidInCamera() { return fluid; }
        @Override public Entity getEntity() { return entity; }
    }

    private static final class Observer extends LocalPlayer {
        boolean blind, dark;
        private Observer() { super(null, null, null, null, null, false, false); }
        @Override public boolean hasEffect(Holder<MobEffect> effect) {
            return effect == MobEffects.BLINDNESS ? blind : effect == MobEffects.DARKNESS && dark;
        }
    }

    private static ViewportEvent.RenderFog fog(View view, float start, float end) throws Exception {
        var event = new ViewportEvent.RenderFog(FogMode.FOG_TERRAIN, view.fluid, view, 1, start, end, FogShape.CYLINDER);
        render.invoke(null, event);
        return event;
    }

    private static void unchanged(View view, String reason) throws Exception {
        var fog = fog(view, 230, 256);
        check(!fog.isCanceled() && fog.getNearPlaneDistance() == 230 && fog.getFarPlaneDistance() == 256, reason);
        var rgb = new ViewportEvent.ComputeFogColor(view, 1, .4f, .5f, .6f);
        color.invoke(null, rgb);
        check(rgb.getRed() == .4f && rgb.getGreen() == .5f && rgb.getBlue() == .6f, reason + " preserves colour");
        check((float) amount.invoke(null, view, 1d) == 0,
                reason + " also suppresses the depth-aware veil");
    }

    private static void advance(Ground ground, View view, float strength, int ticks) throws Exception {
        for (int i = 0; i < ticks; i++) tick.invoke(null, ground, view.position, strength);
    }

    public static void main(String[] args) throws Exception {
        veilResources();
        Class<?> type;
        try { type = Class.forName("net.quiwh.cwivisuals.weather.AshFog"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Missing gradual surface ash fog", missing); }
        tick = type.getDeclaredMethod("tick", ClientLevel.class, Vec3.class, float.class);
        clear = type.getDeclaredMethod("clear");
        render = type.getDeclaredMethod("render", ViewportEvent.RenderFog.class);
        color = type.getDeclaredMethod("color", ViewportEvent.ComputeFogColor.class);
        amount = type.getDeclaredMethod("amount", Camera.class, double.class);
        active = type.getDeclaredMethod("active");
        groundHeight = type.getDeclaredMethod("groundHeight");
        grey = type.getDeclaredMethod("grey");
        for (Method method : new Method[] {tick, clear, render, color, amount, active, groundHeight, grey}) method.setAccessible(true);
        MinimalNativeBootstrap.initialize();
        var setup = NativeAshParticleFixture.class.getDeclaredMethod("initializeClient");
        setup.setAccessible(true); setup.invoke(null);
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); uf.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) uf.get(null);
        var ground = (Ground) unsafe.allocateInstance(Ground.class); ground.loaded = true;
        var view = new View();
        Minecraft.getInstance().level = ground;
        clear.invoke(null);
        check(!(boolean) active.invoke(null), "Idle weather does not retain veil render resources");
        unchanged(view, "Idle weather does not alter baseline fog");

        advance(ground, view, 1, 40);
        check((boolean) active.invoke(null), "Active event supplies the depth-aware veil");
        check((float) amount.invoke(null, view, 1d) == 1,
                "Veil shares the fully exposed particle and fog strength");
        check((float) groundHeight.invoke(null) == 80,
                "Veil uses the cached local surface instead of sampling terrain during rendering");
        var peak = fog(view, 230, 256);
        check(peak.isCanceled(), "Active fog applies its viewport distances");
        check(peak.getNearPlaneDistance() >= -32 && peak.getNearPlaneDistance() <= 0,
                "Ash spreads a broad veil from nearby terrain toward the normal horizon");
        check(peak.getFarPlaneDistance() == 256,
                "Ash must not shorten Sodium's fog-based terrain culling distance");
        check(peak.getFogShape() == FogShape.CYLINDER, "Ash preserves the incoming fog shape");
        var tint = new ViewportEvent.ComputeFogColor(view, 1, .4f, .5f, .6f);
        color.invoke(null, tint);
        check((float) grey.invoke(null) > 0 && (float) grey.invoke(null) < .4f,
                "Veil receives the same dark ambient ash tint");
        check(tint.getRed() < .4f && tint.getGreen() < .5f && tint.getBlue() < .6f
                        && Math.abs(tint.getRed() - tint.getBlue()) < .1,
                "Ash darkens the atmosphere instead of adding a pale veil");
        var daylight = new ViewportEvent.ComputeFogColor(view, 1, .8f, .8f, .8f);
        color.invoke(null, daylight);
        check(daylight.getRed() < .45f && daylight.getGreen() < .45f && daylight.getBlue() < .45f,
                "Daylight ash stays dark neutral grey");
        int reads = ground.heightReads;
        for (int frame = 0; frame < 100; frame++) fog(view, 230, 256);
        check(ground.heightReads == reads, "Render frames do not sample terrain");
        var canceled = new ViewportEvent.RenderFog(FogMode.FOG_TERRAIN, FogType.NONE, view, 1, 8, 16, FogShape.SPHERE);
        canceled.setCanceled(true); render.invoke(null, canceled);
        check(canceled.getNearPlaneDistance() == 8 && canceled.getFarPlaneDistance() == 16,
                "Already-canceled fog from another mod remains untouched");
        var night = new ViewportEvent.ComputeFogColor(view, 1, .02f, .02f, .02f);
        color.invoke(null, night);
        check((float) grey.invoke(null) <= .02f,
                "Depth-aware mist cannot illuminate a dark night");
        check(night.getRed() <= .02f && night.getGreen() <= .02f && night.getBlue() <= .02f,
                "Ash tint does not brighten a dark neutral night");
        var shortRange = fog(view, 8, 16);
        check(shortRange.getNearPlaneDistance() <= 8 && shortRange.getFarPlaneDistance() == 16,
                "Ash preserves an already-shorter fog far plane");
        for (float end : new float[] {32, 64, 128, 256, 512, 1024}) {
            for (float strength : new float[] {.001f, .25f, .6f, 1}) {
                advance(ground, view, strength, 1);
                var broad = fog(view, end * .9f, end);
                check(broad.getFarPlaneDistance() == end,
                        "Every intensity preserves terrain range at far plane " + end);
                check(Float.isFinite(broad.getNearPlaneDistance())
                                && broad.getNearPlaneDistance() < end * .9f,
                        "Density grows by moving the start of a broad fade earlier");
            }
        }
        advance(ground, view, .25f, 1);
        var calm = fog(view, 230, 256);
        check(calm.getFarPlaneDistance() == 256 && calm.getNearPlaneDistance() > peak.getNearPlaneDistance()
                        && calm.getNearPlaneDistance() < 230,
                "Calm periods ease density without moving the terrain cutoff");

        for (FogType fluid : new FogType[] {FogType.WATER, FogType.LAVA, FogType.POWDER_SNOW}) {
            view.fluid = fluid;
            unchanged(view, fluid + " keeps its native fog");
        }
        view.fluid = FogType.NONE;
        ground.liquid = true;
        advance(ground, view, 1, 1);
        unchanged(view, "Submerged cameras preserve fluid fog even when native FogType is NONE");
        ground.liquid = false;
        advance(ground, view, 1, 40);
        var observer = (Observer) unsafe.allocateInstance(Observer.class);
        view.entity = observer; observer.blind = true;
        unchanged(view, "Blindness keeps its native fog");
        observer.blind = false; observer.dark = true;
        unchanged(view, "Darkness keeps its native fog");
        view.entity = null;

        view.position = new Vec3(0, 60, 0);
        advance(ground, view, 1, 40);
        unchanged(view, "Caves and roofed locations are outside the surface fog");
        view.position = new Vec3(0, 128, 0);
        advance(ground, view, 1, 40);
        unchanged(view, "High flight clears the ground ash layer");
        view.position = new Vec3(0, 81.6, 0);
        ground.loaded = false; reads = ground.heightReads;
        advance(ground, view, 1, 40);
        check(ground.heightReads == reads, "Unloaded columns never request height data");
        unchanged(view, "Missing chunks do not supply ground fog");
        ground.loaded = true;
        advance(ground, view, 1, 40);
        float priorStart = fog(view, 230, 256).getNearPlaneDistance();
        for (int step = 1; step <= 100; step++) {
            advance(ground, view, 1 - step / 100f, 1);
            var clearing = fog(view, 230, 256);
            float start = clearing.getNearPlaneDistance();
            check(start >= priorStart && start <= 230 && clearing.getFarPlaneDistance() == 256,
                    "Clearing fog fades monotonically while preserving terrain range");
            priorStart = start;
        }
        unchanged(view, "Zero-strength event restores baseline colour and distances");
        advance(ground, view, 1, 40);
        Minecraft.getInstance().level = null;
        unchanged(view, "World exit suppresses stale fog before the next tick");
        Minecraft.getInstance().level = ground;
        clear.invoke(null);
        check(!(boolean) active.invoke(null) && (float) grey.invoke(null) == 0,
                "Cleanup releases veil activity and stale daytime tint");
        unchanged(view, "Explicit cleanup removes all ash fog state");
        System.out.println("AshFogFixture PASS: " + checks + " checks; native viewport events, surface gating, fades, fluid/effect guards and baseline restoration.");
    }
}
