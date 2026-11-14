package net.quiwh.cwivisuals.weather.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.quiwh.cwivisuals.CwiVisualCommon;

/** Authoritative indefinite ash state; only a successful clear command ends it. */
public record AshWeatherPayload(boolean active, long seed) implements CustomPacketPayload {
    public static final Type<AshWeatherPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CwiVisualCommon.ID, "ash_weather"));
    public static final StreamCodec<FriendlyByteBuf, AshWeatherPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBoolean(payload.active);
                buffer.writeLong(payload.seed);
            }, buffer -> new AshWeatherPayload(buffer.readBoolean(), buffer.readLong()));

    @Override public Type<AshWeatherPayload> type() { return TYPE; }
}
