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
 * How a guided rocket steers (D-0028): lead pursuit with a capped turn.
 *
 * <p>Each tick the rocket aims at the point where it would meet its target if the target kept
 * its present velocity and the rocket flew straight at its present speed; when no such point
 * exists (a target outrunning it) it aims at the target itself. It then turns toward that aim
 * by at most {@link #TURN_PER_TICK}, so it flies an arc a target can still sidestep late in the
 * run, rather than snapping onto it.
 *
 * <p>Not instantiable: constants and pure functions.
 */
public final class Guidance {
    private Guidance() {}

    /** The most a rocket's heading changes in one tick, in radians (8 degrees). */
    public static final double TURN_PER_TICK = Math.toRadians(8.0);

    /**
     * requires: {@code velocity} and {@code toTarget} non-zero; {@code speed > 0};
     * {@code maxTurn} in {@code [0, π]}<br>
     * effects: returns the rocket's next velocity, of length {@code speed}: its heading turned by at
     * most {@code maxTurn} toward {@link #aimPoint(Vector3, Vector3, double)}<br>
     * throws: {@link IllegalArgumentException} if a requirement fails
     *
     * @param velocity       the rocket's velocity now
     * @param speed          its speed for the next tick
     * @param toTarget       the target's position less the rocket's
     * @param targetVelocity the target's velocity, blocks a tick
     * @param maxTurn        the most the heading may turn, radians
     * @return the next velocity
     */
    public static Vector3 steer(Vector3 velocity, double speed, Vector3 toTarget, Vector3 targetVelocity, double maxTurn) {
        if (!(speed > 0.0) || !Double.isFinite(speed)) {
            throw new IllegalArgumentException("speed must be finite and > 0, was " + speed);
        }
        if (!(maxTurn >= 0.0 && maxTurn <= Math.PI)) {
            throw new IllegalArgumentException("maxTurn must be in [0, π], was " + maxTurn);
        }
        Vector3 heading = velocity.normalize();
        Vector3 aim = aimPoint(toTarget, targetVelocity, speed);
        return rotateToward(heading, aim.normalize(), maxTurn).scale(speed);
    }

    /**
     * requires: {@code speed > 0}; {@code toTarget} non-zero<br>
     * effects: returns where to aim, relative to the rocket: {@code toTarget + targetVelocity·t}
     * for the least {@code t > 0} at which a rocket flying straight at {@code speed} meets a
     * target keeping {@code targetVelocity}; {@code toTarget} itself if there is no such {@code t}
     *
     * @param toTarget       the target's position less the rocket's
     * @param targetVelocity the target's velocity
     * @param speed          the rocket's speed
     * @return the aim point, relative to the rocket
     */
    public static Vector3 aimPoint(Vector3 toTarget, Vector3 targetVelocity, double speed) {
        // |p + v t| = s t  <=>  (v.v - s^2) t^2 + 2 (p.v) t + p.p = 0
        double a = targetVelocity.dot(targetVelocity) - speed * speed;
        double b = 2.0 * toTarget.dot(targetVelocity);
        double c = toTarget.dot(toTarget);
        double t = Double.NaN;
        if (Math.abs(a) < 1e-9) {
            if (b < 0.0) {
                t = -c / b;
            }
        } else {
            double discriminant = b * b - 4.0 * a * c;
            if (discriminant >= 0.0) {
                double root = Math.sqrt(discriminant);
                double t1 = (-b - root) / (2.0 * a);
                double t2 = (-b + root) / (2.0 * a);
                double least = Math.min(t1, t2);
                double most = Math.max(t1, t2);
                t = least > 0.0 ? least : (most > 0.0 ? most : Double.NaN);
            }
        }
        if (!(t > 0.0) || !Double.isFinite(t)) {
            return toTarget;
        }
        Vector3 aim = toTarget.add(targetVelocity.scale(t));
        return aim.length() < 1e-9 ? toTarget : aim;
    }

    /**
     * requires: {@code from} and {@code to} of length 1; {@code maxAngle} in {@code [0, π]}<br>
     * effects: returns the unit vector {@code from} turned toward {@code to} by
     * {@code min(maxAngle, angle between them)}, in the plane they span; when they are opposite,
     * about the vertical, so a target dead astern is met by a level turn rather than a loop (about
     * east when heading straight up or down)
     *
     * @param from     the heading now
     * @param to       the heading wanted
     * @param maxAngle the most it may turn, radians
     * @return the new heading, of length 1
     */
    public static Vector3 rotateToward(Vector3 from, Vector3 to, double maxAngle) {
        double angle = from.angleTo(to);
        if (angle <= maxAngle) {
            return to.normalize();
        }
        Vector3 axis = from.cross(to);
        if (axis.length() < 1e-9) {
            // Opposite: turn about the part of the vertical square to the heading.
            axis = Vector3.UP.subtract(from.scale(from.dot(Vector3.UP)));
            if (axis.length() < 1e-9) {
                axis = new Vector3(1.0, 0.0, 0.0);
            }
        }
        Vector3 k = axis.normalize();
        // Rodrigues' rotation; k is perpendicular to from, so the k(k.from) term vanishes.
        return from.scale(Math.cos(maxAngle)).add(k.cross(from).scale(Math.sin(maxAngle))).normalize();
    }
}
