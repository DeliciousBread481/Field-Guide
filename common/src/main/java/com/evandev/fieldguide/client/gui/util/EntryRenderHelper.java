package com.evandev.fieldguide.client.gui.util;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.data.CompositeFieldGuideEntry;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class EntryRenderHelper {

    private static final Map<String, Optional<ResourceLocation>> OVERRIDE_CACHE = new HashMap<>();

    public static void clearCache() {
        OVERRIDE_CACHE.clear();
        IconCacheManager.clearCache();
    }

    private static Optional<ResourceLocation> getResourcePackOverride(Object entry, boolean isPage) {
        String key = entry.toString() + (isPage ? "_page" : "_grid");

        if (OVERRIDE_CACHE.containsKey(key)) {
            return OVERRIDE_CACHE.get(key);
        }

        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        if (id != null) {
            ResourceLocation specificLoc = new ResourceLocation(id.getNamespace(), "textures/fieldguide/entries/" + id.getPath() + (isPage ? "_page.png" : "_grid.png"));
            ResourceLocation defaultLoc = new ResourceLocation(id.getNamespace(), "textures/fieldguide/entries/" + id.getPath() + ".png");

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

    public static void renderEntityNormalized(GuiGraphics guiGraphics, LivingEntity entity, int x, int y, int maxWidth, int maxHeight, float baseScale, boolean silhouette, int color, boolean isPage, float bounceScale) {
        Optional<ResourceLocation> textureOpt = getResourcePackOverride(entity.getType(), isPage);

        if (textureOpt.isEmpty()) {
            textureOpt = IconCacheManager.getOrGenerateIcon(entity.getType(), isPage, () -> {
                PoseStack pose = new PoseStack();

                pose.scale(70f, -70f, -70f);

                pose.mulPose(Axis.XP.rotationDegrees(30.0F));
                pose.mulPose(Axis.YP.rotationDegrees(330.0F));

                pose.translate(0, -0.2f, 0);

                entity.setYRot(0.0F);
                entity.setXRot(0.0F);
                entity.yHeadRot = 0.0F;
                entity.yHeadRotO = 0.0F;
                entity.yBodyRot = 0.0F;
                entity.yBodyRotO = 0.0F;

                entity.tickCount = 0;
                entity.walkAnimation.setSpeed(0.0F);
                entity.walkAnimation.position(0.0F);
                entity.attackAnim = 0.0F;
                entity.oAttackAnim = 0.0F;

                MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
                Minecraft.getInstance().getEntityRenderDispatcher().render(entity, 0, 0, 0, 0.0F, 1.0F, pose, buffers, LightTexture.FULL_BRIGHT);
                buffers.endBatch();
            });
        }

        textureOpt.ifPresent(texture -> drawCachedTexture(guiGraphics, texture, x, y, maxWidth, maxHeight, silhouette, color, bounceScale));
    }

    public static void renderBlock(GuiGraphics guiGraphics, Block block, int x, int y, float baseScale, boolean silhouette, boolean isPage, float bounceScale) {
        Optional<ResourceLocation> textureOpt = getResourcePackOverride(block, isPage);

        if (textureOpt.isEmpty()) {
            textureOpt = IconCacheManager.getOrGenerateIcon(block, isPage, () -> {
                PoseStack pose = new PoseStack();

                pose.scale(100f, -100f, -100f);

                pose.mulPose(Axis.XP.rotationDegrees(30.0F));
                pose.mulPose(Axis.YP.rotationDegrees(330.0F));

                pose.translate(-0.5, -0.5, -0.5);

                MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
                BlockState state = block.defaultBlockState();

                for (Property<?> prop : state.getProperties()) {
                    if (prop instanceof IntegerProperty intProp) {
                        String name = prop.getName();
                        if (!name.equals("bites") && !name.equals("level") && !name.equals("rotation")) {
                            int max = intProp.getPossibleValues().stream().max(Integer::compareTo).orElse(0);
                            state = state.setValue(intProp, max);
                        }
                    }
                }

                Property<?> verticalProp = state.getProperties().stream()
                        .filter(p -> p instanceof EnumProperty<?>)
                        .filter(p -> p.getName().equals("half"))
                        .findFirst()
                        .orElse(null);

                if (verticalProp == null) {
                    Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                } else {
                    Collection<?> values = verticalProp.getPossibleValues();
                    if (!values.isEmpty() && values.iterator().next() instanceof Comparable) {
                        @SuppressWarnings("unchecked")
                        Collection<Comparable<?>> sorted = (Collection<Comparable<?>>) values;
                        values = sorted.stream()
                                .sorted((a, b) -> Integer.compare(((Enum<?>) b).ordinal(), ((Enum<?>) a).ordinal()))
                                .toList();
                    }
                    for (Object value : values) {
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        BlockState variant = state.setValue((Property) verticalProp, (Comparable) value);
                        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(variant, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                        pose.translate(0.0F, 1.0F, 0.0F);
                    }
                }
                buffers.endBatch();
            });
        }

        int color = ModConfig.get().getListSilhouetteColorInt();
        textureOpt.ifPresent(texture -> drawCachedTexture(guiGraphics, texture, x, y, (int) (baseScale * 2), (int) (baseScale * 2), silhouette, color, bounceScale));
    }

    public static void renderStructure(GuiGraphics guiGraphics, CompositeFieldGuideEntry composite, int x, int y, int size, boolean silhouette, boolean isPage, float bounceScale) {
        Optional<ResourceLocation> textureOpt = getResourcePackOverride(composite, isPage);

        if (textureOpt.isEmpty()) {
            textureOpt = IconCacheManager.getOrGenerateIcon(composite, isPage, () -> {
                PoseStack pose = new PoseStack();

                pose.scale(30f, -30f, -30f);

                pose.mulPose(Axis.XP.rotationDegrees(30.0F));
                pose.mulPose(Axis.YP.rotationDegrees(330.0F));

                pose.translate(0, -2.0, 0);

                MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
                Block logBlock = null;
                Block leavesBlock = null;
                Block rootsBlock = null;

                for (Object comp : composite.getComponents()) {
                    if (comp instanceof Block b) {
                        String path = BuiltInRegistries.BLOCK.getKey(b).getPath();
                        if ((path.endsWith("_log") || path.endsWith("_stem") || path.endsWith("_hyphae") || path.endsWith("_wood")) && !path.startsWith("stripped_")) {
                            if (logBlock == null) logBlock = b;
                        }
                        if (path.endsWith("_leaves") || path.endsWith("wart_block")) {
                            if (leavesBlock == null) leavesBlock = b;
                        }
                        if (path.endsWith("_roots") && !path.startsWith("potted_")) {
                            if (rootsBlock == null) rootsBlock = b;
                        }
                    }
                }

                if (logBlock == null) logBlock = (Block) composite.getDisplayEntry();
                if (leavesBlock == null) leavesBlock = logBlock;

                renderMiniTree(pose, buffers, logBlock.defaultBlockState(), leavesBlock.defaultBlockState(), rootsBlock != null ? rootsBlock.defaultBlockState() : null);

                buffers.endBatch();
            });
        }

        int color = ModConfig.get().getListSilhouetteColorInt();
        textureOpt.ifPresent(texture -> drawCachedTexture(guiGraphics, texture, x, y, size, size, silhouette, color, bounceScale));
    }

    private static void renderMiniTree(PoseStack pose, MultiBufferSource.BufferSource buffers, BlockState log, BlockState leaves, BlockState roots) {
        var blockRenderer = Minecraft.getInstance().getBlockRenderer();

        if (roots != null) {
            pose.pushPose();
            pose.translate(-0.5, 0, -0.5);
            blockRenderer.renderSingleBlock(roots, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }

        int trunkStart = roots != null ? 1 : 0;

        for (int y = trunkStart; y <= trunkStart + 3; y++) {
            pose.pushPose();
            pose.translate(-0.5, y, -0.5);
            blockRenderer.renderSingleBlock(log, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }

        int leafBase = trunkStart + 2;

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                pose.pushPose();
                pose.translate(x - 0.5, leafBase, z - 0.5);
                blockRenderer.renderSingleBlock(leaves, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                pose.pushPose();
                pose.translate(x - 0.5, leafBase + 1, z - 0.5);
                blockRenderer.renderSingleBlock(leaves, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (Math.abs(x) == 1 && Math.abs(z) == 1) continue;
                if (x == 0 && z == 0) continue;
                pose.pushPose();
                pose.translate(x - 0.5, leafBase + 2, z - 0.5);
                blockRenderer.renderSingleBlock(leaves, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }

        pose.pushPose();
        pose.translate(-0.5, leafBase + 3, -0.5);
        blockRenderer.renderSingleBlock(leaves, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    private static void drawCachedTexture(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y, int width, int height, boolean silhouette, int color, float bounceScale) {
        int scaledWidth = (int) (width * bounceScale);
        int scaledHeight = (int) (height * bounceScale);
        int drawX = x - scaledWidth / 2;
        int drawY = y - scaledHeight / 2;

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
            Matrix4f matrix = guiGraphics.pose().last().pose();

            consumer.vertex(matrix, drawX, drawY + scaledHeight, 0).color(255, 255, 255, 255).uv(0.0F, 1.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0, 0, 1).endVertex();
            consumer.vertex(matrix, drawX + scaledWidth, drawY + scaledHeight, 0).color(255, 255, 255, 255).uv(1.0F, 1.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0, 0, 1).endVertex();
            consumer.vertex(matrix, drawX + scaledWidth, drawY, 0).color(255, 255, 255, 255).uv(1.0F, 0.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0, 0, 1).endVertex();
            consumer.vertex(matrix, drawX, drawY, 0).color(255, 255, 255, 255).uv(0.0F, 0.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(0, 0, 1).endVertex();

            guiGraphics.flush();

            RenderSystem.setShaderFogStart(Float.MAX_VALUE);
            RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
            RenderSystem.disableDepthTest();
        } else {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableBlend();
            guiGraphics.blit(texture, drawX, drawY, 0, 0, scaledWidth, scaledHeight, scaledWidth, scaledHeight);
        }
    }
}