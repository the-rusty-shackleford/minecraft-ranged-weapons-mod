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
import com.nfx.rangedweapons.api.WeaponClass;
import com.nfx.rangedweapons.api.WeaponProfile;
import com.nfx.rangedweapons.api.WeaponStats;
import com.nfx.rangedweaponsmod.domain.FireClock;
import com.nfx.rangedweaponsmod.domain.ReloadPlan;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import com.nfx.rangedweaponsmod.domain.AmmoChoice;
import com.nfx.rangedweaponsmod.domain.MagazineChoice;
import com.nfx.rangedweaponsmod.domain.StanceSpread;
import com.nfx.rangedweaponsmod.domain.StanceSpread.Stance;
import com.nfx.rangedweaponsmod.domain.Trigger;
import com.nfx.rangedweaponsmod.domain.Trigger.Action;
import com.nfx.rangedweaponsmod.domain.Trigger.Inputs;
import com.nfx.rangedweaponsmod.net.ShotFiredPayload;
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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
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
     * effects: records that {@code player} asked for a reload -- or, with
     * {@code swap}, a swap: out with what is loaded, in with the next
     * magazine or kind of round; honoured on the next tick through the same
     * rules as everything else
     *
     * @param player the player who pressed the reload key
     * @param swap   whether Shift was held: a swap rather than a top-up
     */
    public static void onReloadKey(Player player, boolean swap) {
        Gunnery gunnery = player.getData(ModData.GUNNERY);
        player.setData(ModData.GUNNERY, swap ? gunnery.swapAsked() : gunnery.reloadAsked());
    }

    /**
     * effects: records whether {@code player} is aiming down the sights,
     * which tightens spread by the gun's handling
     *
     * @param player the player
     * @param aiming whether the sights are up
     */
    public static void onAim(Player player, boolean aiming) {
        Gunnery gunnery = player.getData(ModData.GUNNERY);
        if (gunnery.aiming() != aiming) {
            player.setData(ModData.GUNNERY, gunnery.aiming(aiming));
        }
    }

    /**
     * One tick of the trigger finger.
     *
     * <p>effects: if {@code player} holds a gun in its main hand that the
     * protocol resolves, applies the {@link Trigger}'s action for this tick
     * to the gun, the player's inventory and the level, and records the
     * finger's new state; a pending reload or swap request is consumed
     * either way. Otherwise nothing. A player with infinite materials
     * (creative) fires without spending rounds or ammunition and never needs
     * to reload. A magazine-fed gun holding loose rounds -- loaded before
     * magazines existed, or by creative -- has them adopted into a magazine
     * first.
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
        boolean magazineFed = GunItem.isMagazineFed(stack);
        // Creative has unlimited ammunition, as it has unlimited arrows: the
        // gun is presented to the trigger as full and no shot spends a round.
        boolean unlimited = player.hasInfiniteMaterials();
        if (magazineFed && !unlimited) {
            Magazines.adopt(stack, weapon);
        }
        WeaponStats stats = weapon.stats(stack);
        Reload reload = stack.get(ModData.RELOAD.get());
        int capacity = weapon.capacity(stack);
        int rounds = unlimited ? capacity : weapon.rounds(stack);
        boolean ammoAvailable;
        boolean swapAvailable;
        if (unlimited) {
            ammoAvailable = true;
            swapAvailable = false;
        } else if (magazineFed) {
            // What can be loaded is a magazine with rounds in it; the one in
            // the gun is not carried and so is never its own replacement.
            boolean carried = !Magazines.loadedMagazineSlots(player, stack, weapon).isEmpty();
            ammoAvailable = carried;
            swapAvailable = carried;
        } else {
            ammoAvailable = countAmmo(player, weapon, stack) > 0;
            swapAvailable = nextKind(player, weapon, stack).isPresent();
        }

        // Only the automatic class fires for as long as the trigger is held;
        // every other gun fires once per pull, whatever its rate allows.
        boolean automatic = weapon.profile().weaponClass() == WeaponClass.AUTOMATIC;
        Inputs inputs = new Inputs(gunnery.held(), gunnery.reloadRequested(), now, gunnery.nextShotAt(),
                rounds, capacity, reload != null, reload != null && reload.done(now), ammoAvailable,
                gunnery.clickedThisPress(), Math.min(stats.fireRateTicks(), FireClock.MAX_RATE_TICKS),
                automatic, gunnery.firedThisPress(), gunnery.swapRequested(), swapAvailable);
        Action action = Trigger.tick(inputs);

        Gunnery after = gunnery.reloadRequested() || gunnery.swapRequested() ? gunnery.requestsHandled() : gunnery;
        switch (action) {
            case FIRE -> after = fire(player, level, weapon, stack, stats, now, after, unlimited, magazineFed);
            case START_RELOAD -> startReload(player, level, weapon, stack, stats, now, false, magazineFed, after);
            case START_SWAP -> startReload(player, level, weapon, stack, stats, now, true, magazineFed, after);
            case FINISH_RELOAD -> {
                stack.remove(ModData.RELOAD.get());
                if (unlimited) {
                    finishCreativeReload(player, level, weapon, stack, capacity);
                } else if (magazineFed) {
                    after = finishMagazineChange(player, level, weapon, stack, after, reload.swap());
                } else if (reload.swap()) {
                    finishKindSwap(player, level, weapon, stack, capacity);
                } else {
                    finishReload(player, level, weapon, stack, rounds, capacity);
                }
            }
            case CLICK_EMPTY -> {
                play(level, player, ModSounds.EMPTY_CLICK.get(), 0.6f, 1.0f);
                after = after.clicked();
            }
            case IDLE -> { }
        }
        // The action worked after the shot -- the pump, the bolt -- on its
        // own tick, whatever the trigger is doing by then.
        if (after.cycleAt() > 0 && now >= after.cycleAt()) {
            if (after.cycleSound() != null) {
                BuiltInRegistries.SOUND_EVENT.getOptional(after.cycleSound())
                        .ifPresent(sound -> play(level, player, sound, 1.0f, 1.0f));
            }
            after = after.cycled();
        }
        if (after != gunnery) {
            player.setData(ModData.GUNNERY, after);
        }
    }

    private static Gunnery fire(Player player, ServerLevel level, RangedWeapon weapon, ItemStack stack,
                                WeaponStats stats, long now, Gunnery gunnery, boolean unlimited, boolean magazineFed) {
        Vec3 look = player.getViewVector(1.0f);
        Vec3 origin = muzzle(player, look, gunnery.aiming());
        if (!level.getBlockState(BlockPos.containing(origin)).getCollisionShape(level, BlockPos.containing(origin)).isEmpty()) {
            origin = player.getEyePosition();   // the muzzle is in a wall: shoot from the eye, not from inside it
        }
        Handling handling = Handling.of(stack);
        float spreadMultiplier = StanceSpread.multiplier(stance(player), handling.spread());
        Shot shot = Shot.of(stats.scaled(1.0f, spreadMultiplier), origin, aim(level, player, look, origin));

        weapon.fire(level, player, stack, shot);
        if (!unlimited) {
            weapon.consumeRound(stack);
            if (magazineFed && Magazines.inserted(stack).isPresent()) {
                // The round left the magazine too; the store learns the next one.
                Magazines.pop(stack, weapon);
            }
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
        Gunnery fired = gunnery.firedUntil(FireClock.next(now, Math.min(stats.fireRateTicks(), FireClock.MAX_RATE_TICKS)));
        return handling.cycleSound().map(sound -> fired.cycleDue(now + handling.cycleDelayTicks(), sound)).orElse(fired);
    }

    /**
     * effects: starts the reload on the gun, with the sound. A tube's reload
     * takes the profile's full reload time. A magazine change takes the
     * gun's handling time, the same whatever the magazine holds -- and a
     * quarter more per doubling of the standard capacity for the magazine
     * that would go in now, if one is carried (the choice is made again when
     * the change finishes, on whatever is carried then).
     */
    private static void startReload(Player player, ServerLevel level, RangedWeapon weapon, ItemStack stack,
                                    WeaponStats stats, long now, boolean swap, boolean magazineFed, Gunnery gunnery) {
        int duration;
        if (magazineFed && !player.hasInfiniteMaterials()) {
            int standard = weapon.profile().defaults().capacity();
            int incoming = chooseMagazine(player, stack, weapon, gunnery, swap)
                    .map(slot -> Magazines.contents(player.getInventory().getItem(slot)).capacity())
                    .orElse(standard);
            duration = ReloadPlan.magazineChangeTicks(Handling.of(stack).magazineChangeTicks(), standard, incoming);
        } else {
            duration = ReloadPlan.durationTicks(stats.fullReloadTicks());
        }
        stack.set(ModData.RELOAD.get(), new Reload(now, duration, swap));
        play(level, player, ModSounds.RELOAD_START.get(), 0.8f, 1.0f);
    }

    /** effects: returns the inventory slot a change or a swap would take its magazine from right now, if any */
    private static Optional<Integer> chooseMagazine(Player player, ItemStack stack, RangedWeapon weapon, Gunnery gunnery,
                                                    boolean swap) {
        List<Integer> slots = Magazines.loadedMagazineSlots(player, stack, weapon);
        OptionalInt last = gunnery.lastSwapSlot() < 0 ? OptionalInt.empty() : OptionalInt.of(gunnery.lastSwapSlot());
        OptionalInt choice = swap ? MagazineChoice.forSwap(slots, last) : MagazineChoice.forReload(slots);
        return choice.isPresent() ? Optional.of(choice.getAsInt()) : Optional.empty();
    }

    /** Creative loads its native round, or keeps the one it holds; it needs no magazine and no ammunition. */
    private static void finishCreativeReload(Player player, ServerLevel level, RangedWeapon weapon, ItemStack stack,
                                             int capacity) {
        Optional<Item> round = chooseAmmo(player, weapon, stack);
        Item native_ = weapon.profile().ammoItem().flatMap(BuiltInRegistries.ITEM::getOptional).orElse(null);
        Item chosen = round.orElse(weapon.loadedAmmo(stack).orElse(native_));
        if (chosen != null) {
            weapon.load(stack, capacity, chosen);
        } else {
            weapon.load(stack, capacity);
        }
        play(level, player, ModSounds.RELOAD_END.get(), 0.8f, 1.0f);
    }

    /**
     * The magazine change: the magazine in the gun comes out and the chosen
     * one goes in, trading places in the inventory -- so a half-spent
     * magazine is kept, and a swap walks the carried magazines in order.
     *
     * <p>effects: if a loaded magazine the gun takes is carried, puts the
     * gun's magazine (if any) in its slot and it in the gun, with the sound;
     * returns the finger remembering the slot. Otherwise nothing: the
     * magazine was dropped mid-change.
     */
    private static Gunnery finishMagazineChange(Player player, ServerLevel level, RangedWeapon weapon, ItemStack stack,
                                                Gunnery gunnery, boolean swap) {
        Optional<Integer> choice = chooseMagazine(player, stack, weapon, gunnery, swap);
        if (choice.isEmpty()) {
            return gunnery;
        }
        int slot = choice.get();
        ItemStack incoming = player.getInventory().getItem(slot);
        ItemStack outgoing = Magazines.eject(stack, weapon);
        player.getInventory().setItem(slot, outgoing);
        Magazines.insert(stack, weapon, incoming);
        play(level, player, ModSounds.RELOAD_END.get(), 0.8f, 1.0f);
        return gunnery.swappedFrom(slot);
    }

    /**
     * A gun loaded directly changing what it is loaded with: the rounds in
     * it go back to the inventory and the next kind carried goes in.
     *
     * <p>effects: gives the loaded rounds back (dropped if they do not fit),
     * then loads the kind after the loaded one in inventory order, as many
     * as are carried; the sound plays if anything was loaded
     */
    private static void finishKindSwap(Player player, ServerLevel level, RangedWeapon weapon, ItemStack stack, int capacity) {
        Optional<Item> next = nextKind(player, weapon, stack);
        int rounds = weapon.rounds(stack);
        Optional<Item> loaded = weapon.loadedAmmo(stack)
                .or(() -> weapon.profile().ammoItem().flatMap(BuiltInRegistries.ITEM::getOptional));
        if (rounds > 0 && loaded.isPresent()) {
            player.getInventory().placeItemBackInInventory(new ItemStack(loaded.get(), rounds));
        }
        weapon.load(stack, 0);
        if (next.isEmpty()) {
            return;
        }
        int loadedNow = ReloadPlan.roundsToLoad(0, capacity, countItem(player, next.get()));
        if (loadedNow == 0) {
            return;
        }
        takeItem(player, next.get(), loadedNow);
        weapon.load(stack, loadedNow, next.get());
        play(level, player, ModSounds.RELOAD_END.get(), 0.8f, 1.0f);
    }

    private static void finishReload(Player player, ServerLevel level, RangedWeapon weapon, ItemStack stack,
                                     int rounds, int capacity) {
        Optional<Item> round = chooseAmmo(player, weapon, stack);
        if (round.isEmpty()) {
            return;
        }
        int available = countItem(player, round.get());
        int loaded = ReloadPlan.roundsToLoad(rounds, capacity, available);
        if (loaded == 0) {
            return;
        }
        takeItem(player, round.get(), loaded);
        weapon.load(stack, rounds + loaded, round.get());
        play(level, player, ModSounds.RELOAD_END.get(), 0.8f, 1.0f);
    }

    /**
     * effects: returns the kind of round a swap of a directly loaded gun
     * would load next: the accepted kind after the loaded one in inventory
     * order, wrapping round; the first carried if the loaded one is not
     * carried; empty if no kind other than the loaded one is carried
     */
    public static Optional<Item> nextKind(Player player, RangedWeapon weapon, ItemStack stack) {
        List<Item> carried = carriedKinds(player, weapon);
        Optional<Item> loaded = weapon.loadedAmmo(stack)
                .or(() -> weapon.profile().ammoItem().flatMap(BuiltInRegistries.ITEM::getOptional));
        return AmmoChoice.next(loaded, carried);
    }

    /** effects: returns the accepted kinds of round the player carries, in inventory order, each once */
    private static List<Item> carriedKinds(Player player, RangedWeapon weapon) {
        List<Item> carried = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (weapon.profile().acceptsAmmo(candidate) && !carried.contains(candidate.getItem())) {
                carried.add(candidate.getItem());
            }
        }
        return carried;
    }

    /**
     * effects: returns the round a reload of {@code stack} would load, by
     * {@link AmmoChoice}: what it holds while it holds any, else the first
     * accepted round in the inventory, hotbar first
     */
    public static Optional<Item> chooseAmmo(Player player, RangedWeapon weapon, ItemStack stack) {
        return AmmoChoice.choose(weapon.loadedAmmo(stack), weapon.rounds(stack), carriedKinds(player, weapon));
    }

    /**
     * effects: returns how many rounds a reload of {@code stack} could draw
     * on: the count of the round {@link #chooseAmmo} picks, zero if none
     */
    public static int countAmmo(Player player, RangedWeapon weapon, ItemStack stack) {
        return chooseAmmo(player, weapon, stack).map(round -> countItem(player, round)).orElse(0);
    }

    static int countItem(Player player, Item item) {
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * requires: the player carries at least {@code count} of {@code item}<br>
     * effects: removes that many, first slots first
     */
    static void takeItem(Player player, Item item, int count) {
        Inventory inventory = player.getInventory();
        int remaining = count;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
            }
        }
    }

    /** How far along the look the crosshair is taken to point when nothing is in the way. */
    private static final double AIM_RANGE = 96.0;

    /**
     * effects: returns the direction a round leaves {@code origin} in so
     * that it arrives where the crosshair points: at the first block the
     * eye's line of sight meets within {@link #AIM_RANGE}, or at that range
     * along the look if it meets none. A round starts below and beside the
     * eye, so a round fired parallel to the look would land that much off
     * the crosshair at every distance. If the point is at or behind the
     * muzzle -- the player's face is at a wall -- the look itself.
     */
    static Vec3 aim(ServerLevel level, Player player, Vec3 look, Vec3 origin) {
        Vec3 eye = player.getEyePosition();
        Vec3 far = eye.add(look.scale(AIM_RANGE));
        HitResult hit = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 target = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
        Vec3 toTarget = target.subtract(origin);
        return toTarget.dot(look) > 1e-3 ? toTarget : look;
    }

    /**
     * effects: returns where the muzzle is: forward of the eye, and, from
     * the hip, out to the main-hand side and a little down; aiming down the
     * sights, the gun is at the eye and the muzzle is on the line of sight,
     * or a scope's magnification would show the round leaving from the
     * corner of the view
     */
    public static Vec3 muzzle(Player player, Vec3 look, boolean aiming) {
        if (aiming) {
            return player.getEyePosition().add(look.scale(MUZZLE_FORWARD));
        }
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
        return new Stance(player.isSprinting(), moving, player.isCrouching(), !player.onGround(),
                player.getData(ModData.GUNNERY).aiming());
    }

    private static void play(ServerLevel level, Player player, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
