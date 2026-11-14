package net.quiwh.cwivisuals.weather.server.mixin;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.WeatherCommand;
import net.quiwh.cwivisuals.weather.server.AshWeatherServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WeatherCommand.class)
public abstract class WeatherCommandMixin {
    @Inject(method = "setClear(Lnet/minecraft/commands/CommandSourceStack;I)I", at = @At("RETURN"))
    private static void cwi$leaveAsh(CommandSourceStack source, int duration, CallbackInfoReturnable<Integer> callback) {
        // Vanilla's default-duration clear returns -1 despite succeeding.
        AshWeatherServer.stop(source.getServer());
    }

    @Inject(method = {
            "setRain(Lnet/minecraft/commands/CommandSourceStack;I)I",
            "setThunder(Lnet/minecraft/commands/CommandSourceStack;I)I"
    }, at = @At("HEAD"))
    private static void cwi$requireClear(CommandSourceStack source, int duration, CallbackInfoReturnable<Integer> callback)
            throws CommandSyntaxException {
        AshWeatherServer.requireClear(source.getServer());
    }
}
