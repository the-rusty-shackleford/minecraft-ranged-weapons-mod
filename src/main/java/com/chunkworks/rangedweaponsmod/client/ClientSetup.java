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
package com.chunkworks.rangedweaponsmod.client;

import com.chunkworks.rangedweaponsmod.RangedWeaponsMod;
import com.chunkworks.rangedweaponsmod.GunItem;
import com.chunkworks.rangedweaponsmod.ModItems;
import com.nfx.rangedweapons.api.Grip;
import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.RangedWeapons;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.minecraft.world.item.component.DyedItemColor;
import com.chunkworks.rangedweaponsmod.ModData;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The client's mod-bus registrations: the reload key, the ammo counter, and
 * how a gun is held. Loaded on the client only. The loader routes each
 * event to the mod bus from its type.
 */
@EventBusSubscriber(modid = RangedWeaponsMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    /** Reads the resolved profile at use, so a datapack reload also changes the hold. */
    private static final IClientItemExtensions GUN = new IClientItemExtensions() {
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            RangedWeapon weapon = RangedWeapons.resolve(stack);
            Grip legacy = ((GunItem) stack.getItem()).grip() == GunItem.Grip.ONE_HANDED
                    ? Grip.ONE_HANDED : Grip.TWO_HANDED;
            Grip grip = weapon == null ? legacy : weapon.profile().grip().orElse(legacy);
            return grip == Grip.ONE_HANDED ? ArmPoses.PISTOL.getValue() : HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
    };

    /** The revolver alone supplies articulated geometry; its grip follows the same profile. */
    private static final IClientItemExtensions REVOLVER = new IClientItemExtensions() {
        @Override
        public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return RevolverRenderer.instance();
        }

        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return GUN.getArmPose(entity, hand, stack);
        }
    };

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(Keys.RELOAD);
        event.register(Keys.AIM);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        // Above the hotbar, so it hides with the rest of the HUD on F1.
        event.registerAbove(VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(RangedWeaponsMod.MOD_ID, "ammo"), GunHud::render);
        // Below the crosshair: the mask must not cover it.
        event.registerBelow(VanillaGuiLayers.CROSSHAIR,
                ResourceLocation.fromNamespaceAndPath(RangedWeaponsMod.MOD_ID, "scope"), Aiming::renderScope);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModData.MAGAZINE_MENU.get(), MagazineScreen::new);
    }

    /** A magazine's band takes the dye's colour; undyed, it is the band as painted. */
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 1 ? DyedItemColor.getOrDefault(stack, 0xFFFFFFFF) : 0xFFFFFFFF,
                ModItems.magazines().toArray(Item[]::new));
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(GUN, ModItems.guns().stream().filter(item -> item != ModItems.REVOLVER.get()).toArray(Item[]::new));
        event.registerItem(REVOLVER, ModItems.REVOLVER.get());
    }
}
