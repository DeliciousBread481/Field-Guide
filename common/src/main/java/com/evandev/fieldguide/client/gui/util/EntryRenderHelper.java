package com.evandev.fieldguide.client.gui.util;

import com.evandev.fieldguide.Constants;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.awt.*;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;

public class EntryRenderHelper {

    /**
     * Calculates a scaling factor based on entry size using an inverse square relationship.
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
     * Renders an entry normalized to fit within a standard widget box.
     * Defaults to the standard sepia silhouette color.
     */
    public static void renderEntityNormalized(GuiGraphics guiGraphics, LivingEntity entity, int x, int y, int maxWidth, int maxHeight, float baseScale, boolean silhouette) {
        renderEntityNormalized(guiGraphics, entity, x, y, maxWidth, maxHeight, baseScale, silhouette, Constants.LIST_SILHOUETTE_COLOR);
    }

    /**
     * Renders an entry normalized to fit within a standard widget box with a specific silhouette color.
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
     * Renders an entry with a fixed pose and no animation.
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

        Lighting.setupForFlatItems();
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

    public static void renderBlock(GuiGraphics guiGraphics, Block block, int x, int y, float scale, boolean silhouette) {
        BlockState state = block.defaultBlockState();

        // Check if block has "vertical" property
        Property<?> verticalProp = state.getProperties().stream()
                .filter(p -> p instanceof EnumProperty<?>)
                .filter(p -> {
                    String name = p.getName();
                    return name.equals("half");
                })
                .findFirst()
                .orElse(null);

        // Set blockstates to max values
        state = stateWithMaxPropertyValue(state, "age");
        state = stateWithMaxPropertyValue(state, "flower_amount");

        // Rendering
        float cameraXAngle = 30;
        float cameraYAngle = 20;

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x, y, 50.0);
        pose.scale(scale, -scale, scale);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(cameraXAngle));
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(cameraYAngle));
        pose.translate(-0.5, -0.5, -0.5);

        if (silhouette) {
            Color rgb = new Color(Constants.LIST_SILHOUETTE_COLOR);
            float r = rgb.getRed() / 255F;
            float g = rgb.getGreen() / 255F;
            float b = rgb.getBlue() / 255F;

            RenderSystem.setShaderFogColor(r, g, b);
            RenderSystem.setShaderFogStart(0.0F);
            RenderSystem.setShaderFogEnd(0.1F);
        }

        Lighting.setupForFlatItems();
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();

        if (verticalProp == null) {
            // Render single block
            dispatcher.renderSingleBlock(state, pose, guiGraphics.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } else {
            // Render multiple blocks vertically
            Collection<?> values = verticalProp.getPossibleValues();

            // Sort values in correct order from bottom -> top
            if (!values.isEmpty() && values.iterator().next() instanceof Comparable) {
                @SuppressWarnings("unchecked")
                Collection<Comparable<?>> sorted = (Collection<Comparable<?>>) values;
                values = sorted.stream()
                        .sorted((a, b) -> Integer.compare(
                                ((Enum<?>) b).ordinal(),
                                ((Enum<?>) a).ordinal()
                        ))
                        .toList();
            }

            for (Object value : values) {
                @SuppressWarnings({"rawtypes", "unchecked"})
                BlockState variant = state.setValue((Property) verticalProp, (Comparable) value);

                dispatcher.renderSingleBlock(variant, pose, guiGraphics.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);

                // Move up 1 block
                pose.translate(0.0F, 1.0F, 0.0F);
            }
        }

        guiGraphics.flush();
        pose.popPose();

        if (silhouette) {
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        }
        Lighting.setupFor3DItems();
    }

    public static BlockState stateWithMaxPropertyValue(BlockState state, String propertyName) {
        if (state == null || propertyName == null) {
            return state;
        }

        // Find the property by name
        Optional<Property<?>> propertyOpt = state.getProperties().stream()
                .filter(p -> p.getName().equals(propertyName))
                .findFirst();

        if (propertyOpt.isEmpty()) {
            return state;
        }

        Property<?> property = propertyOpt.get();

        // Find the maximum possible value
        Comparable<?> maxValue = property.getPossibleValues().stream()
                .max((a, b) -> {
                    if (a instanceof Number && b instanceof Number) {
                        return Double.compare(((Number) a).doubleValue(),
                                ((Number) b).doubleValue());
                    }
                    return (a).compareTo(b);
                })
                .orElse(null);

        if (maxValue == null) {
            return state;
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        BlockState newState = state.setValue((Property) property, (Comparable) maxValue);
        return newState;
    }
}