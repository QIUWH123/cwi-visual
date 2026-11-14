package net.quiwh.cwivisuals.weather.server;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** One persisted, server-owned ash event; no particles or gameplay are stored here. */
public final class AshWeatherState extends SavedData {
    private static final String SAVE_ID = "cwi_visual_weather";
    private static final Factory<AshWeatherState> FACTORY = new Factory<>(AshWeatherState::new, AshWeatherState::load);
    private boolean active;
    private long seed;

    public static AshWeatherState get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, SAVE_ID);
    }

    public static AshWeatherState existing(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().get(FACTORY, SAVE_ID);
    }

    public boolean active() { return active; }
    public long seed() { return seed; }

    public void start(long eventSeed) {
        active = true;
        seed = eventSeed;
        setDirty();
    }

    /** Returns true only for the transition from active ash to clear weather. */
    public boolean cancel() {
        if (!active()) return false;
        active = false;
        seed = 0;
        setDirty();
        return true;
    }

    public static AshWeatherState load(CompoundTag tag, HolderLookup.Provider registries) {
        AshWeatherState state = new AshWeatherState();
        // Existing timed events become indefinite; an explicit new-format clear wins.
        state.active = tag.contains("active") ? tag.getBoolean("active")
                : tag.getInt("durationTicks") > 0 && tag.getInt("remainingTicks") > 0;
        if (state.active) state.seed = tag.getLong("seed");
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("active", active);
        tag.putLong("seed", seed);
        return tag;
    }
}
