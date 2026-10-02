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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chunkworks.rangedweaponsmod.domain.TargetFacts.Kind;
import org.junit.jupiter.api.Test;

/**
 * Partitions. Kind: creature / decoration / vehicle (vanilla or tagged: one kind here, decided by
 * the adapter) / other. Alive: yes / no. Invulnerable: yes / no. Attackable: yes / no. The shooter
 * or their mount or passenger: yes / no. Player: no / yes hurtable with PvP / yes hurtable without
 * PvP / yes not hurtable (creative, spectator).
 */
final class TargetFactsTest {

    private static TargetFacts mob(Kind kind) {
        return new TargetFacts(kind, true, false, true, false, false, false, true);
    }

    private static TargetFacts player(boolean hurtable, boolean pvp) {
        return new TargetFacts(Kind.CREATURE, true, false, true, false, true, hurtable, pvp);
    }

    @Test
    void creaturesAndVehiclesAreTargets() {
        assertTrue(mob(Kind.CREATURE).valid());
        assertTrue(mob(Kind.VEHICLE).valid());
    }

    @Test
    void decorationsAndEverythingElseAreNot() {
        assertFalse(mob(Kind.DECORATION).valid());
        assertFalse(mob(Kind.OTHER).valid());
    }

    @Test
    void theDeadTheInvulnerableAndTheUnattackableAreNot() {
        assertFalse(new TargetFacts(Kind.CREATURE, false, false, true, false, false, false, true).valid());
        assertFalse(new TargetFacts(Kind.CREATURE, true, true, true, false, false, false, true).valid());
        assertFalse(new TargetFacts(Kind.VEHICLE, true, false, false, false, false, false, true).valid());
    }

    @Test
    void theShooterTheirMountAndTheirPassengersAreNot() {
        assertFalse(new TargetFacts(Kind.CREATURE, true, false, true, true, false, false, true).valid());
        assertFalse(new TargetFacts(Kind.VEHICLE, true, false, true, true, false, false, true).valid());
    }

    @Test
    void aPlayerIsATargetOnlyWhenTheyCouldBeHurt() {
        assertTrue(player(true, true).valid());
        assertFalse(player(true, false).valid(), "the server forbids PvP");
        assertFalse(player(false, true).valid(), "creative or spectator");
    }

    @Test
    void aKindIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> new TargetFacts(null, true, false, true, false, false, false, true));
    }
}
