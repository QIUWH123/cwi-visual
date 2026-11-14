package net.quiwh.cwivisuals;

import com.mojang.logging.LogUtils;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.quiwh.cwivisuals.colour.WorldColours;
import net.quiwh.cwivisuals.config.VisualsConfig;
import net.quiwh.cwivisuals.firelight.FirelightClient;
import net.quiwh.cwivisuals.weather.AshPreview;
import net.quiwh.cwivisuals.weather.network.AshWeatherNetwork;
import org.slf4j.Logger;

@Mod(value = CwiVisuals.ID, dist = Dist.CLIENT)
public final class CwiVisuals {
    public static final String ID = "cwi_visual";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean configurationPending;
    private static ClientLevel previousLevel;

    public CwiVisuals(IEventBus modBus, ModContainer container) {
        AshWeatherNetwork.setClientReceiver(AshPreview::applySync);
        container.registerConfig(ModConfig.Type.CLIENT, VisualsConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(CwiVisuals::configLoaded);
        modBus.addListener(CwiVisuals::configReloaded);
        modBus.addListener(CwiVisuals::reloadListeners);
        NeoForge.EVENT_BUS.addListener(CwiVisuals::tick);
        NeoForge.EVENT_BUS.addListener(CwiVisuals::loggedOut);
        LOGGER.info("CWI Visual loaded: ash weather, client world colours and selected-source firelight");
    }

    private static void configLoaded(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == VisualsConfig.SPEC) configurationPending = true;
    }

    private static void configReloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == VisualsConfig.SPEC) configurationPending = true;
    }

    private static void reloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            // Reload application is on the game thread. Rebuild sources and uniforms next tick.
            FirelightClient.reset();
        });
    }

    private static void tick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean changed = configurationPending;
        if (changed) {
            configurationPending = false;
            WorldColours.configure(VisualsConfig.colourProfile());
            AshPreview.configure(VisualsConfig.ENABLED.get() && VisualsConfig.WEATHER.get());
            FirelightClient.configure(VisualsConfig.ENABLED.get() && VisualsConfig.FIRELIGHT.get(),
                    VisualsConfig.FIRELIGHT_STRENGTH.get().floatValue(), Set.copyOf(VisualsConfig.SOURCES.get()));
        }
        ClientLevel level = minecraft.level;
        if (level != previousLevel) {
            FirelightClient.reset();
            previousLevel = level;
            changed = true;
        }
        if (changed) {
            if (level == null) {
                WorldColours.clear();
            } else {
                WorldColours.enterDimension(level.dimension().location().toString());
                minecraft.levelRenderer.allChanged();
            }
        }
    }

    private static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        AshPreview.disconnect();
        WorldColours.clear();
        FirelightClient.reset();
        previousLevel = null;
    }
}
