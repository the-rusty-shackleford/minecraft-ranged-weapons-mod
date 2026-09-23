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
 * Immutable visual phase of a six-chamber action, independent of rendering.
 * AF: cylinder angle and cocked-hammer angle, in degrees, plus cylinder opening
 * in [0,1]. RI: finite angles; opening in [0,1]. A shot indexes exactly 60 degrees;
 * the hammer strikes promptly and is cocked again before the next allowed shot.
 */
public record RevolverAction(double cylinder, double hammer, double opening) {
    public static final int CHAMBERS = 6;
    public static final double CYCLE_TICKS = 8;
    public static final double COCKED = 35;

    /** effects: constructs a pose; throws: IllegalArgumentException for non-finite angles or invalid opening. */
    public RevolverAction {
        if (!Double.isFinite(cylinder) || !Double.isFinite(hammer)
                || !Double.isFinite(opening) || opening < 0 || opening > 1)
            throw new IllegalArgumentException("invalid revolver pose");
    }

    /**
     * requires: chamber in [0,5], finite elapsed and reloadProgress in [0,1].
     * effects: samples the last accepted shot; negative elapsed is an idle/new-clock
     * pose. A completed or absent reload uses progress 0 or 1 (closed).
     * throws: IllegalArgumentException for arguments outside those partitions.
     */
    public static RevolverAction sample(int chamber, double elapsed, double reloadProgress) {
        if (chamber < 0 || chamber >= CHAMBERS || !Double.isFinite(elapsed)
                || !Double.isFinite(reloadProgress) || reloadProgress < 0 || reloadProgress > 1)
            throw new IllegalArgumentException("invalid action sample");
        double turn = chamber * 60.0;
        double hammer = COCKED;
        if (elapsed >= 0 && elapsed < CYCLE_TICKS) {
            turn = (chamber - 1 + ease(elapsed / CYCLE_TICKS)) * 60;
            hammer = elapsed < 1 ? COCKED * (1 - ease(elapsed))
                    : COCKED * ease((elapsed - 2) / (CYCLE_TICKS - 2));
        }
        double opening = ease(reloadProgress / .18) * ease((1 - reloadProgress) / .18);
        return new RevolverAction(turn, hammer, opening);
    }

    private static double ease(double value) {
        double t = Math.clamp(value,0,1);
        return t*t*(3-2*t);
    }
}
