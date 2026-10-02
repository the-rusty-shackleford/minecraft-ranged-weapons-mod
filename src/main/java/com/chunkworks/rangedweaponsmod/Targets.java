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

import com.chunkworks.rangedweaponsmod.domain.TargetFacts;
import com.chunkworks.rangedweaponsmod.domain.TargetFacts.Kind;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;

/**
 * The adapter from an entity to {@link TargetFacts}: what the seeker may lock onto (D-0028).
 *
 * <p>A vehicle is vanilla's kind -- boats, minecarts, and everything built on them: Vanilla
 * Wheels' vehicles, Shippy Ships' ships -- or an entity type in the tag
 * {@code rangedweaponsmod:lockable_vehicles}, which names the modded vehicles that are not
 * (Immersive Aircraft's and its addons', Automobility's cars), each optional so a missing mod is
 * no error; a datapack adds any other.
 *
 * <p>Not instantiable.
 */
public final class Targets {
    private Targets() {}

    /** Modded vehicles that do not extend vanilla's, so the seeker can still lock onto them. */
    public static final TagKey<EntityType<?>> LOCKABLE_VEHICLES = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(RangedWeaponsMod.MOD_ID, "lockable_vehicles"));

    /**
     * effects: returns what {@code shooter} sees of {@code entity}: its kind, whether it is alive,
     * invulnerable, attackable, the shooter or in the shooter's vehicle, and, for a player, whether
     * they could be hurt and whether the shooter may hurt them (the server's PvP and the teams'
     * friendly fire, as vanilla decides it)
     */
    public static TargetFacts facts(Player shooter, Entity entity) {
        Kind kind;
        if (entity instanceof ArmorStand) {
            kind = Kind.DECORATION;
        } else if (entity instanceof LivingEntity) {
            kind = Kind.CREATURE;
        } else if (entity instanceof VehicleEntity || entity.getType().is(LOCKABLE_VEHICLES)) {
            kind = Kind.VEHICLE;
        } else {
            kind = Kind.OTHER;
        }
        boolean kin = entity == shooter || shooter.isPassengerOfSameVehicle(entity);
        boolean isPlayer = entity instanceof Player;
        boolean hurtable = entity instanceof Player p && !p.isCreative() && !p.isSpectator();
        boolean pvp = !(entity instanceof Player p) || shooter.canHarmPlayer(p);
        return new TargetFacts(kind, entity.isAlive() && !entity.isRemoved(), entity.isInvulnerable(),
                entity.isAttackable(), kin, isPlayer, hurtable, pvp);
    }

    /** effects: returns whether the seeker in {@code shooter}'s hands may lock onto {@code entity} */
    public static boolean valid(Player shooter, Entity entity) {
        return facts(shooter, entity).valid();
    }
}
