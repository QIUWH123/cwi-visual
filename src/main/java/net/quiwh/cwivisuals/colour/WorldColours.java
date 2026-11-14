package net.quiwh.cwivisuals.colour;

import java.util.Objects;
import java.util.Set;
import net.minecraft.world.phys.Vec3;

/** Worker-safe colour transforms. Lifecycle callbacks alone update the published snapshot. */
public final class WorldColours {
    private static final ColourProfile.Tint NEUTRAL = new ColourProfile.Tint(0, 0);
    private static final ColourProfile DISABLED = new ColourProfile(false, Set.of(),
            NEUTRAL, NEUTRAL, NEUTRAL, NEUTRAL);
    private static volatile State state = new State(DISABLED, null);

    private WorldColours() {}

    public static synchronized void configure(ColourProfile profile) {
        state = new State(Objects.requireNonNull(profile, "profile"), state.dimension());
    }

    public static synchronized void enterDimension(String dimension) {
        state = new State(state.profile(), dimension);
    }

    public static synchronized void clear() {
        state = new State(state.profile(), null);
    }

    public static int grass(int original) {
        State current = state;
        return current.active() ? current.profile().grass().packed(original) : original;
    }

    public static int foliage(int original) {
        State current = state;
        return current.active() ? current.profile().foliage().packed(original) : original;
    }

    public static int water(int original) {
        State current = state;
        return current.active() ? current.profile().water().packed(original) : original;
    }

    public static Vec3 sky(Vec3 original) {
        State current = state;
        return current.active() ? current.profile().sky().sky(original) : original;
    }

    /** The renderer may briefly retain an old level while a new level is being constructed. */
    public static Vec3 sky(Vec3 original, String dimension) {
        State current = state;
        return current.dimension() != null && current.profile().appliesTo(dimension)
                ? current.profile().sky().sky(original) : original;
    }

    private record State(ColourProfile profile, String dimension) {
        boolean active() {
            return profile.appliesTo(dimension);
        }
    }
}
