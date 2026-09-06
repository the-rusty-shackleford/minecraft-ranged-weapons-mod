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
package com.nfx.rangedweaponsmod.gametest;

import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.RangedWeapons;
import com.nfx.rangedweapons.api.WeaponClass;
import com.nfx.rangedweapons.fallback.Fallback;
import com.nfx.rangedweaponsmod.Handling;
import com.nfx.rangedweaponsmod.ModData;
import com.nfx.rangedweaponsmod.ModItems;
import com.nfx.rangedweaponsmod.PlayerGunnery;
import com.nfx.rangedweaponsmod.RangedWeaponsMod;
import com.nfx.rangedweaponsmod.Reload;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The gunnery on a real headless server, driven tick by tick on the
 * framework's mock player -- a plain {@code Player} in the level with no
 * connection, which is exactly why {@link PlayerGunnery#tick} takes a
 * {@code Player} -- plus one test through the game-bus hook.
 *
 * <p>Partitions. Fire: the held trigger fires on its first tick and then
 * every fire-rate ticks (counted through rounds spent); a release stops it;
 * a shot launches the protocol's bullet. Reload: an empty trigger starts a
 * reload only when the inventory has ammunition, and finishing it loads
 * exactly the lesser of the space and the ammunition, consuming that much;
 * no ammunition means no reload and rounds stay at zero; the reload key
 * tops up a part magazine without the trigger. Data: profile and handling
 * read back from the data maps.
 *
 * <p>The class has a public no-argument constructor and instance test
 * methods because the gametest registry instantiates the holder class
 * reflectively before invoking each test.
 */
@GameTestHolder(RangedWeaponsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GunneryGameTests {

    private static final int ARENA_SIZE = 9;
    private static final int FIRE_RATE = 3;
    private static final int CAPACITY = 50;
    private static final int RELOAD_TICKS = 50;

    public GunneryGameTests() {}

    /** A mock player in the arena, facing along +X, holding a gun with {@code rounds} in it and {@code ammo} rounds in the inventory. */
    private record Gunner(Player player, ItemStack gun, RangedWeapon weapon) {}

    private static Gunner gunner(GameTestHelper helper, int rounds, int ammo) {
        return gunner(helper, GameType.SURVIVAL, rounds, ammo);
    }

    private static Gunner gunner(GameTestHelper helper, GameType mode, int rounds, int ammo) {
        layFloor(helper);
        Player player = helper.makeMockPlayer(mode);
        // The mock answers isCreative() from its game type but its abilities
        // stay survival's; vanilla's own routine sets them, and infinite
        // materials is an ability.
        mode.updatePlayerAbilities(player.getAbilities());
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 4.5));
        // moveTo sets the body yaw; a living entity looks along its *head*
        // yaw, so that is set too, or the mock keeps facing south.
        player.moveTo(at.x, at.y, at.z, -90.0f, 0.0f);
        player.setYHeadRot(-90.0f);
        player.setYBodyRot(-90.0f);
        ItemStack gun = new ItemStack(ModItems.MACHINE_GUN.get());
        RangedWeapon weapon = RangedWeapons.resolve(gun);
        if (weapon == null) {
            helper.fail("the machine gun resolves to no weapon");
        }
        weapon.load(gun, rounds);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, gun);
        if (ammo > 0) {
            player.getInventory().add(new ItemStack(ModItems.ROUND.get(), ammo));
        }
        return new Gunner(player, gun, weapon);
    }

    /** Runs the gunnery on the mock player at every test tick in {@code [from, to]}. */
    private static void driveTicks(GameTestHelper helper, Gunner g, int from, int to) {
        ServerLevel level = helper.getLevel();
        for (int t = from; t <= to; t++) {
            helper.runAtTickTime(t, () -> PlayerGunnery.tick(g.player(), level));
        }
    }

    private static void layFloor(GameTestHelper helper) {
        for (int x = 0; x < ARENA_SIZE; x++) {
            for (int z = 0; z < ARENA_SIZE; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
            }
        }
    }

    // --- data --------------------------------------------------------------

    @GameTest(template = "arena")
    public void modAndProtocolAreLoaded(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded(RangedWeaponsMod.MOD_ID), "this mod is loaded");
        helper.assertTrue(ModList.get().isLoaded("rangedweapons"), "the nested protocol is loaded");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public void theMachineGunIsAProtocolWeaponFromItsProfile(GameTestHelper helper) {
        ItemStack gun = new ItemStack(ModItems.MACHINE_GUN.get());
        RangedWeapon weapon = RangedWeapons.resolve(gun);
        helper.assertTrue(weapon != null, "the machine gun resolves to a weapon");
        helper.assertValueEqual(weapon.capacity(gun), CAPACITY, "capacity from the profile");
        helper.assertValueEqual(weapon.stats(gun).fireRateTicks(), FIRE_RATE, "fire rate from the profile");
        helper.assertValueEqual(weapon.stats(gun).fullReloadTicks(), RELOAD_TICKS, "full reload from the profile");
        helper.assertTrue(weapon.profile().weaponClass() == WeaponClass.AUTOMATIC, "class from the profile");
        helper.assertTrue(weapon.profile().ammoItem().map(Object::toString).orElse("").equals("rangedweaponsmod:round"),
                "ammunition from the profile");
        helper.assertValueEqual(weapon.rounds(gun), 0, "a fresh gun is empty");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public void handlingComesFromTheDataMapWithClassDefaults(GameTestHelper helper) {
        Handling declared = Handling.of(new ItemStack(ModItems.MACHINE_GUN.get()));
        helper.assertValueEqual(declared.recoilPitch(), 0.55f, "recoil pitch from the data map");
        helper.assertValueEqual(declared.recovery(), 0.35f, "recovery from the data map");
        Handling fallback = Handling.of(new ItemStack(ModItems.ROUND.get()));
        helper.assertTrue(fallback.equals(Handling.defaultFor(WeaponClass.UNCLASSIFIED)),
                "an item with neither handling nor profile gets the unclassified default");
        helper.succeed();
    }

    // --- firing -------------------------------------------------------------

    @GameTest(template = "arena", timeoutTicks = 60)
    public void aHeldTriggerFiresAtTheFireRate(GameTestHelper helper) {
        Gunner g = gunner(helper, CAPACITY, 0);
        PlayerGunnery.onTrigger(g.player(), true);
        // Ticks 1..13 with a shot every third tick from the first: 1, 4, 7, 10, 13.
        driveTicks(helper, g, 1, 13);
        helper.runAtTickTime(14, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), CAPACITY - 5, "rounds after 13 ticks held");
            helper.assertValueEqual(g.gun().getDamageValue(), 5, "one durability per shot");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void creativeFiresAnEmptyGunWithoutAmmunitionOrReload(GameTestHelper helper) {
        Gunner g = gunner(helper, GameType.CREATIVE, 0, 0);
        PlayerGunnery.onTrigger(g.player(), true);
        driveTicks(helper, g, 1, 7);                       // shots at 1, 4, 7; no reload, ever
        helper.runAtTickTime(8, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 0, "rounds are never spent");
            helper.assertFalse(g.gun().has(ModData.RELOAD.get()), "no reload was started");
            // (Wear is vanilla's to skip, and it does so only for a connected player.)
            // The clock is the witness that it fired: the last shot, at tick 7,
            // set the next for tick 10, two ticks after this callback at 8.
            helper.assertValueEqual(g.player().getData(ModData.GUNNERY).nextShotAt(),
                    helper.getLevel().getGameTime() + 2, "next shot after firing at 1, 4, 7");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void releasingTheTriggerStopsFire(GameTestHelper helper) {
        Gunner g = gunner(helper, CAPACITY, 0);
        PlayerGunnery.onTrigger(g.player(), true);
        driveTicks(helper, g, 1, 4);                       // shots at 1 and 4
        helper.runAtTickTime(5, () -> PlayerGunnery.onTrigger(g.player(), false));
        driveTicks(helper, g, 5, 13);
        helper.runAtTickTime(14, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), CAPACITY - 2, "rounds after a release");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void aShotLaunchesTheProtocolsBullet(GameTestHelper helper) {
        Gunner g = gunner(helper, CAPACITY, 0);
        PlayerGunnery.onTrigger(g.player(), true);
        driveTicks(helper, g, 1, 1);
        helper.runAtTickTime(2, () -> {
            helper.assertEntityPresent(Fallback.BULLET.get());
            helper.succeed();
        });
    }

    // --- reloading ----------------------------------------------------------

    @GameTest(template = "arena", timeoutTicks = 100)
    public void anEmptyTriggerReloadsFromTheInventory(GameTestHelper helper) {
        Gunner g = gunner(helper, 0, 64);
        PlayerGunnery.onTrigger(g.player(), true);
        driveTicks(helper, g, 1, RELOAD_TICKS + 1);
        helper.runAtTickTime(2, () -> {
            Reload reload = g.gun().get(ModData.RELOAD.get());
            helper.assertTrue(reload != null, "a reload started on the empty trigger");
            helper.assertValueEqual(reload.durationTicks(), RELOAD_TICKS, "reload duration from the profile");
        });
        helper.runAtTickTime(RELOAD_TICKS, () ->
                helper.assertValueEqual(g.weapon().rounds(g.gun()), 0, "still empty before the reload is over"));
        helper.runAtTickTime(RELOAD_TICKS + 2, () -> {
            helper.assertTrue(g.gun().get(ModData.RELOAD.get()) == null, "the reload is over");
            helper.assertValueEqual(g.weapon().rounds(g.gun()), CAPACITY, "a full magazine");
            helper.assertValueEqual(PlayerGunnery.countAmmo(g.player(), g.weapon().profile()), 64 - CAPACITY,
                    "ammunition consumed");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void noAmmunitionMeansNoReloadAndAnEmptyGun(GameTestHelper helper) {
        Gunner g = gunner(helper, 0, 0);
        PlayerGunnery.onTrigger(g.player(), true);
        driveTicks(helper, g, 1, 10);
        helper.runAtTickTime(11, () -> {
            helper.assertTrue(g.gun().get(ModData.RELOAD.get()) == null, "no reload without ammunition");
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 0, "still empty");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void aReloadLoadsOnlyWhatTheInventoryHas(GameTestHelper helper) {
        Gunner g = gunner(helper, 10, 5);
        PlayerGunnery.onReloadKey(g.player());
        driveTicks(helper, g, 1, RELOAD_TICKS + 1);
        helper.runAtTickTime(RELOAD_TICKS + 2, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 15, "ten plus the five available");
            helper.assertValueEqual(PlayerGunnery.countAmmo(g.player(), g.weapon().profile()), 0, "all five consumed");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void theReloadKeyTopsUpAPartMagazineWithoutTheTrigger(GameTestHelper helper) {
        Gunner g = gunner(helper, 20, 64);
        PlayerGunnery.onReloadKey(g.player());
        driveTicks(helper, g, 1, RELOAD_TICKS + 1);
        helper.runAtTickTime(RELOAD_TICKS + 2, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), CAPACITY, "topped up");
            helper.assertValueEqual(PlayerGunnery.countAmmo(g.player(), g.weapon().profile()), 64 - 30,
                    "thirty consumed");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void aGunMidReloadNeitherFiresNorRestarts(GameTestHelper helper) {
        Gunner g = gunner(helper, 20, 64);
        PlayerGunnery.onReloadKey(g.player());
        PlayerGunnery.onTrigger(g.player(), true);
        driveTicks(helper, g, 1, RELOAD_TICKS + 1);
        helper.runAtTickTime(RELOAD_TICKS - 5, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 20, "no shot while reloading");
            helper.assertValueEqual(g.gun().get(ModData.RELOAD.get()).startedAt(), helper.getLevel().getGameTime() - (RELOAD_TICKS - 6),
                    "the reload was not restarted by the held trigger");
        });
        helper.runAtTickTime(RELOAD_TICKS + 2, () -> {
            helper.assertTrue(g.gun().get(ModData.RELOAD.get()) == null, "the reload finished");
            helper.assertTrue(g.weapon().rounds(g.gun()) <= CAPACITY && g.weapon().rounds(g.gun()) >= CAPACITY - 1,
                    "reloaded, then the held trigger fires again: " + g.weapon().rounds(g.gun()));
            helper.succeed();
        });
    }

    // --- the real event path -----------------------------------------------

    /**
     * The same behaviour through the game bus: PlayerTickEvent.Post is
     * posted for the mock player, and the mod's registered listener must be
     * the one that fires the gun. (A placed ServerPlayer is ticked by its
     * network connection, not by the level, so the framework's mock server
     * player never ticks; posting the event is the honest way to reach the
     * hook.)
     */
    @GameTest(template = "arena", timeoutTicks = 60)
    public void theGameBusHookFiresTheGun(GameTestHelper helper) {
        Gunner g = gunner(helper, CAPACITY, 0);
        PlayerGunnery.onTrigger(g.player(), true);
        for (int t = 1; t <= 13; t++) {
            helper.runAtTickTime(t, () -> NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(g.player())));
        }
        helper.runAtTickTime(14, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), CAPACITY - 5, "rounds after 13 ticks through the event");
            helper.succeed();
        });
    }
}
