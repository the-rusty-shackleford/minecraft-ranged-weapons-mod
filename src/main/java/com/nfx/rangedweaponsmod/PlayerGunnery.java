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

import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.RangedWeapons;
import com.nfx.rangedweapons.api.Shot;
import com.nfx.rangedweapons.api.ShotReport;
import com.nfx.rangedweapons.api.WeaponProfile;
import com.nfx.rangedweapons.api.WeaponStats;
import com.nfx.rangedweaponsmod.domain.FireClock;
import com.nfx.rangedweaponsmod.domain.ReloadPlan;
import com.nfx.rangedweaponsmod.domain.StanceSpread;
import com.nfx.rangedweaponsmod.domain.StanceSpread.Stance;
import com.nfx.rangedweaponsmod.domain.Trigger;
import com.nfx.rangedweaponsmod.domain.Trigger.Action;
import com.nfx.rangedweaponsmod.domain.Trigger.Inputs;
import com.nfx.rangedweaponsmod.net.ShotFiredPayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A player's trigger finger, on the server: the adapter between the game
 * and the pure {@link Trigger}. Each tick it reads the player and the held
 * gun into {@link Inputs}, asks the trigger what to do, and does it --
 * fires through the protocol, reloads from the inventory, or clicks.
 *
 * <p>Takes a {@link Player}, not a {@code ServerPlayer}, so the gametest
 * framework's mock player can drive it directly; the one thing that needs
 * a real connection, telling the shooter about the kick, is skipped for a
 * player without one.
 *
 * <p>Cost, stated: for a player not holding a gun, one item-class check per
 * tick. For one holding a gun, a data-map lookup, a handful of component
 * reads and, on the tick of a shot, the shot itself.
 */
public final class PlayerGunnery {
    private PlayerGunnery() {}

    /**
     * Where a shot starts, relative to the eye: this far along the look, this
     * far to the main-hand side, and this far below -- about where the gun's
     * muzzle sits in first person, so the tracer leaves the gun rather than
     * the shooter's face.
     */
    private static final double MUZZLE_FORWARD = 1.1;
    private static final double MUZZLE_SIDE = 0.32;
    private static final double MUZZLE_DOWN = 0.22;
    /** Squared blocks moved since the last tick above which the player counts as moving. */
    private static final double MOVING_THRESHOLD_SQR = 0.05 * 0.05;

    /** The game-bus hook. Both sides fire it; only the server acts. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level() instanceof ServerLevel level) {
            tick(player, level);
        }
    }

    /**
     * effects: records the trigger's new state for {@code player} if it
     * changed; a press re-arms the dry click. Edge-triggered: vanilla
     * repeats a held use every four ticks, and a repeat is not a new pull.
     *
     * @param player the player whose trigger changed
     * @param held   whether it is now held
     */
    public static void onTrigger(Player player, boolean held) {
        Gunnery gunnery = player.getData(ModData.GUNNERY);
        if (gunnery.held() == held) {
            return;
        }
        player.setData(ModData.GUNNERY, held ? gunnery.pressed() : gunnery.released());
    }

    /**
     * effects: records that {@code player} asked for a reload; honoured on
     * the next tick through the same rules as everything else
     *
     * @param player the player who pressed the reload key
     */
    public static void onReloadKey(Player player) {
        player.setData(ModData.GUNNERY, player.getData(ModData.GUNNERY).reloadAsked());
    }

    /**
     * One tick of the trigger finger.
     *
     * <p>effects: if {@code player} holds a gun in its main hand that the
     * protocol resolves, applies the {@link Trigger}'s action for this tick
     * to the gun, the player's inventory and the level, and records the
     * finger's new state; a pending reload request is consumed either way.
     * Otherwise nothing. A player with infinite materials (creative) fires
     * without spending rounds or ammunition and never needs to reload.
     *
     * @param player the player
     * @param level  the level the player is in
     */
    public static void tick(Player player, ServerLevel level) {
        ItemStack stack = player.getMainHandItem();
        if (!GunItem.isGun(stack)) {
            return;
        }
        RangedWeapon weapon = RangedWeapons.resolve(stack);
        if (weapon == null) {
            return;
        }
        Gunnery gunnery = player.getData(ModData.GUNNERY);
        long now = level.getGameTime();
        WeaponStats stats = weapon.stats(stack);
        Reload reload = stack.get(ModData.RELOAD.get());
        int capacity = weapon.capacity(stack);
        // Creative has unlimited ammunition, as it has unlimited arrows: the
        // gun is presented to the trigger as full and no shot spends a round.
        boolean unlimited = player.hasInfiniteMaterials();
        int rounds = unlimited ? capacity : weapon.rounds(stack);
        boolean ammoAvailable = unlimited || countAmmo(player, weapon.profile()) > 0;

        Inputs inputs = new Inputs(gunnery.held(), gunnery.reloadRequested(), now, gunnery.nextShotAt(),
                rounds, capacity, reload != null, reload != null && reload.done(now), ammoAvailable,
                gunnery.clickedThisPress(), Math.min(stats.fireRateTicks(), FireClock.MAX_RATE_TICKS));
        Action action = Trigger.tick(inputs);

        Gunnery after = gunnery.reloadRequested() ? gunnery.reloadHandled() : gunnery;
        switch (action) {
            case FIRE -> after = fire(player, level, weapon, stack, stats, now, after, unlimited);
            case START_RELOAD -> startReload(player, level, stack, stats, now);
            case FINISH_RELOAD -> finishReload(player, level, weapon, stack, rounds, capacity);
            case CLICK_EMPTY -> {
                play(level, player, ModSounds.EMPTY_CLICK.get(), 0.6f, 1.0f);
                after = after.clicked();
            }
            case IDLE -> { }
        }
        if (after != gunnery) {
            player.setData(ModData.GUNNERY, after);
        }
    }

    private static Gunnery fire(Player player, ServerLevel level, RangedWeapon weapon, ItemStack stack,
                                WeaponStats stats, long now, Gunnery gunnery, boolean unlimited) {
        Vec3 look = player.getViewVector(1.0f);
        Vec3 origin = muzzle(player, look);
        Handling handling = Handling.of(stack);
        float spreadMultiplier = StanceSpread.multiplier(stance(player), handling.spread());
        Shot shot = Shot.of(stats.scaled(1.0f, spreadMultiplier), origin, look);

        weapon.fire(level, player, stack, shot);
        if (!unlimited) {
            weapon.consumeRound(stack);
        }
        stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);   // a no-op for creative, as vanilla has it
        ShotReport.play(level, player, weapon.profile(), origin, SoundSource.PLAYERS,
                0.95f + player.getRandom().nextFloat() * 0.1f);
        // Firing breaks a sprint, the way drawing a bow does.
        player.setSprinting(false);

        if (player instanceof ServerPlayer serverPlayer) {
            float yawKick = handling.recoilYaw() * (player.getRandom().nextBoolean() ? 1.0f : -1.0f);
            PacketDistributor.sendToPlayer(serverPlayer, new ShotFiredPayload(handling.recoilPitch(), yawKick, handling.recovery()));
        }
        return gunnery.firedUntil(FireClock.next(now, Math.min(stats.fireRateTicks(), FireClock.MAX_RATE_TICKS)));
    }

    private static void startReload(Player player, ServerLevel level, ItemStack stack, WeaponStats stats, long now) {
        stack.set(ModData.RELOAD.get(), new Reload(now, ReloadPlan.durationTicks(stats.fullReloadTicks())));
        play(level, player, ModSounds.RELOAD_START.get(), 0.8f, 1.0f);
    }

    private static void finishReload(Player player, ServerLevel level, RangedWeapon weapon, ItemStack stack,
                                     int rounds, int capacity) {
        stack.remove(ModData.RELOAD.get());
        boolean unlimited = player.hasInfiniteMaterials();
        int available = unlimited ? capacity : countAmmo(player, weapon.profile());
        int loaded = ReloadPlan.roundsToLoad(rounds, capacity, available);
        if (loaded == 0) {
            return;
        }
        if (!unlimited) {
            takeAmmo(player, weapon.profile(), loaded);
        }
        weapon.load(stack, rounds + loaded);
        play(level, player, ModSounds.RELOAD_END.get(), 0.8f, 1.0f);
    }

    /**
     * effects: returns how many of the profile's ammunition item the player
     * carries, zero if the profile names none
     *
     * @param player  the player
     * @param profile the weapon's profile
     * @return the count
     */
    public static int countAmmo(Player player, WeaponProfile profile) {
        Item ammo = ammoItem(profile);
        if (ammo == null) {
            return 0;
        }
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ammo)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** requires: the player carries at least {@code count}; effects: removes that many, first slots first */
    private static void takeAmmo(Player player, WeaponProfile profile, int count) {
        Item ammo = ammoItem(profile);
        Inventory inventory = player.getInventory();
        int remaining = count;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ammo)) {
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }
        }
    }

    private static Item ammoItem(WeaponProfile profile) {
        return profile.ammoItem().map(BuiltInRegistries.ITEM::get).orElse(null);
    }

    /** effects: returns where the muzzle is: forward of the eye, out to the main-hand side, a little down */
    public static Vec3 muzzle(Player player, Vec3 look) {
        double side = player.getMainArm() == HumanoidArm.RIGHT ? MUZZLE_SIDE : -MUZZLE_SIDE;
        Vec3 right = new Vec3(-look.z, 0.0, look.x);
        double length = right.length();
        right = length < 1e-6 ? Vec3.ZERO : right.scale(1.0 / length);
        return player.getEyePosition()
                .add(look.scale(MUZZLE_FORWARD))
                .add(right.scale(side))
                .subtract(0.0, MUZZLE_DOWN, 0.0);
    }

    private static Stance stance(Player player) {
        double dx = player.getX() - player.xo;
        double dz = player.getZ() - player.zo;
        boolean moving = dx * dx + dz * dz > MOVING_THRESHOLD_SQR;
        return new Stance(player.isSprinting(), moving, player.isCrouching(), !player.onGround());
    }

    private static void play(ServerLevel level, Player player, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
