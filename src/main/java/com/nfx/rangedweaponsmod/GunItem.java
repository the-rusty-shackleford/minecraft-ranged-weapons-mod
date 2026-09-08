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
import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.RangedWeapons;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A gun a player can hold. Deliberately almost nothing: what the gun
 * <em>is</em> lives in its {@code rangedweapons:weapons} profile, how it is
 * <em>operated</em> lives in the protocol's weapon for it, and how it is
 * <em>fired by a player</em> lives in {@link PlayerGunnery}. This class
 * marks an item as one the trigger works on and shows the load on hover.
 *
 * <p>None of vanilla's use-item machinery is involved: right-click is taken
 * over on the client and turned into trigger state, because vanilla repeats
 * a use at most every four ticks and slows a player using an item to a
 * fifth of their speed, neither of which is a gun.
 */
public final class GunItem extends Item {

    /** How the gun is carried in third person. */
    public enum Grip {
        /** One hand, the way any item is held. */
        ONE_HANDED,
        /** Both hands on it, the way a crossbow is carried. */
        TWO_HANDED
    }

    private final Grip grip;
    private final boolean scoped;

    /**
     * @param properties the item's
     * @param grip       how it is carried
     * @param scoped     whether aiming down its sights looks through a scope: a real
     *                   magnification and the scope's mask, rather than a slight lean in
     */
    public GunItem(Properties properties, Grip grip, boolean scoped) {
        super(properties);
        this.grip = grip;
        this.scoped = scoped;
    }

    /** How this gun is carried in third person. */
    public Grip grip() {
        return grip;
    }

    /** Whether aiming down its sights looks through a scope. */
    public boolean scoped() {
        return scoped;
    }

    /** effects: returns whether {@code stack} is a gun this mod's trigger works on */
    public static boolean isGun(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof GunItem;
    }

    /** effects: returns whether {@code stack} is a gun with a scope */
    public static boolean isScoped(ItemStack stack) {
        return stack.getItem() instanceof GunItem gun && gun.scoped();
    }

    /**
     * The use key is not the trigger: the trigger is the attack key, taken
     * by the client's {@code TriggerInput}, so that right-clicking a chest,
     * a door or a villager with a gun in hand does what it does with
     * anything else in hand. A gun's own use therefore passes, and passes
     * without starting a "use" -- vanilla's item-use path drops the hand out
     * of view and raises it again, which is the one thing that must never
     * happen at a machine gun's cadence.
     *
     * <p>effects: returns pass for either hand
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    /**
     * Vanilla plays the re-equip animation -- the hand drops out of view and
     * comes back -- whenever the held stack's data changes, and every shot
     * changes it: a round spent, a point of wear. At a machine gun's cadence
     * that is a hand that never stops dropping. A spent round is the same
     * gun. Only a different gun, a different slot, or the magazine going
     * out or coming in re-equips; the last gives the reload its dip.
     */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged
                || oldStack.getItem() != newStack.getItem()
                || oldStack.has(ModData.RELOAD.get()) != newStack.has(ModData.RELOAD.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RangedWeapon weapon = RangedWeapons.resolve(stack);
        if (weapon == null) {
            tooltip.add(Component.translatable("item.rangedweaponsmod.gun.no_profile").withStyle(ChatFormatting.RED));
            return;
        }
        tooltip.add(Component.translatable("item.rangedweaponsmod.gun.rounds", weapon.rounds(stack), weapon.capacity(stack))
                .withStyle(ChatFormatting.GRAY));
        weapon.profile().ammoFamily().ifPresent(family -> tooltip.add(
                Component.translatable("item.rangedweaponsmod.gun.takes", AmmoFamilies.displayName(family))
                        .withStyle(ChatFormatting.DARK_GRAY)));
    }
}
