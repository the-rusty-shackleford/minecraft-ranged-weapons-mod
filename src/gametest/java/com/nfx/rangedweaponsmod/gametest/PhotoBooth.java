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
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import com.nfx.rangedweaponsmod.domain.Magazine;
import com.nfx.rangedweaponsmod.Magazines;
import com.nfx.rangedweaponsmod.MagazineMenu;
import com.nfx.rangedweaponsmod.client.ArmPoses;
import com.nfx.rangedweaponsmod.ModTabs;
import com.nfx.rangedweapons.api.RangedWeapon;
import com.nfx.rangedweapons.api.RangedWeapons;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import com.nfx.rangedweaponsmod.RangedWeaponsMod;
import com.nfx.rangedweaponsmod.client.ClientConfig;
import com.nfx.rangedweaponsmod.client.Keys;
import com.nfx.rangedweaponsmod.client.RecoilCamera;
import com.nfx.rangedweaponsmod.net.ShotFiredPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.resources.ResourceLocation;
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

    /**
     * The calibration items are carried like the gun being calibrated, or
     * their photos answer the wrong question: two-handed unless
     * {@code -Drangedweaponsmod.booth.pose=one}, which is the pistol's
     * one-armed aim.
     */
    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        boolean one = "one".equals(System.getProperty("rangedweaponsmod.booth.pose"));
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return one ? ArmPoses.PISTOL.getValue() : HumanoidModel.ArmPose.CROSSBOW_HOLD;
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
            player.getInventory().setItem(2, new ItemStack(ModItems.SMALL_ROUND.get(), 32));
            player.getInventory().setItem(3, new ItemStack(ModItems.SHELL.get(), 16));
            player.getInventory().setItem(4, new ItemStack(ModItems.PISTOL.get()));
            player.getInventory().setItem(5, new ItemStack(ModItems.SHOTGUN.get()));
            player.getInventory().setItem(6, new ItemStack(ModItems.RIFLE.get()));
            player.getInventory().setItem(7, new ItemStack(ModItems.SCOPED_RIFLE.get()));
            hold(player, ModItems.MACHINE_GUN.get());
            firstPerson.run();
        }));
        s.add(new Step(t[0] += SETTLE, () -> shoot(mc, "booth-gun-first")));
        // Each term of the in-hand kick alone, under a kick big enough to
        // read (5 degrees, recovery off), so a frame answers one question:
        // which way does this term move the gun? Then the shipped mix at the
        // plateau a machine gun reaches.
        kickFrame(s, t, mc, "booth-kick-camera-only", 0.0, 0.0, 0.0, 0.0);
        kickFrame(s, t, mc, "booth-kick-back", 0.1, 0.0, 0.0, 0.0);
        kickFrame(s, t, mc, "booth-kick-rise", 0.0, 0.1, 0.0, 0.0);
        kickFrame(s, t, mc, "booth-kick-pitch", 0.0, 0.0, 8.0, 0.0);
        kickFrame(s, t, mc, "booth-kick-yaw", 0.0, 0.0, 0.0, 8.0);
        s.add(new Step(t[0] += 5, () -> {
            shippedKick();
            RecoilCamera.reset();
        }));
        s.add(new Step(t[0] += 1, () -> RecoilCamera.onShotFired(new ShotFiredPayload(0.55f, 0.25f, 0.35f))));
        s.add(new Step(t[0] += 3, () -> RecoilCamera.onShotFired(new ShotFiredPayload(0.55f, 0.25f, 0.35f))));
        s.add(new Step(t[0] += 3, () -> RecoilCamera.onShotFired(new ShotFiredPayload(0.55f, 0.25f, 0.35f))));
        s.add(new Step(t[0] += 1, () -> shoot(mc, "booth-gun-first-kick")));

        // A real burst, through the real path: survival, a loaded gun, the
        // trigger held by the same message the client sends, rounds spent
        // on the integrated server and synced back. What the player sees.
        s.add(new Step(t[0] += 20, () -> onServer(mc, sp -> {
            shippedKick();
            sp.setGameMode(GameType.SURVIVAL);
            // A stone block three blocks ahead at eye height: the burst hits
            // it, so the frames show debris, sparks and cracks, and the
            // fifth round takes it down.
            BlockPos ahead = BlockPos.containing(sp.getEyePosition().add(0.0, 0.0, 3.0));
            sp.serverLevel().setBlock(ahead, Blocks.STONE.defaultBlockState(), 3);
            ItemStack gun = new ItemStack(ModItems.MACHINE_GUN.get());
            RangedWeapon weapon = RangedWeapons.resolve(gun);
            if (weapon != null) {
                weapon.load(gun, weapon.capacity(gun));
            }
            sp.setItemInHand(InteractionHand.MAIN_HAND, gun);
        })));
        s.add(new Step(t[0] += SETTLE, () -> shoot(mc, "booth-burst-0")));
        // The attack key itself -- left click -- pressed and held the way a
        // mouse does it, so every step of the client's click path runs, not
        // our message. Held on a stone block in survival: were the click not
        // taken, the block would be mined, and the cracks in the frames would
        // be the pick's, not the bullets'.
        s.add(new Step(t[0] += 1, () -> {
            KeyMapping.set(mc.options.keyAttack.getKey(), true);
            KeyMapping.click(mc.options.keyAttack.getKey());
        }));
        s.add(new Step(t[0] += 2, () -> shoot(mc, "booth-burst-1")));
        s.add(new Step(t[0] += 3, () -> shoot(mc, "booth-burst-2")));
        s.add(new Step(t[0] += 3, () -> shoot(mc, "booth-burst-3")));
        s.add(new Step(t[0] += 3, () -> shoot(mc, "booth-burst-4")));
        s.add(new Step(t[0] += 1, () -> KeyMapping.set(mc.options.keyAttack.getKey(), false)));
        s.add(new Step(t[0] += 10, () -> onServer(mc, sp -> {
            sp.setGameMode(GameType.CREATIVE);
            BlockPos ahead = BlockPos.containing(sp.getEyePosition().add(0.0, 0.0, 3.0));
            sp.serverLevel().setBlock(ahead, Blocks.AIR.defaultBlockState(), 3);
        })));
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

        // The parts a gun is assembled from, across the top inventory row,
        // photographed in the survival inventory screen (creative's shows its
        // tabs instead) beside the guns in the hotbar.
        s.add(new Step(t[0] += 1, () -> onServer(mc, sp -> {
            sp.setGameMode(GameType.SURVIVAL);
            int slot = 9;
            for (var part : ModItems.parts()) {
                sp.getInventory().setItem(slot++, new ItemStack(part));
            }
        })));
        s.add(new Step(t[0] += 5, () -> mc.setScreen(new InventoryScreen(player))));
        s.add(new Step(t[0] += SETTLE, () -> shoot(mc, "booth-parts-inventory")));
        s.add(new Step(t[0] += 1, () -> mc.setScreen(null)));

        // Magazines: the HUD with a labelled, dyed magazine in the pistol,
        // survival so the label shows; then the screen a rifle magazine is
        // filled in, holding a mixed load, with rounds in the inventory for
        // the fill button to take.
        s.add(new Step(t[0] += 1, () -> onServer(mc, sp -> {
            ItemStack pistol = new ItemStack(ModItems.PISTOL.get());
            ItemStack magazine = new ItemStack(ModItems.PISTOL_MAGAZINE.get());
            magazine.set(DataComponents.CUSTOM_NAME, Component.literal("Bedside"));
            magazine.set(DataComponents.DYED_COLOR, new DyedItemColor(0xC0392B, true));
            Magazines.setContents(magazine, Magazine.<Item>empty(15).push(ModItems.SMALL_ROUND.get(), 9));
            RangedWeapon weapon = RangedWeapons.resolve(pistol);
            if (weapon != null) {
                Magazines.insert(pistol, weapon, magazine);
            }
            sp.setItemInHand(InteractionHand.MAIN_HAND, pistol);
            sp.getInventory().setItem(1, magazine.copy());
        })));
        s.add(new Step(t[0] += SETTLE, () -> shoot(mc, "booth-magazine-hud")));
        s.add(new Step(t[0] += 1, () -> onServer(mc, sp -> {
            ItemStack magazine = new ItemStack(ModItems.RIFLE_MAGAZINE.get());
            Magazines.setContents(magazine, Magazine.<Item>empty(30).push(ModItems.ROUND.get(), 12).push(Items.IRON_NUGGET, 3));
            sp.setItemInHand(InteractionHand.MAIN_HAND, magazine);
            sp.getInventory().setItem(9, new ItemStack(ModItems.ROUND.get(), 40));
            MagazineMenu.open(sp, InteractionHand.MAIN_HAND);
        })));
        s.add(new Step(t[0] += SETTLE, () -> shoot(mc, "booth-magazine-screen")));
        s.add(new Step(t[0] += 1, () -> {
            if (mc.player != null) {
                mc.player.closeContainer();
            }
            onServer(mc, sp -> sp.setGameMode(GameType.CREATIVE));
        }));

        // The mod's own creative tab, as a player finds it.
        s.add(new Step(t[0] += 10, () -> {
            CreativeModeInventoryScreen screen = new CreativeModeInventoryScreen(player, player.connection.enabledFeatures(), false);
            mc.setScreen(screen);
            selectTab(screen, ModTabs.RANGED_WEAPONS.get());
            // What the screen has to work with: the loader's sorted tabs, and
            // whether ours came out with items (a tab without any is hidden).
            var sorted = net.neoforged.neoforge.common.CreativeModeTabRegistry.getSortedCreativeModeTabs();
            RangedWeaponsMod.LOGGER.info("photo booth: {} sorted tabs: {}", sorted.size(),
                    sorted.stream().map(tab -> BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab) + (tab.hasAnyItems() ? "" : "(empty)")).toList());
            RangedWeaponsMod.LOGGER.info("photo booth: ours has {} items, shown={}", ModTabs.RANGED_WEAPONS.get().getDisplayItems().size(),
                    sorted.contains(ModTabs.RANGED_WEAPONS.get()) && ModTabs.RANGED_WEAPONS.get().hasAnyItems());
        }));
        s.add(new Step(t[0] += SETTLE, () -> shoot(mc, "booth-creative-tab")));
        s.add(new Step(t[0] += 1, () -> mc.setScreen(null)));

        // Every other gun: first person, and third person from the front and
        // from behind -- the view a player checks their own hold in.
        for (var gun : List.of(ModItems.PISTOL, ModItems.SHOTGUN, ModItems.RIFLE, ModItems.SCOPED_RIFLE)) {
            String label = "booth-" + gun.getId().getPath();
            s.add(new Step(t[0] += 5, () -> {
                hold(player, gun.get());
                firstPerson.run();
            }));
            s.add(new Step(t[0] += SETTLE, () -> shoot(mc, label + "-first")));
            s.add(new Step(t[0] += 1, thirdFront));
            s.add(new Step(t[0] += SETTLE, () -> shoot(mc, label + "-third")));
            s.add(new Step(t[0] += 1, thirdBack));
            s.add(new Step(t[0] += SETTLE, () -> shoot(mc, label + "-third-back")));
            // From the right side: the head turns west and the front camera
            // follows it, while the body (which trails the head by up to 50
            // degrees) stays turned toward the south. The hand and what it
            // holds are nearest the camera.
            s.add(new Step(t[0] += 1, () -> {
                thirdFront.run();
                player.setYRot(-90.0f);
                player.setYHeadRot(-90.0f);
                player.setYBodyRot(-40.0f);
            }));
            s.add(new Step(t[0] += SETTLE, () -> shoot(mc, label + "-third-side")));
            s.add(new Step(t[0] += 1, () -> {
                player.setYRot(0.0f);
                player.setYHeadRot(0.0f);
                player.setYBodyRot(0.0f);
            }));
        }
        // A reference item from another mod, for comparison by eye only
        // (-Dbooth.reference=<item id>, its jar in run/booth/mods): the same
        // views as our guns get. Nothing of it is read but the pictures.
        String reference = System.getProperty("rangedweaponsmod.booth.reference", "");
        if (!reference.isEmpty() && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(reference))) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(reference));
            String label = "booth-reference-" + ResourceLocation.parse(reference).getPath();
            s.add(new Step(t[0] += 5, () -> {
                hold(player, item);
                firstPerson.run();
            }));
            s.add(new Step(t[0] += SETTLE, () -> shoot(mc, label + "-first")));
            s.add(new Step(t[0] += 1, thirdFront));
            s.add(new Step(t[0] += SETTLE, () -> shoot(mc, label + "-third")));
            s.add(new Step(t[0] += 1, () -> {
                thirdFront.run();
                player.setYRot(-90.0f);
                player.setYHeadRot(-90.0f);
                player.setYBodyRot(-40.0f);
            }));
            s.add(new Step(t[0] += SETTLE, () -> shoot(mc, label + "-third-side")));
            s.add(new Step(t[0] += 1, () -> {
                player.setYRot(0.0f);
                player.setYHeadRot(0.0f);
                player.setYBodyRot(0.0f);
            }));
        }

        // The scoped rifle, aimed: the mask, the zoom, the gun out of the way.
        s.add(new Step(t[0] += 5, () -> {
            hold(player, ModItems.SCOPED_RIFLE.get());
            firstPerson.run();
            KeyMapping.set(Keys.AIM.getKey(), true);
        }));
        s.add(new Step(t[0] += SETTLE, () -> shoot(mc, "booth-scoped_rifle-aimed")));
        s.add(new Step(t[0] += 1, () -> KeyMapping.set(Keys.AIM.getKey(), false)));
        s.add(new Step(t[0] += 10, () -> mc.setScreen(new InventoryScreen(player))));
        s.add(new Step(t[0] += SETTLE, () -> shoot(mc, "booth-lineup-inventory")));
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

    /** The config's own defaults: what ships. */
    private static void shippedKick() {
        ClientConfig.MODEL_BACK.set(ClientConfig.MODEL_BACK.getDefault());
        ClientConfig.MODEL_RISE.set(ClientConfig.MODEL_RISE.getDefault());
        ClientConfig.MODEL_PITCH.set(ClientConfig.MODEL_PITCH.getDefault());
        ClientConfig.MODEL_YAW.set(ClientConfig.MODEL_YAW.getDefault());
    }

    /** One frame under a 5-degree kick all but held (1% recovery) with only the given in-hand terms. */
    private static void kickFrame(List<Step> s, int[] t, Minecraft mc, String name, double back, double rise, double pitch, double yaw) {
        s.add(new Step(t[0] += 5, () -> {
            ClientConfig.MODEL_BACK.set(back); ClientConfig.MODEL_RISE.set(rise);
            ClientConfig.MODEL_PITCH.set(pitch); ClientConfig.MODEL_YAW.set(yaw);
            RecoilCamera.reset();
            RecoilCamera.onShotFired(new ShotFiredPayload(5.0f, 5.0f, 0.01f));
        }));
        s.add(new Step(t[0] += 2, () -> shoot(mc, name)));
    }

    /** Selects {@code tab} on the creative screen; the game keeps the method private, and a booth is the place to reach past that. */
    private static void selectTab(CreativeModeInventoryScreen screen, CreativeModeTab tab) {
        try {
            var method = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
            method.setAccessible(true);
            method.invoke(screen, tab);
        } catch (ReflectiveOperationException e) {
            RangedWeaponsMod.LOGGER.warn("photo booth: could not select the creative tab", e);
        }
    }

    /** Runs {@code action} on the integrated server's thread for this player. */
    private static void onServer(Minecraft mc, java.util.function.Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            RangedWeaponsMod.LOGGER.warn("photo booth: no integrated server; burst skipped");
            return;
        }
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp != null) {
                action.accept(sp);
            }
        });
    }

    private static void hold(LocalPlayer player, Item item) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
    }

    private static void shoot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                message -> RangedWeaponsMod.LOGGER.info("photo booth: {}", message.getString()));
    }
}
