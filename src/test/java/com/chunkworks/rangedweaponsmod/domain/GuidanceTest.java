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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Partitions. Target: stationary ahead / stationary off-axis / crossing / approaching / behind /
 * outrunning the rocket. Turn: within the cap / beyond it / directly opposite. Inputs: valid /
 * zero velocity / non-positive speed / turn out of [0, π]. The flights run the real motor and
 * the real turn cap tick by tick, as the entity does.
 */
final class GuidanceTest {

    /** One flight, as the entity flies it: returns the closest approach, and the worst turn in a tick. */
    private record Flight(double closest, double worstTurn, int ticks) {}

    private static Flight fly(Vector3 launch, Vector3 heading, Vector3 target, Vector3 targetVelocity) {
        Vector3 position = launch;
        Vector3 velocity = heading.normalize().scale(Motor.speed(0));
        Vector3 at = target;
        double closest = at.subtract(position).length();
        double worstTurn = 0.0;
        for (int age = 0; age < Motor.LIFETIME_TICKS; age++) {
            double speed = Motor.speed(age);
            Vector3 next = Motor.steers(age) && at.subtract(position).length() > 1e-9
                    ? Guidance.steer(velocity, speed, at.subtract(position), targetVelocity, Guidance.TURN_PER_TICK)
                    : velocity.normalize().scale(speed);
            worstTurn = Math.max(worstTurn, velocity.angleTo(next));
            Vector3 start = position;
            position = position.add(next);
            at = at.add(targetVelocity);
            closest = Math.min(closest, distanceToSegment(at, start, position));
            velocity = next;
            if (closest <= Motor.PROXIMITY) {
                return new Flight(closest, worstTurn, age + 1);
            }
        }
        return new Flight(closest, worstTurn, Motor.LIFETIME_TICKS);
    }

    private static double distanceToSegment(Vector3 p, Vector3 a, Vector3 b) {
        Vector3 ab = b.subtract(a);
        double length2 = ab.dot(ab);
        double t = length2 < 1e-12 ? 0.0 : Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / length2));
        return p.subtract(a.add(ab.scale(t))).length();
    }

    @Test
    void aStationaryTargetAheadIsReached() {
        Flight f = fly(Vector3.ZERO, new Vector3(0, 0, 1), new Vector3(0, 0, 60), Vector3.ZERO);
        assertTrue(f.closest() <= Motor.PROXIMITY, "closest " + f.closest());
    }

    @Test
    void aStationaryTargetThirtyDegreesOffTheLaunchLineIsReached() {
        Vector3 target = new Vector3(40 * Math.sin(Math.toRadians(30)), 0, 40 * Math.cos(Math.toRadians(30)));
        Flight f = fly(Vector3.ZERO, new Vector3(0, 0, 1), target, Vector3.ZERO);
        assertTrue(f.closest() <= Motor.PROXIMITY, "closest " + f.closest());
    }

    @Test
    void aTargetCrossingAtASprintIsIntercepted() {
        // A sprinting player moves about 0.28 blocks a tick.
        Flight f = fly(Vector3.ZERO, new Vector3(0, 0, 1), new Vector3(-10, 0, 50), new Vector3(0.28, 0, 0));
        assertTrue(f.closest() <= Motor.PROXIMITY, "closest " + f.closest());
    }

    @Test
    void anAircraftCrossingHighAndFastIsIntercepted() {
        // A plane at full throttle: about 1.2 blocks a tick, 30 up, crossing 70 out.
        Flight f = fly(Vector3.ZERO, new Vector3(0, 0.4, 1), new Vector3(-40, 30, 70), new Vector3(1.2, 0, 0));
        assertTrue(f.closest() <= Motor.PROXIMITY, "closest " + f.closest());
    }

    @Test
    void aTargetBehindTheLauncherIsTurnedOntoAndReached() {
        Flight f = fly(Vector3.ZERO, new Vector3(0, 0, 1), new Vector3(0, 0, -40), Vector3.ZERO);
        assertTrue(f.closest() <= Motor.PROXIMITY, "closest " + f.closest());
        assertTrue(f.ticks() > 20, "a full turn round takes time, took " + f.ticks());
    }

    @Test
    void noTickTurnsTheHeadingMoreThanTheCap() {
        Vector3[][] cases = {
                {new Vector3(0, 0, -40), Vector3.ZERO},
                {new Vector3(-10, 0, 50), new Vector3(0.28, 0, 0)},
                {new Vector3(30, 20, 5), new Vector3(0, 0, 0.5)},
        };
        for (Vector3[] c : cases) {
            Flight f = fly(Vector3.ZERO, new Vector3(0, 0, 1), c[0], c[1]);
            assertTrue(f.worstTurn() <= Guidance.TURN_PER_TICK + 1e-9, "turned " + Math.toDegrees(f.worstTurn()));
        }
    }

    @Test
    void aStationaryTargetIsAimedAtDirectly() {
        Vector3 p = new Vector3(3, 4, 12);
        assertEquals(p, Guidance.aimPoint(p, Vector3.ZERO, 2.5));
    }

    @Test
    void aCrossingTargetIsLedToWhereTheyWillMeet() {
        Vector3 p = new Vector3(0, 0, 40);
        Vector3 v = new Vector3(0.5, 0, 0);
        Vector3 aim = Guidance.aimPoint(p, v, 2.5);
        double t = aim.length() / 2.5;
        Vector3 there = p.add(v.scale(t));
        assertEquals(0.0, aim.subtract(there).length(), 1e-6);
        assertTrue(aim.x() > 0, "led ahead of the target");
    }

    @Test
    void aTargetOutrunningTheRocketIsAimedAtItself() {
        Vector3 p = new Vector3(0, 0, 40);
        assertEquals(p, Guidance.aimPoint(p, new Vector3(0, 0, 3.0), 2.5));
    }

    @Test
    void aTargetApproachingAtTheRocketsOwnSpeedIsMetHalfway() {
        Vector3 aim = Guidance.aimPoint(new Vector3(0, 0, 40), new Vector3(0, 0, -2.5), 2.5);
        assertEquals(20.0, aim.z(), 1e-9);
    }

    @Test
    void aTurnWithinTheCapLandsOnTheWantedHeading() {
        Vector3 from = new Vector3(0, 0, 1);
        Vector3 to = new Vector3(Math.sin(Math.toRadians(5)), 0, Math.cos(Math.toRadians(5)));
        assertEquals(0.0, Guidance.rotateToward(from, to, Guidance.TURN_PER_TICK).subtract(to).length(), 1e-9);
    }

    @Test
    void aTurnBeyondTheCapTurnsExactlyTheCapTowardIt() {
        Vector3 from = new Vector3(0, 0, 1);
        Vector3 to = new Vector3(1, 0, 0);
        Vector3 got = Guidance.rotateToward(from, to, Guidance.TURN_PER_TICK);
        assertEquals(Guidance.TURN_PER_TICK, from.angleTo(got), 1e-9);
        assertTrue(got.x() > 0, "toward the wanted heading");
        assertEquals(1.0, got.length(), 1e-9);
    }

    @Test
    void anOppositeHeadingTurnsByTheCapAsALevelTurn() {
        Vector3 got = Guidance.rotateToward(new Vector3(0, 0, 1), new Vector3(0, 0, -1), Guidance.TURN_PER_TICK);
        assertEquals(Guidance.TURN_PER_TICK, new Vector3(0, 0, 1).angleTo(got), 1e-9);
        assertEquals(0.0, got.y(), 1e-9);
    }

    @Test
    void steeringKeepsTheGivenSpeed() {
        Vector3 v = Guidance.steer(new Vector3(0, 0, 1), 2.5, new Vector3(5, 0, 30), Vector3.ZERO, Guidance.TURN_PER_TICK);
        assertEquals(2.5, v.length(), 1e-9);
    }

    @Test
    void badInputsAreRefused() {
        Vector3 z = new Vector3(0, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> Guidance.steer(Vector3.ZERO, 2.5, z, Vector3.ZERO, 0.1));
        assertThrows(IllegalArgumentException.class, () -> Guidance.steer(z, 0.0, z, Vector3.ZERO, 0.1));
        assertThrows(IllegalArgumentException.class, () -> Guidance.steer(z, 2.5, z, Vector3.ZERO, -0.1));
        assertThrows(IllegalArgumentException.class, () -> Guidance.steer(z, 2.5, z, Vector3.ZERO, 4.0));
        assertThrows(IllegalArgumentException.class, () -> new Vector3(Double.NaN, 0, 0));
    }
}
