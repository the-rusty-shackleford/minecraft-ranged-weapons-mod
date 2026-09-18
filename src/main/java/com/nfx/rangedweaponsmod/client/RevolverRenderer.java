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
package com.nfx.rangedweaponsmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nfx.rangedweaponsmod.ModData;
import com.nfx.rangedweaponsmod.RangedWeaponsMod;
import com.nfx.rangedweaponsmod.domain.RevolverAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Four reload-baked parts: frame, cylinder, hammer and hinged crane. The item's ordinary
 * display transforms still place the complete gun in every context. Only the
 * articulated parts move, about model-space pivots. No geometry is built
 * per frame and resource reloads replace the engine's baked models normally.
 */
@EventBusSubscriber(modid=RangedWeaponsMod.MOD_ID,value=Dist.CLIENT)
public final class RevolverRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ModelResourceLocation BODY=model("body"), CYLINDER=model("cylinder"), HAMMER=model("hammer"), CRANE=model("crane");
    private static final class Holder { private static final RevolverRenderer INSTANCE=new RevolverRenderer(); }
    private RevolverRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());
    }
    /** effects: returns the renderer, initialized only after Minecraft's renderer exists. */
    public static RevolverRenderer instance() { return Holder.INSTANCE; }
    private static ModelResourceLocation model(String part) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(RangedWeaponsMod.MOD_ID,"item/revolver_"+part));
    }
    /** effects: includes the articulated parts in normal model baking. */
    @SubscribeEvent public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(BODY); event.register(CYLINDER); event.register(HAMMER); event.register(CRANE);
    }

    /** effects: draws a gun with the action accepted by the server; never changes gameplay state. */
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        Minecraft mc=Minecraft.getInstance();
        double time=mc.level==null ? 0 : mc.level.getGameTime()+mc.getTimer().getGameTimeDeltaPartialTick(false);
        var cycle=stack.get(ModData.REVOLVER_CYCLE);
        var reload=stack.get(ModData.RELOAD);
        double progress=reload==null ? 0 : Math.clamp((time-reload.startedAt())/reload.durationTicks(),0,1);
        RevolverAction action=RevolverAction.sample(cycle==null ? 0 : cycle.chamber(),cycle==null ? -1 : time-cycle.firedAt(),progress);
        draw(BODY,stack,pose,buffers,light,overlay);
        pose.pushPose();
        pose.translate(0,5.6/16,6.7/16);
        pose.mulPose(Axis.XP.rotationDegrees((float)(-90*action.opening())));
        pose.translate(0,-5.6/16,-6.7/16);
        draw(CRANE,stack,pose,buffers,light,overlay);
        pose.translate(6.75/16,7.5/16,8.0/16);
        pose.mulPose(Axis.XP.rotationDegrees((float)action.cylinder()));
        pose.translate(-6.75/16,-7.5/16,-8.0/16);
        draw(CYLINDER,stack,pose,buffers,light,overlay);
        pose.popPose();
        pose.pushPose();
        pose.translate(3.8/16,8.2/16,8.0/16);
        pose.mulPose(Axis.ZP.rotationDegrees((float)action.hammer()));
        pose.translate(-3.8/16,-8.2/16,-8.0/16);
        draw(HAMMER,stack,pose,buffers,light,overlay);
        pose.popPose();
    }

    private static void draw(ModelResourceLocation model,ItemStack stack,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        Minecraft mc=Minecraft.getInstance();
        var vertices=ItemRenderer.getFoilBufferDirect(buffers,Sheets.cutoutBlockSheet(),true,stack.hasFoil());
        mc.getItemRenderer().renderModelLists(mc.getModelManager().getModel(model),stack,light,overlay,pose,vertices);
    }
}
