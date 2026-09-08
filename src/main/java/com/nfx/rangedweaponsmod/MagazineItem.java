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

import com.nfx.rangedweapons.api.AmmoFamilies;
import com.nfx.rangedweaponsmod.domain.Magazine;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A detachable magazine: an item that holds rounds of one family, in the
 * order they were loaded, and goes into a gun whole.
 *
 * <p>Capacity and family are the item's, so an extended or a double-stack
 * magazine is another item with other numbers -- the guns accept any
 * magazine of their family. What is loaded is a {@link MagazineContents}
 * component. A label is vanilla's custom name (an anvil), a colour is
 * vanilla's dye (the item is tagged dyeable); both travel with the
 * magazine into the gun and back out.
 *
 * <p>Using it (right-click, in either hand) opens the screen it is filled
 * in: a row of slots for the runs of rounds, and a button that fills it from
 * the inventory.
 */
public final class MagazineItem extends Item {

    private final TagKey<Item> family;
    private final int capacity;

    /**
     * @param properties the item's; the stack size is forced to one, since
     *                   two magazines with different loads cannot share a stack
     * @param family     the rounds it takes
     * @param capacity   how many, at least one
     */
    public MagazineItem(Properties properties, TagKey<Item> family, int capacity) {
        super(properties.stacksTo(1));
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1, was " + capacity);
        }
        this.family = family;
        this.capacity = capacity;
    }

    public TagKey<Item> family() {
        return family;
    }

    public int capacity() {
        return capacity;
    }

    /** effects: returns whether {@code round} is a round this magazine takes */
    public boolean accepts(ItemStack round) {
        return !round.isEmpty() && round.is(family);
    }

    /** effects: returns whether {@code stack} is a magazine */
    public static boolean isMagazine(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof MagazineItem;
    }

    /**
     * effects: on the server, opens the magazine screen for the magazine in
     * {@code hand}; returns success on either side so the hand swings once
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            MagazineMenu.open(player, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return Magazines.contents(stack).rounds() > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        Magazine<Item> magazine = Magazines.contents(stack);
        return Math.round(13.0f * magazine.rounds() / magazine.capacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xE0C060;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Magazine<Item> magazine = Magazines.contents(stack);
        tooltip.add(Component.translatable("item.rangedweaponsmod.magazine.holds", capacity, AmmoFamilies.displayName(family))
                .withStyle(ChatFormatting.DARK_GRAY));
        if (magazine.isEmpty()) {
            tooltip.add(Component.translatable("item.rangedweaponsmod.magazine.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.rangedweaponsmod.magazine.loaded", magazine.rounds(), capacity)
                .withStyle(ChatFormatting.GRAY));
        // The runs in firing order: what comes out first is listed first.
        for (Magazine.Segment<Item> segment : magazine.segments()) {
            tooltip.add(Component.literal("  " + segment.count() + " × ").append(segment.round().getDescription())
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
