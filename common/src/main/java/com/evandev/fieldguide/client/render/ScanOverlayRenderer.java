package com.evandev.fieldguide.client.render;

import com.evandev.fieldguide.client.ModRenderTypes;
import com.evandev.fieldguide.client.scanning.FieldGuideScanner;
import com.evandev.fieldguide.config.ModConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.Objects;

public class ScanOverlayRenderer {
    private static final float VERTICAL_BUFFER = 1.3f;

    public static void render(PoseStack poseStack, float partialTick, Camera camera, MultiBufferSource.BufferSource bufferSource) {
        FieldGuideScanner scanner = FieldGuideScanner.getInstance();
        Minecraft mc = Minecraft.getInstance();

        Entity outOfRangeEntity = scanner.getOutOfRangeEntity();
        Entity targetEntity = scanner.getScanningEntity() != null ? scanner.getScanningEntity() : (scanner.getFadingEntity() != null ? scanner.getFadingEntity() : outOfRangeEntity);
        BlockPos outOfRangePos = scanner.getOutOfRangePos();
        BlockPos targetBlock = (scanner.getScanningTarget() instanceof Block && scanner.getScanningPos() != null) ? scanner.getScanningPos() : (scanner.getFadingPos() != null ? scanner.getFadingPos() : outOfRangePos);

        if (targetEntity == null && targetBlock == null) return;
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        Vec3 camPos = camera.getPosition();
        float red, green, blue, alpha;

        if ((outOfRangeEntity != null && targetEntity == outOfRangeEntity) || (outOfRangePos != null && targetBlock == outOfRangePos)) {
            red = 1.0F;
            green = 0.0F;
            blue = 0.0F;
            float pulse = (float) (Math.sin(System.currentTimeMillis() / 200.0) * 0.5 + 0.5);
            alpha = 0.0F + (pulse * 0.2F);
        } else {
            int colorInt = ModConfig.get().getScanOverlayColorInt();
            Color c = new Color(colorInt);
            red = c.getRed() / 255.0F;
            green = c.getGreen() / 255.0F;
            blue = c.getBlue() / 255.0F;
            alpha = (float) (scanner.getScanningEntity() != null || scanner.getScanningTarget() != null
                    ? ModConfig.get().scanOverlayAlpha
                    : ModConfig.get().scanOverlayAlpha * scanner.getFadeProgress(partialTick));
        }

        if (targetBlock != null && alpha > 0.01f) {
            renderBlockOverlay(poseStack, partialTick, camPos, bufferSource, targetBlock, scanner, mc, red, green, blue, alpha);
        }

        if (targetEntity != null && alpha > 0.01f) {
            renderEntityOverlay(poseStack, partialTick, camPos, bufferSource, targetEntity, outOfRangeEntity, scanner, mc, red, green, blue, alpha);
        }
    }

    private static void renderBlockOverlay(PoseStack poseStack, float partialTick, Vec3 camPos, MultiBufferSource.BufferSource bufferSource, BlockPos targetBlock, FieldGuideScanner scanner, Minecraft mc, float red, float green, float blue, float alpha) {
        boolean isOutOfRange = scanner.getOutOfRangePos() != null && targetBlock == scanner.getOutOfRangePos();
        float progress = isOutOfRange ? 1.0f : (scanner.getScanningTarget() != null ? scanner.getScanProgress(partialTick) : scanner.getFadeProgress(partialTick));
        if (progress <= 0.0f) return;

        float fillHeight = scanner.getScanningTarget() != null ? progress : 1.0f;
        BlockState state = Objects.requireNonNull(mc.level).getBlockState(targetBlock);

        if (!state.isAir()) {
            Vec3 offset = state.getOffset(mc.level, targetBlock);
            double x = targetBlock.getX() - camPos.x + offset.x;
            double y = targetBlock.getY() - camPos.y + offset.y;
            double z = targetBlock.getZ() - camPos.z + offset.z;

            poseStack.pushPose();
            poseStack.translate(x, y, z);

            double shapeHeight = 0;
            if (mc.player != null) {
                shapeHeight = state.isCollisionShapeFullBlock(mc.level, targetBlock) ? 1.0 : Math.max(1.0, state.getShape(mc.level, targetBlock, CollisionContext.of(mc.player)).max(Direction.Axis.Y));
            }
            float localScanLimitY = fillHeight >= 1.0f ? 10000.0f : (float) (shapeHeight * fillHeight);

            if (ModRenderTypes.SCAN_BLOCK_SHADER != null) {
                ModRenderTypes.SCAN_BLOCK_SHADER.getUniform("ScanLimitY").set(localScanLimitY);
                Matrix4f modelViewMat = new Matrix4f(poseStack.last().pose());
                ModRenderTypes.SCAN_BLOCK_SHADER.getUniform("InverseModelViewMat").set(modelViewMat.invert());
            }

            MultiBufferSource depthSource = requestedType -> new TintedVertexConsumer(bufferSource.getBuffer(ModRenderTypes.wrapForDepth(requestedType, false)), 1, 1, 1, 1);
            mc.getBlockRenderer().renderSingleBlock(state, poseStack, depthSource, 15728880, OverlayTexture.pack(0, 10));
            bufferSource.endBatch();

            MultiBufferSource tintedSource = requestedType -> new TintedVertexConsumer(bufferSource.getBuffer(ModRenderTypes.wrapForScan(requestedType, false)), red, green, blue, alpha);
            mc.getBlockRenderer().renderSingleBlock(state, poseStack, tintedSource, 15728880, OverlayTexture.pack(0, 10));
            bufferSource.endBatch();

            poseStack.popPose();
        }
    }

    private static void renderEntityOverlay(PoseStack poseStack, float partialTick, Vec3 camPos, MultiBufferSource.BufferSource bufferSource, Entity targetEntity, Entity outOfRangeEntity, FieldGuideScanner scanner, Minecraft mc, float red, float green, float blue, float alpha) {
        float fillHeight = (outOfRangeEntity != null || scanner.getScanningEntity() == null) ? 1.0f : scanner.getScanProgress(partialTick);

        double x = Mth.lerp(partialTick, targetEntity.xOld, targetEntity.getX()) - camPos.x;
        double y = Mth.lerp(partialTick, targetEntity.yOld, targetEntity.getY()) - camPos.y;
        double z = Mth.lerp(partialTick, targetEntity.zOld, targetEntity.getZ()) - camPos.z;

        poseStack.pushPose();
        poseStack.translate(x, y, z);

        double entityHeight = targetEntity.getBbHeight();
        float localScanLimitY = fillHeight >= 1.0f ? 10000.0f : (float) (entityHeight * fillHeight * VERTICAL_BUFFER);

        if (ModRenderTypes.SCAN_ENTITY_SHADER != null) {
            ModRenderTypes.SCAN_ENTITY_SHADER.getUniform("ScanLimitY").set(localScanLimitY);
            Matrix4f modelViewMat = new Matrix4f(poseStack.last().pose());
            ModRenderTypes.SCAN_ENTITY_SHADER.getUniform("InverseModelViewMat").set(modelViewMat.invert());
        }

        @SuppressWarnings("unchecked")
        EntityRenderer<Entity> renderer = (EntityRenderer<Entity>) mc.getEntityRenderDispatcher().getRenderer(targetEntity);
        float yaw = Mth.lerp(partialTick, targetEntity.yRotO, targetEntity.getYRot());

        MultiBufferSource depthSource = requestedType -> new TintedVertexConsumer(bufferSource.getBuffer(ModRenderTypes.wrapForDepth(requestedType, true)), 1, 1, 1, 1);
        renderer.render(targetEntity, yaw, partialTick, poseStack, depthSource, 15728880);
        bufferSource.endBatch();

        MultiBufferSource forcedSource = requestedType -> new TintedVertexConsumer(bufferSource.getBuffer(ModRenderTypes.wrapForScan(requestedType, true)), red, green, blue, alpha);
        renderer.render(targetEntity, yaw, partialTick, poseStack, forcedSource, 15728880);
        bufferSource.endBatch();

        poseStack.popPose();
    }
}