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

import com.nfx.rangedweapons.api.RangedWeapons;
import com.chunkworks.rangedweaponsmod.Magazines;
import com.chunkworks.rangedweaponsmod.ModData;
import com.chunkworks.rangedweaponsmod.ModItems;
import com.chunkworks.rangedweaponsmod.Reload;
import com.chunkworks.rangedweaponsmod.ServerConfig;
import com.chunkworks.rangedweaponsmod.domain.FeedMode;
import com.chunkworks.rangedweaponsmod.domain.Magazine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.CameraType;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Real-client HUD partitions: normal and compact GUI, gear idle/browsing,
 * renamed magazine, reload progress, right/left main hand; then the server's
 * loose mode (the counter shows rounds and the kind, never "No magazine", and
 * a stack of eight boxes shows its count on the hotbar) and the switch back,
 * which adopts the loose rounds into a magazine the counter then names. The
 * real Backpacks+ and Quick Slot jars must be installed in this disposable
 * client. Screenshots are captured after actual GUI rendering, never by
 * drawing stand-in controls.
 */
@EventBusSubscriber(modid=BoothMod.MOD_ID,value=Dist.CLIENT)
public final class HudBooth {
    private static final boolean ACTIVE=Boolean.getBoolean("rangedweaponsmod.hud");
    private static int tick;
    private static int phase;
    private static int changedAt;
    private static String pending;
    private static boolean initialized;
    private static final String[] NAMES={"normal-idle","normal-gear","compact-gear","compact-left","normal-reload","loose-rounds","magazines-adopted"};
    private HudBooth() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if(!ACTIVE) return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null) return;
        tick++;
        if(!initialized) {
            initialized=true;
            if(!ModList.get().isLoaded("backpacksplus") || !ModList.get().isLoaded("quickslot")) {
                fail(mc,"Backpacks+ and Quick Slot must be present");return;
            }
            mc.options.hideGui=false;
            mc.getWindow().setWindowed(1280,960);
            mc.options.mainHand().set(net.minecraft.world.entity.HumanoidArm.RIGHT);
            mc.options.broadcastOptions();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.guiScale().set(2);mc.resizeDisplay();
            mc.player.setYRot(0);mc.player.setXRot(20);
            mc.getSingleplayerServer().execute(()->{
                var sp=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                if(sp==null) return;
                sp.setGameMode(GameType.SURVIVAL);
                sp.serverLevel().setDayTime(6000);
                sp.serverLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,sp.server);
                sp.setHealth(20);sp.getFoodData().setFoodLevel(20);
                sp.getInventory().clearContent();
                ItemStack bag=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("backpacksplus:expedition_backpack")));
                var cells=NonNullList.withSize(40,ItemStack.EMPTY);
                cells.set(36,new ItemStack(Items.IRON_SWORD));cells.set(37,new ItemStack(Items.IRON_PICKAXE));
                cells.set(38,new ItemStack(Items.TORCH,32));cells.set(39,new ItemStack(Items.APPLE,8));
                bag.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(cells));
                sp.setItemSlot(EquipmentSlot.CHEST,bag);
                ItemStack gun=new ItemStack(ModItems.MACHINE_GUN.get());
                ItemStack mag=new ItemStack(ModItems.MACHINE_GUN_BOX.get());
                mag.set(DataComponents.CUSTOM_NAME,Component.literal("Expedition reserve ammunition magazine"));
                Magazines.setContents(mag,Magazine.<Item>empty(100).push(ModItems.ROUND.get(),100));
                Magazines.insert(gun,RangedWeapons.resolve(gun),mag);
                sp.setItemSlot(EquipmentSlot.MAINHAND,gun);
            });
            changedAt=tick;
        }
        if(phase>=NAMES.length) {
            if(tick-changedAt>10) {
                browse(mc,false);
                com.chunkworks.rangedweaponsmod.RangedWeaponsMod.LOGGER.info("booth: PASS all checks ran; HUD with actual gear controls");
                mc.stop();
            }
            return;
        }
        if(pending==null && tick-changedAt>=50) pending="hud-"+NAMES[phase];
        if(tick>1000) fail(mc,"HUD capture never completed");
    }

    @SubscribeEvent
    public static void rendered(RenderGuiEvent.Post event) {
        if(!ACTIVE || pending==null) return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.screen!=null) return;
        if(mc.player.getItemBySlot(EquipmentSlot.CHEST).isEmpty() || mc.player.getMainHandItem().isEmpty()) {
            fail(mc,"server equipment did not synchronize");return;
        }
        String name=pending;pending=null;
        Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),message->{});
        com.chunkworks.rangedweaponsmod.RangedWeaponsMod.LOGGER.info("booth: PASS HUD captured {} at {}x{}",name,event.getGuiGraphics().guiWidth(),event.getGuiGraphics().guiHeight());
        phase++;changedAt=tick;
        if(phase==1) browse(mc,true);
        if(phase==2) {mc.options.guiScale().set(4);mc.resizeDisplay();}
        if(phase==3) {mc.options.mainHand().set(net.minecraft.world.entity.HumanoidArm.LEFT);mc.options.broadcastOptions();}
        if(phase==4) {
            browse(mc,false);mc.options.guiScale().set(2);mc.resizeDisplay();
            mc.getSingleplayerServer().execute(()->{
                var sp=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                sp.getMainHandItem().set(ModData.RELOAD.get(),new Reload(sp.level().getGameTime(),200));
            });
        }
        if(phase==5) {
            // Loose mode: a machine gun with forty loose rounds and no magazine, a stack of eight boxes beside it.
            mc.getSingleplayerServer().execute(()->{
                var sp=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                ServerConfig.FEED.set(FeedMode.LOOSE);
                ItemStack gun=new ItemStack(ModItems.MACHINE_GUN.get());
                RangedWeapons.resolve(gun).load(gun,40,ModItems.ROUND.get());
                sp.setItemSlot(EquipmentSlot.MAINHAND,gun);
                sp.getInventory().setItem(1,new ItemStack(ModItems.MACHINE_GUN_BOX.get(),8));
            });
        }
        if(phase==6) {
            // Back to magazines: the loose rounds are adopted into a box on the gunnery's next look.
            mc.getSingleplayerServer().execute(()->ServerConfig.FEED.set(FeedMode.MAGAZINES));
        }
    }

    private static void browse(Minecraft mc,boolean down) {
        for(var key:mc.options.keyMappings) if(key.getName().equals("key.backpacksplus.gear")) key.setDown(down);
    }

    private static void fail(Minecraft mc,String message) {
        com.chunkworks.rangedweaponsmod.RangedWeaponsMod.LOGGER.error("booth: FAIL {}",message);
        browse(mc,false);mc.stop();
    }
}
