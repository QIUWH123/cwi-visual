package net.quiwh.cwivisuals.weather.server;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.quiwh.cwivisuals.weather.network.AshWeatherNetwork;
import net.quiwh.cwivisuals.weather.network.AshWeatherPayload;

/** Indefinite Overworld ash authority and transition synchronization, without gameplay effects. */
public final class AshWeatherServer {
    private static final SimpleCommandExceptionType CLEAR_FIRST = new SimpleCommandExceptionType(
            Component.literal("Ashstorm is active. Use /weather clear before selecting rain or thunder."));

    public static void register() {
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> AshWeatherCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(AshWeatherServer::tick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> sync(event));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> sync(event));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> sync(event));
    }

    public static int start(CommandSourceStack source) {
        ServerLevel level = source.getServer().overworld();
        AshWeatherState state = AshWeatherState.get(level);
        state.start(level.getRandom().nextLong());
        holdClear(level);
        broadcast(level, snapshot(state));
        source.sendSuccess(() -> Component.literal("Set the weather to ashstorm"), true);
        return 1;
    }

    /** Called after a successful native clear command, including already-clear vanilla weather. */
    public static void stop(MinecraftServer server) {
        ServerLevel level = server.overworld();
        AshWeatherState state = AshWeatherState.existing(level);
        if (state != null && state.cancel()) broadcast(level, snapshot(state));
    }

    public static void requireClear(MinecraftServer server) throws CommandSyntaxException {
        AshWeatherState state = AshWeatherState.existing(server.overworld());
        if (state != null && state.active()) throw CLEAR_FIRST.create();
    }

    private static void tick(LevelTickEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)) return;
        AshWeatherState state = AshWeatherState.existing(level);
        if (state != null && state.active()) holdClear(level);
    }

    private static void holdClear(ServerLevel level) {
        // Refresh before native weather advances so automatic rain cannot interrupt ash.
        // This does not dirty our SavedData or generate per-tick network traffic.
        level.setWeatherParameters(2, 0, false, false);
    }

    private static AshWeatherPayload snapshot(AshWeatherState state) {
        return state == null || !state.active()
                ? new AshWeatherPayload(false, 0)
                : new AshWeatherPayload(true, state.seed());
    }

    private static void broadcast(ServerLevel level, AshWeatherPayload payload) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) AshWeatherNetwork.send(player, payload);
    }

    private static void sync(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.server.getLevel(Level.OVERWORLD);
            if (level != null) AshWeatherNetwork.send(player, snapshot(AshWeatherState.existing(level)));
        }
    }

    private AshWeatherServer() {}
}
