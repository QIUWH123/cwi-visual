package net.quiwh.cwivisuals.weather;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer.FogMode;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.quiwh.cwivisuals.CwiVisuals;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** A surface haze tied to the ash envelope; render callbacks never sample terrain. */
@EventBusSubscriber(modid = CwiVisuals.ID, value = Dist.CLIENT)
public final class AshFog {
    private static ClientLevel fogLevel;
    private static float exposure;
    private static float previous;
    private static float current;
    private static float groundHeight = Float.NaN;
    private static float grey;
    private static boolean submerged;

    private AshFog() {}

    static void clear() {
        fogLevel = null;
        exposure = previous = current = 0;
        groundHeight = Float.NaN;
        grey = 0;
        submerged = false;
    }

    static void tick(ClientLevel level, Vec3 camera, float strength) {
        if (level != fogLevel) {
            clear();
            fogLevel = level;
        }
        previous = current;
        BlockPos position = BlockPos.containing(camera);
        float target = 0;
        submerged = false;
        if (level.isLoaded(position)) {
            var fluid = level.getFluidState(position);
            // Native FogType can be NONE in custom fluids handled by another mod.
            submerged = !fluid.isEmpty() && camera.y < position.getY() + fluid.getHeight(level, position);
            if (!submerged) {
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                        position.getX(), position.getZ());
                groundHeight = Float.isFinite(groundHeight)
                        ? Mth.lerp(.1f, groundHeight, surface) : surface;
                double height = camera.y - surface;
                if (height >= 0) {
                    float altitude = Mth.clamp((float) ((height - 8) / 16), 0, 1);
                    target = 1 - altitude * altitude * (3 - 2 * altitude);
                }
            }
        }
        // Ease crossing a roof/cave/altitude boundary over one second rather than snapping.
        exposure = Mth.approach(exposure, target, .05f);
        current = exposure * (Float.isFinite(strength) ? Mth.clamp(strength, 0, 1) : 0);
    }

    static boolean active() {
        return fogLevel != null && (previous > 0 || current > 0);
    }

    static float groundHeight() { return groundHeight; }

    static float grey() { return grey; }

    static float amount(Camera camera, double partialTick) {
        if (fogLevel == null || Minecraft.getInstance().level != fogLevel || submerged
                || camera.getFluidInCamera() != FogType.NONE) return 0;
        if (camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) return 0;
        return Mth.lerp(Mth.clamp((float) partialTick, 0, 1), previous, current);
    }

    @SubscribeEvent
    public static void render(ViewportEvent.RenderFog event) {
        if (event.isCanceled() || event.getMode() != FogMode.FOG_TERRAIN || event.getType() != FogType.NONE) return;
        float strength = amount(event.getCamera(), event.getPartialTick());
        float near = event.getNearPlaneDistance(), far = event.getFarPlaneDistance();
        if (strength <= 0 || !Float.isFinite(near) || !Float.isFinite(far) || far <= near) return;
        // Sodium also uses the fog end to cull terrain. Keep that horizon intact and
        // spread a broad fade across it; a slightly negative start gives nearby dust a veil.
        float start = Math.min(near, -Math.min(32, far / 8));
        event.setNearPlaneDistance(Mth.lerp(strength, near, start));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void color(ViewportEvent.ComputeFogColor event) {
        float strength = amount(event.getCamera(), event.getPartialTick()) * .95f;
        if (strength <= 0) return;
        float red = event.getRed(), green = event.getGreen(), blue = event.getBlue();
        // Neutralize the atmosphere without making nights brighter than their incoming fog.
        float brightness = .2126f * red + .7152f * green + .0722f * blue;
        grey = Math.min(Math.max(red, Math.max(green, blue)),
                Mth.clamp(brightness / .6f, 0, 1) * (AshParticleProfiles.GREY_CHANNEL / 255f));
        event.setRed(Mth.lerp(strength, red, grey));
        event.setGreen(Mth.lerp(strength, green, grey));
        event.setBlue(Mth.lerp(strength, blue, grey));
    }
}
