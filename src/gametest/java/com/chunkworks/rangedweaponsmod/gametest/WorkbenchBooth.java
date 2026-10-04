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

import com.chunkworks.rangedweaponsmod.LauncherItem;
import com.chunkworks.rangedweaponsmod.ModBlocks;
import com.chunkworks.rangedweaponsmod.ModData;
import com.chunkworks.rangedweaponsmod.ModItems;
import com.chunkworks.rangedweaponsmod.RangedWeaponsMod;
import com.chunkworks.rangedweaponsmod.WorkbenchBlock;
import com.chunkworks.rangedweaponsmod.WorkbenchMenu;
import com.chunkworks.rangedweaponsmod.client.WorkbenchScreen;
import com.nfx.rangedweapons.api.RangedWeapons;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The weapons workbench's booth (-PboothWorkbench; D-0029): every frame its art and screen are
 * judged by, on a real client, through the real keys and clicks.
 *
 * <ul>
 *   <li>The bench between a crafting table and a smithing table, front on and from a corner, for
 *   its faces beside vanilla's.</li>
 *   <li>A chipless launcher's sight raised: "No lock-on chip" under the crosshair, and no ring.</li>
 *   <li>The use key on the bench opens its screen: empty, with a launcher shift-clicked into the
 *   weapon slot (the chip slot opens), and with a chip shift-clicked onto it.</li>
 *   <li>With EMI (its jar copied into {@code run/booth/mods} by the build for this booth only): the
 *   bench's category and every bench recipe in it, the rifle's recipe page, and EMI's own "+"
 *   filling the bench's grid from the inventory ({@link WorkbenchBoothEmi}).</li>
 *   <li>The fitted launcher's sight raised: the ring, and no hint.</li>
 * </ul>
 *
 * Screenshots land in {@code run/booth/screenshots}, named {@code workbench-*}.
 */
@EventBusSubscriber(modid = BoothMod.MOD_ID, value = Dist.CLIENT)
public final class WorkbenchBooth {
    private WorkbenchBooth() {}

    private static int tick;
    /** The bench, three blocks ahead of the player's feet, facing them. */
    private static BlockPos bench;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("rangedweaponsmod.workbench")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        switch (++tick) {
            case 40 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.pauseOnLostFocus = false;
                onServer(mc, WorkbenchBooth::prepare);
            }
            case 80 -> {
                mc.setScreen(null);
                mc.mouseHandler.grabMouse();
                face(mc, 0.0f, 30.0f);
            }
            case 90 -> {
                check("the bench stands between a crafting table and a smithing table, facing the player",
                        () -> mc.level.getBlockState(bench).is(ModBlocks.WEAPONS_WORKBENCH.get())
                                && mc.level.getBlockState(bench).getValue(WorkbenchBlock.FACING) == Direction.NORTH);
                shoot(mc, "workbench-blocks-front");
                onServer(mc, sp -> sp.connection.teleport(bench.getX() + 2.6, bench.getY(), bench.getZ() - 1.6, 50.0f, 30.0f));
            }
            case 100 -> shoot(mc, "workbench-blocks-corner");
            case 105 -> onServer(mc, sp -> sp.connection.teleport(bench.getX() + 0.5, bench.getY(), bench.getZ() - 6.5, 180.0f, 0.0f));
            case 115 -> {
                check("a chipless launcher in hand", () -> LauncherItem.isLauncher(mc.player.getMainHandItem())
                        && LauncherItem.chip(mc.player.getMainHandItem()).isEmpty());
                KeyMapping.set(mc.options.keyUse.getKey(), true);
            }
            case 125 -> {
                check("the sight is up and the seeker may not lock", () -> mc.player.getData(ModData.AIMING)
                        && !LauncherItem.canLock(mc.player, mc.player.getMainHandItem()));
                shoot(mc, "workbench-hud-no-chip");
                KeyMapping.set(mc.options.keyUse.getKey(), false);
                onServer(mc, sp -> sp.connection.teleport(bench.getX() + 0.5, bench.getY(), bench.getZ() - 2.0, 0.0f, 35.0f));
            }
            case 140 -> KeyMapping.click(mc.options.keyUse.getKey());
            case 160 -> {
                check("the use key on the bench opened its screen", () -> mc.screen instanceof WorkbenchScreen);
                shoot(mc, "workbench-screen-empty");
                if (mc.screen instanceof WorkbenchScreen screen) {
                    mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, WorkbenchMenu.HOTBAR_START, 0, ClickType.QUICK_MOVE, mc.player);
                }
            }
            case 170 -> {
                check("shift-click put the launcher in the weapon slot, and the chip slot opened",
                        () -> mc.screen instanceof WorkbenchScreen s
                                && LauncherItem.isLauncher(s.getMenu().getSlot(WorkbenchMenu.WEAPON).getItem())
                                && s.getMenu().chipSlotOpen());
                shoot(mc, "workbench-screen-launcher");
                if (mc.screen instanceof WorkbenchScreen screen) {
                    mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, WorkbenchMenu.HOTBAR_START + 1, 0, ClickType.QUICK_MOVE, mc.player);
                }
            }
            case 180 -> {
                check("shift-click fitted the chip to the launcher",
                        () -> mc.screen instanceof WorkbenchScreen s
                                && !LauncherItem.chip(s.getMenu().getSlot(WorkbenchMenu.WEAPON).getItem()).isEmpty());
                shoot(mc, "workbench-screen-fitted");
                if (mc.screen instanceof WorkbenchScreen screen) {
                    mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, WorkbenchMenu.WEAPON, 0, ClickType.QUICK_MOVE, mc.player);
                }
            }
            case 190 -> {
                // Shift-click sends it to the inventory first and the hotbar after, as a crafting table does.
                check("the launcher came back to the inventory with its chip", () -> mc.player.getInventory().items.stream()
                        .anyMatch(stack -> LauncherItem.isLauncher(stack) && !LauncherItem.chip(stack).isEmpty()));
                mc.player.closeContainer();
            }
            case 200 -> {
                boolean emi = ModList.get().isLoaded("emi");
                check("EMI is in the booth (copied in by -PboothWorkbench)", () -> emi);
                if (emi) {
                    WorkbenchBoothEmi.showRiflePage(mc);
                }
            }
            case 230 -> {
                if (ModList.get().isLoaded("emi")) {
                    check("EMI lists every bench recipe under the bench", () -> WorkbenchBoothEmi.everyBenchRecipeListed());
                    check("EMI's page for the rifle is open", () -> mc.screen != null);
                    shoot(mc, "workbench-emi-rifle");
                }
                mc.setScreen(null);
                mc.mouseHandler.grabMouse();
            }
            case 240 -> KeyMapping.click(mc.options.keyUse.getKey());
            case 255 -> {
                check("the bench is open again", () -> mc.screen instanceof WorkbenchScreen);
                if (ModList.get().isLoaded("emi") && mc.screen instanceof WorkbenchScreen screen) {
                    check("EMI's + fills the bench's grid with the rifle", () -> WorkbenchBoothEmi.fillRifle(screen));
                }
            }
            case 270 -> {
                check("the grid holds the rifle's parts and the bench offers a rifle",
                        () -> mc.screen instanceof WorkbenchScreen s
                                && s.getMenu().getSlot(WorkbenchMenu.RESULT).getItem().is(ModItems.RIFLE.get()));
                shoot(mc, "workbench-emi-filled");
                mc.player.closeContainer();
            }
            case 285 -> onServer(mc, sp -> {
                // The fitted launcher into the hand, wherever shift-click put it.
                var inventory = sp.getInventory();
                for (int slot = 0; slot < inventory.items.size(); slot++) {
                    if (LauncherItem.isLauncher(inventory.items.get(slot)) && slot != 0) {
                        ItemStack there = inventory.getItem(0);
                        inventory.setItem(0, inventory.items.get(slot));
                        inventory.setItem(slot, there);
                        break;
                    }
                }
                inventory.selected = 0;
                sp.connection.teleport(bench.getX() + 0.5, bench.getY(), bench.getZ() - 6.5, 180.0f, 0.0f);
            });
            case 295 -> KeyMapping.set(mc.options.keyUse.getKey(), true);
            case 305 -> {
                check("with the chip fitted the seeker may lock", () -> mc.player.getData(ModData.AIMING)
                        && LauncherItem.canLock(mc.player, mc.player.getMainHandItem()));
                shoot(mc, "workbench-hud-chipped");
                KeyMapping.set(mc.options.keyUse.getKey(), false);
            }
            case 320 -> {
                RangedWeaponsMod.LOGGER.info("booth: PASS all checks ran");
                mc.stop();
            }
            default -> { }
        }
    }

    /** A clean floor, the three tables in a row three blocks ahead, and the inventory the booth works from. */
    private static void prepare(ServerPlayer p) {
        var level = p.serverLevel();
        level.setDayTime(6000);
        level.setWeatherParameters(6000, 0, false, false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        BlockPos floor = p.blockPosition().below();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                level.setBlockAndUpdate(floor.offset(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = 1; y <= 5; y++) {
                    level.setBlockAndUpdate(floor.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        bench = floor.offset(0, 1, 3);
        level.setBlockAndUpdate(bench, ModBlocks.WEAPONS_WORKBENCH.get().defaultBlockState().setValue(WorkbenchBlock.FACING, Direction.NORTH));
        level.setBlockAndUpdate(bench.west(2), Blocks.CRAFTING_TABLE.defaultBlockState());
        level.setBlockAndUpdate(bench.east(2), Blocks.SMITHING_TABLE.defaultBlockState());
        p.setGameMode(GameType.SURVIVAL);
        p.getInventory().clearContent();
        p.getInventory().selected = 0;
        ItemStack launcher = new ItemStack(ModItems.ROCKET_LAUNCHER.get());
        RangedWeapons.resolve(launcher).load(launcher, 1, ModItems.ROCKET.get());
        p.getInventory().setItem(0, launcher);
        p.getInventory().setItem(1, new ItemStack(ModItems.LOCK_ON_CHIP.get()));
        // A rifle's parts, for EMI to fill the grid from.
        p.getInventory().setItem(9, new ItemStack(ModItems.UPPER_RECEIVER.get()));
        p.getInventory().setItem(10, new ItemStack(ModItems.STOCK.get()));
        p.getInventory().setItem(11, new ItemStack(ModItems.LOWER_RECEIVER.get()));
        p.getInventory().setItem(12, new ItemStack(ModItems.BARREL.get()));
        p.connection.teleport(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5, 0, 30);
    }

    private static void face(Minecraft mc, float yaw, float pitch) {
        mc.player.setYRot(yaw);
        mc.player.setYHeadRot(yaw);
        mc.player.setXRot(pitch);
    }

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    static void check(String what, BooleanSupplier condition) {
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
