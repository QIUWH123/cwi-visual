package net.quiwh.cwivisuals.weather;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.world.level.biome.Biome;
import pigcart.particlerain.ParticleLoader;
import pigcart.particlerain.config.ParticleData;

/** Private sandstorm descriptors retuned as soft, visible ash-grey fog wisps. */
public final class AshParticleProfiles {
    static final String SOFT_SPRITE = "particlerain:fog_translucent";
    static final int GREY_CHANNEL = 96;

    private AshParticleProfiles() {}

    public static ParticleData create(ParticleData source) {
        ParticleData ash = ParticleLoader.GSON.fromJson(
                ParticleLoader.GSON.toJson(source), ParticleData.class);
        ash.id = source.id;
        ash.tintType = ParticleData.TintType.CUSTOM;
        ash.customTint = new Color(GREY_CHANNEL, GREY_CHANNEL, GREY_CHANNEL);
        boolean grit = "skittering_dust".equals(source.id);
        boolean haze = "dust_haze".equals(source.id);
        ash.renderType = ParticleData.RenderType.TRANSLUCENT;
        ash.spriteLocations = new ArrayList<>(Arrays.asList(SOFT_SPRITE));
        ash.animateSprites = false;
        ash.rotationType = ParticleData.RotationType.COPY_CAMERA;
        ash.distanceScaling = 0f;
        ash.size = haze ? 2.0f : grit ? .16f : 1.25f;
        ash.opacity = haze ? .85f : grit ? .50f : .95f;
        ash.gravity = grit ? .008f : .004f;
        ash.windStrength = Math.min(source.windStrength, .06f);
        ash.stormWindStrength = Math.min(source.stormWindStrength, .08f);
        ash.bounciness = 0f;
        ash.rotationAmount = .02f;
        // CWI's surface biomes include rainy climates. Keep manually spawned dust alive there.
        ash.precipitation = new ArrayList<>(Arrays.asList(Biome.Precipitation.values()));
        ash.updateTransientVariables();
        return ash;
    }
}
