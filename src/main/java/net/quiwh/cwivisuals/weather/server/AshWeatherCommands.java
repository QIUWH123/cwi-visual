package net.quiwh.cwivisuals.weather.server;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Adds one literal to Minecraft's weather root without replacing native weather handlers. */
public final class AshWeatherCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("weather")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("ashstorm")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> AshWeatherServer.start(context.getSource()))));
    }

    private AshWeatherCommands() {}
}
