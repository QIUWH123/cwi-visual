package net.quiwh.cwivisuals.config;

import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.quiwh.cwivisuals.colour.ColourProfile;

/** Client policy is baked on the client thread; render workers never read config objects. */
public final class VisualsConfig {
    public static final List<String> DEFAULT_SOURCES = List.of(
            "minecraft:torch", "minecraft:wall_torch",
            "minecraft:soul_torch", "minecraft:soul_wall_torch",
            "minecraft:lantern", "minecraft:soul_lantern",
            "minecraft:campfire", "minecraft:soul_campfire",
            "minecraft:fire", "minecraft:soul_fire");

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Enable client weather and persistent world presentation. No gameplay changes.")
            .define("enabled", true);

    static { BUILDER.push("weather"); }
    public static final ModConfigSpec.BooleanValue WEATHER = BUILDER
            .comment("Render server-selected ash storms with particles, surface fog and rolling mist. Disabling clears local effects while retaining the selected storm.")
            .define("enabled", true);
    static { BUILDER.pop(); }

    static { BUILDER.push("firelight"); }
    public static final ModConfigSpec.BooleanValue FIRELIGHT = BUILDER
            .comment("Flicker around the explicit light-source allowlist. Active shader packs and standalone Firelight disable this module.")
            .define("enabled", true);
    public static final ModConfigSpec.DoubleValue FIRELIGHT_STRENGTH = BUILDER
            .comment("Gentle default. Zero disables flicker exactly; 1 is upstream Firelight strength.")
            .defineInRange("strength", 0.35, 0.0, 1.0);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SOURCES = BUILDER
            .comment("Only these block IDs create flicker. Must emit light in their current state; unlit/waterlogged states are excluded.",
                    "Unknown IDs are ignored. An empty list disables all sources. Other luminous blocks can be added explicitly.")
            .defineListAllowEmpty("sources", DEFAULT_SOURCES, VisualsConfig::validId);

    static { BUILDER.pop().push("colour"); }
    public static final ModConfigSpec.BooleanValue COLOUR = BUILDER.define("enabled", true);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSIONS = BUILDER
            .comment("Dimensions receiving colour grading. Empty means no dimensions. Firelight is independent of this list.")
            .defineListAllowEmpty("dimensions", List.of("minecraft:overworld"), VisualsConfig::validId);
    public static final ModConfigSpec.IntValue GRASS_TARGET = target("grassTarget", 0x8B8A62);
    public static final ModConfigSpec.DoubleValue GRASS_STRENGTH = strength("grassStrength", 0.18);
    public static final ModConfigSpec.IntValue FOLIAGE_TARGET = target("foliageTarget", 0x7D805E);
    public static final ModConfigSpec.DoubleValue FOLIAGE_STRENGTH = strength("foliageStrength", 0.15);
    public static final ModConfigSpec.IntValue WATER_TARGET = target("waterTarget", 0x617675);
    public static final ModConfigSpec.DoubleValue WATER_STRENGTH = strength("waterStrength", 0.12);
    public static final ModConfigSpec.IntValue SKY_TARGET = target("skyTarget", 0xA6ADA0);
    public static final ModConfigSpec.DoubleValue SKY_STRENGTH = strength("skyStrength", 0.08);

    static { BUILDER.pop(); }
    public static final ModConfigSpec SPEC = BUILDER.build();

    private VisualsConfig() {}

    public static ColourProfile colourProfile() {
        return new ColourProfile(ENABLED.get() && COLOUR.get(), Set.copyOf(DIMENSIONS.get()),
                new ColourProfile.Tint(GRASS_TARGET.get(), GRASS_STRENGTH.get()),
                new ColourProfile.Tint(FOLIAGE_TARGET.get(), FOLIAGE_STRENGTH.get()),
                new ColourProfile.Tint(WATER_TARGET.get(), WATER_STRENGTH.get()),
                new ColourProfile.Tint(SKY_TARGET.get(), SKY_STRENGTH.get()));
    }

    private static ModConfigSpec.IntValue target(String name, int defaultValue) {
        return BUILDER.comment("RGB target as an integer, 0 to 16777215 (0xRRGGBB).").defineInRange(name, defaultValue, 0, 0xFFFFFF);
    }

    private static ModConfigSpec.DoubleValue strength(String name, double defaultValue) {
        return BUILDER.comment("Blend amount: 0 keeps the original colour, 1 uses the target.").defineInRange(name, defaultValue, 0.0, 1.0);
    }

    private static boolean validId(Object value) {
        return value instanceof String id && id.contains(":") && ResourceLocation.tryParse(id) != null;
    }
}
