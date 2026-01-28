package com.evandev.fieldguide.client.gui.util;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.data.EntryVisual;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class EntryRenderHelper {

    private static final Map<String, Optional<ResourceLocation>> OVERRIDE_CACHE = new HashMap<>();

    public static void clearCache() {
        OVERRIDE_CACHE.clear();
    }

    private static Optional<ResourceLocation> getOverride(Object entry, boolean isPage) {
        String key = entry.toString() + (isPage ? "_page" : "_grid");

        if (OVERRIDE_CACHE.containsKey(key)) {
            return OVERRIDE_CACHE.get(key);
        }

        ResourceLocation id = null;
        if (entry instanceof EntityType<?> type) {
            id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        } else if (entry instanceof Block block) {
            id = BuiltInRegistries.BLOCK.getKey(block);
        }

        if (id != null) {
            ResourceLocation specificLoc = new ResourceLocation(id.getNamespace(),
                    "textures/fieldguide/entries/" + id.getPath() + (isPage ? "_page.png" : "_grid.png"));

            ResourceLocation defaultLoc = new ResourceLocation(id.getNamespace(),
                    "textures/fieldguide/entries/" + id.getPath() + ".png");

            // Check specific first (e.g., pig_page.png), then default (pig.png)
            if (Minecraft.getInstance().getResourceManager().getResource(specificLoc).isPresent()) {
                OVERRIDE_CACHE.put(key, Optional.of(specificLoc));
                return Optional.of(specificLoc);
            } else if (Minecraft.getInstance().getResourceManager().getResource(defaultLoc).isPresent()) {
                OVERRIDE_CACHE.put(key, Optional.of(defaultLoc));
                return Optional.of(defaultLoc);
            }
        }

        OVERRIDE_CACHE.put(key, Optional.empty());
        return Optional.empty();
    }

    private static boolean tryRenderOverride(GuiGraphics guiGraphics, Object entry, int x, int y, int width, int height, boolean silhouette, int color, boolean isPage) {
        Optional<ResourceLocation> override = getOverride(entry, isPage);

        if (override.isPresent()) {
            ResourceLocation texture = override.get();
            int drawX = x - width / 2;
            int drawY = y - height / 2;

            if (silhouette) {
                Color rgb = new Color(color);
                float r = rgb.getRed() / 255F;
                float g = rgb.getGreen() / 255F;
                float b = rgb.getBlue() / 255F;

                RenderSystem.enableDepthTest();
                RenderSystem.setShaderFogColor(r, g, b);
                RenderSystem.setShaderFogStart(0.0F);
                RenderSystem.setShaderFogEnd(0.1F);

                guiGraphics.flush();

                VertexConsumer consumer = guiGraphics.bufferSource().getBuffer(RenderType.entityCutout(texture));
                guiGraphics.pose().last();

                consumer.vertex(drawX, drawY + height, 0)
                        .color(255, 255, 255, 255)
                        .uv(0.0F, 1.0F)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(LightTexture.FULL_BRIGHT)
                        .normal(0, 0, 1)
                        .endVertex();

                consumer.vertex(drawX + width, drawY + height, 0)
                        .color(255, 255, 255, 255)
                        .uv(1.0F, 1.0F)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(LightTexture.FULL_BRIGHT)
                        .normal(0, 0, 1)
                        .endVertex();

                consumer.vertex(drawX + width, drawY, 0)
                        .color(255, 255, 255, 255)
                        .uv(1.0F, 0.0F)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(LightTexture.FULL_BRIGHT)
                        .normal(0, 0, 1)
                        .endVertex();

                consumer.vertex(drawX, drawY, 0)
                        .color(255, 255, 255, 255)
                        .uv(0.0F, 0.0F)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(LightTexture.FULL_BRIGHT)
                        .normal(0, 0, 1)
                        .endVertex();

                guiGraphics.flush();

                RenderSystem.setShaderFogStart(Float.MAX_VALUE);
                RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
                RenderSystem.disableDepthTest();

            } else {
                guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                guiGraphics.blit(texture, drawX, drawY, 0, 0, width, height, width, height);
                guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }

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

    public static void renderEntityNormalized(GuiGraphics guiGraphics, LivingEntity entity, int x, int y, int maxWidth, int maxHeight, float baseScale, boolean silhouette, int color, boolean isPage) {
        if (tryRenderOverride(guiGraphics, entity.getType(), x, y, maxWidth, maxHeight, silhouette, color, isPage)) {
            return;
        }

        ResourceLocation id = ClientFieldGuideManager.getEntryId(entity.getType());
        EntryVisual visual = ClientFieldGuideManager.getInstance().getEntryVisual(id);

        float visualScale = visual.scale;
        float yOff = visual.yOffset;
        if (isPage) {
            if (visual.pageScale != null) visualScale = visual.pageScale;
            if (visual.pageYOffset != null) yOff = visual.pageYOffset;
        } else {
            if (visual.gridScale != null) visualScale = visual.gridScale;
            if (visual.gridYOffset != null) yOff = visual.gridYOffset;
        }

        float dynamicFactor = getScaleFactorForEntity(entity);
        float standardScale = (baseScale * 0.32F) * dynamicFactor;
        float finalScale = standardScale * visualScale;

        float entityHeight = entity.getBbHeight();
        if (entityHeight * finalScale > maxHeight * 0.9f) {
            finalScale = (maxHeight * 0.9f) / entityHeight;
        }

        int feetY = (int) (y + (entityHeight * finalScale / 2.0f) + yOff);

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

        pose.mulPose(cameraOrientation);

        // Setup entity rotations
        float normalizedBodyRot = 180.0F + bodyYAngle;

        entity.setYRot(normalizedBodyRot);
        entity.setXRot(0.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        entity.yBodyRot = normalizedBodyRot;
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
        setupEntityLighting();

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

    public static void renderBlock(GuiGraphics guiGraphics, Block block, int x, int y, float baseScale, boolean silhouette, boolean isPage) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(block);
        EntryVisual visual = ClientFieldGuideManager.getInstance().getEntryVisual(id);

        float visualScale = visual.scale;
        if (isPage) {
            if (visual.pageScale != null) visualScale = visual.pageScale;
        } else {
            if (visual.gridScale != null) visualScale = visual.gridScale;
        }

        float finalScale = baseScale * visualScale;

        int estimatedSize = (int) (finalScale * 2);

        if (tryRenderOverride(guiGraphics, block, x, y, estimatedSize, estimatedSize, silhouette, Constants.LIST_SILHOUETTE_COLOR, isPage)) {
            return;
        }

        BlockState state = block.defaultBlockState();

        // Check if block has "vertical" property
        Property<?> verticalProp = state.getProperties().stream()
                .filter(p -> p instanceof EnumProperty<?>)
                .filter(p -> p.getName().equals("half"))
                .findFirst()
                .orElse(null);

        // Property maximizer
        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof IntegerProperty intProp) {
                String name = prop.getName();

                if (!name.equals("bites") && !name.equals("level") && !name.equals("rotation")) {
                    int max = intProp.getPossibleValues().stream().max(Integer::compareTo).orElse(0);
                    state = state.setValue(intProp, max);
                }
            }
        }

        float cameraXAngle = 30;
        float cameraYAngle = 210;

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x, y, 50.0);

        pose.scale(finalScale, -finalScale, finalScale);

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
            dispatcher.renderSingleBlock(state, pose, guiGraphics.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } else {
            // Render multiple blocks vertically
            Collection<?> values = verticalProp.getPossibleValues();
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

    private static void setupBlockLighting() {
        Vector3f light0 = new Vector3f(0.2F, -1.0F, -0.7F);
        light0.normalize();

        Vector3f light1 = new Vector3f(-0.2F, 0.0F, 0.7F);
        light1.normalize();

        RenderSystem.setShaderLights(light0, light1);
    }

    private static void setupEntityLighting() {
        Vector3f light0 = new Vector3f(-1.0F, -1.0F, 1.0F);
        light0.normalize();

        Vector3f light1 = new Vector3f(1.0F, -1.0F, 1.0F);
        light1.normalize();

        RenderSystem.setShaderLights(light0, light1);
    }

}