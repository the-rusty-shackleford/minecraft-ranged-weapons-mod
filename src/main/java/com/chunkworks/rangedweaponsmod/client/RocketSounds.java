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
package com.chunkworks.rangedweaponsmod.client;

import com.chunkworks.rangedweaponsmod.ModSounds;
import com.chunkworks.rangedweaponsmod.Rocket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * A rocket's motor as heard on a client: a loop that rides with the rocket and ends with it.
 * Called by the rocket itself on its first client tick, so every client tracking it hears it.
 */
public final class RocketSounds {
    private RocketSounds() {}

    /** effects: starts the motor's loop on {@code rocket} */
    public static void motor(Rocket rocket) {
        Minecraft.getInstance().getSoundManager().play(new Motor(rocket));
    }

    private static final class Motor extends AbstractTickableSoundInstance {
        private final Rocket rocket;

        Motor(Rocket rocket) {
            super(ModSounds.ROCKET_MOTOR.get(), SoundSource.PLAYERS, RandomSource.create());
            this.rocket = rocket;
            this.looping = true;
            this.delay = 0;
            this.volume = 2.0f;
            this.x = rocket.getX();
            this.y = rocket.getY();
            this.z = rocket.getZ();
        }

        @Override
        public void tick() {
            if (rocket.isRemoved()) {
                this.stop();
                return;
            }
            this.x = rocket.getX();
            this.y = rocket.getY();
            this.z = rocket.getZ();
        }
    }
}
