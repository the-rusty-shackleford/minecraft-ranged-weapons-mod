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
 * A point or a displacement in blocks, for the rocket's flight and the seeker's geometry.
 *
 * <p>Immutable. AF: the vector {@code (x, y, z)}. RI: every component is finite.
 *
 * @param x east
 * @param y up
 * @param z south
 */
public record Vector3(double x, double y, double z) {

    /** The zero vector. */
    public static final Vector3 ZERO = new Vector3(0.0, 0.0, 0.0);
    /** Straight up. */
    public static final Vector3 UP = new Vector3(0.0, 1.0, 0.0);

    /**
     * @throws IllegalArgumentException if a component is not finite
     */
    public Vector3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("components must be finite, were " + x + ", " + y + ", " + z);
        }
    }

    /** effects: returns this plus {@code o} */
    public Vector3 add(Vector3 o) {
        return new Vector3(x + o.x, y + o.y, z + o.z);
    }

    /** effects: returns this minus {@code o} */
    public Vector3 subtract(Vector3 o) {
        return new Vector3(x - o.x, y - o.y, z - o.z);
    }

    /** requires: {@code k} finite. effects: returns this scaled by {@code k} */
    public Vector3 scale(double k) {
        return new Vector3(x * k, y * k, z * k);
    }

    /** effects: returns the dot product with {@code o} */
    public double dot(Vector3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    /** effects: returns the cross product this × {@code o} */
    public Vector3 cross(Vector3 o) {
        return new Vector3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    /** effects: returns the length */
    public double length() {
        return Math.sqrt(dot(this));
    }

    /**
     * effects: returns this scaled to length 1<br>
     * throws: {@link IllegalArgumentException} if this is (nearly) the zero vector
     */
    public Vector3 normalize() {
        double length = length();
        if (length < 1e-12) {
            throw new IllegalArgumentException("the zero vector has no direction");
        }
        return scale(1.0 / length);
    }

    /**
     * effects: returns the angle in radians between this and {@code o}, in {@code [0, π]}<br>
     * throws: {@link IllegalArgumentException} if either is (nearly) the zero vector
     */
    public double angleTo(Vector3 o) {
        double cos = normalize().dot(o.normalize());
        return Math.acos(Math.max(-1.0, Math.min(1.0, cos)));
    }
}
