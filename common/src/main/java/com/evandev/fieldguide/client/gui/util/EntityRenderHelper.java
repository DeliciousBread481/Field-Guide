package com.evandev.fieldguide.client.gui.util;

import com.evandev.fieldguide.Constants;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.awt.*;

public class EntityRenderHelper {

    /**
     * Calculates a scaling factor based on entity size using an inverse square relationship.
     */
    private static float getScaleFactorForEntity(LivingEntity entity) {
        try {
            float width = entity.getBbWidth();
            float height = entity.getBbHeight();
            float referenceSize = Math.max(width, height);

            float scaleFactor = 1.0F / (referenceSize * referenceSize);
            float calibrationFactor = 2.0F;
            scaleFactor *= calibrationFactor;

            float minScale = 0.1F;
            float maxScale = 0.35F;

            if (referenceSize >= 3.0F) {
                float extraScaleFactor = 30.0F / referenceSize;
                scaleFactor *= extraScaleFactor;
                return Math.min(Math.max(scaleFactor, minScale * extraScaleFactor), maxScale);
            } else {
                return Math.min(Math.max(scaleFactor, minScale), maxScale) * 3.5F;
            }
        } catch (Exception e) {
            return 0.35F;
        }
    }

    /**
     * Renders an entity normalized to fit within a standard widget box.
     * Defaults to the standard sepia silhouette color.
     */
    public static void renderEntityNormalized(GuiGraphics guiGraphics, LivingEntity entity, int x, int y, int maxWidth, int maxHeight, float baseScale, boolean silhouette) {
        renderEntityNormalized(guiGraphics, entity, x, y, maxWidth, maxHeight, baseScale, silhouette, Constants.LIST_SILHOUETTE_COLOR);
    }

    /**
     * Renders an entity normalized to fit within a standard widget box with a specific silhouette color.
     */
    public static void renderEntityNormalized(GuiGraphics guiGraphics, LivingEntity entity, int x, int y, int maxWidth, int maxHeight, float baseScale, boolean silhouette, int color) {
        float dynamicFactor = getScaleFactorForEntity(entity);
        float finalScale = (baseScale * 0.32F) * dynamicFactor;

        float entityHeight = entity.getBbHeight();

        if (entityHeight * finalScale > maxHeight * 0.9f) {
            finalScale = (maxHeight * 0.9f) / entityHeight;
        }

        if (!Float.isFinite(finalScale) || finalScale <= 0.0F) {
            finalScale = baseScale * 0.3F;
        }

        int feetY = (int) (y + (entityHeight * finalScale / 2.0f));

        int minX = x - maxWidth / 2;
        int minY = y - maxHeight / 2;
        int maxX = x + maxWidth / 2;
        int maxY = y + maxHeight / 2;

        guiGraphics.enableScissor(minX, minY, maxX, maxY);

        renderEntityStatic(guiGraphics, entity, x, feetY, finalScale, silhouette, color);

        guiGraphics.disableScissor();
    }

    /**
     * Renders an entity with a fixed pose and no animation.
     */
    public static void renderEntityStatic(GuiGraphics guiGraphics, LivingEntity entity, int x, int y, float scale, boolean silhouette, int color) {
        float cameraXAngle = -30;
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
            Color rgb = new Color(color);
            float r = rgb.getRed() / 255F;
            float g = rgb.getGreen() / 255F;
            float b = rgb.getBlue() / 255F;

            RenderSystem.setShaderFogColor(r, g, b);
            RenderSystem.setShaderFogStart(0.0F);
            RenderSystem.setShaderFogEnd(0.1F);
        }

        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        cameraOrientation.conjugate();
        dispatcher.overrideCameraOrientation(cameraOrientation);
        dispatcher.setRenderShadow(false);

        dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 0.0F, pose, guiGraphics.bufferSource(), LightTexture.FULL_BRIGHT);

        guiGraphics.flush();

        dispatcher.setRenderShadow(true);
        pose.popPose();

        if (silhouette) {
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        }

        Lighting.setupFor3DItems();
    }
}