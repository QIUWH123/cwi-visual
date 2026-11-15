package net.quiwh.cwivisuals.weather;

import neoforge.fun.qu_an.minecraft.asyncparticles.client.compat.particlerain.ParticleRainCompat;

/** Reads the same live count as the installed weather spawner, without owning its lifecycle. */
final class WeatherParticleBudget {
    private WeatherParticleBudget() {}

    static int count() {
        // CWI requires AsyncParticles 21.1.4.12. Its mixins redirect bookkeeping
        // to this atomic counter; ParticleSpawner's native field can remain stale.
        return ParticleRainCompat.particleCount.get();
    }
}
