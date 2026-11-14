package net.quiwh.cwivisuals;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.quiwh.cwivisuals.weather.network.AshWeatherNetwork;
import net.quiwh.cwivisuals.weather.server.AshWeatherServer;

/** Weather authority is common; all rendering remains in the separate client entrypoint. */
@Mod(CwiVisualCommon.ID)
public final class CwiVisualCommon {
    public static final String ID = "cwi_visual";

    public CwiVisualCommon(IEventBus modBus) {
        modBus.addListener(AshWeatherNetwork::register);
        AshWeatherServer.register();
    }
}
