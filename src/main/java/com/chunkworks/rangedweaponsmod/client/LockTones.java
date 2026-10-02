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

import com.chunkworks.rangedweaponsmod.LauncherItem;
import com.chunkworks.rangedweaponsmod.ModData;
import com.chunkworks.rangedweaponsmod.ModSounds;
import com.chunkworks.rangedweaponsmod.domain.Seeker;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The seeker's voice in the holder's ears (D-0028), read off the synced {@link ModData#LOCK}: a
 * growl while a target is acquired, rising in pitch with the progress; a steady tone while locked;
 * a short drop-out when a lock is lost rather than fired. Heard by the holder only, at the
 * listener, as a seeker's tone is in a headset.
 *
 * <p>Cost, stated: one attachment read and a few comparisons per client tick.
 */
public final class LockTones {
    private LockTones() {}

    /** Ticks after a shot during which a vanishing lock was fired, not lost. */
    private static final int FIRED_GRACE_TICKS = 5;

    private static Tone growl;
    private static Tone lock;
    private static boolean wasLocked;
    private static long lastShotTick = Long.MIN_VALUE;
    private static long ticks;

    /** effects: steps the tones for this client tick */
    static void tick(Minecraft mc, LocalPlayer player) {
        ticks++;
        boolean launcher = player != null && LauncherItem.isLauncher(player.getMainHandItem());
        Seeker seeker = player == null ? Seeker.IDLE : player.getData(ModData.LOCK);
        boolean acquiring = launcher && seeker.isAcquiring() && !seeker.isLocked();
        boolean locked = launcher && seeker.isLocked();
        if (acquiring && (growl == null || growl.isStopped())) {
            growl = new Tone(ModSounds.SEEKER_GROWL.get(), () -> currentlyAcquiring(),
                    () -> 0.8 + 0.8 * currentProgress());
            mc.getSoundManager().play(growl);
        }
        if (locked && (lock == null || lock.isStopped())) {
            lock = new Tone(ModSounds.SEEKER_LOCK.get(), () -> currentlyLocked(), () -> 1.0);
            mc.getSoundManager().play(lock);
        }
        if (wasLocked && !locked && ticks - lastShotTick > FIRED_GRACE_TICKS && player != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.SEEKER_LOST.get(), 1.0f, 0.6f));
        }
        wasLocked = locked;
    }

    /** effects: notes that the holder fired, so the lock going with the rocket is not heard as lost */
    public static void onShotFired() {
        lastShotTick = ticks;
    }

    /** effects: silence, for a new world */
    static void reset() {
        wasLocked = false;
        growl = null;
        lock = null;
    }

    private static Seeker current() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null || !LauncherItem.isLauncher(player.getMainHandItem()) ? Seeker.IDLE : player.getData(ModData.LOCK);
    }

    private static boolean currentlyAcquiring() {
        Seeker s = current();
        return s.isAcquiring() && !s.isLocked();
    }

    private static boolean currentlyLocked() {
        return current().isLocked();
    }

    private static double currentProgress() {
        return current().progress();
    }

    /** A looping tone at the listener that stops itself when its condition ends. */
    private static final class Tone extends AbstractTickableSoundInstance {
        private final BooleanSupplier active;
        private final DoubleSupplier pitchOf;

        Tone(SoundEvent sound, BooleanSupplier active, DoubleSupplier pitchOf) {
            super(sound, SoundSource.PLAYERS, RandomSource.create());
            this.active = active;
            this.pitchOf = pitchOf;
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.6f;
            this.pitch = (float) pitchOf.getAsDouble();
        }

        @Override
        public void tick() {
            if (!active.getAsBoolean()) {
                this.stop();
                return;
            }
            this.pitch = (float) pitchOf.getAsDouble();
        }
    }
}
