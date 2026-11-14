package net.quiwh.cwivisuals.weather;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pigcart.particlerain.VersionUtil;
import pigcart.particlerain.config.ParticleData;
import pigcart.particlerain.mixin.access.ParticleAccessor;
import pigcart.particlerain.particle.CustomParticle;

/** Soft dust with gentle wind response, bounded distant collisions and a shared event fade. */
class AshParticle extends CustomParticle implements ParticleAccessor {
    // At most 0.99 blocks/second diagonally, with gradual acceleration and direction changes.
    private static final double MAX_AXIS_DRIFT = .035;
    private static final double WIND_STEP = .001;
    private final boolean lightweight;
    private final String profileId;
    private final int profileKind;
    private volatile float visibility = 1;
    private int collisionTicks;
    private double checkedX;
    private double checkedY;
    private double checkedZ;
    private boolean collisionAnimation;

    AshParticle(ClientLevel level, double x, double y, double z, ParticleData data, boolean lightweight) {
        super(level, x, y, z, data);
        // Native free dust can live for thousands of ticks. Keep our visual population responsive.
        lifetime = Math.min(lifetime, 100);
        // This pack's WindLink integration may replace descriptor sprites/sizes in super().
        setSprite(VersionUtil.getSprite(VersionUtil.parseId(AshParticleProfiles.SOFT_SPRITE)));
        quadSize = data.size;
        setSize(quadSize, quadSize);
        yd = Math.clamp(yd, -.008, .008);
        this.lightweight = lightweight;
        profileId = data.id;
        profileKind = switch (profileId) {
            case "dust" -> 0;
            case "skittering_dust" -> 1;
            case "dust_haze" -> 2;
            case null, default -> -1;
        };
        checkedX = x;
        checkedY = y;
        checkedZ = z;
    }

    final String profileId() { return profileId; }
    final int profileKind() { return profileKind; }

    @Override public ParticleRenderType getRenderType() { return SoftAshRenderType.INSTANCE; }

    // These accessors normally come from Particle Rain's base-particle mixin. Explicit overrides
    // also let the headless native fixture exercise this exact production class.
    @Override public double getX() { return x; }
    @Override public double getY() { return y; }
    @Override public double getZ() { return z; }
    @Override public double getXd() { return xd; }
    @Override public void setXd(double value) { xd = gentleWind(xd, value); }
    @Override public double getZd() { return zd; }
    @Override public void setZd(double value) { zd = gentleWind(zd, value); }

    private static double gentleWind(double current, double target) {
        if (!Double.isFinite(target)) target = 0;
        target = Math.clamp(target, -MAX_AXIS_DRIFT, MAX_AXIS_DRIFT);
        return Math.clamp(current + Math.clamp(target - current, -WIND_STEP, WIND_STEP),
                -MAX_AXIS_DRIFT, MAX_AXIS_DRIFT);
    }

    final void setVisibility(float value) {
        visibility = Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }

    final void hideImmediately() {
        visibility = 0;
        alpha = 0;
    }

    @Override public void tickFading() {
        // Native fading assigns a fresh alpha each tick, so calm periods do not compound it.
        super.tickFading();
        float entry = smooth(age / 20.0f);
        float exit = smooth((lifetime - age) / 30.0f);
        alpha *= visibility * entry * exit;
    }

    private static float smooth(float value) {
        float t = Math.max(0, Math.min(1, value));
        return t * t * (3 - 2 * t);
    }

    @Override public void tickCollisionAnim() {
        collisionAnimation = true;
        super.tickCollisionAnim();
    }

    @Override public void tickCollisions() {
        // Bound direct native gravity/collision impulses before the next movement step.
        xd = Math.clamp(xd, -MAX_AXIS_DRIFT, MAX_AXIS_DRIFT);
        zd = Math.clamp(zd, -MAX_AXIS_DRIFT, MAX_AXIS_DRIFT);
        yd = Math.clamp(yd, -.008, .008);
        if (collisionAnimation) return;
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dx = x - camera.x, dy = y - camera.y, dz = z - camera.z;
        // Preserve full contact behavior for grit and the dust the player can inspect closely.
        if (!lightweight || dx * dx + dy * dy + dz * dz <= 24 * 24) {
            super.tickCollisions();
            checkedX = x;
            checkedY = y;
            checkedZ = z;
            collisionTicks = 0;
            return;
        }
        if ((collisionTicks++ & 3) != 0) return;

        double speed = Math.sqrt(xd * xd + yd * yd + zd * zd);
        double reach = speed > 1.0E-9 ? 1 + quadSize / speed : 0;
        Vec3 from = new Vec3(checkedX, checkedY, checkedZ);
        Vec3 to = new Vec3(x + xd * reach, y + yd * reach, z + zd * reach);
        var hit = level.clip(VersionUtil.getClipContext(from, to));
        checkedX = x;
        checkedY = y;
        checkedZ = z;
        // Cover the interval skipped since the previous query instead of only the newest step.
        if (hit.getType() != HitResult.Type.MISS) onCollision(hit);
    }
}
