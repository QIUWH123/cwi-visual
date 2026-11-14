package net.quiwh.cwivisuals.weather;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.quiwh.cwivisuals.CwiVisuals;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Depth-aware ground mist, independently composited without changing terrain visibility. */
@EventBusSubscriber(modid = CwiVisuals.ID, value = Dist.CLIENT)
public final class AshVeil {
    private static final ResourceLocation SHADER_ID =
            ResourceLocation.fromNamespaceAndPath(CwiVisuals.ID, "ash_veil");
    private static final Matrix4f INVERSE = new Matrix4f();
    private static ShaderInstance shader;
    private static TextureTarget depthCopy;
    private static ClientLevel bufferLevel;
    private static boolean failed;
    private static boolean cleanupQueued;
    private static int elapsedTicks;

    private AshVeil() {}

    @EventBusSubscriber(modid = CwiVisuals.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ShaderRegistration {
        private ShaderRegistration() {}

        @SubscribeEvent
        public static void register(RegisterShadersEvent event) {
            shader = null;
            failed = false;
            releaseDepth();
            try {
                event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADER_ID,
                        DefaultVertexFormat.POSITION), loaded -> shader = loaded);
            } catch (IOException | RuntimeException exception) {
                failed = true;
                CwiVisuals.LOGGER.warn("Ash ground veil unavailable; base fog and particles remain active", exception);
            }
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (!AshFog.active() || client.level == null || client.level != bufferLevel && depthCopy != null) {
            clear();
        } else if (!client.isPaused()) {
            elapsedTicks++;
        }
    }

    static void clear() {
        elapsedTicks = 0;
        if (depthCopy != null && !cleanupQueued) {
            if (RenderSystem.isOnRenderThread()) releaseDepth();
            else {
                cleanupQueued = true;
                RenderSystem.recordRenderCall(AshVeil::releaseDepth);
            }
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft client = Minecraft.getInstance();
        ShaderInstance currentShader = shader;
        if (client.level == null || failed || currentShader == null || ShaderPacks.active()) return;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float strength = AshFog.amount(event.getCamera(), partialTick);
        float ground = AshFog.groundHeight();
        if (strength <= .0001f || !Float.isFinite(ground)) return;
        RenderTarget main = client.getMainRenderTarget();
        if (main.width <= 0 || main.height <= 0 || main.getDepthTextureId() < 0) return;

        INVERSE.set(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        if (!Float.isFinite(INVERSE.determinant()) || Math.abs(INVERSE.determinant()) < 1.0E-12f) return;
        INVERSE.invert();
        if (!INVERSE.isFinite()) return;

        RenderState state = RenderState.capture();
        try {
            // Sample a separate attachment: reading the depth of the framebuffer being
            // drawn into is undefined even when depth writes are disabled.
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            if (depthCopy == null || depthCopy.width != main.width || depthCopy.height != main.height
                    || depthCopy.isStencilEnabled() != main.isStencilEnabled() || bufferLevel != client.level) {
                releaseDepth();
                depthCopy = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
                if (main.isStencilEnabled()) depthCopy.enableStencil();
                bufferLevel = client.level;
            }
            depthCopy.copyDepthFrom(main);
            main.bindWrite(true);
            currentShader.setSampler("DepthSampler", depthCopy.getDepthTextureId());
            currentShader.safeGetUniform("InverseViewProjection").set(INVERSE);
            Vec3 camera = event.getCamera().getPosition();
            currentShader.safeGetUniform("CameraOffset").set(wrap(camera.x), (float) (camera.y - ground), wrap(camera.z));
            float time = (elapsedTicks + partialTick) / 20f;
            currentShader.safeGetUniform("Time").set(time);
            currentShader.safeGetUniform("Strength").set(strength);
            float grey = AshFog.grey();
            currentShader.safeGetUniform("AshColor").set(grey, grey, grey);
            currentShader.safeGetUniform("MaxDistance").set(Math.min(256f, client.options.getEffectiveRenderDistance() * 16f));
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
            RenderSystem.setShader(() -> currentShader);
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            buffer.addVertex(-1, -1, 0);
            buffer.addVertex(1, -1, 0);
            buffer.addVertex(1, 1, 0);
            buffer.addVertex(-1, 1, 0);
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } catch (RuntimeException exception) {
            failed = true;
            releaseDepth();
            CwiVisuals.LOGGER.warn("Ash ground veil stopped; base fog and particles remain active", exception);
        } finally {
            state.restore();
        }
    }

    private static float wrap(double coordinate) {
        return (float) (coordinate - Math.floor(coordinate / 6144.0) * 6144.0);
    }

    private static void releaseDepth() {
        if (depthCopy != null) {
            depthCopy.destroyBuffers();
            depthCopy = null;
        }
        bufferLevel = null;
        cleanupQueued = false;
    }

    /** Restore the surrounding renderer, including a mod's non-default blend or viewport. */
    private record RenderState(ShaderInstance shader, boolean depth, boolean blend, boolean depthWrite,
                               int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha,
                               int equationRgb, int equationAlpha, int drawFramebuffer, int readFramebuffer,
                               int activeTexture, int texture0, int[] viewport) {
        private static RenderState capture() {
            int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            RenderSystem.activeTexture(active);
            int[] viewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            return new RenderState(RenderSystem.getShader(), GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_BLEND), GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA),
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                    active, texture, viewport);
        }

        private void restore() {
            RenderSystem.setShader(() -> shader);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(texture0);
            RenderSystem.activeTexture(activeTexture);
            RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
            GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.depthMask(depthWrite);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    /** Active shader packs may replace the depth convention; leave their pipeline intact. */
    private static final class ShaderPacks {
        private static final Method ACTIVE;
        private static final Object API;
        private static final boolean UNKNOWN;

        static {
            Method active = null;
            Object api = null;
            boolean unknown = false;
            for (String name : new String[] {"net.irisshaders.iris.api.v0.IrisApi", "net.coderbot.iris.api.v0.IrisApi"}) {
                try {
                    Class<?> type = Class.forName(name);
                    api = type.getMethod("getInstance").invoke(null);
                    active = type.getMethod("isShaderPackInUse");
                    break;
                } catch (ClassNotFoundException absent) {
                    // This is an optional client integration.
                } catch (ReflectiveOperationException | LinkageError unavailable) {
                    unknown = true;
                }
            }
            ACTIVE = active;
            API = api;
            UNKNOWN = unknown;
        }

        private static boolean active() {
            if (ACTIVE == null) return UNKNOWN;
            try {
                return Boolean.TRUE.equals(ACTIVE.invoke(API));
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return true;
            }
        }
    }
}
