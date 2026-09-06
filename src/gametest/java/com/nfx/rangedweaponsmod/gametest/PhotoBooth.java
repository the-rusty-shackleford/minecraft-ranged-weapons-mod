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
package com.nfx.rangedweaponsmod.gametest;

import com.nfx.rangedweaponsmod.ModItems;
import com.nfx.rangedweaponsmod.RangedWeaponsMod;
import com.nfx.rangedweaponsmod.client.ClientConfig;
import com.nfx.rangedweaponsmod.client.RecoilCamera;
import com.nfx.rangedweaponsmod.net.ShotFiredPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.registries.DeferredItem;


import java.util.ArrayList;
import java.util.List;

/**
 * Eyes for the art: in the {@code photoBooth} dev run, once the world is
 * up, runs a scripted sequence of poses and screenshots and quits. The
 * files land in {@code run/screenshots/booth-*.png} to be looked at.
 *
 * <p>The sequence: the machine gun in first person, in third person from
 * behind and in front, mid-recoil (a synthetic kick), and in the inventory;
 * then each calibration item in first person and third person from the
 * front. Client only, active only under the
 * {@code rangedweaponsmod.photobooth} system property.
 */
// The subscriber is registered by the mod whose file it lives in: the
// gametest mod, not the main one.
@EventBusSubscriber(modid = BoothMod.MOD_ID, value = Dist.CLIENT)
public final class PhotoBooth {
    private PhotoBooth() {}

    private static final boolean ACTIVE = Boolean.getBoolean("rangedweaponsmod.photobooth");
    /** Ticks between a pose change and its photo: chunks lit, camera settled. */
    private static final int SETTLE = 30;

    private record Step(int at, Runnable action) {}

    private static List<Step> steps;
    private static int tick = 0;

    /** The calibration items are carried like the gun, or their photos answer the wrong question. */
    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }
        }, BoothMod.BOOTH_ITEMS.stream().map(DeferredItem::get).toArray(Item[]::new));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ACTIVE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        if (steps == null) {
            steps = script(mc, player);
        }
        tick++;
        for (Step step : steps) {
            if (step.at() == tick) {
                step.action().run();
            }
        }
    }

    private static List<Step> script(Minecraft mc, LocalPlayer player) {
        List<Step> s = new ArrayList<>();
        int[] t = {40};
        Runnable firstPerson = () -> mc.options.setCameraType(CameraType.FIRST_PERSON);
        Runnable thirdFront = () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        Runnable thirdBack = () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);

        s.add(new Step(t[0], () -> {
            // Face south, so third-person-front frames are comparable.
            player.setYRot(0.0f);
            player.setYHeadRot(0.0f);
            player.setYBodyRot(0.0f);
            player.setXRot(10.0f);
            player.getInventory().setItem(1, new ItemStack(ModItems.ROUND.get(), 32));
            hold(player, ModItems.MACHINE_GUN.get());
            firstPerson.run();
        }));
        s.add(new Step(t[0] += SETTLE, () -> shoot(mc, "booth-gun-first")));
        // Each term of the in-hand kick alone, under a kick big enough to
        // read (5 degrees, recovery off), so a frame answers one question:
        // which way does this term move the gun? Then the shipped mix at the
        // plateau a machine gun reaches.
        kickFrame(s, t, mc, "booth-kick-camera-only", 0.0, 0.0, 0.0);
        kickFrame(s, t, mc, "booth-kick-rise", 0.1, 0.0, 0.0);
        kickFrame(s, t, mc, "booth-kick-pitch", 0.0, 8.0, 0.0);
        kickFrame(s, t, mc, "booth-kick-yaw", 0.0, 0.0, 8.0);
        s.add(new Step(t[0] += 5, () -> {
            ClientConfig.MODEL_RISE.set(0.1); ClientConfig.MODEL_PITCH.set(6.0); ClientConfig.MODEL_YAW.set(1.0);
            RecoilCamera.reset();
        }));
        s.add(new Step(t[0] += 1, () -> RecoilCamera.onShotFired(new ShotFiredPayload(0.55f, 0.25f, 0.35f))));
        s.add(new Step(t[0] += 3, () -> RecoilCamera.onShotFired(new ShotFiredPayload(0.55f, 0.25f, 0.35f))));
        s.add(new Step(t[0] += 3, () -> RecoilCamera.onShotFired(new ShotFiredPayload(0.55f, 0.25f, 0.35f))));
        s.add(new Step(t[0] += 1, () -> shoot(mc, "booth-gun-first-kick")));
        s.add(new Step(t[0] += 20, thirdBack));
        s.add(new Step(t[0] += SETTLE, () -> shoot(mc, "booth-gun-third-back")));
        s.add(new Step(t[0] += 1, thirdFront));
        s.add(new Step(t[0] += SETTLE, () -> shoot(mc, "booth-gun-third-front")));
        s.add(new Step(t[0] += 1, () -> {
            firstPerson.run();
            mc.setScreen(new InventoryScreen(player));
        }));
        s.add(new Step(t[0] += SETTLE, () -> shoot(mc, "booth-gun-inventory")));
        s.add(new Step(t[0] += 1, () -> mc.setScreen(null)));

        char name = 'a';
        for (var item : BoothMod.BOOTH_ITEMS) {
            String label = "booth-" + name;
            s.add(new Step(t[0] += 5, () -> {
                hold(player, item.get());
                firstPerson.run();
            }));
            s.add(new Step(t[0] += SETTLE, () -> shoot(mc, label + "-first")));
            s.add(new Step(t[0] += 1, thirdFront));
            s.add(new Step(t[0] += SETTLE, () -> shoot(mc, label + "-third")));
            name++;
        }
        s.add(new Step(t[0] += 20, mc::stop));
        return s;
    }

    /** One frame under a 5-degree kick all but held (1% recovery) with only the given in-hand terms. */
    private static void kickFrame(List<Step> s, int[] t, Minecraft mc, String name, double rise, double pitch, double yaw) {
        s.add(new Step(t[0] += 5, () -> {
            ClientConfig.MODEL_RISE.set(rise); ClientConfig.MODEL_PITCH.set(pitch); ClientConfig.MODEL_YAW.set(yaw);
            RecoilCamera.reset();
            RecoilCamera.onShotFired(new ShotFiredPayload(5.0f, 5.0f, 0.01f));
        }));
        s.add(new Step(t[0] += 2, () -> shoot(mc, name)));
    }

    private static void hold(LocalPlayer player, Item item) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
    }

    private static void shoot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                message -> RangedWeaponsMod.LOGGER.info("photo booth: {}", message.getString()));
    }
}
