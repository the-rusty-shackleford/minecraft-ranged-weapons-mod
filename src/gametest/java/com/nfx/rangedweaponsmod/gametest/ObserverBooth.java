/*
 * Ranged Weapons Mod - guns for players, on the Ranged Weapons protocol.
 * Copyright (C) 2026 Rusty Shackleford and nfx
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.nfx.rangedweaponsmod.gametest;

import com.mojang.authlib.GameProfile;
import com.nfx.rangedweapons.api.Grip;
import com.nfx.rangedweapons.api.RangedWeapons;
import com.nfx.rangedweaponsmod.ModData;
import com.nfx.rangedweaponsmod.ModItems;
import com.nfx.rangedweaponsmod.PlayerGunnery;
import com.nfx.rangedweaponsmod.RangedWeaponsMod;
import com.nfx.rangedweaponsmod.Reload;
import com.nfx.rangedweaponsmod.client.Keys;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Observer regression through a real client, level, entity tracker and packet
 * handlers. A server-controlled player is the stimulus; the observing client is
 * never given state directly. No second rendering client is needed. This proves
 * observer delivery, separately from the real local aim key exercised here.
 *
 * <p>Partitions: state present on first tracking / changed while tracked /
 * cleared after a weapon leaves; reload present / complete; one / two hands;
 * local key press / release; pillager gun hold. Run with -PboothObservers.
 */
@EventBusSubscriber(modid = BoothMod.MOD_ID, value = Dist.CLIENT)
public final class ObserverBooth {
    private ObserverBooth() {}

    private static final UUID ACTOR = UUID.fromString("9656b118-f62e-47a5-85bf-aa9510785dc9");
    private static int tick;
    private static volatile int pillagerId;
    private static volatile long initialReloadAt;
    private static FakePlayer actor;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("rangedweaponsmod.photobooth") || !Boolean.getBoolean("rangedweaponsmod.observers")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        switch (++tick) {
            case 40 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.player.setYRot(0);
                mc.player.setYHeadRot(0);
                mc.player.setXRot(0);
                onServer(mc, ObserverBooth::spawn);
            }
            case 80 -> {
                check("observer received a remote player", () -> remote(mc) != null && remote(mc) != mc.player);
                check("initial tracking includes accepted aim", () -> remote(mc).getData(ModData.AIMING));
                check("initial equipment includes the reload clock", () -> {
                    Reload reload = remote(mc).getMainHandItem().get(ModData.RELOAD);
                    return reload != null && reload.startedAt() == initialReloadAt && reload.durationTicks() == 1000;
                });
                check("pistol grip arrives in the synced profile", () ->
                        RangedWeapons.resolve(remote(mc).getMainHandItem()).profile().grip().orElseThrow() == Grip.ONE_HANDED);
                check("pillager exposes crossbow hold on the observing client", () ->
                        ((Pillager) mc.level.getEntity(pillagerId)).getArmPose() == AbstractIllager.IllagerArmPose.CROSSBOW_HOLD);
                shoot(mc, "observer-initial");
            }
            case 90 -> onServer(mc, sp -> {
                PlayerGunnery.onAim(actor, false);
                actor.getMainHandItem().remove(ModData.RELOAD);
                actor.doTick();
            });
            case 120 -> {
                check("observer sees aim release", () -> !remote(mc).getData(ModData.AIMING));
                check("observer sees reload removal", () -> !remote(mc).getMainHandItem().has(ModData.RELOAD));
            }
            case 130 -> onServer(mc, sp -> {
                actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SHOTGUN.get()));
                actor.getInventory().add(new ItemStack(ModItems.SHELL.get(), 6));
                PlayerGunnery.onAim(actor, true);
                PlayerGunnery.onReloadKey(actor, false);
                PlayerGunnery.tick(actor, actor.serverLevel());
                actor.doTick();
            });
            case 150 -> {
                check("observer sees accepted aim change", () -> remote(mc).getData(ModData.AIMING));
                check("real reload start reaches the observer", () -> {
                    Reload reload = remote(mc).getMainHandItem().get(ModData.RELOAD);
                    return reload != null && reload.progress(mc.level.getGameTime()) > 0
                            && reload.progress(mc.level.getGameTime()) < 1;
                });
                check("shotgun grip arrives in the synced profile", () ->
                        RangedWeapons.resolve(remote(mc).getMainHandItem()).profile().grip().orElseThrow() == Grip.TWO_HANDED);
                shoot(mc, "observer-reloading");
            }
            case 200 -> onServer(mc, sp -> {
                PlayerGunnery.tick(actor, actor.serverLevel());
                actor.doTick();
            });
            case 230 -> {
                check("real reload completion reaches the observer", () ->
                        !remote(mc).getMainHandItem().has(ModData.RELOAD)
                        && RangedWeapons.resolve(remote(mc).getMainHandItem()).rounds(remote(mc).getMainHandItem()) == 6);
                shoot(mc, "observer-loaded");
                onServer(mc, sp -> {
                    actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    PlayerGunnery.tick(actor, actor.serverLevel());
                    actor.doTick();
                    sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PISTOL.get()));
                });
            }
            case 260 -> {
                check("observer sees weapon removal clear aim", () -> !remote(mc).getData(ModData.AIMING)
                        && remote(mc).getMainHandItem().isEmpty());
                KeyMapping.set(Keys.AIM.getKey(), true);
            }
            case 290 -> {
                check("real local aim key is acknowledged by the server", () -> mc.player.getData(ModData.AIMING));
                KeyMapping.set(Keys.AIM.getKey(), false);
            }
            case 320 -> {
                check("real local key release is acknowledged", () -> !mc.player.getData(ModData.AIMING));
                onServer(mc, sp -> actor.discard());
            }
            case 340 -> {
                RangedWeaponsMod.LOGGER.info("booth: PASS all checks ran");
                mc.stop();
            }
            default -> { }
        }
    }

    private static Player remote(Minecraft mc) {
        return mc.level.getPlayerByUUID(ACTOR);
    }

    private static void spawn(ServerPlayer observer) {
        var level = observer.serverLevel();
        observer.setGameMode(GameType.CREATIVE);
        BlockPos floor = observer.blockPosition().below();
        for (int x = -4; x <= 4; x++) {
            for (int z = -2; z <= 8; z++) {
                level.setBlockAndUpdate(floor.offset(x, 0, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = 1; y <= 4; y++) {
                    level.setBlockAndUpdate(floor.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        actor = new FakePlayer(level, new GameProfile(ACTOR, "BoothGunner"));
        actor.moveTo(observer.getX(), observer.getY(), observer.getZ() + 4, 180, 0);
        actor.setYHeadRot(180);
        actor.setYBodyRot(180);
        actor.setNoGravity(true);
        actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PISTOL.get()));
        initialReloadAt = level.getGameTime();
        actor.getMainHandItem().set(ModData.RELOAD, new Reload(initialReloadAt, 1000));
        PlayerGunnery.onAim(actor, true);
        observer.connection.send(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, actor));
        level.addNewPlayer(actor);
        actor.doTick();
        Pillager pillager = EntityType.PILLAGER.create(level);
        pillager.moveTo(observer.getX() + 2, observer.getY(), observer.getZ() + 4, 180, 0);
        pillager.setYHeadRot(180);
        pillager.setYBodyRot(180);
        pillager.setNoAi(true);
        pillager.setPersistenceRequired();
        pillager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.RIFLE.get()));
        level.addFreshEntity(pillager);
        pillagerId = pillager.getId();
    }

    private static void onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        UUID observer = mc.player.getUUID();
        server.execute(() -> action.accept(server.getPlayerList().getPlayer(observer)));
    }

    private static void check(String what, BooleanSupplier condition) {
        try {
            if (condition.getAsBoolean()) {
                RangedWeaponsMod.LOGGER.info("booth: PASS {}", what);
            } else {
                RangedWeaponsMod.LOGGER.error("booth: FAIL {}", what);
            }
        } catch (RuntimeException e) {
            RangedWeaponsMod.LOGGER.error("booth: FAIL {} -- {}", what, e.toString());
        }
    }

    private static void shoot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                message -> RangedWeaponsMod.LOGGER.info("photo booth: {}", message.getString()));
    }
}
