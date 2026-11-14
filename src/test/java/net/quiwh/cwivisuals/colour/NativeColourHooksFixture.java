package net.quiwh.cwivisuals.colour;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Uses real native tint entry points and the production callbacks; does not emulate a loader transform. */
public final class NativeColourHooksFixture {
    public static void main(String[] args) throws Exception {
        ColourFixture.main(args);
        ColourFixture.enter("minecraft:overworld");
        ColourFixture.configure(true, Set.of("minecraft:overworld"), 0xA0B0C0, .5);
        BlockPos position = new BlockPos(12, 80, -7);
        ColorResolver[] observed = new ColorResolver[1];
        BlockAndTintGetter view = (BlockAndTintGetter) Proxy.newProxyInstance(
                NativeColourHooksFixture.class.getClassLoader(), new Class<?>[]{BlockAndTintGetter.class},
                (proxy, method, values) -> {
                    if (method.getName().equals("getBlockTint")) {
                        if (!position.equals(values[0])) throw new AssertionError("native tint changed position");
                        observed[0] = (ColorResolver) values[1];
                        return 0x7F204060;
                    }
                    throw new AssertionError("palette sampled live world data through " + method.getName());
                });
        Class<?> hooks = Class.forName("net.quiwh.cwivisuals.colour.mixin.BiomeColoursMixin");
        String[] nativeNames = {"getAverageGrassColor", "getAverageFoliageColor", "getAverageWaterColor"};
        String[] callbackNames = {"cwi$grass", "cwi$foliage", "cwi$water"};
        ColorResolver[] resolvers = {BiomeColors.GRASS_COLOR_RESOLVER,
                BiomeColors.FOLIAGE_COLOR_RESOLVER, BiomeColors.WATER_COLOR_RESOLVER};
        for (int i = 0; i < nativeNames.length; i++) {
            int nativeColour = (int) BiomeColors.class.getMethod(nativeNames[i], BlockAndTintGetter.class,
                    BlockPos.class).invoke(null, view, position);
            if (observed[0] != resolvers[i]) throw new AssertionError("unexpected native colour resolver");
            Method callback = hooks.getDeclaredMethod(callbackNames[i], BlockAndTintGetter.class,
                    BlockPos.class, CallbackInfoReturnable.class);
            callback.setAccessible(true);
            CallbackInfoReturnable<Integer> result = new CallbackInfoReturnable<>(nativeNames[i], true, nativeColour);
            callback.invoke(null, view, position, result);
            if (result.getReturnValueI() != 0x7F607890) throw new AssertionError("native palette callback did not tint RGB and preserve alpha");
            ColourFixture.clear();
            result = new CallbackInfoReturnable<>(nativeNames[i], true, nativeColour);
            callback.invoke(null, view, position, result);
            if (result.getReturnValueI() != nativeColour) throw new AssertionError("native palette leaked after disconnect");
            ColourFixture.enter("minecraft:overworld");
        }
        ClientLevel.class.getDeclaredMethod("getSkyColor", Vec3.class, float.class);
        System.out.println("NativeColourHooksFixture: 3 native resolvers and callback lifecycle passed; sky target exists");
        ColourFixture.clear();
    }
}
