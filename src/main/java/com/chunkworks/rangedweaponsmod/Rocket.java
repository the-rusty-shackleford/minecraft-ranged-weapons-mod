/*
 * Ranged Weapons Mod - guns for players, on the Ranged Weapons protocol.
 * Copyright (C) 2026 Rusty Shackleford and nfx
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.chunkworks.rangedweaponsmod;

import com.chunkworks.rangedweaponsmod.domain.Guidance;
import com.chunkworks.rangedweaponsmod.domain.Motor;
import com.chunkworks.rangedweaponsmod.domain.Seeker;
import com.chunkworks.rangedweaponsmod.domain.Vector3;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * A rocket in flight (D-0028). The server flies it: the {@link Motor} gives its speed by age and
 * the {@link Guidance} its heading toward its target, if it has one, once the motor has lit. It
 * detonates on touching a block or an entity, within {@link Motor#PROXIMITY} of its target, or when
 * its motor is spent; it breaks without a blast if it touches anything before
 * {@link Motor#ARMING_DISTANCE}. The blast is the game's explosion with the rocket as its source,
 * so its owner is credited and the server's PvP rule applies; its power and whether it breaks
 * blocks are the world's {@link RocketConfig}.
 *
 * <p>Clients draw it where the server says it is, every tick (its type sends position and
 * velocity each tick), moving it along its velocity in between, with a trail and its motor's sound.
 *
 * <p>AF: a rocket owned by {@code getOwner()}, launched {@code tickCount} ticks ago, {@code flown}
 * blocks along its path so far, homing on entity {@code targetId} ({@link Seeker#NONE}: flying
 * straight), which was at {@code lastTargetPos} a tick ago.<br>
 * RI (server): {@code flown >= 0}; {@code lastTargetPos} is null when {@code targetId} is NONE.
 */
public final class Rocket extends Projectile {

    private int targetId = Seeker.NONE;
    @Nullable private Vec3 lastTargetPos;
    private double flown;
    private boolean motorHeard;

    public Rocket(EntityType<? extends Rocket> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    /**
     * requires: the logical server; {@code direction} non-zero<br>
     * effects: launches a rocket owned by {@code owner} from {@code origin} along {@code direction},
     * homing on {@code target} if one is given, and returns it
     */
    public static Rocket launch(ServerLevel level, LivingEntity owner, Vec3 origin, Vec3 direction, @Nullable Entity target) {
        Rocket rocket = new Rocket(ModData.ROCKET.get(), level);
        rocket.setOwner(owner);
        rocket.setPos(origin);
        Vec3 velocity = direction.normalize().scale(Motor.speed(0));
        rocket.setDeltaMovement(velocity);
        rocket.face(velocity);
        rocket.xRotO = rocket.getXRot();
        rocket.yRotO = rocket.getYRot();
        if (target != null) {
            rocket.targetId = target.getId();
            rocket.lastTargetPos = target.getBoundingBox().getCenter();
        }
        level.addFreshEntity(rocket);
        return rocket;
    }

    /** effects: returns the entity this rocket homes on, or {@link Seeker#NONE} */
    public int targetId() {
        return targetId;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // Nothing synchronised beyond position, velocity and rotation, which the entity type sends.
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved()) {
            return;
        }
        Vec3 velocity = this.getDeltaMovement();
        if (this.level().isClientSide) {
            this.setPos(this.position().add(velocity));
            this.face(velocity);
            this.trail(velocity);
            if (!this.motorHeard && FMLEnvironment.dist.isClient()) {
                this.motorHeard = true;
                com.chunkworks.rangedweaponsmod.client.RocketSounds.motor(this);
            }
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        int age = this.tickCount;
        if (age >= Motor.LIFETIME_TICKS) {
            this.detonate(level);
            return;
        }
        Entity target = this.liveTarget(level);
        double speed = Motor.speed(age);
        Vec3 next = velocity.lengthSqr() < 1e-12 ? this.getLookAngle().scale(speed) : velocity.normalize().scale(speed);
        if (target != null) {
            Vec3 at = target.getBoundingBox().getCenter();
            Vec3 targetVelocity = this.lastTargetPos == null ? Vec3.ZERO : at.subtract(this.lastTargetPos);
            this.lastTargetPos = at;
            Vec3 to = at.subtract(this.position());
            if (Motor.steers(age) && to.lengthSqr() > 1e-6 && velocity.lengthSqr() > 1e-12) {
                Vector3 steered = Guidance.steer(v(velocity), speed, v(to), v(targetVelocity), Guidance.TURN_PER_TICK);
                next = new Vec3(steered.x(), steered.y(), steered.z());
            }
        }
        this.setDeltaMovement(next);
        this.face(next);

        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            this.flown += hit.getLocation().distanceTo(this.position());
            this.setPos(hit.getLocation());
            if (Motor.armed(this.flown)) {
                this.detonate(level);
            } else {
                this.dud(level);
            }
            return;
        }
        Vec3 from = this.position();
        Vec3 to = from.add(next);
        if (target != null) {
            Vec3 nearest = nearestOnSegment(from, to, target.getBoundingBox());
            if (distanceToBox(nearest, target.getBoundingBox()) <= Motor.PROXIMITY
                    && Motor.armed(this.flown + from.distanceTo(nearest))) {
                this.flown += from.distanceTo(nearest);
                this.setPos(nearest);
                this.detonate(level);
                return;
            }
        }
        this.flown += next.length();
        this.setPos(to);
    }

    /** effects: returns the target if it is still in this level and alive; forgets it otherwise */
    @Nullable
    private Entity liveTarget(ServerLevel level) {
        if (this.targetId == Seeker.NONE) {
            return null;
        }
        Entity target = level.getEntity(this.targetId);
        if (target == null || !target.isAlive() || target.isRemoved()) {
            this.targetId = Seeker.NONE;
            this.lastTargetPos = null;
            return null;
        }
        return target;
    }

    /** The owner cannot be struck for the first ticks, whatever vanilla's leaving rule says. */
    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(entity == this.getOwner() && this.tickCount < 10);
    }

    /** effects: the blast, then gone */
    private void detonate(ServerLevel level) {
        level.explode(this, this.getX(), this.getY(), this.getZ(), RocketConfig.power(),
                RocketConfig.breakBlocks() ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        this.discard();
    }

    /** effects: a puff and the dud's sound, no blast, nothing dropped; then gone */
    private void dud(ServerLevel level) {
        level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY(), this.getZ(), 8, 0.1, 0.1, 0.1, 0.02);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.ROCKET_DUD.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        this.discard();
    }

    /** effects: smoke behind, and fire once the motor has lit (client) */
    private void trail(Vec3 velocity) {
        Vec3 tail = this.position().subtract(velocity.normalize().scale(0.4));
        this.level().addParticle(ParticleTypes.LARGE_SMOKE, tail.x, tail.y, tail.z, 0.0, 0.01, 0.0);
        if (Motor.steers(this.tickCount)) {
            this.level().addParticle(ParticleTypes.FLAME, tail.x, tail.y, tail.z, 0.0, 0.0, 0.0);
        }
    }

    /** effects: points the rocket along {@code velocity} */
    private void face(Vec3 velocity) {
        double horizontal = velocity.horizontalDistance();
        this.setYRot((float) (Mth.atan2(velocity.x, velocity.z) * Mth.RAD_TO_DEG));
        this.setXRot((float) (Mth.atan2(velocity.y, horizontal) * Mth.RAD_TO_DEG));
    }

    /** Seen as far as anything a player would track: vanilla culls by size, which hides a rocket at 19 blocks. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double range = 160.0 * getViewScale();
        return distance < range * range;
    }

    private static Vector3 v(Vec3 vec) {
        return new Vector3(vec.x, vec.y, vec.z);
    }

    /** effects: returns the point of segment {@code a}–{@code b} nearest the centre of {@code box} */
    static Vec3 nearestOnSegment(Vec3 a, Vec3 b, AABB box) {
        Vec3 c = box.getCenter();
        Vec3 ab = b.subtract(a);
        double length2 = ab.lengthSqr();
        double t = length2 < 1e-12 ? 0.0 : Mth.clamp(c.subtract(a).dot(ab) / length2, 0.0, 1.0);
        return a.add(ab.scale(t));
    }

    /** effects: returns the distance from {@code p} to {@code box}, 0 inside it */
    static double distanceToBox(Vec3 p, AABB box) {
        double dx = Math.max(0.0, Math.max(box.minX - p.x, p.x - box.maxX));
        double dy = Math.max(0.0, Math.max(box.minY - p.y, p.y - box.maxY));
        double dz = Math.max(0.0, Math.max(box.minZ - p.z, p.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
