package net.quiwh.cwivisuals.colour.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.quiwh.cwivisuals.colour.WorldColours;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientLevelColourMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void cwi$enterDimension(CallbackInfo callback) {
        ClientLevel level = (ClientLevel) (Object) this;
        WorldColours.enterDimension(level.dimension().location().toString());
    }

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void cwi$sky(Vec3 position, float partialTick, CallbackInfoReturnable<Vec3> result) {
        ClientLevel level = (ClientLevel) (Object) this;
        Vec3 original = result.getReturnValue();
        Vec3 colour = WorldColours.sky(original, level.dimension().location().toString());
        if (colour != original) result.setReturnValue(colour);
    }
}
