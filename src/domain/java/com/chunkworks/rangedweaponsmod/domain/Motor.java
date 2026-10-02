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
 * A rocket's motor and fuses (D-0028), as numbers over its age and the distance it has flown.
 *
 * <p>The rocket leaves the tube slowly and straight, then its motor lights and it accelerates to
 * its top speed; it flies without gravity until its motor is spent, when it detonates. It arms
 * only once it is clear of the shooter: a contact closer than that breaks it without a blast, so
 * a shot into a wall at the shooter's feet cannot kill them.
 *
 * <p>Not instantiable: constants and pure functions.
 */
public final class Motor {
    private Motor() {}

    /** Ticks the rocket coasts at {@link #EJECT_SPEED} before the motor lights; it does not steer meanwhile. */
    public static final int EJECT_TICKS = 3;
    /** Blocks a tick while ejected. */
    public static final double EJECT_SPEED = 0.6;
    /** The age, in ticks, by which the motor has reached {@link #TOP_SPEED}. */
    public static final int FULL_BURN_TICKS = 15;
    /** Blocks a tick at full burn. */
    public static final double TOP_SPEED = 2.5;
    /** The age, in ticks, at which the motor is spent and the rocket detonates where it is. */
    public static final int LIFETIME_TICKS = 160;
    /** Blocks flown from the launch point before a contact sets the warhead off. */
    public static final double ARMING_DISTANCE = 4.0;
    /** Blocks from its target's box at which a guided rocket detonates without touching it. */
    public static final double PROXIMITY = 1.5;

    /**
     * requires: {@code age >= 0}<br>
     * effects: returns the rocket's speed in blocks a tick at {@code age}: {@link #EJECT_SPEED}
     * before {@link #EJECT_TICKS}, then rising linearly to {@link #TOP_SPEED} at
     * {@link #FULL_BURN_TICKS}, then {@link #TOP_SPEED}<br>
     * throws: {@link IllegalArgumentException} if {@code age < 0}
     *
     * @param age ticks since launch
     * @return blocks a tick
     */
    public static double speed(int age) {
        if (age < 0) {
            throw new IllegalArgumentException("age must be >= 0, was " + age);
        }
        if (age < EJECT_TICKS) {
            return EJECT_SPEED;
        }
        if (age >= FULL_BURN_TICKS) {
            return TOP_SPEED;
        }
        double t = (double) (age - EJECT_TICKS) / (FULL_BURN_TICKS - EJECT_TICKS);
        return EJECT_SPEED + (TOP_SPEED - EJECT_SPEED) * t;
    }

    /**
     * requires: {@code age >= 0}<br>
     * effects: returns whether the rocket steers at {@code age}: only once its motor has lit
     *
     * @param age ticks since launch
     * @return whether guidance acts this tick
     */
    public static boolean steers(int age) {
        if (age < 0) {
            throw new IllegalArgumentException("age must be >= 0, was " + age);
        }
        return age >= EJECT_TICKS;
    }

    /**
     * requires: {@code flown >= 0}, finite<br>
     * effects: returns whether a contact after flying {@code flown} blocks sets the warhead off
     *
     * @param flown blocks from the launch point
     * @return whether the rocket is armed
     */
    public static boolean armed(double flown) {
        if (!(flown >= 0.0) || !Double.isFinite(flown)) {
            throw new IllegalArgumentException("flown must be finite and >= 0, was " + flown);
        }
        return flown >= ARMING_DISTANCE;
    }
}
