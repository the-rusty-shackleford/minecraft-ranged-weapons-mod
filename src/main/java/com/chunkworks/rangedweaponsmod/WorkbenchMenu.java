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
package com.chunkworks.rangedweaponsmod;

import com.mojang.datafixers.util.Pair;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * The weapons workbench's screen (D-0029): on the left a crafting grid and its result for the
 * bench's own recipes ({@link AssemblyRecipe}), on the right the fittings -- a weapon slot and,
 * with a weapon in it that takes one, a slot for its lock-on chip -- and the player's inventory
 * below where every container puts it.
 *
 * <p>The chip slot is a view of the weapon: what it shows is the chip fitted to the launcher in
 * the weapon slot ({@link ModData#CHIP}), and what is put in or taken out is written straight onto
 * the launcher, so a chip goes on and comes off whole, its wear with it, and a launcher taken out
 * of the bench takes its chip along. Like the crafting table, the bench keeps nothing: on
 * closing, the grid and the weapon slot go back to the player.
 *
 * <p>The result is worked out on the server, as the crafting table's is, and sent to the client;
 * the client's own copy of the menu never looks a recipe up.
 */
public final class WorkbenchMenu extends AbstractContainerMenu {

    public static final int RESULT = 0;
    public static final int GRID_START = 1;
    public static final int GRID_END = 10;
    public static final int WEAPON = 10;
    public static final int CHIP = 11;
    public static final int INVENTORY_START = 12;
    public static final int HOTBAR_START = 39;
    public static final int END = 48;

    // Where the slots stand, in the screen's pixels; the background is drawn to match
    // (devtools/art/workbench_art.py).
    public static final int GRID_X = 12;
    public static final int GRID_Y = 17;
    public static final int RESULT_X = 102;
    public static final int RESULT_Y = 35;
    public static final int WEAPON_X = 146;
    public static final int WEAPON_Y = 21;
    public static final int CHIP_X = 146;
    public static final int CHIP_Y = 49;

    static final ResourceLocation EMPTY_LAUNCHER = ResourceLocation.fromNamespaceAndPath(RangedWeaponsMod.MOD_ID, "item/empty_slot_launcher");
    static final ResourceLocation EMPTY_CHIP = ResourceLocation.fromNamespaceAndPath(RangedWeaponsMod.MOD_ID, "item/empty_slot_chip");

    private final CraftingContainer grid = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer result = new ResultContainer();
    private final SimpleContainer weapon = new SimpleContainer(1);
    private final ChipView chip = new ChipView();
    private final ContainerLevelAccess access;
    private final Player player;

    /** The client's copy, which needs nothing but the menu type to open. */
    public WorkbenchMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    /** The server's, at the bench in {@code access}. */
    public WorkbenchMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(ModData.WORKBENCH_MENU.get(), id);
        this.access = access;
        this.player = inventory.player;
        addSlot(new AssemblyResultSlot(player, grid, result, 0, RESULT_X, RESULT_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(grid, col + row * 3, GRID_X + col * 18, GRID_Y + row * 18));
            }
        }
        addSlot(new WeaponSlot());
        addSlot(new ChipSlot());
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    /** effects: returns whether the chip slot is open: a weapon that takes a chip is in the weapon slot */
    public boolean chipSlotOpen() {
        return Fittings.takesChip(weapon.getItem(0));
    }

    /** effects: the grid changed: on the server, the result is looked up again and sent */
    @Override
    public void slotsChanged(Container container) {
        if (container == grid) {
            access.execute((level, pos) -> refreshResult(level));
        }
    }

    private void refreshResult(Level level) {
        if (level.isClientSide || !(player instanceof ServerPlayer server)) {
            return;
        }
        CraftingInput input = grid.asCraftInput();
        ItemStack made = ItemStack.EMPTY;
        Optional<RecipeHolder<AssemblyRecipe>> found = level.getRecipeManager().getRecipeFor(AssemblyRecipe.TYPE.get(), input, level);
        if (found.isPresent() && result.setRecipeUsed(level, server, found.get())) {
            ItemStack assembled = found.get().value().assemble(input, level.registryAccess());
            if (assembled.isItemEnabled(level.enabledFeatures())) {
                made = assembled;
            }
        }
        result.setItem(0, made);
        setRemoteSlot(RESULT, made);
        server.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), RESULT, made));
    }

    /** effects: on the server, the grid and the weapon slot go back to the player, as a crafting table's grid does */
    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> {
            clearContainer(player, grid);
            clearContainer(player, weapon);
        });
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.WEAPONS_WORKBENCH.get());
    }

    /** Double-click gathering never takes from the result or the chip slot. */
    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != result && slot.container != chip && super.canTakeItemForPickAll(stack, slot);
    }

    /**
     * Shift-click. From the result: as many crafts as fit, into the inventory. From the grid, the
     * weapon or the chip: into the inventory. From the inventory: a chip to the chip slot, a weapon
     * that takes a fitting to the weapon slot, anything else to the grid, and failing those between
     * the inventory and the hotbar.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index == RESULT) {
            access.execute((level, pos) -> stack.getItem().onCraftedBy(stack, level, player));
            if (!moveItemStackTo(stack, INVENTORY_START, END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, before);
        } else if (index < INVENTORY_START) {
            if (!moveItemStackTo(stack, INVENTORY_START, END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (ChipItem.isChip(stack) && slots.get(CHIP).mayPlace(stack) && !slots.get(CHIP).hasItem()) {
            if (!moveItemStackTo(stack, CHIP, CHIP + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (Fittings.fittable(stack) && !slots.get(WEAPON).hasItem()) {
            if (!moveItemStackTo(stack, WEAPON, WEAPON + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, GRID_START, GRID_END, false)) {
            if (index < HOTBAR_START) {
                if (!moveItemStackTo(stack, HOTBAR_START, END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, INVENTORY_START, HOTBAR_START, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == before.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        if (index == RESULT) {
            player.drop(stack, false);
        }
        return before;
    }

    /** The weapon slot: one weapon that takes a fitting. */
    private final class WeaponSlot extends Slot {
        WeaponSlot() {
            super(weapon, 0, WEAPON_X, WEAPON_Y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return Fittings.fittable(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(InventoryMenu.BLOCK_ATLAS, EMPTY_LAUNCHER);
        }
    }

    /** The chip slot: open only under a weapon that takes a chip, and then one lock-on chip. */
    private final class ChipSlot extends Slot {
        ChipSlot() {
            super(chip, 0, CHIP_X, CHIP_Y);
        }

        @Override
        public boolean isActive() {
            return chipSlotOpen();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return chipSlotOpen() && ChipItem.isChip(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(InventoryMenu.BLOCK_ATLAS, EMPTY_CHIP);
        }
    }

    /**
     * The chip slot's container: a view of the chip fitted to the launcher in the weapon slot.
     * Reads hand out a copy, since the fitted chip is a component value shared by copies of the
     * stack; writes refit the launcher. Empty, and refusing writes, with no launcher there.
     */
    private final class ChipView implements Container {
        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return getItem(0).isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return LauncherItem.chip(weapon.getItem(0));
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack fitted = getItem(0);
            if (fitted.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            setItem(0, ItemStack.EMPTY);
            return fitted;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            ItemStack launcher = weapon.getItem(0);
            if (!Fittings.takesChip(launcher)) {
                return;
            }
            LauncherItem.fit(launcher, stack);
            weapon.setChanged();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            weapon.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            setItem(0, ItemStack.EMPTY);
        }
    }

    /**
     * The result slot of the bench: the crafting table's, with the leftovers of the bench's own
     * recipes left in the grid instead of the table's.
     */
    private static final class AssemblyResultSlot extends ResultSlot {
        private final CraftingContainer grid;
        private final Player player;

        AssemblyResultSlot(Player player, CraftingContainer grid, Container result, int slot, int x, int y) {
            super(player, grid, result, slot, x, y);
            this.grid = grid;
            this.player = player;
        }

        /** effects: one of each ingredient taken from the grid, leftovers put back or handed over, as the table's slot does */
        @Override
        public void onTake(Player player, ItemStack stack) {
            checkTakeAchievements(stack);
            CraftingInput.Positioned positioned = grid.asPositionedCraftInput();
            CraftingInput input = positioned.input();
            CommonHooks.setCraftingPlayer(player);
            NonNullList<ItemStack> leftovers = player.level().getRecipeManager()
                    .getRemainingItemsFor(AssemblyRecipe.TYPE.get(), input, player.level());
            CommonHooks.setCraftingPlayer(null);
            for (int row = 0; row < input.height(); row++) {
                for (int col = 0; col < input.width(); col++) {
                    int cell = col + positioned.left() + (row + positioned.top()) * grid.getWidth();
                    ItemStack there = grid.getItem(cell);
                    ItemStack left = leftovers.get(col + row * input.width());
                    if (!there.isEmpty()) {
                        grid.removeItem(cell, 1);
                        there = grid.getItem(cell);
                    }
                    if (left.isEmpty()) {
                        continue;
                    }
                    if (there.isEmpty()) {
                        grid.setItem(cell, left);
                    } else if (ItemStack.isSameItemSameComponents(there, left)) {
                        left.grow(there.getCount());
                        grid.setItem(cell, left);
                    } else if (!this.player.getInventory().add(left)) {
                        this.player.drop(left, false);
                    }
                }
            }
        }
    }
}
