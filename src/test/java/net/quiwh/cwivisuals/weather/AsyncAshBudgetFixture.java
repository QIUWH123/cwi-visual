package net.quiwh.cwivisuals.weather;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import net.neoforged.fml.ModList;
import pigcart.particlerain.ParticleSpawner;
import pigcart.particlerain.config.ConfigData;
import pigcart.particlerain.config.ConfigManager;

/** Reproduces AsyncParticles' separate live count and frozen native count without starting Minecraft. */
public final class AsyncAshBudgetFixture {
    public static void main(String[] args) throws Exception {
        MinimalNativeBootstrap.initialize();
        MinimalNativeBootstrap.set(ModList.class, ModList.get(), "indexedMods",
                Collections.singletonMap("asyncparticles", null));
        var config = new ConfigData();
        config.perf.particleAmount = 1500;
        MinimalNativeBootstrap.set(ConfigManager.class, null, "config", config);
        Class<?> compat = Class.forName(
                "neoforge.fun.qu_an.minecraft.asyncparticles.client.compat.particlerain.ParticleRainCompat");
        var live = (AtomicInteger) compat.getField("particleCount").get(null);
        var hasBudget = AshPreview.class.getDeclaredMethod("hasBudget");
        hasBudget.setAccessible(true);
        int checks = 0;
        for (int cycle = 0; cycle < 100; cycle++) {
            // The installed constructor mixin leaves its last count in this vanilla field.
            // Its remove mixin decrements only the atomic count, never this field.
            ParticleSpawner.particleCount = config.perf.particleAmount;
            live.set(0);
            if (!(boolean) hasBudget.invoke(null)) {
                throw new AssertionError("Ash cannot restart after AsyncParticles removes the previous batch: cycle " + cycle);
            }
            checks++;
            live.set(config.perf.particleAmount);
            if ((boolean) hasBudget.invoke(null)) throw new AssertionError("Live weather cap must still block emission");
            checks++;
            live.decrementAndGet();
            if (!(boolean) hasBudget.invoke(null)) throw new AssertionError("Removing a live particle must reopen a slot");
            checks++;
            if (ParticleSpawner.particleCount != config.perf.particleAmount || live.get() != config.perf.particleAmount - 1) {
                throw new AssertionError("Budget reads must not reset either mod's counter");
            }
            checks++;
        }
        compat.getMethod("onParticleEngineClear").invoke(null);
        if (!(boolean) hasBudget.invoke(null)) throw new AssertionError("Engine clear must permit another preview");
        checks++;
        System.out.println("AsyncAshBudgetFixture PASS: " + checks
                + " checks against the installed AsyncParticles counter; 100 saturation/recovery cycles.");
    }
}
