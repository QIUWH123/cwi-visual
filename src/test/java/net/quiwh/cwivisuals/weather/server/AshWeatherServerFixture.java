package net.quiwh.cwivisuals.weather.server;

import com.mojang.brigadier.CommandDispatcher;
import io.netty.buffer.Unpooled;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandSource;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.commands.WeatherCommand;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedPlayerList;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import sun.misc.Unsafe;

/** Native command, persistence and wire contracts; starts no Minecraft server or client. */
public final class AshWeatherServerFixture {
    private static int assertions;
    private static Class<?> stateType;
    private static Class<?> payloadType;

    public static void main(String[] args) throws Exception {
        try {
            stateType = Class.forName("net.quiwh.cwivisuals.weather.server.AshWeatherState");
            payloadType = Class.forName("net.quiwh.cwivisuals.weather.network.AshWeatherPayload");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Authoritative ash weather state and payload are missing", missing);
        }
        persistenceAndMigration();
        commandTree();
        nativeServiceLifecycle();
        payloadRoundTrips();
        nativeMixinTargets();
        commonSideBoundary();
        System.out.println("AshWeatherServerFixture: " + assertions + " command/state/wire/side checks passed");
        System.out.println("Headless native contracts only; loader transformation and multiplayer require runtime acceptance.");
    }

    private static void persistenceAndMigration() throws Exception {
        Object state = stateType.getConstructor().newInstance();
        check(!(boolean) call(state, "active"), "new state must be clear");
        check(!((SavedData) state).isDirty(), "new clear state must not dirty saves");
        try {
            call(state, "start", new Class<?>[]{long.class}, 71L);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("Indefinite ash state must start without a duration", missing);
        }
        check((boolean) call(state, "active"), "ash start did not activate persisted weather");
        check(((SavedData) state).isDirty(), "start must be saved");
        CompoundTag nbt = ((SavedData) state).save(new CompoundTag(), null);
        check(nbt.getBoolean("active") && nbt.getLong("seed") == 71L, "new save format lost indefinite weather state");
        check(!nbt.contains("durationTicks") && !nbt.contains("remainingTicks"), "new saves retain a timed storm");
        Object restored = stateType.getMethod("load", CompoundTag.class, HolderLookup.Provider.class).invoke(null, nbt, null);
        check((boolean) call(restored, "active") && (long) call(restored, "seed") == 71L, "reload did not restore indefinite ash");
        call(restored, "start", new Class<?>[]{long.class}, Long.MIN_VALUE);
        check((long) call(restored, "seed") == Long.MIN_VALUE, "repeated start must replace gust seed");
        check((boolean) call(restored, "cancel"), "active clear should report a state change");
        check(!(boolean) call(restored, "active"), "clear did not end persisted ash");
        check(!(boolean) call(restored, "cancel"), "repeated clear invented a new transition");
        CompoundTag cleared = ((SavedData) restored).save(new CompoundTag(), null);
        check(!cleared.getBoolean("active"), "clear state was not saved");

        CompoundTag old = new CompoundTag();
        old.putInt("durationTicks", 1200);
        old.putInt("remainingTicks", 1);
        old.putLong("seed", 98L);
        Object migrated = stateType.getMethod("load", CompoundTag.class, HolderLookup.Provider.class).invoke(null, old, null);
        check((boolean) call(migrated, "active") && (long) call(migrated, "seed") == 98L, "active 0.2.1 weather did not migrate to indefinite ash");
        CompoundTag migratedTag = ((SavedData) migrated).save(new CompoundTag(), null);
        check(migratedTag.getBoolean("active") && !migratedTag.contains("remainingTicks"), "migration did not emit the new state format");
        old.putInt("remainingTicks", 0);
        check(!(boolean) call(stateType.getMethod("load", CompoundTag.class, HolderLookup.Provider.class).invoke(null, old, null), "active"), "expired old storm reactivated");
        old.putInt("remainingTicks", -1);
        check(!(boolean) call(stateType.getMethod("load", CompoundTag.class, HolderLookup.Provider.class).invoke(null, old, null), "active"), "malformed negative old duration reactivated ash");
        old.putInt("remainingTicks", 30);
        old.putBoolean("active", false);
        check(!(boolean) call(stateType.getMethod("load", CompoundTag.class, HolderLookup.Provider.class).invoke(null, old, null), "active"), "explicit clear state must take precedence over legacy fields");
    }

    private static void commandTree() throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        WeatherCommand.register(dispatcher);
        var weather = dispatcher.getRoot().getChild("weather");
        Map<String, Object> nativeHandlers = new HashMap<>();
        for (String name : new String[]{"clear", "rain", "thunder"}) {
            nativeHandlers.put(name, weather.getChild(name).getCommand());
            nativeHandlers.put(name + "Duration", weather.getChild(name).getChild("duration").getCommand());
        }
        Class.forName("net.quiwh.cwivisuals.weather.server.AshWeatherCommands")
                .getMethod("register", CommandDispatcher.class).invoke(null, dispatcher);
        check(dispatcher.getRoot().getChild("weather") == weather, "weather extension replaced native root");
        for (String name : new String[]{"clear", "rain", "thunder"}) {
            check(weather.getChild(name).getCommand() == nativeHandlers.get(name), "changed native " + name + " handler");
            check(weather.getChild(name).getChild("duration").getCommand() == nativeHandlers.get(name + "Duration"), "changed native duration handler");
        }
        CommandSourceStack operator = source(2);
        var ash = dispatcher.parse("weather ashstorm", operator);
        check(!ash.getReader().canRead() && ash.getContext().getCommand() != null, "default ash command unavailable");
        for (String invalid : new String[]{"1", "1200", "60s", "1d", "0.5s", "0", "-1", "nonsense", "1 extra"}) {
            var parsed = dispatcher.parse("weather ashstorm " + invalid, operator);
            check(parsed.getReader().canRead() || !parsed.getExceptions().isEmpty(), "ash must reject duration or trailing arguments: " + invalid);
        }
        check(dispatcher.parse("weather ashstorm", source(1)).getReader().canRead(), "ash bypassed native permission level 2");
        check(dispatcher.getRoot().getChild("cwiweather") == null, "legacy command was registered");
    }

    private static void nativeServiceLifecycle() throws Exception {
        // Skip constructors that open saves, threads, registries and network listeners. Only the
        // native data/getter graph used by the service is populated; no world/server is started.
        FMLPaths.loadAbsolutePaths(Path.of("build/native-weather-game").toAbsolutePath());
        set(FMLLoader.class, null, "loadingModList", LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of()));
        set(FMLLoader.class, null, "dist", Dist.DEDICATED_SERVER);
        ModList mods = ModList.of(List.of(), List.of());
        set(ModList.class, mods, "indexedMods", Map.of());
        set(ModList.class, mods, "mods", List.of());
        set(ModList.class, mods, "sortedContainers", List.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Unsafe unsafe = unsafe();
        MinecraftServer server = (MinecraftServer) unsafe.allocateInstance(DedicatedServer.class);
        ServerLevel overworld = (ServerLevel) unsafe.allocateInstance(ServerLevel.class);
        ServerChunkCache chunks = (ServerChunkCache) unsafe.allocateInstance(ServerChunkCache.class);
        PlayerList players = (PlayerList) unsafe.allocateInstance(DedicatedPlayerList.class);
        set(PlayerList.class, players, "players", List.of());
        set(PlayerList.class, players, "playersView", List.of());
        set(MinecraftServer.class, server, "playerList", players);
        set(MinecraftServer.class, server, "levels", Map.of(Level.OVERWORLD, overworld));
        ServerTickRateManager tickRate = new ServerTickRateManager(server);
        set(MinecraftServer.class, server, "tickRateManager", tickRate);
        GameRules rules = new GameRules();
        Map<String, Object> nativeWeather = new HashMap<>();
        ServerLevelData levelData = (ServerLevelData) Proxy.newProxyInstance(
                AshWeatherServerFixture.class.getClassLoader(), new Class<?>[]{ServerLevelData.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getGameRules")) return rules;
                    if (Set.of("setClearWeatherTime", "setRainTime", "setThunderTime", "setRaining", "setThundering").contains(method.getName())) {
                        nativeWeather.put(method.getName(), arguments[0]);
                        return null;
                    }
                    throw new AssertionError("Weather service touched unrelated level data: " + method.getName());
                });
        set(ServerLevel.class, overworld, "server", server);
        set(ServerLevel.class, overworld, "serverLevelData", levelData);
        set(ServerLevel.class, overworld, "chunkSource", chunks);
        set(Level.class, overworld, "dimension", Level.OVERWORLD);
        set(Level.class, overworld, "levelData", levelData);
        set(Level.class, overworld, "random", RandomSource.create(123));
        DimensionDataStorage storage = new DimensionDataStorage(new File("build/native-weather-unused"), null, null);
        Object state = stateType.getConstructor().newInstance();
        storage.set("cwi_visual_weather", (SavedData) state);
        set(ServerChunkCache.class, chunks, "dataStorage", storage);

        CommandSourceStack operator = source(2);
        set(CommandSourceStack.class, operator, "server", server);
        set(CommandSourceStack.class, operator, "source", CommandSource.NULL);
        set(CommandSourceStack.class, operator, "silent", true);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        WeatherCommand.register(dispatcher);
        Class.forName("net.quiwh.cwivisuals.weather.server.AshWeatherCommands")
                .getMethod("register", CommandDispatcher.class).invoke(null, dispatcher);
        check(dispatcher.execute("weather ashstorm", operator) == 1, "indefinite ash command did not succeed");
        check((boolean) call(state, "active"), "ash command did not start authoritative weather");
        check(nativeWeather.get("setClearWeatherTime").equals(2)
                && nativeWeather.get("setRainTime").equals(0) && nativeWeather.get("setThunderTime").equals(0)
                && nativeWeather.get("setRaining").equals(false) && nativeWeather.get("setThundering").equals(false), "ash did not hold native rain and thunder clear");
        check(dispatcher.execute("weather ashstorm", operator) == 1 && (boolean) call(state, "active"), "repeated ash command failed");
        Class<?> service = Class.forName("net.quiwh.cwivisuals.weather.server.AshWeatherServer");
        Method tick = service.getDeclaredMethod("tick", LevelTickEvent.Pre.class);
        tick.setAccessible(true);
        Method snapshot = service.getDeclaredMethod("snapshot", stateType);
        snapshot.setAccessible(true);
        ((SavedData) state).setDirty(false);
        for (int i = 0; i < 24001; i++) {
            nativeWeather.put("setClearWeatherTime", 0);
            nativeWeather.put("setRaining", true);
            tick.invoke(null, new LevelTickEvent.Pre(() -> true, overworld));
        }
        check((boolean) call(state, "active"), "long-running ash expired automatically");
        check(nativeWeather.get("setClearWeatherTime").equals(2) && nativeWeather.get("setRaining").equals(false), "active ash allowed native weather to become rain");
        check(!((SavedData) state).isDirty(), "pinning weather rewrites SavedData every tick");
        check((boolean) payloadType.getMethod("active").invoke(snapshot.invoke(null, state)), "snapshot lost active state");
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        tick.invoke(null, new LevelTickEvent.Pre(() -> true, overworld));
        check((boolean) call(state, "active"), "weather-cycle pause ended indefinite ash");
        tickRate.setFrozen(true);
        tickRate.tick();
        tick.invoke(null, new LevelTickEvent.Pre(() -> true, overworld));
        check((boolean) call(state, "active"), "tick freeze ended indefinite ash");
        tickRate.setFrozen(false);
        tickRate.tick();
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(true, null);
        ServerLevel nether = (ServerLevel) unsafe.allocateInstance(ServerLevel.class);
        set(Level.class, nether, "dimension", Level.NETHER);
        nativeWeather.put("setClearWeatherTime", 0);
        tick.invoke(null, new LevelTickEvent.Pre(() -> true, nether));
        check(nativeWeather.get("setClearWeatherTime").equals(0), "another dimension tick pinned global weather");
        tick.invoke(null, new LevelTickEvent.Pre(() -> true, overworld));
        check(nativeWeather.get("setClearWeatherTime").equals(2), "overworld did not own the native clear hold");

        Class<?> mixin = Class.forName("net.quiwh.cwivisuals.weather.server.mixin.WeatherCommandMixin");
        Method guard = mixin.getDeclaredMethod("cwi$requireClear", CommandSourceStack.class, int.class, CallbackInfoReturnable.class);
        guard.setAccessible(true);
        Method leaveAsh = mixin.getDeclaredMethod("cwi$leaveAsh", CommandSourceStack.class, int.class, CallbackInfoReturnable.class);
        leaveAsh.setAccessible(true);
        for (String weather : new String[]{"rain", "thunder"}) {
            for (int duration : new int[]{-1, 20}) {
                Map<String, Object> before = new HashMap<>(nativeWeather);
                try {
                    guard.invoke(null, operator, duration, new CallbackInfoReturnable<Integer>(weather, false));
                    throw new AssertionError("Active ash allowed /weather " + weather);
                } catch (java.lang.reflect.InvocationTargetException rejected) {
                    check(rejected.getCause() instanceof com.mojang.brigadier.exceptions.CommandSyntaxException, "weather rejection must use native command failure");
                    check(rejected.getCause().getMessage().contains("/weather clear"), "weather rejection must explain how to end ash");
                }
                check((boolean) call(state, "active") && before.equals(nativeWeather), "rejected weather changed ash or native weather");
            }
        }
        for (String duration : new String[]{"", " 1s"}) {
            call(state, "start", new Class<?>[]{long.class}, 91L);
            int result = dispatcher.execute("weather clear" + duration, operator);
            CallbackInfoReturnable<Integer> callback = new CallbackInfoReturnable<>("clear", false, result);
            leaveAsh.invoke(null, operator, duration.isEmpty() ? -1 : 20, callback);
            check(!(boolean) call(state, "active"), "successful native clear did not exit ash");
            check(result == (duration.isEmpty() ? -1 : 20) && callback.getReturnValueI() == result && !callback.isCancelled(), "native clear return semantics changed");
            check(nativeWeather.get("setRaining").equals(false) && nativeWeather.get("setThundering").equals(false), "native clear flags changed");
        }
        Map<String, Object> clearWeather = new HashMap<>(nativeWeather);
        tick.invoke(null, new LevelTickEvent.Pre(() -> true, overworld));
        check(clearWeather.equals(nativeWeather), "inactive ash kept overriding native weather");
        check(!(boolean) payloadType.getMethod("active").invoke(snapshot.invoke(null, state)), "clear snapshot remains active");
        for (String weather : new String[]{"rain", "thunder"}) {
            for (String duration : new String[]{"", " 1s"}) {
                CallbackInfoReturnable<Integer> callback = new CallbackInfoReturnable<>(weather, false);
                guard.invoke(null, operator, duration.isEmpty() ? -1 : 20, callback);
                int result = dispatcher.execute("weather " + weather + duration, operator);
                check(result == (duration.isEmpty() ? -1 : 20) && !callback.isCancelled(), "native weather behavior changed outside ash");
                check(nativeWeather.get("setRaining").equals(true) && nativeWeather.get("setThundering").equals(weather.equals("thunder")), "native weather flags changed outside ash");
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void payloadRoundTrips() throws Exception {
        StreamCodec codec = (StreamCodec) payloadType.getField("CODEC").get(null);
        var constructor = payloadType.getConstructor(boolean.class, long.class);
        for (Object[] values : new Object[][]{{true, 71L}, {true, -5L}, {false, 0L}, {true, Long.MIN_VALUE}}) {
            Object payload = constructor.newInstance(values);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                codec.encode(buffer, payload);
                check(buffer.readableBytes() == 9, "indefinite weather wire format retained timed fields");
                check(payload.equals(codec.decode(buffer)), "payload round trip lost weather state");
                check(!buffer.isReadable(), "payload codec left unread bytes");
            } finally {
                buffer.release();
            }
        }
        Class<?> network = Class.forName("net.quiwh.cwivisuals.weather.network.AshWeatherNetwork");
        Object payload = constructor.newInstance(true, 6L);
        network.getMethod("receive", payloadType).invoke(null, payload);
        AtomicReference<Object> received = new AtomicReference<>();
        network.getMethod("setClientReceiver", Consumer.class).invoke(null, (Consumer<Object>) received::set);
        network.getMethod("receive", payloadType).invoke(null, payload);
        check(received.get() == payload, "common receiver did not hand off authoritative state");
    }

    private static void nativeMixinTargets() throws Exception {
        String descriptor = "(Lnet/minecraft/commands/CommandSourceStack;I)I";
        Set<String> methods = new HashSet<>();
        visit("net.minecraft.server.commands.WeatherCommand", new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                if (Set.of("setClear", "setRain", "setThunder").contains(name) && descriptor.equals(desc)
                        && (access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) == (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)) methods.add(name + desc);
                return null;
            }
        });
        check(methods.size() == 3, "native weather method descriptors changed");
        Map<String, Set<String>> selected = new HashMap<>();
        Map<String, Set<String>> injectionPoints = new HashMap<>();
        visit("net.quiwh.cwivisuals.weather.server.mixin.WeatherCommandMixin", new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (!annotation.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) return null;
                        selected.put(name, new HashSet<>());
                        injectionPoints.put(name, new HashSet<>());
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override public AnnotationVisitor visitArray(String field) {
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override public void visit(String ignored, Object value) { if (field.equals("method")) selected.get(name).add(value.toString()); }
                                    @Override public AnnotationVisitor visitAnnotation(String ignored, String desc) {
                                        return new AnnotationVisitor(Opcodes.ASM9) {
                                            @Override public void visit(String key, Object value) { if (field.equals("at") && key.equals("value")) injectionPoints.get(name).add(value.toString()); }
                                        };
                                    }
                                };
                            }
                        };
                    }
                };
            }
        });
        check(selected.get("cwi$leaveAsh").equals(Set.of("setClear" + descriptor)), "only clear may end active ash");
        check(injectionPoints.get("cwi$leaveAsh").equals(Set.of("RETURN")), "ash may clear only after native clear succeeds");
        check(selected.get("cwi$requireClear").equals(Set.of("setRain" + descriptor, "setThunder" + descriptor)), "rain/thunder guard targets changed");
        check(injectionPoints.get("cwi$requireClear").equals(Set.of("HEAD")), "rain/thunder must fail before mutating native weather");
    }

    private static void commonSideBoundary() throws Exception {
        for (String name : new String[]{"net.quiwh.cwivisuals.CwiVisualCommon", "net.quiwh.cwivisuals.weather.server.AshWeatherState",
                "net.quiwh.cwivisuals.weather.server.AshWeatherCommands", "net.quiwh.cwivisuals.weather.server.AshWeatherServer",
                "net.quiwh.cwivisuals.weather.network.AshWeatherNetwork", "net.quiwh.cwivisuals.weather.network.AshWeatherPayload",
                "net.quiwh.cwivisuals.weather.server.mixin.WeatherCommandMixin"}) {
            String bytes = new String(classBytes(name), java.nio.charset.StandardCharsets.ISO_8859_1);
            check(!bytes.contains("net/minecraft/client/") && !bytes.contains("pigcart/")
                    && !bytes.contains("net/quiwh/cwivisuals/CwiVisuals"), "common class links client presentation: " + name);
        }
    }

    private static CommandSourceStack source(int permission) throws Exception {
        var source = (CommandSourceStack) unsafe().allocateInstance(CommandSourceStack.class);
        var permissionField = CommandSourceStack.class.getDeclaredField("permissionLevel");
        permissionField.setAccessible(true);
        permissionField.setInt(source, permission);
        return source;
    }

    private static Unsafe unsafe() throws Exception {
        var field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object call(Object target, String name) throws Exception { return call(target, name, new Class<?>[0]); }
    private static Object call(Object target, String name, Class<?>[] arguments, Object... values) throws Exception {
        Method method = target.getClass().getMethod(name, arguments);
        return method.invoke(target, values);
    }
    private static byte[] classBytes(String name) throws Exception {
        try (InputStream input = AshWeatherServerFixture.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (input == null) throw new AssertionError("Missing class " + name);
            return input.readAllBytes();
        }
    }
    private static void visit(String name, ClassVisitor visitor) throws Exception { new ClassReader(classBytes(name)).accept(visitor, 0); }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
