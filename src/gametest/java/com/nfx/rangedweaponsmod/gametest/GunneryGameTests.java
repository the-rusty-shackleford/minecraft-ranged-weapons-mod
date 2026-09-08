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
                new Expected(ModItems.PISTOL.get(), WeaponClass.SIDEARM, 12, 5, 1, true, "small", 6.0f, 0.6f),
                new Expected(ModItems.SHOTGUN.get(), WeaponClass.SHOTGUN, 6, 13, 6, true, "shell", 4.0f, 3.0f),
                new Expected(ModItems.RIFLE.get(), WeaponClass.RIFLE, 10, 6, 1, false, "medium", 12.0f, 1.0f),
                new Expected(ModItems.SCOPED_RIFLE.get(), WeaponClass.RIFLE, 5, 10, 1, false, "medium", 16.0f, 1.5f),
                new Expected(ModItems.MACHINE_GUN.get(), WeaponClass.AUTOMATIC, 50, 3, 1, false, "medium", 6.0f, 0.4f))) {
            ItemStack stack = new ItemStack(e.item());
            RangedWeapon weapon = RangedWeapons.resolve(stack);
            helper.assertTrue(weapon != null, e.item() + " resolves");
            helper.assertValueEqual(weapon.profile().weaponClass(), e.cls(), e.item() + " class");
            helper.assertValueEqual(weapon.capacity(stack), e.capacity(), e.item() + " capacity");
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
    public void aReloadTakesAnyRoundOfTheFamilyAndNotAnother(GameTestHelper helper) {
        Gunner g = gunner(helper, 0, 0);
        // Another mod's medium round (the gametest pack tags iron nuggets medium),
        // and a small round (gold nuggets) that must be left alone.
        g.player().getInventory().add(new ItemStack(Items.IRON_NUGGET, 10));
        g.player().getInventory().add(new ItemStack(Items.GOLD_NUGGET, 10));
        helper.assertValueEqual(PlayerGunnery.countAmmo(g.player(), g.weapon(), g.gun()), 10, "only the medium rounds count");
        PlayerGunnery.onTrigger(g.player(), true);          // empty gun, trigger pulled: a reload starts
        driveTicks(helper, g, 1, RELOAD_TICKS + 1);
        helper.runAtTickTime(RELOAD_TICKS + 2, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), 10, "loaded from the foreign medium rounds");
            helper.assertValueEqual(g.player().getInventory().countItem(Items.IRON_NUGGET), 0, "all ten spent");
            helper.assertValueEqual(g.player().getInventory().countItem(Items.GOLD_NUGGET), 10, "the small rounds untouched");
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

    // --- the press, through the item ---------------------------------------

    @GameTest(template = "arena", timeoutTicks = 60)
    public void theGunsUseIsThePressAndConsumesTheClick(GameTestHelper helper) {
        Gunner g = gunner(helper, CAPACITY, 0);
        var result = g.gun().getItem().use(helper.getLevel(), g.player(), InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult().consumesAction(), "the click is consumed, so the off hand is not tried");
        helper.assertFalse(result.getResult().shouldSwing(), "no arm swing");
        helper.assertTrue(g.player().getData(ModData.GUNNERY).held(), "the trigger is held after use()");
        driveTicks(helper, g, 1, 7);                       // shots at 1, 4, 7
        helper.runAtTickTime(8, () -> {
            helper.assertValueEqual(g.weapon().rounds(g.gun()), CAPACITY - 3, "rounds after a use() press held 7 ticks");
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
        helper.succeed();
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
            helper.assertValueEqual(PlayerGunnery.countAmmo(g.player(), g.weapon(), g.gun()), 64 - CAPACITY,
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
            helper.assertValueEqual(PlayerGunnery.countAmmo(g.player(), g.weapon(), g.gun()), 0, "all five consumed");
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
            helper.assertValueEqual(PlayerGunnery.countAmmo(g.player(), g.weapon(), g.gun()), 64 - 30,
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
