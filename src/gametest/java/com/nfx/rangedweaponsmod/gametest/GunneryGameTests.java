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

import com.nfx.rangedweapons.api.AmmoFamilies;
import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.RangedWeapons;
import net.minecraft.core.registries.BuiltInRegistries;
import com.nfx.rangedweapons.api.WeaponProfile;
import com.nfx.rangedweapons.api.WeaponClass;
import com.nfx.rangedweapons.fallback.Fallback;
import com.nfx.rangedweaponsmod.Handling;
import com.nfx.rangedweaponsmod.ModData;
import com.nfx.rangedweaponsmod.ModItems;
import com.nfx.rangedweaponsmod.GunItem;
import com.nfx.rangedweaponsmod.MagazineFedWeapon;
import net.minecraft.world.item.Item;
import com.nfx.rangedweaponsmod.domain.Magazine;
import com.nfx.rangedweaponsmod.Magazines;
import com.nfx.rangedweaponsmod.MagazineMenu;
import com.nfx.rangedweaponsmod.MagazineItem;
import com.nfx.rangedweaponsmod.PlayerGunnery;
import com.nfx.rangedweaponsmod.RangedWeaponsMod;
import com.nfx.rangedweaponsmod.Reload;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The gunnery on a real headless server, driven tick by tick on the
 * framework's mock player -- a plain {@code Player} in the level with no
 * connection, which is exactly why {@link PlayerGunnery#tick} takes a
 * {@code Player} -- plus one test through the game-bus hook.
 *
 * <p>Partitions. Fire: the held trigger fires on its first tick and then
 * every fire-rate ticks (counted through rounds spent); a release stops it;
 * a shot launches the protocol's bullet. Magazines (the machine gun): loose
 * rounds are adopted into a box; an empty trigger takes the first loaded
 * magazine carried and only a loaded one; R changes a part magazine for
 * the first loaded one and keeps it; Shift+R walks the carried magazines
 * in inventory order and wraps; a mixed magazine fires in order with the
 * next round's stats; the screen fills in inventory order from the family
 * only. The tube (the shotgun): an empty trigger reloads from the
 * inventory, loading the lesser of the space and the rounds carried; the
 * key tops up without the trigger; a gun mid-reload neither fires nor
 * restarts; Shift+R unloads and loads the next kind, or nothing with
 * nothing to change to. Data: profile and handling read back from the data
 * maps.
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
    private static final int CAPACITY = 75;
    private static final int RELOAD_TICKS = 75;

    public GunneryGameTests() {}

    /** A mock player in the arena, facing along +X, holding a gun with {@code rounds} in it and {@code ammo} rounds in the inventory. */
    private record Gunner(Player player, ItemStack gun, RangedWeapon weapon) {}

    private static Gunner gunner(GameTestHelper helper, int rounds, int ammo) {
        return gunner(helper, GameType.SURVIVAL, rounds, ammo);
    }

    private static Gunner gunner(GameTestHelper helper, GameType mode, int rounds, int ammo) {
        return gunner(helper, mode, ModItems.MACHINE_GUN.get(), rounds, ammo);
    }

    private static Gunner gunner(GameTestHelper helper, GameType mode, net.minecraft.world.item.Item gunItem, int rounds, int ammo) {
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
        ItemStack gun = new ItemStack(gunItem);
        RangedWeapon weapon = RangedWeapons.resolve(gun);
        if (weapon == null) {
            helper.fail(gunItem + " resolves to no weapon");
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

    // --- the lineup ----------------------------------------------------------

    @GameTest(template = "arena")
    public void everyGunResolvesWithItsOwnNumbers(GameTestHelper helper) {
        record Expected(net.minecraft.world.item.Item item, WeaponClass cls, int capacity, int rate, int pellets, boolean falloff, String family, float damage, float knockback) {}
        for (Expected e : List.of(
                new Expected(ModItems.PISTOL.get(), WeaponClass.SIDEARM, 15, 5, 1, true, "small", 6.0f, 0.6f),
                new Expected(ModItems.SHOTGUN.get(), WeaponClass.SHOTGUN, 6, 13, 6, true, "shell", 4.0f, 3.0f),
                new Expected(ModItems.RIFLE.get(), WeaponClass.RIFLE, 30, 6, 1, false, "medium", 12.0f, 1.0f),
                new Expected(ModItems.SCOPED_RIFLE.get(), WeaponClass.RIFLE, 30, 10, 1, false, "medium", 16.0f, 1.5f),
                new Expected(ModItems.MACHINE_GUN.get(), WeaponClass.AUTOMATIC, 75, 3, 1, false, "medium", 6.0f, 0.4f))) {
            ItemStack stack = new ItemStack(e.item());
            RangedWeapon weapon = RangedWeapons.resolve(stack);
            helper.assertTrue(weapon != null, e.item() + " resolves");
            helper.assertValueEqual(weapon instanceof MagazineFedWeapon, GunItem.isMagazineFed(stack),
                    e.item() + " is on the native tier exactly when it is magazine-fed");
            helper.assertValueEqual(weapon.profile().weaponClass(), e.cls(), e.item() + " class");
            helper.assertValueEqual(weapon.capacity(stack), e.capacity(), e.item() + " capacity (with no magazine, the profile's)");
            helper.assertValueEqual(weapon.stats(stack).capacity(), weapon.capacity(stack), e.item() + " stats agree on the capacity");
            helper.assertValueEqual(weapon.stats(stack).fireRateTicks(), e.rate(), e.item() + " fire rate");
            helper.assertValueEqual(weapon.stats(stack).projectilesPerShot(), e.pellets(), e.item() + " projectiles");
            helper.assertValueEqual(weapon.stats(stack).damage(), e.damage(), e.item() + " damage");
            helper.assertValueEqual(weapon.stats(stack).knockback(), e.knockback(), e.item() + " knockback");
            helper.assertValueEqual(weapon.profile().falloff().isPresent(), e.falloff(), e.item() + " falloff");
            helper.assertValueEqual(weapon.profile().ammoFamily().orElseThrow(), AmmoFamilies.family(e.family()), e.item() + " family");
            helper.assertTrue(weapon.profile().acceptsAmmo(new ItemStack(BuiltInRegistries.ITEM.get(weapon.profile().ammoItem().orElseThrow()))),
                    e.item() + " takes its own round");
        }
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void aSemiAutomaticFiresOncePerPullHoweverLongItIsHeld(GameTestHelper helper) {
        Gunner g = gunner(helper, GameType.SURVIVAL, ModItems.PISTOL.get(), 12, 0);
        PlayerGunnery.onTrigger(g.player(), true);
        driveTicks(helper, g, 1, 20);                       // held twenty ticks: rate would allow four
        helper.runAtTickTime(21, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 11, "one round per pull");
            PlayerGunnery.onTrigger(g.player(), false);
            PlayerGunnery.onTrigger(g.player(), true);
        });
        driveTicks(helper, g, 22, 22);
        helper.runAtTickTime(23, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 10, "a second pull, a second round");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void theShotgunThrowsItsPellets(GameTestHelper helper) {
        Gunner g = gunner(helper, GameType.SURVIVAL, ModItems.SHOTGUN.get(), 6, 0);
        PlayerGunnery.onTrigger(g.player(), true);
        driveTicks(helper, g, 1, 1);
        helper.runAtTickTime(2, () -> {
            int pellets = helper.getLevel().getEntities(Fallback.BULLET.get(), helper.getBounds(), e -> true).size();
            helper.assertValueEqual(pellets, 6, "pellets in the air one tick after the shot");
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 5, "one shell for all six");
            helper.succeed();
        });
    }

    // --- slugs -------------------------------------------------------------

    @GameTest(template = "arena", timeoutTicks = 120)
    public void anEmptyShotgunLoadsTheFirstShellOrSlugCarriedAndFiresAccordingly(GameTestHelper helper) {
        Gunner g = gunner(helper, GameType.SURVIVAL, ModItems.SHOTGUN.get(), 0, 0);
        // Slugs before shells in the hotbar: slugs it is.
        g.player().getInventory().setItem(3, new ItemStack(ModItems.SLUG.get(), 4));
        g.player().getInventory().setItem(4, new ItemStack(ModItems.SHELL.get(), 4));
        helper.assertValueEqual(PlayerGunnery.chooseAmmo(g.player(), g.weapon(), g.gun()).orElseThrow(), ModItems.SLUG.get(), "the first round carried");
        helper.assertValueEqual(PlayerGunnery.countAmmo(g.player(), g.weapon(), g.gun()), 4, "only the slugs count toward this reload");
        int reloadTicks = g.weapon().stats(g.gun()).fullReloadTicks();
        PlayerGunnery.onTrigger(g.player(), true);          // empty gun, trigger pulled: a reload starts
        driveTicks(helper, g, 1, reloadTicks + 1);
        helper.runAtTickTime(reloadTicks + 2, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 4, "the four slugs loaded");
            helper.assertValueEqual(g.weapon().loadedAmmo(g.gun()).orElseThrow(), ModItems.SLUG.get(), "the gun knows it holds slugs");
            helper.assertValueEqual(g.player().getInventory().countItem(ModItems.SLUG.get()), 0, "all four slugs spent");
            helper.assertValueEqual(g.player().getInventory().countItem(ModItems.SHELL.get()), 4, "the shells untouched");
            helper.assertValueEqual(g.weapon().stats(g.gun()).projectilesPerShot(), 1, "a slug is one projectile");
            helper.assertValueEqual(g.weapon().stats(g.gun()).damage(), 18.0f, "the slug's damage");
            helper.assertValueEqual(g.weapon().stats(g.gun()).capacity(), 6, "the shotgun's own capacity");
            // Holding slugs, a top-up takes shells for nothing: the shells stay.
            helper.assertValueEqual(PlayerGunnery.chooseAmmo(g.player(), g.weapon(), g.gun()).orElseThrow(), ModItems.SLUG.get(), "still slugs while any remain");
            helper.assertValueEqual(PlayerGunnery.countAmmo(g.player(), g.weapon(), g.gun()), 0, "no slugs left to top up with");
        });
        helper.runAtTickTime(reloadTicks + 3, () -> {
            PlayerGunnery.onTrigger(g.player(), false);
            PlayerGunnery.onTrigger(g.player(), true);
        });
        driveTicks(helper, g, reloadTicks + 4, reloadTicks + 4);
        helper.runAtTickTime(reloadTicks + 5, () -> {
            int inFlight = helper.getLevel().getEntities(Fallback.BULLET.get(), helper.getBounds(), e -> true).size();
            helper.assertValueEqual(inFlight, 1, "one slug in the air, not six pellets");
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 3, "one slug spent");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 40)
    public void anAimedRoundLeavesFromTheLineOfSight(GameTestHelper helper) {
        // Facing +X: from the hip the muzzle is out to +Z (the right hand) and
        // down; aimed, it is on the eye's line. The round is read where it
        // spawns, in the tick it is fired, since it then flies toward the
        // crosshair and the offset closes.
        Gunner g = gunner(helper, CAPACITY, 0);
        double eyeZ = g.player().getEyePosition().z;
        PlayerGunnery.onTrigger(g.player(), true);
        helper.runAtTickTime(1, () -> {
            PlayerGunnery.tick(g.player(), helper.getLevel());
            var bullets = helper.getLevel().getEntities(Fallback.BULLET.get(), helper.getBounds(), e -> true);
            helper.assertValueEqual(bullets.size(), 1, "one round from the hip");
            double off = Math.abs(bullets.get(0).getZ() - eyeZ);
            helper.assertTrue(off > 0.25, "from the hip the round starts beside the eye line, was " + off + " off");
            bullets.forEach(net.minecraft.world.entity.Entity::discard);
            PlayerGunnery.onTrigger(g.player(), false);
            PlayerGunnery.onAim(g.player(), true);
            PlayerGunnery.onTrigger(g.player(), true);
        });
        helper.runAtTickTime(4, () -> {                     // the clock allows the next round at 4
            PlayerGunnery.tick(g.player(), helper.getLevel());
            var bullets = helper.getLevel().getEntities(Fallback.BULLET.get(), helper.getBounds(), e -> true);
            helper.assertValueEqual(bullets.size(), 1, "one round aimed");
            double off = Math.abs(bullets.get(0).getZ() - eyeZ);
            helper.assertTrue(off < 0.01, "aimed, the round starts on the eye line, was " + off + " off");
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public void aimingIsRememberedForSpread(GameTestHelper helper) {
        Gunner g = gunner(helper, 0, 0);
        helper.assertFalse(g.player().getData(ModData.GUNNERY).aiming(), "sights down to begin with");
        PlayerGunnery.onAim(g.player(), true);
        helper.assertTrue(g.player().getData(ModData.GUNNERY).aiming(), "sights up");
        PlayerGunnery.onTrigger(g.player(), true);
        helper.assertTrue(g.player().getData(ModData.GUNNERY).aiming(), "a trigger pull does not drop them");
        PlayerGunnery.onAim(g.player(), false);
        helper.assertFalse(g.player().getData(ModData.GUNNERY).aiming(), "sights down");
        helper.succeed();
    }

    // --- aim ---------------------------------------------------------------

    @GameTest(template = "arena", timeoutTicks = 60)
    public void aRoundGoesWhereTheCrosshairPointsNotParallelToIt(GameTestHelper helper) {
        // Looking ten degrees down at a glass block three blocks ahead, the
        // crosshair rests just inside the block's bottom edge. A round from
        // the muzzle -- a fifth of a block lower -- fired parallel to the look
        // would pass under it; aimed at the crosshair's point, it shatters it.
        Gunner g = gunner(helper, CAPACITY, 0);
        g.player().setXRot(10.0f);
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.GLASS);
        PlayerGunnery.onTrigger(g.player(), true);
        driveTicks(helper, g, 1, 1);
        helper.runAtTickTime(2, () -> PlayerGunnery.onTrigger(g.player(), false));
        helper.runAtTickTime(6, () -> {
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(4, 2, 4));
            helper.succeed();
        });
    }

    // --- ammunition families -------------------------------------------------

    @GameTest(template = "arena", timeoutTicks = 100)
    public void aMagazineOfAnotherModsMediumRoundGoesInAndASmallMagazineDoesNot(GameTestHelper helper) {
        Gunner g = gunner(helper, 0, 0);
        // Another mod's medium round (the gametest pack tags iron nuggets
        // medium) in a rifle magazine -- the family's, so the machine gun
        // takes it -- and a pistol magazine, another family, that it must not.
        g.player().getInventory().setItem(3, box(ModItems.PISTOL_MAGAZINE.get(), ModItems.SMALL_ROUND.get(), 10));
        g.player().getInventory().setItem(4, box(ModItems.RIFLE_MAGAZINE.get(), Items.IRON_NUGGET, 10));
        helper.assertValueEqual(Magazines.loadedMagazineSlots(g.player(), g.weapon()), List.of(4), "only the medium magazine counts");
        PlayerGunnery.onTrigger(g.player(), true);          // empty gun, trigger pulled: a magazine change starts
        driveTicks(helper, g, 1, RELOAD_TICKS + 1);
        helper.runAtTickTime(RELOAD_TICKS + 2, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 10, "loaded from the foreign medium rounds");
            helper.assertValueEqual(g.weapon().loadedAmmo(g.gun()).orElseThrow(), Items.IRON_NUGGET, "the store names the round");
            helper.assertValueEqual(g.weapon().stats(g.gun()).damage(), 9.0f, "and fires with the round's own damage");
            helper.assertTrue(g.player().getInventory().getItem(4).isEmpty(), "the magazine left its slot for the gun");
            helper.assertTrue(g.player().getInventory().getItem(3).is(ModItems.PISTOL_MAGAZINE.get()), "the small magazine untouched");
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public void theRoundIsMediumAndTheGunTakesTheFamily(GameTestHelper helper) {
        RangedWeapon weapon = RangedWeapons.resolve(new ItemStack(ModItems.MACHINE_GUN.get()));
        if (weapon == null) {
            helper.fail("the machine gun resolves to no weapon");
            return;
        }
        WeaponProfile profile = weapon.profile();
        helper.assertTrue(profile.ammoFamily().isPresent() && profile.ammoFamily().get().equals(AmmoFamilies.MEDIUM), "the machine gun takes the medium family");
        helper.assertTrue(new ItemStack(ModItems.ROUND.get()).is(AmmoFamilies.MEDIUM), "the round is a medium round");
        helper.assertTrue(new ItemStack(ModItems.ROUND.get()).is(AmmoFamilies.ALL), "and ammunition");
        helper.assertTrue(profile.acceptsAmmo(new ItemStack(ModItems.ROUND.get())), "so the gun takes its own round");
        helper.succeed();
    }

    // --- the use key is not the trigger ------------------------------------

    @GameTest(template = "arena", timeoutTicks = 60)
    public void theGunsUsePassesTheClickOnAndPullsNoTrigger(GameTestHelper helper) {
        Gunner g = gunner(helper, CAPACITY, 0);
        var result = g.gun().getItem().use(helper.getLevel(), g.player(), InteractionHand.MAIN_HAND);
        helper.assertFalse(result.getResult().consumesAction(), "the use key passes on, so a door or chest under the crosshair gets it");
        helper.assertFalse(result.getResult().shouldSwing(), "no arm swing");
        helper.assertFalse(g.player().getData(ModData.GUNNERY).held(), "the trigger is not held after use()");
        driveTicks(helper, g, 1, 7);
        helper.runAtTickTime(8, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), CAPACITY, "no round spent by a use() held 7 ticks");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void aRepeatedPressWhileHeldDoesNotReArmTheEmptyClick(GameTestHelper helper) {
        Gunner g = gunner(helper, 0, 0);                   // empty, no ammunition: only the click
        PlayerGunnery.onTrigger(g.player(), true);
        driveTicks(helper, g, 1, 2);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(g.player().getData(ModData.GUNNERY).clickedThisPress(), "clicked once on the pull");
            PlayerGunnery.onTrigger(g.player(), true);     // vanilla's four-tick repeat of a held use
            helper.assertTrue(g.player().getData(ModData.GUNNERY).clickedThisPress(), "a repeat is not a new pull");
            PlayerGunnery.onTrigger(g.player(), false);
            PlayerGunnery.onTrigger(g.player(), true);
            helper.assertFalse(g.player().getData(ModData.GUNNERY).clickedThisPress(), "a release and a pull re-arm it");
            helper.succeed();
        });
    }

    @GameTest(template = "arena")
    public void theOffHandIsNotOperated(GameTestHelper helper) {
        Gunner g = gunner(helper, CAPACITY, 0);
        g.player().setItemInHand(InteractionHand.OFF_HAND, g.gun().copy());
        var result = g.gun().getItem().use(helper.getLevel(), g.player(), InteractionHand.OFF_HAND);
        helper.assertFalse(result.getResult().consumesAction(), "an off-hand gun passes the click on");
        helper.assertFalse(g.player().getData(ModData.GUNNERY).held(), "and pulls no trigger");
        PlayerGunnery.onTrigger(g.player(), true);
        driveTicks(helper, g, 1, 2);
        helper.runAtTickTime(3, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), CAPACITY - 1, "the main-hand gun fires on the trigger");
            helper.assertValueEqual(g.weapon().rounds(g.player().getOffhandItem()), CAPACITY, "the off-hand gun does not");
            helper.succeed();
        });
    }

    // --- reloading: magazines ------------------------------------------------

    /** A magazine of {@code item} holding {@code count} of {@code round}. */
    private static ItemStack box(MagazineItem item, Item round, int count) {
        ItemStack magazine = new ItemStack(item);
        Magazines.setContents(magazine, Magazine.<Item>empty(item.capacity()).push(round, count));
        return magazine;
    }

    @GameTest(template = "arena")
    public void magazinesStackOneTakeADyeAndKnowTheirFamily(GameTestHelper helper) {
        for (MagazineItem item : ModItems.magazines()) {
            ItemStack stack = new ItemStack(item);
            helper.assertValueEqual(stack.getMaxStackSize(), 1, item + " stacks to one: two loads cannot share a stack");
            helper.assertTrue(stack.is(ItemTags.DYEABLE), item + " takes a dye in the crafting grid");
            helper.assertTrue(item.accepts(new ItemStack(BuiltInRegistries.ITEM.get(
                    item.family().equals(AmmoFamilies.SMALL) ? ModItems.SMALL_ROUND.getId() : ModItems.ROUND.getId()))),
                    item + " takes its family's round");
            helper.assertFalse(item.accepts(new ItemStack(ModItems.SHELL.get())), item + " refuses a shell");
        }
        helper.assertValueEqual(ModItems.PISTOL_MAGAZINE.get().capacity(), 15, "the pistol magazine");
        helper.assertValueEqual(ModItems.RIFLE_MAGAZINE.get().capacity(), 30, "the rifle magazine");
        helper.assertValueEqual(ModItems.MACHINE_GUN_BOX.get().capacity(), 75, "the box");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void looseRoundsInAMagazineFedGunBecomeAMagazineOnFirstSight(GameTestHelper helper) {
        // The gunner loads the store directly, the way a gun saved before
        // magazines existed (or filled by creative) holds its rounds.
        Gunner g = gunner(helper, 20, 0);
        helper.assertTrue(Magazines.inserted(g.gun()).isEmpty(), "no magazine before the first tick");
        driveTicks(helper, g, 1, 1);
        helper.runAtTickTime(2, () -> {
            ItemStack magazine = Magazines.inserted(g.gun()).orElseThrow();
            helper.assertTrue(magazine.is(ModItems.MACHINE_GUN_BOX.get()), "adopted into the machine gun's box");
            helper.assertValueEqual(Magazines.contents(magazine).rounds(), 20, "with the rounds it held");
            helper.assertValueEqual(Magazines.contents(magazine).next().orElseThrow(), ModItems.ROUND.get(), "of its native round");
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 20, "the store agrees");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void anEmptyTriggerLoadsTheFirstLoadedMagazineCarried(GameTestHelper helper) {
        Gunner g = gunner(helper, 0, 0);
        g.player().getInventory().setItem(2, new ItemStack(ModItems.MACHINE_GUN_BOX.get()));   // empty: passed over
        g.player().getInventory().setItem(5, box(ModItems.MACHINE_GUN_BOX.get(), ModItems.ROUND.get(), 40));
        g.player().getInventory().setItem(7, box(ModItems.MACHINE_GUN_BOX.get(), ModItems.ROUND.get(), 75));
        PlayerGunnery.onTrigger(g.player(), true);
        driveTicks(helper, g, 1, RELOAD_TICKS + 1);
        helper.runAtTickTime(2, () -> {
            Reload reload = g.gun().get(ModData.RELOAD.get());
            helper.assertTrue(reload != null && !reload.swap(), "a magazine change started on the empty trigger");
            helper.assertValueEqual(reload.durationTicks(), RELOAD_TICKS, "it takes the gun's full reload time");
        });
        helper.runAtTickTime(RELOAD_TICKS, () ->
                helper.assertValueEqual(g.weapon().rounds(g.gun()), 0, "still empty before the change is over"));
        helper.runAtTickTime(RELOAD_TICKS + 2, () -> {
            helper.assertTrue(g.gun().get(ModData.RELOAD.get()) == null, "the change is over");
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 40, "the first loaded magazine, not the fullest");
            helper.assertTrue(g.player().getInventory().getItem(5).isEmpty(), "it left slot 5 (the gun had nothing to put there)");
            helper.assertValueEqual(Magazines.contents(g.player().getInventory().getItem(7)).rounds(), 75, "the other stays");
            helper.assertValueEqual(g.player().getData(ModData.GUNNERY).lastSwapSlot(), 5, "the finger remembers where it took it from");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void noLoadedMagazineMeansNoReloadAndAnEmptyGun(GameTestHelper helper) {
        Gunner g = gunner(helper, 0, 0);
        g.player().getInventory().setItem(2, new ItemStack(ModItems.MACHINE_GUN_BOX.get()));   // empty
        g.player().getInventory().add(new ItemStack(ModItems.ROUND.get(), 64));                // loose rounds load nothing
        PlayerGunnery.onTrigger(g.player(), true);
        driveTicks(helper, g, 1, 10);
        helper.runAtTickTime(11, () -> {
            helper.assertTrue(g.gun().get(ModData.RELOAD.get()) == null, "no change without a loaded magazine");
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 0, "still empty");
            helper.assertTrue(g.player().getData(ModData.GUNNERY).clickedThisPress(), "the dry click sounded instead");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void theReloadKeyChangesAPartMagazineForTheFirstLoadedOneAndKeepsIt(GameTestHelper helper) {
        Gunner g = gunner(helper, 20, 0);                    // adopted into a box of 20 on the first tick
        g.player().getInventory().setItem(6, box(ModItems.MACHINE_GUN_BOX.get(), ModItems.ROUND.get(), 75));
        driveTicks(helper, g, 1, 1);
        helper.runAtTickTime(2, () -> PlayerGunnery.onReloadKey(g.player(), false));
        driveTicks(helper, g, 3, RELOAD_TICKS + 3);
        helper.runAtTickTime(RELOAD_TICKS + 4, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 75, "the full magazine went in");
            ItemStack kept = g.player().getInventory().getItem(6);
            helper.assertTrue(kept.is(ModItems.MACHINE_GUN_BOX.get()), "the part magazine took its slot");
            helper.assertValueEqual(Magazines.contents(kept).rounds(), 20, "with what it still held");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 300)
    public void shiftRWalksTheCarriedMagazinesInInventoryOrderAndWrapsRound(GameTestHelper helper) {
        Gunner g = gunner(helper, 10, 0);                    // A: adopted, 10 rounds
        g.player().getInventory().setItem(1, box(ModItems.MACHINE_GUN_BOX.get(), ModItems.ROUND.get(), 20));   // B
        g.player().getInventory().setItem(3, box(ModItems.MACHINE_GUN_BOX.get(), ModItems.ROUND.get(), 30));   // C
        driveTicks(helper, g, 1, 1);
        int change = RELOAD_TICKS + 2;
        // Three swaps in a row: B in (A to slot 1), C in (B to slot 3), then round to slot 1 again: A in (C to slot 1).
        helper.runAtTickTime(2, () -> PlayerGunnery.onReloadKey(g.player(), true));
        driveTicks(helper, g, 3, 2 + change);
        helper.runAtTickTime(3 + change, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 20, "B went in first");
            helper.assertValueEqual(Magazines.contents(g.player().getInventory().getItem(1)).rounds(), 10, "A took B's slot");
            PlayerGunnery.onReloadKey(g.player(), true);
        });
        driveTicks(helper, g, 4 + change, 3 + 2 * change);
        helper.runAtTickTime(4 + 2 * change, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 30, "then C, the next after slot 1");
            helper.assertValueEqual(Magazines.contents(g.player().getInventory().getItem(3)).rounds(), 20, "B took C's slot");
            PlayerGunnery.onReloadKey(g.player(), true);
        });
        driveTicks(helper, g, 5 + 2 * change, 4 + 3 * change);
        helper.runAtTickTime(5 + 3 * change, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 10, "then round to A in slot 1");
            helper.assertValueEqual(Magazines.contents(g.player().getInventory().getItem(1)).rounds(), 30, "C took A's slot");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void aMixedMagazineFiresItsRoundsInOrderAndTheStatsFollowTheNextRound(GameTestHelper helper) {
        Gunner g = gunner(helper, 0, 0);
        ItemStack magazine = new ItemStack(ModItems.MACHINE_GUN_BOX.get());
        Magazines.setContents(magazine, Magazine.<Item>empty(75).push(ModItems.ROUND.get(), 2).push(Items.IRON_NUGGET, 1));
        Magazines.insert(g.gun(), g.weapon(), magazine);
        helper.assertValueEqual(g.weapon().rounds(g.gun()), 3, "three rounds in the store");
        helper.assertValueEqual(g.weapon().loadedAmmo(g.gun()).orElseThrow(), ModItems.ROUND.get(), "the round fires first");
        float base = g.weapon().stats(g.gun()).damage();
        PlayerGunnery.onTrigger(g.player(), true);
        driveTicks(helper, g, 1, 4);                       // shots at 1 and 4
        helper.runAtTickTime(5, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 1, "two spent");
            helper.assertValueEqual(g.weapon().loadedAmmo(g.gun()).orElseThrow(), Items.IRON_NUGGET, "the nugget is next");
            helper.assertValueEqual(g.weapon().stats(g.gun()).damage(), 9.0f, "and the stats are its now");
            helper.assertTrue(base != 9.0f, "which differ from the round's (" + base + ")");
            helper.assertValueEqual(Magazines.contents(Magazines.inserted(g.gun()).orElseThrow()).rounds(), 1, "the magazine agrees");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void theMagazineScreenFillsFromTheInventoryInOrderAndTakesOnlyItsFamily(GameTestHelper helper) {
        Gunner g = gunner(helper, 0, 0);
        Player player = g.player();
        ItemStack magazine = new ItemStack(ModItems.RIFLE_MAGAZINE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, magazine);
        player.getInventory().setItem(3, new ItemStack(Items.IRON_NUGGET, 4));     // medium, first in order
        player.getInventory().setItem(4, new ItemStack(ModItems.SMALL_ROUND.get(), 9)); // not the family
        player.getInventory().setItem(9, new ItemStack(ModItems.ROUND.get(), 64));   // medium, plenty
        MagazineMenu menu = new MagazineMenu(1, player.getInventory(), InteractionHand.MAIN_HAND);
        helper.assertTrue(menu.stillValid(player), "the menu is for the magazine in hand");
        helper.assertTrue(menu.clickMenuButton(player, MagazineMenu.FILL_BUTTON), "the fill button loads something");
        Magazine<Item> filled = Magazines.contents(player.getMainHandItem());
        helper.assertValueEqual(filled.rounds(), 30, "filled to the rifle magazine's capacity");
        helper.assertValueEqual(filled.segments().get(0).round(), Items.IRON_NUGGET, "the first kind carried fires first");
        helper.assertValueEqual(filled.segments().get(0).count(), 4, "all four of it");
        helper.assertValueEqual(filled.segments().get(1).count(), 26, "then the rounds up to the capacity");
        helper.assertValueEqual(player.getInventory().countItem(ModItems.ROUND.get()), 38, "twenty-six rounds taken");
        helper.assertValueEqual(player.getInventory().countItem(ModItems.SMALL_ROUND.get()), 9, "the small rounds untouched");
        helper.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(ModItems.SMALL_ROUND.get())), "a run slot refuses another family");
        helper.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(ModItems.ROUND.get())), "and takes its own");
        helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 26, "the slots show the runs");
        // Shift-clicking a run out puts its rounds back in the inventory and the magazine forgets them.
        menu.quickMoveStack(player, 1);
        helper.assertValueEqual(Magazines.contents(player.getMainHandItem()).rounds(), 4, "the second run came out");
        helper.assertValueEqual(player.getInventory().countItem(ModItems.ROUND.get()), 64, "and is back in the inventory");
        helper.succeed();
    }

    // --- reloading: a gun loaded directly (the shotgun) -----------------------

    /** The shotgun: a tube loaded round by round from the inventory, {@code rounds} in it and {@code shells} carried. */
    private static Gunner shotgunner(GameTestHelper helper, int rounds, int shells) {
        Gunner g = gunner(helper, GameType.SURVIVAL, ModItems.SHOTGUN.get(), rounds, 0);
        if (shells > 0) {
            g.player().getInventory().add(new ItemStack(ModItems.SHELL.get(), shells));
        }
        return g;
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void anEmptyShotgunReloadsFromTheInventory(GameTestHelper helper) {
        Gunner g = shotgunner(helper, 0, 64);
        int reloadTicks = g.weapon().stats(g.gun()).fullReloadTicks();
        PlayerGunnery.onTrigger(g.player(), true);
        driveTicks(helper, g, 1, reloadTicks + 1);
        helper.runAtTickTime(2, () -> {
            Reload reload = g.gun().get(ModData.RELOAD.get());
            helper.assertTrue(reload != null, "a reload started on the empty trigger");
            helper.assertValueEqual(reload.durationTicks(), reloadTicks, "reload duration from the profile");
        });
        helper.runAtTickTime(reloadTicks, () ->
                helper.assertValueEqual(g.weapon().rounds(g.gun()), 0, "still empty before the reload is over"));
        helper.runAtTickTime(reloadTicks + 2, () -> {
            helper.assertTrue(g.gun().get(ModData.RELOAD.get()) == null, "the reload is over");
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 6, "a full tube");
            helper.assertValueEqual(PlayerGunnery.countAmmo(g.player(), g.weapon(), g.gun()), 64 - 6, "shells consumed");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void aShotgunReloadLoadsOnlyWhatTheInventoryHasAndTheKeyTopsUpWithoutTheTrigger(GameTestHelper helper) {
        Gunner g = shotgunner(helper, 2, 3);
        int reloadTicks = g.weapon().stats(g.gun()).fullReloadTicks();
        PlayerGunnery.onReloadKey(g.player(), false);
        driveTicks(helper, g, 1, reloadTicks + 1);
        helper.runAtTickTime(reloadTicks + 2, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 5, "two plus the three available");
            helper.assertValueEqual(PlayerGunnery.countAmmo(g.player(), g.weapon(), g.gun()), 0, "all three consumed");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void aGunMidReloadNeitherFiresNorRestarts(GameTestHelper helper) {
        Gunner g = shotgunner(helper, 2, 64);
        int reloadTicks = g.weapon().stats(g.gun()).fullReloadTicks();
        PlayerGunnery.onReloadKey(g.player(), false);
        PlayerGunnery.onTrigger(g.player(), true);
        driveTicks(helper, g, 1, reloadTicks + 1);
        helper.runAtTickTime(reloadTicks - 5, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 2, "no shot while reloading");
            helper.assertValueEqual(g.gun().get(ModData.RELOAD.get()).startedAt(), helper.getLevel().getGameTime() - (reloadTicks - 6),
                    "the reload was not restarted by the held trigger");
        });
        helper.runAtTickTime(reloadTicks + 2, () -> {
            helper.assertTrue(g.gun().get(ModData.RELOAD.get()) == null, "the reload finished");
            helper.assertTrue(g.weapon().rounds(g.gun()) <= 6 && g.weapon().rounds(g.gun()) >= 5,
                    "reloaded, then the held trigger fires again: " + g.weapon().rounds(g.gun()));
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public void shiftROnTheShotgunUnloadsTheTubeAndLoadsTheNextKindCarried(GameTestHelper helper) {
        Gunner g = shotgunner(helper, 0, 0);
        g.weapon().load(g.gun(), 6, ModItems.SHELL.get());
        g.player().getInventory().setItem(3, new ItemStack(ModItems.SHELL.get(), 10));
        g.player().getInventory().setItem(4, new ItemStack(ModItems.SLUG.get(), 4));
        helper.assertValueEqual(PlayerGunnery.nextKind(g.player(), g.weapon(), g.gun()).orElseThrow(), ModItems.SLUG.get(),
                "the kind after shells in inventory order");
        int reloadTicks = g.weapon().stats(g.gun()).fullReloadTicks();
        PlayerGunnery.onReloadKey(g.player(), true);
        driveTicks(helper, g, 1, reloadTicks + 1);
        helper.runAtTickTime(2, () -> {
            Reload reload = g.gun().get(ModData.RELOAD.get());
            helper.assertTrue(reload != null && reload.swap(), "a swap started, full tube or not");
        });
        helper.runAtTickTime(reloadTicks + 2, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 4, "the four slugs loaded");
            helper.assertValueEqual(g.weapon().loadedAmmo(g.gun()).orElseThrow(), ModItems.SLUG.get(), "slugs it is");
            helper.assertValueEqual(g.player().getInventory().countItem(ModItems.SHELL.get()), 16, "the six shells went back");
            helper.assertValueEqual(g.player().getInventory().countItem(ModItems.SLUG.get()), 0, "the slugs went in");
            // With only slugs and shells carried and slugs loaded, the next swap goes back to shells.
            helper.assertValueEqual(PlayerGunnery.nextKind(g.player(), g.weapon(), g.gun()).orElseThrow(), ModItems.SHELL.get(), "round to shells");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public void shiftRWithNothingToSwapToDoesNothing(GameTestHelper helper) {
        Gunner g = shotgunner(helper, 3, 10);              // shells loaded, only shells carried
        g.weapon().load(g.gun(), 3, ModItems.SHELL.get());
        PlayerGunnery.onReloadKey(g.player(), true);
        driveTicks(helper, g, 1, 5);
        helper.runAtTickTime(6, () -> {
            helper.assertTrue(g.gun().get(ModData.RELOAD.get()) == null, "no swap with nothing to swap to");
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 3, "the tube untouched");
            helper.assertFalse(g.player().getData(ModData.GUNNERY).swapRequested(), "the request was consumed");
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
