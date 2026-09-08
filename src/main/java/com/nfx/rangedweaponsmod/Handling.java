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
package com.nfx.rangedweaponsmod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nfx.rangedweapons.api.RangedWeapons;
import com.nfx.rangedweapons.api.WeaponClass;
import com.nfx.rangedweaponsmod.domain.StanceSpread.Factors;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * How a gun feels in a player's hands: the kick of a shot, how fast the
 * view settles, and how stance scales the spread. What the protocol's
 * profile does not say, because none of it exists for a mob.
 *
 * <p>Data, from the {@code rangedweaponsmod:handling} data map, with a
 * default per weapon class for a gun no pack has described. Immutable.
 *
 * <p>RI: {@code recoilPitch, recoilYaw >= 0} and finite; {@code recovery}
 * in {@code (0, 1]}; {@code spread} not null; {@code magazineChangeTicks >= 1};
 * {@code cycleSound} not null; {@code cycleDelayTicks >= 0}.
 *
 * @param recoilPitch         degrees the view kicks up per shot
 * @param recoilYaw           degrees the view kicks sideways per shot, direction random
 * @param recovery            the fraction of the kick recovered each tick
 * @param spread              how stance scales the weapon's spread
 * @param magazineChangeTicks how long a detachable magazine takes to change, for a
 *                            magazine of the standard size; the same whatever it holds
 * @param cycleSound          the sound of the action worked after a shot -- a pump
 *                            racked, a bolt cycled -- played {@code cycleDelayTicks}
 *                            after each shot; empty for a gun that cycles itself
 * @param cycleDelayTicks     ticks after a shot the cycle sound plays
 */
public record Handling(float recoilPitch, float recoilYaw, float recovery, Factors spread, int magazineChangeTicks,
                       Optional<ResourceLocation> cycleSound, int cycleDelayTicks) {

    /** A handling with the class's magazine change time and no cycle sound. */
    public Handling(float recoilPitch, float recoilYaw, float recovery, Factors spread) {
        this(recoilPitch, recoilYaw, recovery, spread, 30);
    }

    /** A handling with no cycle sound. */
    public Handling(float recoilPitch, float recoilYaw, float recovery, Factors spread, int magazineChangeTicks) {
        this(recoilPitch, recoilYaw, recovery, spread, magazineChangeTicks, Optional.empty(), 5);
    }

    private static final Codec<Factors> FACTORS_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(Float.MIN_VALUE, Float.MAX_VALUE).optionalFieldOf("crouching", Factors.DEFAULT.crouching()).forGetter(Factors::crouching),
            Codec.floatRange(Float.MIN_VALUE, Float.MAX_VALUE).optionalFieldOf("moving", Factors.DEFAULT.moving()).forGetter(Factors::moving),
            Codec.floatRange(Float.MIN_VALUE, Float.MAX_VALUE).optionalFieldOf("sprinting", Factors.DEFAULT.sprinting()).forGetter(Factors::sprinting),
            Codec.floatRange(Float.MIN_VALUE, Float.MAX_VALUE).optionalFieldOf("airborne", Factors.DEFAULT.airborne()).forGetter(Factors::airborne),
            Codec.floatRange(Float.MIN_VALUE, Float.MAX_VALUE).optionalFieldOf("aiming", Factors.DEFAULT.aiming()).forGetter(Factors::aiming)
    ).apply(i, Factors::new));

    /**
     * The datapack shape: {@code recoil_pitch}, {@code recoil_yaw}, {@code recovery},
     * optional {@code spread} factors, optional {@code magazine_change_ticks} (30),
     * optional {@code cycle_sound} (a sound event id) with {@code cycle_delay_ticks} (5).
     */
    public static final Codec<Handling> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.floatRange(0.0f, Float.MAX_VALUE).fieldOf("recoil_pitch").forGetter(Handling::recoilPitch),
            Codec.floatRange(0.0f, Float.MAX_VALUE).fieldOf("recoil_yaw").forGetter(Handling::recoilYaw),
            Codec.floatRange(Float.MIN_VALUE, 1.0f).fieldOf("recovery").forGetter(Handling::recovery),
            FACTORS_CODEC.optionalFieldOf("spread", Factors.DEFAULT).forGetter(Handling::spread),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("magazine_change_ticks", 30).forGetter(Handling::magazineChangeTicks),
            ResourceLocation.CODEC.optionalFieldOf("cycle_sound").forGetter(Handling::cycleSound),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("cycle_delay_ticks", 5).forGetter(Handling::cycleDelayTicks)
    ).apply(i, Handling::new));

    /**
     * @throws IllegalArgumentException if the RI does not hold
     */
    public Handling {
        if (!(recoilPitch >= 0.0f) || Float.isInfinite(recoilPitch)) {
            throw new IllegalArgumentException("recoilPitch must be finite and >= 0, was " + recoilPitch);
        }
        if (!(recoilYaw >= 0.0f) || Float.isInfinite(recoilYaw)) {
            throw new IllegalArgumentException("recoilYaw must be finite and >= 0, was " + recoilYaw);
        }
        if (!(recovery > 0.0f && recovery <= 1.0f)) {
            throw new IllegalArgumentException("recovery must be in (0, 1], was " + recovery);
        }
        if (spread == null) {
            throw new IllegalArgumentException("spread must not be null");
        }
        if (magazineChangeTicks < 1) {
            throw new IllegalArgumentException("magazineChangeTicks must be >= 1, was " + magazineChangeTicks);
        }
        if (cycleSound == null) {
            throw new IllegalArgumentException("cycleSound must not be null");
        }
        if (cycleDelayTicks < 0) {
            throw new IllegalArgumentException("cycleDelayTicks must be >= 0, was " + cycleDelayTicks);
        }
    }

    /**
     * effects: returns the handling for {@code stack}: the data map's entry
     * for its item if any pack wrote one, else the default for its profile's
     * class, else the default for an unclassified weapon
     *
     * @param stack the gun
     * @return its handling; never null
     */
    public static Handling of(ItemStack stack) {
        Handling declared = stack.getItemHolder().getData(ModData.HANDLING);
        if (declared != null) {
            return declared;
        }
        return RangedWeapons.profileOf(stack).map(p -> defaultFor(p.weaponClass())).orElse(UNCLASSIFIED);
    }

    private static final Handling UNCLASSIFIED = new Handling(1.0f, 0.5f, 0.4f, Factors.DEFAULT);

    /**
     * effects: returns a default handling for a class: sidearms and rifles
     * kick once and settle, shotguns kick hard, automatics kick lightly and
     * often, a flame weapon not at all; a sidearm's magazine changes in 24
     * ticks, a rifle's in 30, an automatic's box in 50
     *
     * @param weaponClass the profile's class
     * @return a handling; never null
     */
    public static Handling defaultFor(WeaponClass weaponClass) {
        if (weaponClass == WeaponClass.SIDEARM) {
            return new Handling(1.5f, 0.6f, 0.45f, Factors.DEFAULT, 24);
        }
        if (weaponClass == WeaponClass.RIFLE) {
            return new Handling(2.5f, 0.8f, 0.4f, Factors.DEFAULT);
        }
        if (weaponClass == WeaponClass.SHOTGUN) {
            return new Handling(4.0f, 1.2f, 0.35f, Factors.DEFAULT);
        }
        if (weaponClass == WeaponClass.AUTOMATIC) {
            return new Handling(0.55f, 0.25f, 0.35f, Factors.DEFAULT, 50);
        }
        if (weaponClass == WeaponClass.LAUNCHER) {
            return new Handling(5.0f, 1.0f, 0.3f, Factors.DEFAULT);
        }
        if (weaponClass == WeaponClass.FLAME) {
            return new Handling(0.0f, 0.0f, 1.0f, Factors.DEFAULT);
        }
        return UNCLASSIFIED;
    }
}
