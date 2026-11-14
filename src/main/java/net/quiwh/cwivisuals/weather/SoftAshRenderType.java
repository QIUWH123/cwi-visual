package net.quiwh.cwivisuals.weather;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.quiwh.cwivisuals.CwiVisuals;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** An ash-only layer that preserves low alpha instead of cutting off soft dust edges. */
@EventBusSubscriber(modid = CwiVisuals.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SoftAshRenderType implements ParticleRenderType {
    public static final SoftAshRenderType INSTANCE = new SoftAshRenderType();
    private static final ResourceLocation SHADER_ID =
            ResourceLocation.fromNamespaceAndPath(CwiVisuals.ID, "soft_ash");
    private static ShaderInstance shader;

    private SoftAshRenderType() {}

    @SubscribeEvent
    public static void registerShader(RegisterShadersEvent event) throws IOException {
        shader = null;
        event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADER_ID,
                DefaultVertexFormat.PARTICLE), loaded -> shader = loaded);
    }

    @Override
    public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
        // Resource loading owns the shader lifecycle; skip this batch until its callback runs.
        ShaderInstance current = shader;
        if (current == null) return null;
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> current);
        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
    }

    @Override
    public String toString() {
        return "CWI_SOFT_ASH";
    }
}
