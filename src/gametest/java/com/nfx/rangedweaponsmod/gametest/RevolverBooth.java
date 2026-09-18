/* Copyright (C) 2026 Rusty Shackleford and nfx
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.nfx.rangedweaponsmod.gametest;

import com.mojang.authlib.GameProfile;
import com.nfx.rangedweapons.api.RangedWeapons;
import com.nfx.rangedweaponsmod.*;
import com.nfx.rangedweaponsmod.client.Keys;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.util.FakePlayer;

/** Real client gate (-PboothRevolver). Partitions: first/right and left;
 * six attack-key pulls; R reload; third-person and inventory; remote accepted
 * firing cycle and reload delivered by entity tracking. Server owns all gun
 * state; no fabricated client components. Screenshots cover the moving joints. */
@EventBusSubscriber(modid=BoothMod.MOD_ID,value=Dist.CLIENT)
public final class RevolverBooth {
    private RevolverBooth() {}
    private static final UUID ACTOR=UUID.fromString("8a9a533e-1299-43ef-95f8-fb12ac4d637f");
    private static int tick;
    private static FakePlayer actor;
    private static int shots;
    private static int nextPull=120;
    private static boolean pressed;
    private static long lastShot=Long.MIN_VALUE;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("rangedweaponsmod.revolver")) return;
        Minecraft mc=Minecraft.getInstance();
        if (mc.player==null || mc.level==null) return;
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        ++tick;
        // Wait for server acknowledgement before releasing each real key pull.
        // Fixed three-tick taps can fall wholly between rendered input polls.
        if(tick>=nextPull && tick<300 && shots<6 && !pressed) {
            attack(mc,true);pressed=true;
        }
        var cycle=mc.player.getMainHandItem().get(ModData.REVOLVER_CYCLE);
        if(tick>=120 && tick<310 && cycle!=null && cycle.firedAt()!=lastShot) {
            lastShot=cycle.firedAt(); shots++;
            attack(mc,false);pressed=false;nextPull=tick+24;
            check("local accepted shot "+shots,()->cycle.chamber()==shots%6);
        }
        if (((tick>=117 && tick<=142) || (tick>=320 && tick<=374)
                || (tick>=612 && tick<=640)) && tick%2==0) shoot(mc,"revolver-motion-"+tick);
        switch(tick) {
            case 40 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.pauseOnLostFocus=false;
                for(var key:mc.options.keyMappings) if(key.getName().equals("iris.keybind.reload"))
                    key.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL));
                KeyMapping.resetMapping();
                onServer(mc,RevolverBooth::prepare);
            }
            case 80 -> {
                mc.setScreen(null); mc.mouseHandler.grabMouse();
                mc.player.setYRot(0);mc.player.setYHeadRot(0);mc.player.setXRot(0);
            }
            case 110 -> {
                check("six rounds arrive from the server",()->rounds(mc.player)==6);
                shoot(mc,"revolver-first-right");
            }
            case 310 -> {
                check("six real key pulls empty six chambers",()->shots==6 && rounds(mc.player)==0);
                shoot(mc,"revolver-empty");
                check("empty cylinder has no automatic reload without loose ammo",()->!mc.player.getMainHandItem().has(ModData.RELOAD));
                onServer(mc,sp->sp.getInventory().setItem(1,new ItemStack(ModItems.ROUND.get(),12)));
            }
            case 320 -> KeyMapping.click(Keys.RELOAD.getKey());
            case 340 -> {
                check("R starts the cylinder reload",()->mc.player.getMainHandItem().has(ModData.RELOAD));
                shoot(mc,"revolver-reloading");
            }
            case 390 -> {
                check("R reloads six medium rounds",()->rounds(mc.player)==6 && !mc.player.getMainHandItem().has(ModData.RELOAD));
                check("R spends exactly six loose medium rounds",()->mc.player.getInventory().countItem(ModItems.ROUND.get())==6);
                mc.options.mainHand().set(HumanoidArm.LEFT);
            }
            case 430 -> shoot(mc,"revolver-first-left");
            case 431 -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            case 439 -> shoot(mc,"revolver-third-left");
            case 440 -> { mc.options.mainHand().set(HumanoidArm.RIGHT); mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }
            case 475 -> shoot(mc,"revolver-third-front");
            case 480 -> {
                mc.player.setYRot(-90);mc.player.setYHeadRot(-90);mc.player.setYBodyRot(-40);
            }
            case 510 -> {
                shoot(mc,"revolver-third-side");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.setScreen(new InventoryScreen(mc.player));
            }
            case 540 -> { shoot(mc,"revolver-inventory");mc.setScreen(null); }
            case 550 -> {
                mc.player.setYRot(0);mc.player.setYHeadRot(0);mc.player.setXRot(0);
                onServer(mc,RevolverBooth::spawn);
            }
            case 600 -> {
                check("observer receives a remote revolver",()->remote(mc)!=null && rounds(remote(mc))==6);
                shoot(mc,"revolver-observer-idle");
            }
            case 615 -> onServer(mc,sp->{
                PlayerGunnery.onTrigger(actor,true);
                PlayerGunnery.tick(actor,actor.serverLevel());
                actor.doTick();
            });
            case 630 -> {
                check("observer receives accepted cylinder and hammer cycle",()->remote(mc).getMainHandItem().get(ModData.REVOLVER_CYCLE).chamber()==1 && rounds(remote(mc))==5);
                shoot(mc,"revolver-observer-fired");
            }
            case 650 -> onServer(mc,sp->{
                PlayerGunnery.onTrigger(actor,false);
                PlayerGunnery.onReloadKey(actor,false);
                PlayerGunnery.tick(actor,actor.serverLevel());actor.doTick();
            });
            case 670 -> {
                check("observer receives cylinder reload",()->remote(mc).getMainHandItem().has(ModData.RELOAD));
                shoot(mc,"revolver-observer-reloading");
            }
            case 710 -> onServer(mc,sp->{PlayerGunnery.tick(actor,actor.serverLevel());actor.doTick();});
            case 740 -> {
                check("observer receives reload completion",()->rounds(remote(mc))==6 && !remote(mc).getMainHandItem().has(ModData.RELOAD));
                onServer(mc,sp->actor.discard());
            }
            case 760 -> { RangedWeaponsMod.LOGGER.info("booth: PASS all checks ran");mc.stop(); }
            default -> {}
        }
    }
    private static int rounds(Player player) {
        var gun=player.getMainHandItem(); return RangedWeapons.resolve(gun).rounds(gun);
    }
    private static void attack(Minecraft mc,boolean down) {
        KeyMapping.set(mc.options.keyAttack.getKey(),down);
        if(down) KeyMapping.click(mc.options.keyAttack.getKey());
    }
    private static void prepare(ServerPlayer p) {
        var level=p.serverLevel();
        level.setDayTime(6000);level.setWeatherParameters(0,6000,false,false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.getServer());
        BlockPos floor=p.blockPosition().below();
        for(int x=-8;x<=8;x++) for(int z=-8;z<=12;z++) {
            level.setBlockAndUpdate(floor.offset(x,0,z),Blocks.SMOOTH_STONE.defaultBlockState());
            for(int y=1;y<=5;y++) level.setBlockAndUpdate(floor.offset(x,y,z),Blocks.AIR.defaultBlockState());
        }
        p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();
        p.getInventory().selected=0;
        ItemStack gun=new ItemStack(ModItems.REVOLVER.get());
        RangedWeapons.resolve(gun).load(gun,6,ModItems.ROUND.get());
        p.setItemInHand(InteractionHand.MAIN_HAND,gun);
        // Loose ammunition is supplied after the sixth shot so the existing
        // empty-trigger auto-reload does not preempt the R-key test.
        p.connection.teleport(floor.getX()+.5,floor.getY()+1,floor.getZ()+.5,0,0);
    }
    private static void spawn(ServerPlayer observer) {
        var level=observer.serverLevel();
        observer.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        actor=new FakePlayer(level,new GameProfile(ACTOR,"RevolverGunner"));
        actor.moveTo(observer.getX(),observer.getY(),observer.getZ()+2.3,90,0);
        actor.setYHeadRot(90);actor.setYBodyRot(90);actor.setNoGravity(true);
        var gun=new ItemStack(ModItems.REVOLVER.get());RangedWeapons.resolve(gun).load(gun,6,ModItems.ROUND.get());
        actor.setItemInHand(InteractionHand.MAIN_HAND,gun);
        actor.getInventory().setItem(1,new ItemStack(ModItems.ROUND.get(),6));
        PlayerGunnery.onAim(actor,true);
        observer.connection.send(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,actor));
        level.addNewPlayer(actor);actor.doTick();
    }
    private static Player remote(Minecraft mc) {return mc.level.getPlayerByUUID(ACTOR);}
    private static void onServer(Minecraft mc,Consumer<ServerPlayer> action) {
        var server=mc.getSingleplayerServer();UUID id=mc.player.getUUID();
        server.execute(()->action.accept(server.getPlayerList().getPlayer(id)));
    }
    private static void check(String what,BooleanSupplier condition) {
        try {if(condition.getAsBoolean()) RangedWeaponsMod.LOGGER.info("booth: PASS {}",what);
            else RangedWeaponsMod.LOGGER.error("booth: FAIL {}",what);
        } catch(RuntimeException e) {RangedWeaponsMod.LOGGER.error("booth: FAIL {} -- {}",what,e.toString());}
    }
    private static void shoot(Minecraft mc,String name) {
        Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),message->{});
    }
}
