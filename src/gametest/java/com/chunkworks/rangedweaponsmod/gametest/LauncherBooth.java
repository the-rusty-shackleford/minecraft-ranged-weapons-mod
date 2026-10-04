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

import com.chunkworks.rangedweaponsmod.ModData;
import com.chunkworks.rangedweaponsmod.ModItems;
import com.chunkworks.rangedweaponsmod.RangedWeaponsMod;
import com.chunkworks.rangedweaponsmod.Rocket;
import com.chunkworks.rangedweaponsmod.client.Keys;
import com.chunkworks.rangedweaponsmod.domain.Seeker;
import com.nfx.rangedweapons.api.RangedWeapons;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The rocket launcher's booth (-PboothLauncher; D-0028): every frame its art and HUD are judged
 * by, on a real client, through the real keys. The use key raises the seeker on a cow 24 blocks
 * down a stone range; frames of the sight down and up, the brackets half acquired, the lock, the
 * lock's edge marker with the cow behind; the attack key launches, frames of the rocket leaving
 * the tube and in flight, and the cow killed; R reloads; third person front and side; the
 * inventory with the rifle, rockets and the two parts beside it for scale; last, Immersive
 * Aircraft's biplane crossing the sky, tracked, locked, launched at and brought down (its jar is
 * copied into {@code run/booth/mods} by the build for this booth only); then the rocket's model in
 * close studies with no smoke, and a live rocket side-on as it crosses the view. Screenshots land in
 * {@code run/booth/screenshots}, named {@code launcher-*}. The server owns all gun state.
 */
@EventBusSubscriber(modid = BoothMod.MOD_ID, value = Dist.CLIENT)
public final class LauncherBooth {
    private LauncherBooth() {}

    private static int tick;
    private static UUID cow;
    /** The cow's place relative to the player's feet: straight down the range. */
    private static final int COW_AHEAD = 24;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("rangedweaponsmod.launcher")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        if (tick >= 420 && tick < 560) {
            onServer(mc, LauncherBooth::flyPlane);
        }
        if (tick >= 425 && tick < 530) {
            track(mc);
        }
        if (tick >= 631 && tick < 705 && crossing < 2) {
            watchCrossing(mc);
        }
        switch (++tick) {
            case 40 -> {
                // Eight chunks for this booth, put back at its end: the server sends a player only
                // the entities within their view, and the booth's usual three would drop the
                // biplane at about 55 blocks (measured). Immersive Aircraft tracks to twelve.
                renderDistance = mc.options.renderDistance().get();
                mc.options.renderDistance().set(8);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.pauseOnLostFocus = false;
                onServer(mc, LauncherBooth::prepare);
            }
            case 80 -> {
                mc.setScreen(null);
                mc.mouseHandler.grabMouse();
                face(mc, 0.0f, 2.1f);   // the eye down onto the cow's middle
            }
            case 100 -> {
                check("one rocket in the tube", () -> rounds(mc.player) == 1);
                shoot(mc, "launcher-first-hip");
                KeyMapping.set(mc.options.keyUse.getKey(), true);
            }
            case 108 -> shoot(mc, "launcher-first-sight");
            case 116 -> {
                Seeker s = mc.player.getData(ModData.LOCK);
                check("the use key raised a seeker that is acquiring the cow, " + Math.round(s.progress() * 100) + " %",
                        () -> s.isAcquiring() && !s.isLocked());
                shoot(mc, "launcher-hud-acquiring");
            }
            case 150 -> {
                check("locked onto the cow", () -> mc.player.getData(ModData.LOCK).isLocked());
                shoot(mc, "launcher-hud-locked");
                KeyMapping.set(mc.options.keyUse.getKey(), false);
                face(mc, 150.0f, -10.0f);
            }
            case 165 -> {
                check("the lock holds with the sight down and the cow behind",
                        () -> mc.player.getData(ModData.LOCK).isLocked());
                shoot(mc, "launcher-hud-offscreen");
                face(mc, 0.0f, 2.1f);
            }
            case 175 -> attack(mc, true);
            case 177 -> attack(mc, false);
            case 179 -> shoot(mc, "launcher-rocket-leaving");
            case 184 -> shoot(mc, "launcher-rocket-lit");
            case 190 -> shoot(mc, "launcher-rocket-flight");
            case 200 -> {
                check("the lock was spent by the launch", () -> !mc.player.getData(ModData.LOCK).isLocked());
                // The spares only now: an empty tube with rockets carried reloads on the pull that
                // emptied it, before R could be seen to (the revolver booth's trap too).
                onServer(mc, sp -> sp.getInventory().setItem(2, new ItemStack(ModItems.ROCKET.get(), 8)));
            }
            case 230 -> {
                onServer(mc, sp -> check("the locked rocket killed the cow",
                        () -> sp.serverLevel().getEntity(cow) == null || !sp.serverLevel().getEntity(cow).isAlive()));
                KeyMapping.click(Keys.RELOAD.getKey());
            }
            // The server trails this client by eight to ten ticks here (measured): R at 230 is
            // seen as a reload from about 243.
            case 255 -> {
                check("R starts loading the tube", () -> mc.player.getMainHandItem().has(ModData.RELOAD));
                shoot(mc, "launcher-first-reloading");
            }
            case 320 -> {
                check("the tube holds a rocket again", () -> rounds(mc.player) == 1);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                face(mc, 0.0f, 0.0f);
            }
            case 340 -> shoot(mc, "launcher-third-front");
            case 345 -> KeyMapping.set(mc.options.keyUse.getKey(), true);
            case 355 -> {
                shoot(mc, "launcher-third-front-sight");
                KeyMapping.set(mc.options.keyUse.getKey(), false);
                mc.player.setYRot(-90.0f);
                mc.player.setYHeadRot(-90.0f);
                mc.player.setYBodyRot(-40.0f);
            }
            case 380 -> {
                shoot(mc, "launcher-third-side");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.setScreen(new InventoryScreen(mc.player));
            }
            case 410 -> {
                shoot(mc, "launcher-inventory");
                mc.setScreen(null);
            }
            // The biplane (Rusty, 2026-10-02): Immersive Aircraft's, crossing the sky; the camera
            // swings with it, the use key locks, the attack key launches, and it comes down.
            case 418 -> check("Immersive Aircraft is in the booth (copied in by -PboothLauncher)",
                    () -> ModList.get().isLoaded("immersive_aircraft"));
            case 420 -> onServer(mc, LauncherBooth::spawnPlane);
            case 430 -> {
                check("a biplane is flying across the sky", () -> plane != Seeker.NONE && mc.level.getEntity(plane) != null);
                KeyMapping.set(mc.options.keyUse.getKey(), true);
            }
            case 455 -> {
                Seeker s = mc.player.getData(ModData.LOCK);
                check("the seeker is acquiring the biplane, " + Math.round(s.progress() * 100) + " %",
                        () -> s.isAcquiring() && s.candidate() == plane);
                shoot(mc, "launcher-plane-acquiring");
            }
            case 480 -> {
                check("locked onto the biplane", () -> mc.player.getData(ModData.LOCK).locked() == plane);
                shoot(mc, "launcher-plane-locked");
                KeyMapping.set(mc.options.keyUse.getKey(), false);
                attack(mc, true);
            }
            case 482 -> attack(mc, false);
            case 490 -> shoot(mc, "launcher-plane-intercept-1");
            case 496 -> shoot(mc, "launcher-plane-intercept-2");
            case 502 -> shoot(mc, "launcher-plane-intercept-3");
            case 540 -> {
                onServer(mc, sp -> check("the rocket brought the biplane down",
                        () -> plane != Seeker.NONE && (sp.serverLevel().getEntity(plane) == null || sp.serverLevel().getEntity(plane).isRemoved())));
                shoot(mc, "launcher-plane-down");
            }
            // The rocket's geometry, for its art (Astra, 2026-10-02: the flight frames are smoke from
            // behind). Close studies against the sky: a client-side rocket drawn by its own renderer,
            // photographed before it ever ticks, so it makes no smoke; HUD and hand hidden.
            case 560 -> {
                mc.options.hideGui = true;
                face(mc, 0.0f, -20.0f);
            }
            case 570 -> study(mc, "launcher-rocket-model-side", 90.0f, 1.6);
            case 580 -> study(mc, "launcher-rocket-model-side-close", 90.0f, 1.0);
            case 590 -> study(mc, "launcher-rocket-model-front-quarter", 140.0f, 1.6);
            case 600 -> study(mc, "launcher-rocket-model-rear-quarter", 40.0f, 1.6);
            case 610 -> study(mc, "launcher-rocket-model-nose", 180.0f, 1.6);
            case 620 -> study(mc, "launcher-rocket-model-tail", 0.0f, 1.6);
            // Then a live one, launched across the view four blocks out, photographed side-on as the
            // client draws it at the centre and again a little past: its smoke trails behind it.
            case 630 -> {
                face(mc, 0.0f, 0.0f);
                crossing = 0;
                onServer(mc, LauncherBooth::launchAcross);
            }
            case 705 -> {
                check("a live rocket was photographed side-on as it crossed the view", () -> crossing == 2);
                mc.options.hideGui = false;
            }
            case 720 -> {
                mc.options.renderDistance().set(renderDistance);
                RangedWeaponsMod.LOGGER.info("booth: PASS all checks ran");
                mc.stop();
            }
            default -> { }
        }
    }

    private static void prepare(ServerPlayer p) {
        var level = p.serverLevel();
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        BlockPos floor = p.blockPosition().below();
        for (int x = -10; x <= 10; x++) {
            for (int z = -6; z <= COW_AHEAD + 10; z++) {
                level.setBlockAndUpdate(floor.offset(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = 1; y <= 6; y++) {
                    level.setBlockAndUpdate(floor.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.getInventory().selected = 0;
        ItemStack launcher = new ItemStack(ModItems.ROCKET_LAUNCHER.get());
        RangedWeapons.resolve(launcher).load(launcher, 1, ModItems.ROCKET.get());
        // The seeker locks only with a lock-on chip fitted (D-0029).
        com.chunkworks.rangedweaponsmod.LauncherItem.fit(launcher, new ItemStack(ModItems.LOCK_ON_CHIP.get()));
        p.setItemInHand(InteractionHand.MAIN_HAND, launcher);
        // Beside it in the inventory, for scale: the rifle, the two parts and the scope; the
        // spare rockets come after the launch (tick 200).
        p.getInventory().setItem(1, new ItemStack(ModItems.RIFLE.get()));
        p.getInventory().setItem(3, new ItemStack(ModItems.LAUNCH_TUBE.get()));
        p.getInventory().setItem(4, new ItemStack(ModItems.SEEKER.get()));
        p.getInventory().setItem(5, new ItemStack(ModItems.SCOPE.get()));
        p.connection.teleport(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5, 0, 0);
        Cow target = EntityType.COW.spawn(level, floor.offset(0, 1, COW_AHEAD), MobSpawnType.COMMAND);
        if (target != null) {
            target.setNoAi(true);
            cow = target.getUUID();
        }
    }

    /** Immersive Aircraft's biplane, by name: the booth has its jar, the build does not. */
    private static final ResourceLocation BIPLANE = ResourceLocation.parse("immersive_aircraft:biplane");
    /** The biplane's course: from 30 blocks left of the range, 20 up and 35 out, crossing at 16 blocks a second. */
    private static final Vec3 PLANE_FROM = new Vec3(-30.0, 21.0, 35.0);
    private static final Vec3 PLANE_VELOCITY = new Vec3(0.8, 0.0, 0.0);
    private static volatile int plane = Seeker.NONE;
    private static int renderDistance = 3;

    private static void spawnPlane(ServerPlayer p) {
        var level = p.serverLevel();
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(BIPLANE).orElse(null);
        if (type == null) {
            return;
        }
        Entity biplane = type.create(level);
        if (biplane == null) {
            return;
        }
        Vec3 at = p.position().add(PLANE_FROM);
        biplane.moveTo(at.x, at.y, at.z, -90.0f, 0.0f);
        biplane.setNoGravity(true);
        level.addFreshEntity(biplane);
        plane = biplane.getId();
    }

    /** effects: one tick of the biplane's course, as a pilot would hold it; nothing once it is down */
    private static void flyPlane(ServerPlayer p) {
        Entity biplane = plane == Seeker.NONE ? null : p.serverLevel().getEntity(plane);
        if (biplane != null && !biplane.isRemoved()) {
            // Position only: a velocity of its own would be flown again by its own tick.
            biplane.setPos(biplane.position().add(PLANE_VELOCITY));
            biplane.setDeltaMovement(Vec3.ZERO);
        }
    }

    /** effects: turns the camera onto the biplane, as a player swinging with it would */
    private static void track(Minecraft mc) {
        Entity biplane = plane == Seeker.NONE ? null : mc.level.getEntity(plane);
        if (biplane == null) {
            return;
        }
        Vec3 to = biplane.getBoundingBox().getCenter().subtract(mc.player.getEyePosition());
        face(mc, (float) Math.toDegrees(Math.atan2(-to.x, to.z)), (float) Math.toDegrees(Math.atan2(-to.y, to.horizontalDistance())));
    }

    /** A study waiting to be photographed on the next tick, before the level would tick it. */
    private static Rocket study;
    private static String studyName;
    /** How many frames of the live crossing were taken. */
    private static volatile int crossing;

    /**
     * Photographs a pending study at the head of the tick, before the level ticks it: drawn by its
     * renderer in the frames since it was placed, it has made no smoke yet. Then takes it away.
     */
    @SubscribeEvent
    public static void beforeTick(ClientTickEvent.Pre event) {
        if (!Boolean.getBoolean("rangedweaponsmod.launcher") || study == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        shoot(mc, studyName);
        if (mc.level != null) {
            mc.level.removeEntity(study.getId(), Entity.RemovalReason.DISCARDED);
        }
        study = null;
    }

    /**
     * effects: places a client-side rocket {@code distance} blocks along the look, heading
     * {@code yaw} (the camera faces south: 0 nose away, 90 nose to the screen's left, 180 nose at
     * the camera), level, for
     * {@link #beforeTick} to photograph as {@code name}
     */
    private static void study(Minecraft mc, String name, float yaw, double distance) {
        Rocket rocket = new Rocket(ModData.ROCKET.get(), mc.level);
        Vec3 at = mc.player.getEyePosition().add(mc.player.getViewVector(1.0f).scale(distance));
        rocket.setPos(at);
        rocket.xo = at.x;
        rocket.yo = at.y;
        rocket.zo = at.z;
        rocket.setYRot(yaw);
        rocket.yRotO = yaw;
        rocket.setXRot(0.0f);
        rocket.xRotO = 0.0f;
        mc.level.addEntity(rocket);
        study = rocket;
        studyName = name;
    }

    /** effects: a stone wall to the east to catch it, and a rocket launched east four blocks ahead of the player */
    private static void launchAcross(ServerPlayer p) {
        var level = p.serverLevel();
        BlockPos base = p.blockPosition();
        for (int z = 1; z <= 8; z++) {
            for (int y = 0; y <= 6; y++) {
                level.setBlockAndUpdate(base.offset(16, y, z), Blocks.STONE.defaultBlockState());
            }
        }
        Vec3 from = p.getEyePosition().add(-7.0, 0.0, 4.0);
        Rocket.launch(level, p, from, new Vec3(1.0, 0.0, 0.0), null);
    }

    /** effects: photographs the live rocket as the client draws it at the view's centre, and a little past */
    private static void watchCrossing(Minecraft mc) {
        for (Rocket rocket : mc.level.getEntitiesOfClass(Rocket.class, mc.player.getBoundingBox().inflate(24.0))) {
            double dx = rocket.getX() - mc.player.getX();
            if (crossing == 0 && Math.abs(dx) < 0.6) {
                shoot(mc, "launcher-rocket-live-side");
                crossing = 1;
            } else if (crossing == 1 && dx > 2.5) {
                shoot(mc, "launcher-rocket-live-side-past");
                crossing = 2;
            }
        }
    }

    private static void face(Minecraft mc, float yaw, float pitch) {
        mc.player.setYRot(yaw);
        mc.player.setYHeadRot(yaw);
        mc.player.setXRot(pitch);
    }

    private static int rounds(Player player) {
        var gun = player.getMainHandItem();
        return RangedWeapons.resolve(gun).rounds(gun);
    }

    private static void attack(Minecraft mc, boolean down) {
        KeyMapping.set(mc.options.keyAttack.getKey(), down);
        if (down) {
            KeyMapping.click(mc.options.keyAttack.getKey());
        }
    }

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static void check(String what, BooleanSupplier condition) {
        try {
            if (condition.getAsBoolean()) {
                RangedWeaponsMod.LOGGER.info("booth: PASS {}", what);
            } else {
                RangedWeaponsMod.LOGGER.error("booth: FAIL {}", what);
            }
        } catch (RuntimeException e) {
            RangedWeaponsMod.LOGGER.error("booth: FAIL {} -- {}", what, e.toString());
        }
    }

    private static void shoot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> { });
    }
}
