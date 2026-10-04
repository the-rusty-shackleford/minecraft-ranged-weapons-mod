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
import com.chunkworks.rangedweaponsmod.ModItems;
import com.chunkworks.rangedweaponsmod.RangedWeaponsMod;
import com.chunkworks.rangedweaponsmod.WorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The weapons workbench on a real server (D-0029), on the framework's server-side mock player, so
 * the bench works its result out and sends it as it does for a player. Menus are the server's own
 * copies, driven by the clicks a client would send.
 *
 * <p>Partitions. Assembly: a bench grid makes its recipe, a crafting table's makes nothing of the
 * same grid; a round's grid makes eight at the table and nothing at the bench; a craft spends the
 * grid. Keeping: closing hands back the grid and the weapon, its chip on it. Fitting: no chip slot
 * without a weapon; a launcher put in opens it; a chip put in rides the launcher with its wear and
 * comes off with it; a launcher taken out takes its chip and closes the slot. Slots: only a weapon
 * that takes a fitting in the weapon slot, only a chip, one, in the chip slot. Shift-click: a
 * launcher to the weapon slot, a chip onto it, anything else to the grid. The block: used, it opens
 * the bench; broken, it drops itself.
 */
@GameTestHolder(RangedWeaponsMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkbenchGameTests {

    public WorkbenchGameTests() {}

    private static final BlockPos BENCH = new BlockPos(2, 1, 2);
    private static final BlockPos TABLE = new BlockPos(5, 1, 2);
    private static final ItemStack NONE = ItemStack.EMPTY;

    /** A survival player beside a bench, with the server's copy of the bench's menu. */
    private record AtBench(ServerPlayer player, WorkbenchMenu menu) {}

    @SuppressWarnings("removal")
    private static AtBench atBench(GameTestHelper h) {
        h.setBlock(BENCH, ModBlocks.WEAPONS_WORKBENCH.get());
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        WorkbenchMenu menu = new WorkbenchMenu(1, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(BENCH)));
        return new AtBench(player, menu);
    }

    private static void leave(GameTestHelper h, ServerPlayer player) {
        h.getLevel().getServer().getPlayerList().remove(player);
    }

    private static ItemStack of(ItemLike item) {
        return new ItemStack(item);
    }

    /** effects: a lock-on chip with {@code used} of its charges spent */
    private static ItemStack chip(int used) {
        ItemStack chip = of(ModItems.LOCK_ON_CHIP.get());
        chip.setDamageValue(used);
        return chip;
    }

    /** effects: puts a copy of each of {@code cells}, row by row, into the menu's slots from {@code first} */
    private static void fill(AbstractContainerMenu menu, int first, ItemStack... cells) {
        for (int i = 0; i < cells.length; i++) {
            menu.getSlot(first + i).set(cells[i].copy());
        }
    }

    /** effects: the player's first stack of {@code item}, or empty */
    private static ItemStack first(ServerPlayer player, ItemLike item) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item.asItem())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @GameTest(template = "arena")
    public void aRifleIsAssembledAtTheBenchAndNotAtACraftingTable(GameTestHelper h) {
        AtBench b = atBench(h);
        try {
            ItemStack[] rifle = {
                    NONE, of(ModItems.UPPER_RECEIVER.get()), NONE,
                    of(ModItems.STOCK.get()), of(ModItems.LOWER_RECEIVER.get()), of(ModItems.BARREL.get()),
                    NONE, NONE, NONE};
            fill(b.menu(), WorkbenchMenu.GRID_START, rifle);
            h.assertTrue(b.menu().getSlot(WorkbenchMenu.RESULT).getItem().is(ModItems.RIFLE.get()),
                    "the bench makes a rifle of it, made " + b.menu().getSlot(WorkbenchMenu.RESULT).getItem());
            b.menu().clicked(WorkbenchMenu.RESULT, 0, ClickType.QUICK_MOVE, b.player());
            h.assertValueEqual(b.player().getInventory().countItem(ModItems.RIFLE.get()), 1, "rifles in the inventory");
            for (int i = WorkbenchMenu.GRID_START; i < WorkbenchMenu.GRID_END; i++) {
                h.assertTrue(b.menu().getSlot(i).getItem().isEmpty(), "every part spent, cell " + i);
            }
            h.setBlock(TABLE, Blocks.CRAFTING_TABLE);
            CraftingMenu table = new CraftingMenu(2, b.player().getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(TABLE)));
            fill(table, 1, rifle);
            h.assertTrue(table.getSlot(0).getItem().isEmpty(), "a crafting table makes nothing of it, made " + table.getSlot(0).getItem());
        } finally {
            leave(h, b.player());
        }
        h.succeed();
    }

    @GameTest(template = "arena")
    public void aRoundStillCraftsAtTheTableAndNotAtTheBench(GameTestHelper h) {
        AtBench b = atBench(h);
        try {
            ItemStack[] round = {
                    of(Items.IRON_NUGGET), NONE, NONE,
                    of(Items.GUNPOWDER), NONE, NONE,
                    of(BoothMod.COPPER_NUGGET.get()), NONE, NONE};
            fill(b.menu(), WorkbenchMenu.GRID_START, round);
            h.assertTrue(b.menu().getSlot(WorkbenchMenu.RESULT).getItem().isEmpty(),
                    "the bench makes no rounds, made " + b.menu().getSlot(WorkbenchMenu.RESULT).getItem());
            h.setBlock(TABLE, Blocks.CRAFTING_TABLE);
            CraftingMenu table = new CraftingMenu(2, b.player().getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(TABLE)));
            fill(table, 1, round);
            ItemStack made = table.getSlot(0).getItem();
            h.assertTrue(made.is(ModItems.ROUND.get()) && made.getCount() == 8, "the table makes eight rounds of it, made " + made);
        } finally {
            leave(h, b.player());
        }
        h.succeed();
    }

    @GameTest(template = "arena")
    public void theGridAndTheWeaponGoBackToThePlayerOnClosing(GameTestHelper h) {
        AtBench b = atBench(h);
        try {
            b.menu().getSlot(WorkbenchMenu.GRID_START + 4).set(new ItemStack(Items.IRON_INGOT, 5));
            ItemStack launcher = of(ModItems.ROCKET_LAUNCHER.get());
            LauncherItem.fit(launcher, chip(1));
            b.menu().getSlot(WorkbenchMenu.WEAPON).set(launcher);
            b.menu().removed(b.player());
            h.assertValueEqual(b.player().getInventory().countItem(Items.IRON_INGOT), 5, "iron back in the inventory");
            ItemStack back = first(b.player(), ModItems.ROCKET_LAUNCHER.get());
            h.assertTrue(!back.isEmpty(), "the launcher came back");
            h.assertValueEqual(LauncherItem.chip(back).getDamageValue(), 1, "with its chip, worn as it was");
        } finally {
            leave(h, b.player());
        }
        h.succeed();
    }

    @GameTest(template = "arena")
    public void aChipFittedAtTheBenchRidesTheLauncherAndComesOffWithItsWear(GameTestHelper h) {
        AtBench b = atBench(h);
        WorkbenchMenu m = b.menu();
        ServerPlayer p = b.player();
        try {
            h.assertFalse(m.getSlot(WorkbenchMenu.CHIP).isActive(), "no chip slot with no weapon");
            m.setCarried(of(ModItems.ROCKET_LAUNCHER.get()));
            m.clicked(WorkbenchMenu.WEAPON, 0, ClickType.PICKUP, p);
            h.assertTrue(m.getCarried().isEmpty() && m.getSlot(WorkbenchMenu.WEAPON).getItem().is(ModItems.ROCKET_LAUNCHER.get()),
                    "the launcher in the weapon slot");
            h.assertTrue(m.getSlot(WorkbenchMenu.CHIP).isActive(), "the chip slot opens under a launcher");

            m.setCarried(chip(5));
            m.clicked(WorkbenchMenu.CHIP, 0, ClickType.PICKUP, p);
            h.assertTrue(m.getCarried().isEmpty(), "the chip went in");
            h.assertValueEqual(LauncherItem.chip(m.getSlot(WorkbenchMenu.WEAPON).getItem()).getDamageValue(), 5,
                    "fitted to the launcher with its wear");

            m.clicked(WorkbenchMenu.CHIP, 0, ClickType.PICKUP, p);
            h.assertTrue(m.getCarried().is(ModItems.LOCK_ON_CHIP.get()) && m.getCarried().getDamageValue() == 5,
                    "it comes off with its wear, carried " + m.getCarried());
            h.assertTrue(LauncherItem.chip(m.getSlot(WorkbenchMenu.WEAPON).getItem()).isEmpty(), "leaving the launcher bare");

            m.clicked(WorkbenchMenu.CHIP, 0, ClickType.PICKUP, p);
            m.clicked(WorkbenchMenu.WEAPON, 0, ClickType.PICKUP, p);
            h.assertTrue(m.getCarried().is(ModItems.ROCKET_LAUNCHER.get()) && LauncherItem.chip(m.getCarried()).getDamageValue() == 5,
                    "a launcher taken out takes its chip");
            h.assertFalse(m.getSlot(WorkbenchMenu.CHIP).isActive(), "and the chip slot closes");
            h.assertTrue(m.getSlot(WorkbenchMenu.CHIP).getItem().isEmpty(), "empty");
        } finally {
            leave(h, p);
        }
        h.succeed();
    }

    @GameTest(template = "arena")
    public void onlyALauncherTakesTheWeaponSlotAndOnlyOneChipTheChipSlot(GameTestHelper h) {
        AtBench b = atBench(h);
        try {
            Slot weapon = b.menu().getSlot(WorkbenchMenu.WEAPON);
            Slot chip = b.menu().getSlot(WorkbenchMenu.CHIP);
            h.assertFalse(weapon.mayPlace(of(ModItems.RIFLE.get())), "a rifle takes no fitting");
            h.assertFalse(weapon.mayPlace(of(Items.IRON_INGOT)), "iron is no weapon");
            h.assertTrue(weapon.mayPlace(of(ModItems.ROCKET_LAUNCHER.get())), "a launcher takes a chip");
            h.assertValueEqual(weapon.getMaxStackSize(of(ModItems.ROCKET_LAUNCHER.get())), 1, "one launcher");
            h.assertFalse(chip.mayPlace(chip(0)), "no chip with no launcher");
            weapon.set(of(ModItems.ROCKET_LAUNCHER.get()));
            h.assertTrue(chip.mayPlace(chip(0)), "a chip under a launcher");
            h.assertFalse(chip.mayPlace(of(Items.IRON_INGOT)), "nothing else");
            h.assertValueEqual(chip.getMaxStackSize(chip(0)), 1, "one chip");
        } finally {
            leave(h, b.player());
        }
        h.succeed();
    }

    @GameTest(template = "arena")
    public void shiftClickSendsALauncherAndAChipToTheirSlotsAndTheRestToTheGrid(GameTestHelper h) {
        AtBench b = atBench(h);
        WorkbenchMenu m = b.menu();
        ServerPlayer p = b.player();
        try {
            // Inventory slots 9, 10 and 11 are the menu's first three inventory slots.
            p.getInventory().setItem(9, of(ModItems.ROCKET_LAUNCHER.get()));
            p.getInventory().setItem(10, chip(4));
            p.getInventory().setItem(11, new ItemStack(Items.IRON_INGOT, 3));
            m.clicked(WorkbenchMenu.INVENTORY_START, 0, ClickType.QUICK_MOVE, p);
            h.assertTrue(m.getSlot(WorkbenchMenu.WEAPON).getItem().is(ModItems.ROCKET_LAUNCHER.get()), "the launcher to the weapon slot");
            m.clicked(WorkbenchMenu.INVENTORY_START + 1, 0, ClickType.QUICK_MOVE, p);
            h.assertValueEqual(LauncherItem.chip(m.getSlot(WorkbenchMenu.WEAPON).getItem()).getDamageValue(), 4, "the chip onto it");
            h.assertTrue(p.getInventory().getItem(10).isEmpty(), "and out of the inventory");
            m.clicked(WorkbenchMenu.INVENTORY_START + 2, 0, ClickType.QUICK_MOVE, p);
            h.assertValueEqual(m.getSlot(WorkbenchMenu.GRID_START).getItem().getCount(), 3, "anything else to the grid");
        } finally {
            leave(h, p);
        }
        h.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(template = "arena")
    public void theBenchOpensItsScreenWhenUsedAndDropsItselfWhenBroken(GameTestHelper h) {
        h.setBlock(BENCH, ModBlocks.WEAPONS_WORKBENCH.get());
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        try {
            p.setGameMode(GameType.SURVIVAL);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, of(ModItems.RIFLE.get()));
            h.useBlock(BENCH, p);
            h.assertTrue(p.containerMenu instanceof WorkbenchMenu, "used, even with a gun in hand, it opens the bench; open: " + p.containerMenu);
            p.closeContainer();
            h.getLevel().destroyBlock(h.absolutePos(BENCH), true, p);
            h.assertItemEntityPresent(ModItems.WEAPONS_WORKBENCH.get(), BENCH, 2.0);
        } finally {
            leave(h, p);
        }
        h.succeed();
    }
}
