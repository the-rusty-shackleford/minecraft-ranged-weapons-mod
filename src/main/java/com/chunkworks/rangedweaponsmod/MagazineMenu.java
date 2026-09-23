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

import com.chunkworks.rangedweaponsmod.domain.Magazine;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The screen a magazine is filled in: a row of slots for the runs of rounds,
 * first to fire on the left, over the player's inventory, with a button that
 * fills the magazine from the inventory.
 *
 * <p>The runs live on the magazine's stack, in the hand it was opened from;
 * the slots are a view of them. Every change to a slot is written straight
 * back to the magazine, so closing the screen (however it closes) loses
 * nothing and drops nothing -- there is no container to empty. The
 * magazine's own inventory slot is locked while the screen is open, so the
 * thing being filled cannot be moved out from under it.
 *
 * <p>The screen fills exactly one magazine. Magazines stack by load, so a
 * write to a stack of several would load them all from one handful of
 * rounds; before the screen opens, and again before every write on the
 * server (a number-key swap or a pickup can put a stack in the hand while
 * the screen is open), the rest of the stack is set aside
 * ({@link #setAside}), leaving one in the hand.
 *
 * <p>Slot capacity is the magazine's, not the stack's: the last slot that
 * would take a round past the capacity takes only what fits.
 */
public final class MagazineMenu extends AbstractContainerMenu {

    /** How many runs the screen shows; a run longer than a stack takes two. */
    public static final int RUN_SLOTS = 5;
    /** The button id the client sends to fill from the inventory. */
    public static final int FILL_BUTTON = 0;

    static final int RUN_SLOT_X = 44;
    static final int RUN_SLOT_Y = 37;

    private final Inventory inventory;
    private final InteractionHand hand;
    private final SimpleContainer runs = new SimpleContainer(RUN_SLOTS);
    private boolean loading;

    /**
     * effects: on the server, opens this menu for the magazine in
     * {@code hand}. With a stack of several in the hand, one stays there to
     * be filled and the rest are set aside in the inventory first; if they
     * do not fit, nothing opens and the player is told. Returns whether it
     * opened.
     */
    public static boolean open(Player player, InteractionHand hand) {
        if (!setAside(player, hand, false)) {
            player.displayClientMessage(Component.translatable("screen.rangedweaponsmod.magazine.no_room"), true);
            return false;
        }
        ItemStack magazine = player.getItemInHand(hand);
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new MagazineMenu(id, inv, hand), magazine.getHoverName()),
                buf -> buf.writeEnum(hand));
        return true;
    }

    /**
     * effects: leaves one magazine of the stack in {@code hand} there and
     * puts the rest away: onto like stacks with room, then into a free
     * slot, and with none free, on the ground at the player's feet if
     * {@code mayDrop}, else back in the hand. Nothing with one or none in
     * the hand. Returns whether the hand holds at most one magazine now.
     */
    static boolean setAside(Player player, InteractionHand hand, boolean mayDrop) {
        ItemStack held = player.getItemInHand(hand);
        if (held.getCount() <= 1) {
            return true;
        }
        Inventory inventory = player.getInventory();
        int handSlot = hand == InteractionHand.MAIN_HAND ? inventory.selected : -1;
        ItemStack rest = held.split(held.getCount() - 1);
        for (int slot = 0; slot < inventory.items.size() && !rest.isEmpty(); slot++) {
            ItemStack there = inventory.items.get(slot);
            if (slot == handSlot || !ItemStack.isSameItemSameComponents(there, rest)) {
                continue;
            }
            int moved = Math.min(rest.getCount(), there.getMaxStackSize() - there.getCount());
            there.grow(moved);
            rest.shrink(moved);
        }
        if (rest.isEmpty()) {
            return true;
        }
        int free = inventory.getFreeSlot();
        if (free >= 0) {
            inventory.setItem(free, rest);
            return true;
        }
        if (mayDrop) {
            player.drop(rest, false);
            return true;
        }
        held.grow(rest.getCount());
        return false;
    }

    /** The client's constructor: the hand travels with the open-screen packet. */
    public static MagazineMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buf) {
        return new MagazineMenu(id, inventory, buf.readEnum(InteractionHand.class));
    }

    public MagazineMenu(int id, Inventory inventory, InteractionHand hand) {
        super(ModData.MAGAZINE_MENU.get(), id);
        this.inventory = inventory;
        this.hand = hand;
        load();
        runs.addListener(container -> {
            if (!loading) {
                writeBack();
            }
        });
        for (int i = 0; i < RUN_SLOTS; i++) {
            addSlot(new RunSlot(runs, i, RUN_SLOT_X + 18 * i, RUN_SLOT_Y));
        }
        int locked = hand == InteractionHand.MAIN_HAND ? inventory.selected : -1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(inventorySlot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18, locked));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(inventorySlot(inventory, col, 8 + col * 18, 142, locked));
        }
    }

    private static Slot inventorySlot(Inventory inventory, int index, int x, int y, int locked) {
        if (index != locked) {
            return new Slot(inventory, index, x, y);
        }
        return new Slot(inventory, index, x, y) {
            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        };
    }

    /** The magazine being filled: the stack in the hand this was opened from. */
    public ItemStack magazine() {
        return inventory.player.getItemInHand(hand);
    }

    private MagazineItem item() {
        return magazine().getItem() instanceof MagazineItem item ? item : null;
    }

    /** effects: returns what the magazine holds right now, as the domain sees it */
    public Magazine<Item> contents() {
        return Magazines.contents(magazine());
    }

    /** effects: fills the run slots from the magazine's contents, a run longer than a stack split over two */
    private void load() {
        loading = true;
        try {
            runs.clearContent();
            int slot = 0;
            for (Magazine.Segment<Item> segment : contents().segments()) {
                int left = segment.count();
                while (left > 0 && slot < RUN_SLOTS) {
                    int count = Math.min(left, segment.round().getDefaultMaxStackSize());
                    runs.setItem(slot++, new ItemStack(segment.round(), count));
                    left -= count;
                }
            }
        } finally {
            loading = false;
        }
    }

    /**
     * effects: writes the run slots back to the magazine, in order, empties
     * skipped, same kinds merged -- to one magazine: on the server, the rest
     * of a stack in the hand is set aside first
     */
    private void writeBack() {
        MagazineItem item = item();
        if (item == null) {
            return;
        }
        if (!inventory.player.level().isClientSide) {
            setAside(inventory.player, hand, true);
        }
        Magazine<Item> magazine = Magazine.empty(item.capacity());
        for (int i = 0; i < RUN_SLOTS; i++) {
            ItemStack stack = runs.getItem(i);
            if (!stack.isEmpty()) {
                magazine = magazine.push(stack.getItem(), Math.min(stack.getCount(), magazine.space()));
            }
        }
        Magazines.setContents(magazine(), magazine);
    }

    /** effects: returns how many rounds the run slots hold, {@code except} one slot left out */
    private int roundsInRuns(int except) {
        int total = 0;
        for (int i = 0; i < RUN_SLOTS; i++) {
            if (i != except) {
                total += runs.getItem(i).getCount();
            }
        }
        return total;
    }

    /** A run of rounds: takes only the magazine's family, and only what still fits. */
    private final class RunSlot extends Slot {
        RunSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            MagazineItem item = item();
            return item != null && item.accepts(stack);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            MagazineItem item = item();
            if (item == null) {
                return 0;
            }
            int room = item.capacity() - roundsInRuns(getContainerSlot());
            return Math.max(0, Math.min(stack.getMaxStackSize(), room));
        }

        @Override
        public int getMaxStackSize() {
            MagazineItem item = item();
            return item == null ? 0 : Math.max(0, Math.min(Item.ABSOLUTE_MAX_STACK_SIZE, item.capacity() - roundsInRuns(getContainerSlot())));
        }
    }

    /**
     * The fill button.
     *
     * <p>effects: on the server, tops the magazine up from the player's
     * inventory -- the accepted rounds in inventory order, hotbar first, the
     * first kind first, then the next -- as far as the capacity and the row
     * of slots allow, taking the rounds out of the inventory; returns
     * whether anything was loaded. Fills one magazine: the rest of a stack
     * in the hand is set aside first.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != FILL_BUTTON || item() == null || player.level().isClientSide) {
            return false;
        }
        setAside(player, hand, true);
        MagazineItem item = item();
        Magazine<Item> magazine = contents();
        List<Item> order = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (item.accepts(candidate) && !order.contains(candidate.getItem())) {
                order.add(candidate.getItem());
            }
        }
        boolean loaded = false;
        for (Item round : order) {
            if (magazine.isFull()) {
                break;
            }
            int count = Math.min(PlayerGunnery.countItem(player, round), magazine.space());
            Magazine<Item> next = magazine.push(round, count);
            if (count <= 0 || stacksOf(next) > RUN_SLOTS) {
                continue;   // this kind would need a slot the row does not have
            }
            PlayerGunnery.takeItem(player, round, count);
            magazine = next;
            loaded = true;
        }
        if (loaded) {
            Magazines.setContents(magazine(), magazine);
            load();
            broadcastChanges();
        }
        return loaded;
    }

    /** effects: returns how many run slots {@code magazine} needs */
    private static int stacksOf(Magazine<Item> magazine) {
        int stacks = 0;
        for (Magazine.Segment<Item> segment : magazine.segments()) {
            int max = segment.round().getDefaultMaxStackSize();
            stacks += (segment.count() + max - 1) / max;
        }
        return stacks;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < RUN_SLOTS) {
            if (!moveItemStackTo(stack, RUN_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, RUN_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return stack.getCount() == before.getCount() ? ItemStack.EMPTY : before;
    }

    @Override
    public boolean stillValid(Player player) {
        return item() != null;
    }
}
