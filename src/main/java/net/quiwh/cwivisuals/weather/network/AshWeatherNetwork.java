package net.quiwh.cwivisuals.weather.network;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Optional client presentation channel; dedicated servers never resolve rendering classes. */
public final class AshWeatherNetwork {
    private static Consumer<AshWeatherPayload> clientReceiver = payload -> {};

    public static void register(RegisterPayloadHandlersEvent event) {
        // The registrar dispatches on MAIN by default, including integrated-server connections.
        event.registrar("2").optional().playToClient(AshWeatherPayload.TYPE, AshWeatherPayload.CODEC,
                (payload, context) -> receive(payload));
    }

    public static void setClientReceiver(Consumer<AshWeatherPayload> receiver) {
        clientReceiver = Objects.requireNonNull(receiver);
    }

    public static void receive(AshWeatherPayload payload) {
        clientReceiver.accept(payload);
    }

    public static void send(ServerPlayer player, AshWeatherPayload payload) {
        // Optional registration permits absent clients, but unnegotiated sends still throw.
        if (player.connection.hasChannel(AshWeatherPayload.TYPE)) PacketDistributor.sendToPlayer(player, payload);
    }

    private AshWeatherNetwork() {}
}
