package net.quiwh.cwivisuals.weather;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;

/** Empty loader/registry context for native contracts; starts no game or graphics context. */
final class MinimalNativeBootstrap {
    static void initialize() throws Exception {
        FMLPaths.loadAbsolutePaths(Path.of("build/native-ash-game").toAbsolutePath());
        set(FMLLoader.class, null, "loadingModList",
                LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of()));
        set(FMLLoader.class, null, "dist", Dist.CLIENT);
        var mods = ModList.of(List.of(), List.of());
        set(ModList.class, mods, "indexedMods", Map.of());
        set(ModList.class, mods, "mods", List.of());
        set(ModList.class, mods, "sortedContainers", List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
