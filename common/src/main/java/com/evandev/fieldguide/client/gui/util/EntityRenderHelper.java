package com.evandev.fieldguide.client.gui.util;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class EntityRenderHelper {

    /**
     * Renders an entity normalized to fit within a standard widget box.
     */
    public static void renderEntityNormalized(GuiGraphics guiGraphics, LivingEntity entity, int x, int y, float scale, boolean silhouette) {
        float entitySize = Math.max(entity.getBbHeight(), entity.getBbWidth());
        if (entitySize > 0) {
            float scaleFactor = 1.0F / entitySize;
            scale = scale * scaleFactor;
        }
        renderEntityStatic(guiGraphics, entity, x, y, scale, silhouette);
    }

    /**
     * Renders an entity with a fixed pose and no animation.
     */
    public static void renderEntityStatic(GuiGraphics guiGraphics, LivingEntity entity, int x, int y, float scale, boolean silhouette) {
        float cameraXAngle = -10;
        float bodyYAngle = 20;
        float headYAngle = 0;
        Quaternionf poseOrientation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cameraOrientation = new Quaternionf().rotateX(cameraXAngle * ((float) Math.PI / 180F));
        poseOrientation.mul(cameraOrientation);

        entity.setYRot(180.0F + bodyYAngle);
        entity.setXRot(0.0F);
        entity.yHeadRot = entity.getYRot() + headYAngle;
        entity.yHeadRotO = entity.getYRot() + headYAngle;
        entity.yBodyRot = 180.0F + bodyYAngle;
        entity.yBodyRotO = entity.yBodyRot;

        entity.tickCount = 0;
        entity.walkAnimation.setSpeed(0.0F);
        entity.walkAnimation.position(0.0F);
        entity.attackAnim = 0.0F;
        entity.oAttackAnim = 0.0F;

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x, y, 50.0);
        pose.mulPoseMatrix((new Matrix4f()).scaling(scale, scale, -scale));

        pose.mulPose(poseOrientation);

        if (silhouette) {
            // TODO: Apply silhouette shading
        }

        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        cameraOrientation.conjugate();
        dispatcher.overrideCameraOrientation(cameraOrientation);
        dispatcher.setRenderShadow(false);
        dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 0.0F, pose, guiGraphics.bufferSource(), LightTexture.FULL_BRIGHT);

        // Reset
        guiGraphics.flush();
        dispatcher.setRenderShadow(true);
        pose.popPose();
        if (silhouette) {
            // TODO: Reset silhouette shading
        }
        Lighting.setupFor3DItems();
    }
}