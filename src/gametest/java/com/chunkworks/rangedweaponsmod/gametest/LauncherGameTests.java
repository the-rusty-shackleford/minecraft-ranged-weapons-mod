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
package com.chunkworks.rangedweaponsmod.gametest;

import com.chunkworks.backpacksplus.BackpackItems;
import com.chunkworks.backpacksplus.BagContents;
import com.chunkworks.rangedweaponsmod.ModData;
import com.chunkworks.rangedweaponsmod.ModItems;
import com.chunkworks.rangedweaponsmod.PlayerGunnery;
import com.chunkworks.rangedweaponsmod.RangedWeaponsMod;
import com.chunkworks.rangedweaponsmod.Rocket;
import com.chunkworks.rangedweaponsmod.RocketConfig;
import com.chunkworks.rangedweaponsmod.Seeking;
import com.chunkworks.rangedweaponsmod.domain.Seeker;
import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.RangedWeapons;
import com.nfx.rangedweapons.api.WeaponClass;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The rocket launcher on a real headless server (D-0028), on the framework's mock player in a
 * range 20 wide and 40 long with an obsidian backstop at its far end, so a rocket that misses
 * goes off there and not in another test's space.
 *
 * <p>Partitions. Seeker: a creature in the reticle locks at exactly ACQUIRE_TICKS; vehicles
 * (a boat, a minecart) lock; a decoration, an item frame and a bare block never do; a lock
 * outlives the look and the sight and ends with its target. Flight: a locked rocket turns off its
 * launch line onto a moving creature and kills it, and the lock is spent; an unlocked rocket keeps
 * its heading; a locked boat is broken. Blast: breaks a block with the config on, leaves it with
 * it off and still kills beside it. Arming: a contact inside the arming distance is a dud. Ammo:
 * creative fires with none; an empty launcher reloads one rocket from a carried bag. Hands: the off
 * hand's item use is refused under a launcher and only under a launcher.
 *
 * <p>Seeker tests step the seeker in a loop within one tick, since a lock on a still target
 * needs no world ticks; flights let the level fly the rocket. Each step first does the client's
 * half, reporting what the reticle is on; one test reports as a lying client would.
 */
@GameTestHolder(RangedWeaponsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LauncherGameTests {

    public LauncherGameTests() {}

    private static final int WIDTH = 20, LENGTH = 40, HEIGHT = 12;
    /** Where the shooter stands, facing +Z down the range. */
    private static final Vec3 STANCE = new Vec3(3.5, 1.0, 1.5);

    private record Shooter(Player player, ItemStack launcher, RangedWeapon weapon) {}

    /** effects: a floored range with its backstop, and a mock player at {@link #STANCE} holding a launcher with {@code rounds} rockets in it */
    private static Shooter shooter(GameTestHelper h, GameType mode, int rounds) {
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < LENGTH; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
            }
            for (int y = 1; y < HEIGHT; y++) {
                h.setBlock(new BlockPos(x, y, LENGTH - 1), Blocks.OBSIDIAN);
            }
        }
        Player player = h.makeMockPlayer(mode);
        mode.updatePlayerAbilities(player.getAbilities());
        Vec3 at = h.absoluteVec(STANCE);
        player.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
        face(player, 0.0f, 0.0f);
        ItemStack launcher = new ItemStack(ModItems.ROCKET_LAUNCHER.get());
        RangedWeapon weapon = RangedWeapons.resolve(launcher);
        if (weapon == null) {
            h.fail("the launcher resolves to no weapon");
        }
        if (rounds > 0) {
            weapon.load(launcher, rounds, ModItems.ROCKET.get());
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, launcher);
        return new Shooter(player, launcher, weapon);
    }

    /** effects: turns the player's body, head and view to {@code yaw}, {@code pitch} */
    private static void face(Player player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.yHeadRotO = yaw;
    }

    /** effects: points the player's eye at the centre of {@code target} */
    private static void aimAt(Player player, Entity target) {
        Vec3 to = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        float pitch = (float) Math.toDegrees(Math.atan2(-to.y, to.horizontalDistance()));
        face(player, yaw, pitch);
    }

    /** effects: steps the gunnery, seeker included, {@code ticks} times at once, the client's report first */
    private static void step(GameTestHelper h, Shooter s, int ticks) {
        ServerLevel level = h.getLevel();
        for (int i = 0; i < ticks; i++) {
            report(level, s.player());
            PlayerGunnery.tick(s.player(), level);
        }
    }

    /** effects: the client's half (SeekReport): with the sight up, reports what the reticle is on */
    private static void report(ServerLevel level, Player player) {
        Entity seen = player.getData(ModData.AIMING) ? Seeking.inReticle(level, player) : null;
        Seeking.onSeen(player, seen == null ? Seeker.NONE : seen.getId());
    }

    private static Seeker seeker(Shooter s) {
        return s.player().getData(ModData.LOCK);
    }

    private static List<Rocket> rockets(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(Rocket.class, h.getBounds().inflate(8.0));
    }

    // --- the seeker ---------------------------------------------------------

    @GameTest(template = "range")
    public void aCreatureInTheReticleIsLockedAtExactlyAcquireTicks(GameTestHelper h) {
        Shooter s = shooter(h, GameType.SURVIVAL, 1);
        LivingEntity cow = h.spawnWithNoFreeWill(EntityType.COW, new Vec3(3.5, 1.0, 22.5));
        aimAt(s.player(), cow);
        PlayerGunnery.onAim(s.player(), true);
        step(h, s, Seeker.ACQUIRE_TICKS - 1);
        h.assertValueEqual(seeker(s).candidate(), cow.getId(), "acquiring the cow");
        h.assertFalse(seeker(s).isLocked(), "not locked a tick short");
        step(h, s, 1);
        h.assertValueEqual(seeker(s).locked(), cow.getId(), "locked at exactly " + Seeker.ACQUIRE_TICKS + " ticks");
        h.assertTrue(RangedWeapons.resolve(s.launcher()).profile().weaponClass() == WeaponClass.LAUNCHER, "a protocol launcher");
        h.succeed();
    }

    @GameTest(template = "range")
    public void vehiclesAreLockedOnto(GameTestHelper h) {
        Shooter s = shooter(h, GameType.SURVIVAL, 1);
        for (EntityType<?> type : List.of(EntityType.BOAT, EntityType.MINECART)) {
            Entity vehicle = h.spawn(type, new Vec3(3.5, 1.0, 22.5));
            aimAt(s.player(), vehicle);
            PlayerGunnery.onAim(s.player(), true);
            step(h, s, Seeker.ACQUIRE_TICKS);
            h.assertValueEqual(seeker(s).locked(), vehicle.getId(), type + " is locked onto");
            vehicle.discard();
            PlayerGunnery.onAim(s.player(), false);
            step(h, s, 1);
        }
        h.succeed();
    }

    @GameTest(template = "range")
    public void decorationsFramesAndBlocksAreNeverLockedOnto(GameTestHelper h) {
        Shooter s = shooter(h, GameType.SURVIVAL, 1);
        Entity stand = h.spawn(EntityType.ARMOR_STAND, new Vec3(3.5, 1.0, 22.5));
        aimAt(s.player(), stand);
        PlayerGunnery.onAim(s.player(), true);
        step(h, s, Seeker.ACQUIRE_TICKS + 10);
        h.assertTrue(!seeker(s).isLocked() && !seeker(s).isAcquiring(), "an armour stand is a decoration");
        stand.discard();

        BlockPos wall = new BlockPos(3, 2, 15);
        h.setBlock(wall, Blocks.STONE);
        ItemFrame frame = new ItemFrame(h.getLevel(), h.absolutePos(wall.north()), Direction.NORTH);
        h.getLevel().addFreshEntity(frame);
        aimAt(s.player(), frame);
        step(h, s, Seeker.ACQUIRE_TICKS + 10);
        h.assertTrue(!seeker(s).isLocked() && !seeker(s).isAcquiring(), "an item frame is not a creature or a vehicle");
        frame.discard();

        face(s.player(), 0.0f, 10.0f);   // at the floor and the backstop: blocks only
        step(h, s, Seeker.ACQUIRE_TICKS + 10);
        h.assertTrue(!seeker(s).isLocked() && !seeker(s).isAcquiring(), "a block is not a target");
        h.succeed();
    }

    /** A lying client: reports the server cannot stand count for nothing. */
    @GameTest(template = "range")
    public void aReportTheServerCannotStandIsRefused(GameTestHelper h) {
        Shooter s = shooter(h, GameType.SURVIVAL, 1);
        LivingEntity cow = h.spawnWithNoFreeWill(EntityType.COW, new Vec3(3.5, 1.0, 22.5));
        PlayerGunnery.onAim(s.player(), true);
        // Facing away from it.
        face(s.player(), 150.0f, 0.0f);
        for (int i = 0; i < Seeker.ACQUIRE_TICKS + 10; i++) {
            Seeking.onSeen(s.player(), cow.getId());
            PlayerGunnery.tick(s.player(), h.getLevel());
        }
        h.assertFalse(seeker(s).isAcquiring() || seeker(s).isLocked(), "a cow behind the player is refused");
        // Facing it, a wall between.
        for (int x = 0; x < 8; x++) {
            for (int y = 1; y < 6; y++) {
                h.setBlock(new BlockPos(x, y, 12), Blocks.STONE);
            }
        }
        aimAt(s.player(), cow);
        for (int i = 0; i < Seeker.ACQUIRE_TICKS + 10; i++) {
            Seeking.onSeen(s.player(), cow.getId());
            PlayerGunnery.tick(s.player(), h.getLevel());
        }
        h.assertFalse(seeker(s).isAcquiring() || seeker(s).isLocked(), "a cow behind a wall is refused");
        // Something that is no target at all, in plain sight.
        Entity stand = h.spawn(EntityType.ARMOR_STAND, new Vec3(3.5, 1.0, 8.5));
        aimAt(s.player(), stand);
        for (int i = 0; i < Seeker.ACQUIRE_TICKS + 10; i++) {
            Seeking.onSeen(s.player(), stand.getId());
            PlayerGunnery.tick(s.player(), h.getLevel());
        }
        h.assertFalse(seeker(s).isAcquiring() || seeker(s).isLocked(), "an armour stand reported is refused");
        h.succeed();
    }

    @GameTest(template = "range")
    public void aLockOutlivesTheLookAndTheSightAndEndsWithItsTarget(GameTestHelper h) {
        Shooter s = shooter(h, GameType.SURVIVAL, 1);
        LivingEntity cow = h.spawnWithNoFreeWill(EntityType.COW, new Vec3(3.5, 1.0, 22.5));
        aimAt(s.player(), cow);
        PlayerGunnery.onAim(s.player(), true);
        step(h, s, Seeker.ACQUIRE_TICKS);
        h.assertValueEqual(seeker(s).locked(), cow.getId(), "locked");
        face(s.player(), 120.0f, -30.0f);
        PlayerGunnery.onAim(s.player(), false);
        step(h, s, 200);
        h.assertValueEqual(seeker(s).locked(), cow.getId(), "still locked, looking away, sight down, ten seconds on");
        cow.kill();
        step(h, s, 1);
        h.assertFalse(seeker(s).isLocked(), "the lock ends with its target");
        h.succeed();
    }

    // --- flight -------------------------------------------------------------

    @GameTest(template = "range", timeoutTicks = 200)
    public void aLockedRocketTurnsOffItsLineOntoAMovingCreatureAndKillsIt(GameTestHelper h) {
        Shooter s = shooter(h, GameType.SURVIVAL, 1);
        // Eight blocks off the launch line: farther than the blast reaches, so only a turn kills it.
        LivingEntity cow = h.spawnWithNoFreeWill(EntityType.COW, new Vec3(11.5, 1.0, 24.5));
        aimAt(s.player(), cow);
        PlayerGunnery.onAim(s.player(), true);
        step(h, s, Seeker.ACQUIRE_TICKS);
        h.assertValueEqual(seeker(s).locked(), cow.getId(), "locked");
        PlayerGunnery.onAim(s.player(), false);
        face(s.player(), 0.0f, 0.0f);   // straight down the range, away from it
        PlayerGunnery.onTrigger(s.player(), true);
        step(h, s, 1);
        List<Rocket> flying = rockets(h);
        h.assertValueEqual(flying.size(), 1, "one rocket launched");
        h.assertValueEqual(flying.get(0).targetId(), cow.getId(), "homing on the cow");
        h.assertFalse(seeker(s).isLocked(), "the lock went with it");
        h.assertValueEqual(s.weapon().rounds(s.launcher()), 0, "the tube is empty");
        h.succeedWhen(() -> {
            if (cow.isAlive()) {
                cow.setPos(cow.getX(), cow.getY(), cow.getZ() + 0.08);   // walking down the range
            }
            h.assertFalse(cow.isAlive(), "the rocket turned onto the cow and killed it");
        });
    }

    @GameTest(template = "range", timeoutTicks = 200)
    public void anUnlockedRocketKeepsItsHeading(GameTestHelper h) {
        Shooter s = shooter(h, GameType.SURVIVAL, 1);
        LivingEntity cow = h.spawnWithNoFreeWill(EntityType.COW, new Vec3(11.5, 1.0, 24.5));
        PlayerGunnery.onTrigger(s.player(), true);
        step(h, s, 1);
        Rocket rocket = rockets(h).get(0);
        h.assertValueEqual(rocket.targetId(), Seeker.NONE, "no lock, no target");
        Vec3 heading = rocket.getDeltaMovement().normalize();
        h.succeedWhen(() -> {
            if (!rocket.isRemoved()) {
                double off = Math.acos(Math.min(1.0, rocket.getDeltaMovement().normalize().dot(heading)));
                h.assertTrue(off < 1e-6, "the heading never changed, off by " + off);
                h.fail("still flying");
            }
            h.assertTrue(cow.isAlive() && cow.getHealth() == cow.getMaxHealth(), "the cow off the line is untouched");
        });
    }

    @GameTest(template = "range", timeoutTicks = 200)
    public void aLockedBoatIsBrokenByTheRocket(GameTestHelper h) {
        Shooter s = shooter(h, GameType.SURVIVAL, 1);
        Entity boat = h.spawn(EntityType.BOAT, new Vec3(9.5, 1.0, 26.5));
        aimAt(s.player(), boat);
        PlayerGunnery.onAim(s.player(), true);
        step(h, s, Seeker.ACQUIRE_TICKS);
        PlayerGunnery.onTrigger(s.player(), true);
        step(h, s, 1);
        h.succeedWhen(() -> h.assertTrue(boat.isRemoved(), "the boat is broken"));
    }

    /**
     * The tag's path, which Immersive Aircraft's, Man of Many Planes' and Automobility's vehicles take
     * in the pack: an end crystal is neither alive nor vanilla's kind of vehicle, and locks only
     * because this gametest pack's own copy of the tag names it. The real biplane is the launcher
     * booth's (its mod sends a payload every mock player's connection refuses).
     */
    @GameTest(template = "range")
    public void anEntityTheTagNamesIsLockedOnto(GameTestHelper h) {
        Shooter s = shooter(h, GameType.SURVIVAL, 1);
        Entity crystal = h.spawn(EntityType.END_CRYSTAL, new Vec3(3.5, 2.0, 22.5));
        aimAt(s.player(), crystal);
        PlayerGunnery.onAim(s.player(), true);
        step(h, s, Seeker.ACQUIRE_TICKS);
        h.assertValueEqual(seeker(s).locked(), crystal.getId(), "the tagged end crystal is locked onto");
        h.succeed();
    }

    // --- the blast ------------------------------------------------------------

    /** effects: a stone pillar across the range at z 14 and a rocket launched straight at it */
    private static BlockPos pillarAndLaunch(GameTestHelper h) {
        shooter(h, GameType.SURVIVAL, 0);
        BlockPos pillar = new BlockPos(8, 2, 14);
        for (int y = 1; y < 5; y++) {
            h.setBlock(pillar.atY(y), Blocks.STONE);
        }
        Vec3 from = h.absoluteVec(new Vec3(8.5, 2.5, 3.5));
        Rocket.launch(h.getLevel(), h.makeMockPlayer(GameType.SURVIVAL), from, new Vec3(0.0, 0.0, 1.0), null);
        return pillar;
    }

    @GameTest(template = "range", timeoutTicks = 100)
    public void theBlastBreaksBlocksWithTheSwitchOn(GameTestHelper h) {
        BlockPos pillar = pillarAndLaunch(h);
        h.succeedWhen(() -> {
            h.assertTrue(rockets(h).isEmpty(), "the rocket went off");
            h.assertBlockNotPresent(Blocks.STONE, pillar);
        });
    }

    @GameTest(template = "range", timeoutTicks = 100, batch = "rockets_keep_blocks")
    public void theBlastLeavesBlocksWithTheSwitchOffAndStillKills(GameTestHelper h) {
        RocketConfig.setBreakBlocks(false);
        LivingEntity cow = h.spawnWithNoFreeWill(EntityType.COW, new Vec3(9.5, 1.0, 12.5));
        BlockPos pillar = pillarAndLaunch(h);
        h.succeedWhen(() -> {
            h.assertTrue(rockets(h).isEmpty(), "the rocket went off");
            h.assertBlockPresent(Blocks.STONE, pillar);
            h.assertFalse(cow.isAlive(), "the blast still kills beside the pillar");
            RocketConfig.setBreakBlocks(true);
        });
    }

    @GameTest(template = "range", timeoutTicks = 60)
    public void aContactInsideTheArmingDistanceIsADud(GameTestHelper h) {
        Shooter s = shooter(h, GameType.SURVIVAL, 1);
        BlockPos wall = new BlockPos(3, 2, 4);
        for (int x = 1; x < 7; x++) {
            for (int y = 1; y < 5; y++) {
                h.setBlock(new BlockPos(x, y, 4), Blocks.STONE);
            }
        }
        LivingEntity cow = h.spawnWithNoFreeWill(EntityType.COW, new Vec3(7.5, 1.0, 3.5));
        PlayerGunnery.onTrigger(s.player(), true);
        step(h, s, 1);
        h.assertValueEqual(rockets(h).size(), 1, "launched");
        h.succeedWhen(() -> {
            h.assertTrue(rockets(h).isEmpty(), "the rocket is gone");
            h.assertBlockPresent(Blocks.STONE, wall);
            h.assertTrue(cow.getHealth() == cow.getMaxHealth(), "no blast: the cow beside the wall is untouched");
        });
    }

    // --- ammunition -------------------------------------------------------------

    @GameTest(template = "range", timeoutTicks = 60)
    public void creativeFiresWithoutARocket(GameTestHelper h) {
        Shooter s = shooter(h, GameType.CREATIVE, 0);
        h.assertValueEqual(s.player().getInventory().countItem(ModItems.ROCKET.get()), 0, "no rocket anywhere");
        PlayerGunnery.onTrigger(s.player(), true);
        step(h, s, 1);
        h.assertValueEqual(rockets(h).size(), 1, "a rocket all the same");
        h.succeed();
    }

    @GameTest(template = "range", timeoutTicks = 100)
    public void anEmptyLauncherLoadsOneRocketFromACarriedBag(GameTestHelper h) {
        Shooter s = shooter(h, GameType.SURVIVAL, 0);
        ItemStack bag = new ItemStack(BackpackItems.BASIC.get());
        BagContents.identify(bag);
        NonNullList<ItemStack> cells = BagContents.copy(bag);
        cells.set(0, new ItemStack(ModItems.ROCKET.get(), 3));
        BagContents.store(bag, cells);
        s.player().getInventory().setItem(7, bag);
        h.assertValueEqual(PlayerGunnery.countAmmo(s.player(), s.weapon(), s.launcher()), 3, "the bag's rockets are in reach");
        int reloadTicks = s.weapon().stats(s.launcher()).fullReloadTicks();
        PlayerGunnery.onTrigger(s.player(), true);
        ServerLevel level = h.getLevel();
        for (int t = 1; t <= reloadTicks + 3; t++) {
            h.runAtTickTime(t, () -> PlayerGunnery.tick(s.player(), level));
        }
        // Let go once the reload is under way, as a player does: a pull still held when the
        // rocket goes in fires it, the semi-automatic rule, and the tube would be empty again.
        h.runAtTickTime(3, () -> {
            h.assertTrue(s.launcher().get(ModData.RELOAD.get()) != null, "a reload started on the empty trigger, of " + reloadTicks + " ticks");
            PlayerGunnery.onTrigger(s.player(), false);
        });
        h.runAtTickTime(reloadTicks + 6, () -> {
            h.assertValueEqual(s.weapon().rounds(s.launcher()), 1, "one rocket in the tube");
            h.assertValueEqual(BagContents.copy(s.player().getInventory().getItem(7)).get(0).getCount(), 2, "taken from the bag");
            h.succeed();
        });
    }

    // --- the hands ------------------------------------------------------------------

    @GameTest(template = "range")
    public void theOffHandIsNotUsedUnderALauncherAndOnlyUnderOne(GameTestHelper h) {
        Shooter s = shooter(h, GameType.SURVIVAL, 1);
        s.player().setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        PlayerInteractEvent.RightClickItem off = new PlayerInteractEvent.RightClickItem(s.player(), InteractionHand.OFF_HAND);
        NeoForge.EVENT_BUS.post(off);
        h.assertTrue(off.isCanceled(), "a shield in the off hand is not raised under the launcher");
        PlayerInteractEvent.RightClickItem main = new PlayerInteractEvent.RightClickItem(s.player(), InteractionHand.MAIN_HAND);
        NeoForge.EVENT_BUS.post(main);
        h.assertFalse(main.isCanceled(), "the main hand's use is vanilla's");
        s.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.RIFLE.get()));
        PlayerInteractEvent.RightClickItem underRifle = new PlayerInteractEvent.RightClickItem(s.player(), InteractionHand.OFF_HAND);
        NeoForge.EVENT_BUS.post(underRifle);
        h.assertFalse(underRifle.isCanceled(), "under any other gun the off hand is the player's");
        h.assertTrue(Seeking.inReticle(h.getLevel(), s.player()) == null, "nothing in the reticle down an empty range");
        h.succeed();
    }
}
