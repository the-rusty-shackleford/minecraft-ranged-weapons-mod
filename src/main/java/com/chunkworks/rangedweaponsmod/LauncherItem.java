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

import com.chunkworks.rangedweaponsmod.domain.Chip;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The rocket launcher (D-0028): a gun in every way the trigger, the reload and the counter care
 * about -- two hands, one rocket in its tube, fired on the attack key -- whose sight, raised by
 * the use key or the aim key, is a seeker ({@link Seeking}). Rockets are {@link Rocket}s, launched
 * by {@link LauncherWeapon}.
 *
 * <p>The seeker locks only with a lock-on chip fitted at the weapons workbench (D-0029), carried
 * on the stack as {@link ModData#CHIP}; without one the launcher fires straight. Each guided
 * launch wears the chip by one charge. Creative needs no chip, and wears none.
 *
 * <p>Immutable, as items are.
 */
public final class LauncherItem extends GunItem {

    /**
     * @param properties the item's
     */
    public LauncherItem(Properties properties) {
        super(properties, Grip.TWO_HANDED, false, Feed.INTERNAL);
    }

    /** effects: returns whether {@code stack} is a launcher */
    public static boolean isLauncher(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof LauncherItem;
    }

    /** effects: returns the chip fitted to {@code launcher}, a copy of it; empty when none is */
    public static ItemStack chip(ItemStack launcher) {
        FittedChip fitted = launcher.isEmpty() ? null : launcher.get(ModData.CHIP.get());
        return fitted == null ? ItemStack.EMPTY : fitted.chip();
    }

    /**
     * requires: {@code launcher} is a launcher<br>
     * effects: fits one of {@code chip} to {@code launcher}, in place of any it had; an empty
     * {@code chip} takes the fitted one off
     */
    public static void fit(ItemStack launcher, ItemStack chip) {
        if (chip.isEmpty()) {
            launcher.remove(ModData.CHIP.get());
        } else {
            launcher.set(ModData.CHIP.get(), new FittedChip(chip));
        }
    }

    /**
     * effects: returns whether {@code player}'s seeker may lock with {@code launcher}: a launcher
     * with a chip fitted, or any launcher in creative, which needs nothing
     */
    public static boolean canLock(Player player, ItemStack launcher) {
        return isLauncher(launcher) && (player.hasInfiniteMaterials() || !chip(launcher).isEmpty());
    }

    /**
     * requires: the logical server, and a guided launch by {@code player} from {@code launcher}
     * just made<br>
     * effects: spends one charge of the fitted chip; its last burns the chip out, with the sound of
     * a breaking tool and a line on {@code player}'s action bar. Nothing in creative, or with no chip.
     */
    public static void wearChip(Player player, ItemStack launcher) {
        ItemStack chip = chip(launcher);
        if (player.hasInfiniteMaterials() || chip.isEmpty()) {
            return;
        }
        int charges = Math.max(1, chip.getMaxDamage());
        int used = Math.max(0, chip.getDamageValue());
        Optional<Chip> worn = used < charges ? new Chip(used, charges).afterGuidedLaunch() : Optional.empty();
        if (worn.isPresent()) {
            chip.setDamageValue(worn.get().used());
            fit(launcher, chip);
            return;
        }
        fit(launcher, ItemStack.EMPTY);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS,
                0.8f, 0.8f + player.getRandom().nextFloat() * 0.4f);
        player.displayClientMessage(Component.translatable("item.rangedweaponsmod.lock_on_chip.burnt_out"), true);
    }

    /** effects: the gun's lines, then the chip, and how the seeker is used */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ItemStack chip = chip(stack);
        if (chip.isEmpty()) {
            tooltip.add(Component.translatable("item.rangedweaponsmod.rocket_launcher.no_chip").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.rangedweaponsmod.rocket_launcher.fit_chip").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.rangedweaponsmod.rocket_launcher.chip", ChipItem.left(chip), chip.getMaxDamage())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.rangedweaponsmod.rocket_launcher.seeker").withStyle(ChatFormatting.GRAY));
    }
}
