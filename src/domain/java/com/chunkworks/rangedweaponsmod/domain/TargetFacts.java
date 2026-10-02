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
package com.chunkworks.rangedweaponsmod.domain;

/**
 * What the seeker may lock onto (D-0028), as facts about one entity seen by one shooter.
 *
 * <p>Rusty: "any creature or mob or whatever that you could do damage to, not inanimate blocks",
 * and vehicles. A creature is anything alive but a decoration (an armour stand); a vehicle is
 * vanilla's kind (boats, minecarts, Vanilla Wheels' vehicles, ships built on boats) or a modded
 * one named by a tag. A player is a target only when they could be hurt: in survival or
 * adventure, on a server that allows PvP. Nothing the shooter is, rides or carries is ever one.
 *
 * <p>Immutable. AF: one entity as the shooter sees it. RI: none beyond non-null {@code kind}.
 *
 * @param kind         what the entity is
 * @param alive        it is alive and not removed
 * @param invulnerable it is flagged invulnerable
 * @param attackable   the game lets it be attacked
 * @param shooterOrKin it is the shooter, or rides with them, or carries them
 * @param player       it is a player
 * @param hurtable     for a player: in survival or adventure
 * @param pvp          the server allows players to hurt players
 */
public record TargetFacts(Kind kind, boolean alive, boolean invulnerable, boolean attackable, boolean shooterOrKin,
                          boolean player, boolean hurtable, boolean pvp) {

    /** What an entity is, as far as the seeker cares. */
    public enum Kind {
        /** A living thing that is not a decoration. */
        CREATURE,
        /** An armour stand: living by class, a decoration by use. */
        DECORATION,
        /** A vehicle: vanilla's kind, or a modded one in the lockable-vehicles tag. */
        VEHICLE,
        /** Anything else: items, frames, paintings, projectiles, orbs, untagged modded entities. */
        OTHER
    }

    /**
     * @throws IllegalArgumentException if {@code kind} is null
     */
    public TargetFacts {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
    }

    /**
     * effects: returns whether the seeker may lock onto this entity: a creature or a vehicle,
     * alive, attackable, not invulnerable, not the shooter or their mount or passenger, and, if a
     * player, hurtable on a server that allows PvP
     *
     * @return whether it is a valid target
     */
    public boolean valid() {
        if (kind != Kind.CREATURE && kind != Kind.VEHICLE) {
            return false;
        }
        if (!alive || invulnerable || !attackable || shooterOrKin) {
            return false;
        }
        return !player || (hurtable && pvp);
    }
}
