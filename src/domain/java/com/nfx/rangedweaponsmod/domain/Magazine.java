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
package com.nfx.rangedweaponsmod.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What is in a detachable magazine: an ordered run of rounds, the first to
 * be fired first.
 *
 * <p>Kept as segments -- runs of one kind of round -- rather than as a list
 * of every round, because that is how a player loads one: a handful of one
 * kind, then a handful of another. A magazine holding 12 shells and 3 slugs
 * fires the 12 shells, then the 3 slugs. Mixed magazines are the point, not
 * an accident: it is on the player to keep a magazine of slugs, a magazine
 * of shells, and a mixed one for a fight that starts far and ends close.
 *
 * <p>Immutable; every operation returns a new magazine. Rounds are named by
 * {@code T} -- whatever the caller uses for an item -- so this stays free of
 * the game.
 *
 * <p>RI: {@code capacity >= 1}; every segment's count is {@code >= 1}; the
 * counts sum to at most {@code capacity}; no two adjacent segments hold the
 * same round (they would be one segment).<br>
 * AF: AF(capacity, segments) = "a magazine that holds up to {@code capacity}
 * rounds, loaded with the segments' rounds in order, the first segment's
 * first round next to fire".
 *
 * @param <T> however a round is named
 */
public final class Magazine<T> {

    /**
     * A run of one kind of round.
     *
     * @param round the round
     * @param count how many in a row, at least one
     * @param <T>   however a round is named
     */
    public record Segment<T>(T round, int count) {
        public Segment {
            if (round == null) {
                throw new IllegalArgumentException("a segment needs a round");
            }
            if (count < 1) {
                throw new IllegalArgumentException("a segment holds at least one round, was " + count);
            }
        }
    }

    private final int capacity;
    private final List<Segment<T>> segments;

    private Magazine(int capacity, List<Segment<T>> segments) {
        this.capacity = capacity;
        this.segments = List.copyOf(segments);
    }

    /**
     * effects: returns an empty magazine of {@code capacity}<br>
     * throws: {@link IllegalArgumentException} if {@code capacity < 1}
     */
    public static <T> Magazine<T> empty(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1, was " + capacity);
        }
        return new Magazine<>(capacity, List.of());
    }

    /**
     * effects: returns a magazine of {@code capacity} loaded with
     * {@code segments} in order, adjacent runs of the same round merged and
     * empty runs dropped<br>
     * throws: {@link IllegalArgumentException} if {@code capacity < 1} or the
     * counts exceed it
     */
    public static <T> Magazine<T> of(int capacity, List<Segment<T>> segments) {
        Magazine<T> magazine = empty(capacity);
        for (Segment<T> segment : segments) {
            magazine = magazine.push(segment.round(), segment.count());
        }
        return magazine;
    }

    public int capacity() {
        return capacity;
    }

    /** effects: returns the segments, first to fire first; never null, possibly empty */
    public List<Segment<T>> segments() {
        return segments;
    }

    /** effects: returns how many rounds are loaded, in {@code [0, capacity]} */
    public int rounds() {
        int total = 0;
        for (Segment<T> segment : segments) {
            total += segment.count();
        }
        return total;
    }

    /** effects: returns how many more rounds fit */
    public int space() {
        return capacity - rounds();
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }

    public boolean isFull() {
        return space() == 0;
    }

    /** effects: returns the round that fires next, empty if none is loaded */
    public Optional<T> next() {
        return segments.isEmpty() ? Optional.empty() : Optional.of(segments.get(0).round());
    }

    /**
     * effects: returns this magazine with {@code count} of {@code round}
     * loaded after everything already in it, merged into the last segment
     * if that holds the same round; zero rounds changes nothing<br>
     * throws: {@link IllegalArgumentException} if {@code count < 0} or the
     * rounds do not fit
     */
    public Magazine<T> push(T round, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0, was " + count);
        }
        if (count == 0) {
            return this;
        }
        if (count > space()) {
            throw new IllegalArgumentException(count + " rounds do not fit: " + space() + " of " + capacity + " free");
        }
        List<Segment<T>> out = new ArrayList<>(segments);
        int last = out.size() - 1;
        if (last >= 0 && out.get(last).round().equals(round)) {
            out.set(last, new Segment<>(round, out.get(last).count() + count));
        } else {
            out.add(new Segment<>(round, count));
        }
        return new Magazine<>(capacity, out);
    }

    /**
     * effects: returns this magazine with its next round gone: one fired<br>
     * throws: {@link IllegalStateException} if it is empty
     */
    public Magazine<T> pop() {
        if (segments.isEmpty()) {
            throw new IllegalStateException("cannot fire from an empty magazine");
        }
        List<Segment<T>> out = new ArrayList<>(segments);
        Segment<T> first = out.get(0);
        if (first.count() == 1) {
            out.remove(0);
        } else {
            out.set(0, new Segment<>(first.round(), first.count() - 1));
        }
        return new Magazine<>(capacity, out);
    }

    /**
     * Fills the magazine from what a player carries.
     *
     * <p>effects: returns this magazine topped up with rounds from
     * {@code carried} -- the accepted rounds in inventory order with how
     * many of each -- taking the first kind first, as many as it has, then
     * the next, until the magazine is full or the rounds run out; and how
     * many of each kind were taken. A magazine that already holds rounds is
     * topped up after them, so what was loaded still fires first.
     *
     * @param carried the accepted rounds the player carries, in inventory
     *                order, each with its count
     * @return the filled magazine and what it took, by round
     */
    public Filled<T> fill(List<Map.Entry<T, Integer>> carried) {
        Magazine<T> magazine = this;
        List<Map.Entry<T, Integer>> taken = new ArrayList<>();
        for (Map.Entry<T, Integer> entry : carried) {
            if (magazine.isFull()) {
                break;
            }
            int count = Math.min(entry.getValue(), magazine.space());
            if (count <= 0) {
                continue;
            }
            magazine = magazine.push(entry.getKey(), count);
            taken.add(Map.entry(entry.getKey(), count));
        }
        return new Filled<>(magazine, taken);
    }

    /**
     * A magazine after a fill, with what the fill took.
     *
     * @param magazine the magazine afterwards
     * @param taken    the rounds taken, by kind, in the order they were taken
     */
    public record Filled<T>(Magazine<T> magazine, List<Map.Entry<T, Integer>> taken) {}

    @Override
    public boolean equals(Object o) {
        return o instanceof Magazine<?> other && other.capacity == capacity && other.segments.equals(segments);
    }

    @Override
    public int hashCode() {
        return 31 * capacity + segments.hashCode();
    }

    @Override
    public String toString() {
        return "Magazine[" + rounds() + "/" + capacity + " " + segments + "]";
    }
}
