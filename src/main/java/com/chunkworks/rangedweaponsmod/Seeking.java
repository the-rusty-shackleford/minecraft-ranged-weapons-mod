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

import com.chunkworks.rangedweaponsmod.domain.Seeker;
import com.chunkworks.rangedweaponsmod.domain.Vector3;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The launcher's seeker on the server (D-0028): the adapter between a player and the pure
 * {@link Seeker}. Each tick, for a player holding a launcher, it takes the target the holder's
 * client reports in its reticle while the sight is up ({@link ModData#AIMING}, which the use key
 * or the aim key raises), validates it, asks whether the lock still holds, steps the seeker, and
 * stores it in {@link ModData#LOCK} when it changed, whence it reaches the holder's HUD. A player
 * holding anything else has no seeker.
 *
 * <p>Why the client judges contact: it is what shows the player where a moving target is, and
 * what it shows trails the server by the connection and the smoothing. The launcher booth's
 * biplane, crossing at 0.8 blocks a tick, was eight blocks ahead on the server of where the
 * player's reticle sat on it -- eleven degrees off a cone of two and a half -- and never locked
 * while judged on the server. The server still decides: a report counts only for a valid target,
 * within {@link Seeker#RANGE}, that the eye can see, within {@link #SANITY_CONE} of the server's
 * own view of the look.
 *
 * <p>Cost, stated: for a player not holding a launcher, one item-class check per tick. For one
 * holding it, on the server, an entity lookup by id for the lock and for the report, and one block
 * ray. The reticle's search ({@link #inReticle}) runs on the holder's client: with the sight up,
 * eight entity queries along the look, each a box about 16 blocks long padded by the reticle's
 * width there, and a block ray to the best few candidates.
 */
public final class Seeking {
    private Seeking() {}

    /** The seeker on the wire: two ids and two counts. */
    public static final StreamCodec<ByteBuf, Seeker> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, Seeker::locked,
            ByteBufCodecs.INT, Seeker::candidate,
            ByteBufCodecs.VAR_INT, Seeker::contactTicks,
            ByteBufCodecs.VAR_INT, Seeker::gapTicks,
            Seeker::new);

    /** How far off the server's view of the look a reported target may be: generous, for a fast
     * target and a slow connection, while refusing one the player is not facing. */
    public static final double SANITY_CONE = Math.toRadians(30.0);

    /** Blocks of the look searched by one entity query. */
    private static final double SEGMENT = 16.0;
    /** Padding for the size of a large target, whose centre may stand well off the look. */
    private static final double LARGEST_RADIUS = 4.0;

    /**
     * requires: the logical server<br>
     * effects: steps {@code player}'s seeker one tick and stores it if it changed: idle unless a
     * launcher that may lock ({@link LauncherItem#canLock}: a lock-on chip fitted, or creative) is
     * in the main hand of a living, non-spectator player
     */
    public static void tick(Player player, ServerLevel level) {
        Seeker before = player.getData(ModData.LOCK);
        Seeker after;
        if (!player.isAlive() || player.isSpectator() || !LauncherItem.canLock(player, player.getMainHandItem())) {
            after = Seeker.IDLE;
        } else {
            boolean seeking = player.getData(ModData.AIMING);
            Entity seen = seeking ? reported(level, player) : null;
            boolean holds = before.isLocked() && holds(level, player, before.locked());
            after = before.step(seeking, seen == null ? Seeker.NONE : seen.getId(), holds);
        }
        if (!after.equals(before)) {
            player.setData(ModData.LOCK, after);
        }
    }

    /**
     * effects: records what {@code player}'s client reports in its reticle: an entity id, or
     * {@link Seeker#NONE}
     */
    public static void onSeen(Player player, int entity) {
        if (player.getData(ModData.SEEN) != entity) {
            player.setData(ModData.SEEN, entity);
        }
    }

    /**
     * effects: returns the entity {@code player}'s client last reported in its reticle if the
     * report stands: the entity is in this level, a valid target, within {@link Seeker#RANGE},
     * visible from the eye, and within {@link #SANITY_CONE} of the look as the server has it; else
     * null
     */
    static Entity reported(ServerLevel level, Player player) {
        int id = player.getData(ModData.SEEN);
        Entity target = id == Seeker.NONE ? null : level.getEntity(id);
        if (target == null || !Targets.valid(player, target)) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 to = center.subtract(eye);
        if (to.length() > Seeker.RANGE || to.lengthSqr() < 1e-6) {
            return null;
        }
        Vec3 look = player.getViewVector(1.0f);
        double radius = Math.max(target.getBbWidth(), target.getBbHeight()) / 2.0;
        if (Seeker.offAxis(new Vector3(look.x, look.y, look.z), new Vector3(to.x, to.y, to.z), radius) > SANITY_CONE) {
            return null;
        }
        return visible(level, player, eye, center, to.length(), radius) ? target : null;
    }

    /** effects: returns whether a block ray from the eye reaches the target's edge */
    private static boolean visible(Level level, Player player, Vec3 eye, Vec3 center, double distance, double radius) {
        HitResult hit = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || eye.distanceTo(hit.getLocation()) >= distance - radius;
    }

    /**
     * requires: the logical server<br>
     * effects: returns the entity {@code player} is locked onto if the lock still holds, else null;
     * spends the lock either way, as a launch does
     */
    public static Entity spend(Player player, ServerLevel level) {
        Seeker seeker = player.getData(ModData.LOCK);
        Entity target = seeker.isLocked() && holds(level, player, seeker.locked()) ? level.getEntity(seeker.locked()) : null;
        if (seeker.isLocked()) {
            player.setData(ModData.LOCK, seeker.fired());
        }
        return target;
    }

    /**
     * effects: returns whether a lock on entity {@code id} holds for {@code player}: the entity is
     * in this level, still a valid target, and within {@link Seeker#RANGE}
     */
    static boolean holds(ServerLevel level, Player player, int id) {
        Entity target = level.getEntity(id);
        return target != null && Targets.valid(player, target)
                && target.distanceToSqr(player) <= Seeker.RANGE * Seeker.RANGE;
    }

    /**
     * effects: returns the valid target in {@code player}'s reticle as {@code level} shows it: of
     * those within {@link Seeker#RANGE} whose edge is within {@link Seeker#HALF_CONE} of the look,
     * the least off it (the nearer on a tie) that the eye can see; null if none. The client calls it
     * on what it draws, and reports the answer.
     */
    public static Entity inReticle(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vector3 lookV = new Vector3(look.x, look.y, look.z);
        double widen = Math.tan(Seeker.HALF_CONE);
        record Candidate(Entity entity, double off, double distance, double radius) {}
        List<Candidate> found = new ArrayList<>();
        Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (double near = 0.0; near < Seeker.RANGE; near += SEGMENT) {
            double far = Math.min(Seeker.RANGE, near + SEGMENT);
            AABB box = new AABB(eye.add(look.scale(near)), eye.add(look.scale(far))).inflate(far * widen + LARGEST_RADIUS);
            for (Entity entity : level.getEntities(player, box, e -> Targets.valid(player, e))) {
                if (!seen.add(entity)) {
                    continue;   // the segments' boxes overlap
                }
                Vec3 center = entity.getBoundingBox().getCenter();
                Vec3 to = center.subtract(eye);
                double radius = Math.max(entity.getBbWidth(), entity.getBbHeight()) / 2.0;
                Vector3 toV = new Vector3(to.x, to.y, to.z);
                if (Seeker.inReticle(lookV, toV, radius)) {
                    found.add(new Candidate(entity, Seeker.offAxis(lookV, toV, radius), to.length(), radius));
                }
            }
        }
        found.sort(Comparator.comparingDouble(Candidate::off).thenComparingDouble(Candidate::distance));
        for (Candidate c : found) {
            if (visible(level, player, eye, c.entity().getBoundingBox().getCenter(), c.distance(), c.radius())) {
                return c.entity();
            }
        }
        return null;
    }

    /**
     * The off hand's item use is refused while a launcher is in the main hand: a shield or a meal
     * there would put the player "in use", and vanilla discards attack clicks while an item is in
     * use, so the trigger would die. Blocks and entities in reach still get their interaction:
     * this is only the item-use step, which comes after them.
     *
     * <p>effects: cancels an off-hand item use, as a failure, when the main hand holds a launcher
     */
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() == InteractionHand.OFF_HAND && LauncherItem.isLauncher(event.getEntity().getMainHandItem())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }
}
