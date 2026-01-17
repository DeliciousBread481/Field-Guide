package com.evandev.fieldguide.client.gui.util;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class EntityRenderHelper {

    public static void renderEntityInGui(GuiGraphics guiGraphics, LivingEntity entity, int x, int y, float scale, boolean silhouette) {
        // Scaling
        float entitySize = Math.max((float)(entity.getBbHeight() * 0.5), entity.getBbWidth());
        if (entitySize > 0.0001) {
            float scaleFactor = Mth.clamp(1 / entitySize, 0.2F, 2);
            scale = scale * scaleFactor;
        }

        // Posing
        float cameraXAngle = -10;
        float bodyYAngle = 20;
        float headYAngle = 0;
        Quaternionf poseOrientation = new Quaternionf().rotateZ((float)Math.PI);
        Quaternionf cameraOrientation = new Quaternionf().rotateX(cameraXAngle * ((float)Math.PI / 180F));
        poseOrientation.mul(cameraOrientation);

        entity.setYRot(180.0F + bodyYAngle);
        entity.setXRot(0.0F);
        entity.yHeadRot = entity.getYRot() + headYAngle;
        entity.yHeadRotO = entity.getYRot() + headYAngle;
        entity.yBodyRot = 180.0F + bodyYAngle;
        entity.tickCount = 0;

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x, y, 50.0);
        pose.mulPoseMatrix((new Matrix4f()).scaling(scale, scale, -scale));

        pose.mulPose(poseOrientation);

        if (silhouette) {
            // Apply silhouette shading
        }

        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        cameraOrientation.conjugate();
        dispatcher.overrideCameraOrientation(cameraOrientation);
        dispatcher.setRenderShadow(false);
        dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, pose, guiGraphics.bufferSource(), LightTexture.FULL_BRIGHT);

        // Reset
        guiGraphics.flush();
        dispatcher.setRenderShadow(true);
        pose.popPose();
        if (silhouette) {
             // Reset silhouette shading
        }
        Lighting.setupFor3DItems();
    }
}
