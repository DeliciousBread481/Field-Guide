package com.evandev.fieldguide.client.gui.util;

import com.evandev.fieldguide.Constants;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class EntryRenderHelper {

    private static final Map<Object, Optional<ResourceLocation>> OVERRIDE_CACHE = new HashMap<>();

    public static void clearCache() {
        OVERRIDE_CACHE.clear();
    }

    private static Optional<ResourceLocation> getOverride(Object entry) {
        if (OVERRIDE_CACHE.containsKey(entry)) {
            return OVERRIDE_CACHE.get(entry);
        }

        ResourceLocation id = null;
        if (entry instanceof EntityType<?> type) {
            id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        } else if (entry instanceof Block block) {
            id = BuiltInRegistries.BLOCK.getKey(block);
        }

        if (id != null) {
            ResourceLocation texture = new ResourceLocation(id.getNamespace(), "textures/fieldguide/entries/" + id.getPath() + ".png");
            if (Minecraft.getInstance().getResourceManager().getResource(texture).isPresent()) {
                OVERRIDE_CACHE.put(entry, Optional.of(texture));
                return Optional.of(texture);
            }
        }

        OVERRIDE_CACHE.put(entry, Optional.empty());
        return Optional.empty();
    }

    private static boolean tryRenderOverride(GuiGraphics guiGraphics, Object entry, int x, int y, int width, int height, boolean silhouette, int color) {
        Optional<ResourceLocation> override = getOverride(entry);
        if (override.isPresent()) {
            ResourceLocation texture = override.get();

            if (silhouette) {
                Color rgb = new Color(color);
                guiGraphics.setColor(rgb.getRed() / 255F, rgb.getGreen() / 255F, rgb.getBlue() / 255F, 1.0F);
            } else {
                guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }

            int drawX = x - width / 2;
            int drawY = y - height / 2;

            guiGraphics.blit(texture, drawX, drawY, 0, 0, width, height, width, height);

            guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            return true;
        }
        return false;
    }

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
        if (tryRenderOverride(guiGraphics, entity.getType(), x, y, maxWidth, maxHeight, silhouette, color)) {
            return;
        }

        float dynamicFactor = getScaleFactorForEntity(entity);
        float finalScale = (baseScale * 0.32F) * dynamicFactor;

        float entityHeight = entity.getBbHeight();
        if (entityHeight * finalScale > maxHeight * 0.9f) {
            finalScale = (maxHeight * 0.9f) / entityHeight;
        }

        // Standardize Y offset
        int feetY = (int) (y + (entityHeight * finalScale / 2.0f));
        int minX = x - maxWidth / 2;
        int minY = y - maxHeight / 2;
        int maxX = x + maxWidth / 2;
        int maxY = y + maxHeight / 2;

        guiGraphics.enableScissor(minX, minY, maxX, maxY);
        renderEntityStatic(guiGraphics, entity, x, feetY, finalScale, silhouette, color);
        guiGraphics.disableScissor();
    }

    public static void renderEntityStatic(GuiGraphics guiGraphics, LivingEntity entity, int x, int y, float scale, boolean silhouette, int color) {
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x, y, 50.0);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        pose.scale(scale, scale, -scale);
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));

        float cameraXAngle = -30;
        float bodyYAngle = 30;

        Quaternionf cameraOrientation = Axis.XP.rotationDegrees(cameraXAngle);
        Quaternionf bodyOrientation = Axis.YP.rotationDegrees(-bodyYAngle);

        pose.mulPose(cameraOrientation);
        pose.mulPose(bodyOrientation);

        // Setup Entity rotations
        entity.setYRot(180.0F);
        entity.setXRot(0.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        entity.yBodyRot = 180.0F;
        entity.yBodyRotO = entity.yBodyRot;

        entity.tickCount = 0;
        entity.walkAnimation.setSpeed(0.0F);
        entity.walkAnimation.position(0.0F);
        entity.attackAnim = 0.0F;
        entity.oAttackAnim = 0.0F;

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
        dispatcher.setRenderShadow(false);

        dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, pose, guiGraphics.bufferSource(), LightTexture.FULL_BRIGHT);

        guiGraphics.flush();
        dispatcher.setRenderShadow(true);
        pose.popPose();

        RenderSystem.depthMask(false);

        if (silhouette) {
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        }

        Lighting.setupForFlatItems();
    }

    public static void renderBlock(GuiGraphics guiGraphics, Block block, int x, int y, float scale, boolean silhouette) {
        int estimatedSize = (int) (scale * 2);
        if (tryRenderOverride(guiGraphics, block, x, y, estimatedSize, estimatedSize, silhouette, Constants.LIST_SILHOUETTE_COLOR)) {
            return;
        }

        BlockState state = block.defaultBlockState();

        // Check if block has "vertical" property
        Property<?> verticalProp = state.getProperties().stream()
                .filter(p -> p instanceof EnumProperty<?>)
                .filter(p -> p.getName().equals("half"))
                .findFirst()
                .orElse(null);

        // Set blockstates to max values
        state = stateWithMaxPropertyValue(state, "age");
        state = stateWithMaxPropertyValue(state, "flower_amount");
        state = stateWithMaxPropertyValue(state, "pickles");

        // Rendering
        float cameraXAngle = 30;
        float cameraYAngle = 215;

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x, y, 50.0);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(cameraXAngle));
        pose.mulPose(Axis.YP.rotationDegrees(cameraYAngle));
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

        setupBlockLighting();
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
                pose.translate(0.0F, 1.0F, 0.0F);
            }
        }

        guiGraphics.flush();
        pose.popPose();

        if (silhouette) {
            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        }
        Lighting.setupForFlatItems();
    }

    // There's definitely a better way of doing this
    private static void setupBlockLighting() {
        Vector3f light0 = new Vector3f(0.2F, -1.0F, -0.7F);
        light0.normalize();

        Vector3f light1 = new Vector3f(-0.2F, 0.0F, 0.7F);
        light1.normalize();

        RenderSystem.setShaderLights(light0, light1);
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

        @SuppressWarnings({"unchecked", "rawtypes"})
        BlockState newState = state.setValue((Property) property, (Comparable) maxValue);
        return newState;
    }
}