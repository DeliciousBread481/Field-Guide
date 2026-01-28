package com.evandev.fieldguide.mixin.client;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void renderScanOverlay(Entity entity, double x, double y, double z, float rotationYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        ClientFieldGuideManager manager = ClientFieldGuideManager.getInstance();
        boolean isScanning = manager.getScanningEntity() == entity;
        boolean isFading = manager.getFadingEntity() == entity;

        if (!isScanning && !isFading) return;

        float progress = 1.0f;
        float alpha = 0.4f;

        if (isScanning) {
            progress = manager.getScanProgress(partialTicks);
        } else {
            alpha *= manager.getFadeProgress();
        }

        float width = entity.getBbWidth();
        float height = entity.getBbHeight();
        float currentHeight = height * progress;

        float inflation = 0.1F + (width * 0.25F);

        float halfW = (width / 2.0F) + inflation;
        float minH = -inflation;
        float maxH = Math.min(height + inflation, currentHeight + inflation);

        poseStack.pushPose();
        poseStack.translate(x, y, z);

        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        // Front
        fieldguide$drawDoubleSidedQuad(consumer, matrix,
                halfW, minH, halfW,
                halfW, maxH, halfW,
                -halfW, maxH, halfW,
                -halfW, minH, halfW,
                alpha);

        // Back
        fieldguide$drawDoubleSidedQuad(consumer, matrix,
                -halfW, minH, -halfW,
                -halfW, maxH, -halfW,
                halfW, maxH, -halfW,
                halfW, minH, -halfW,
                alpha);

        // Left
        fieldguide$drawDoubleSidedQuad(consumer, matrix,
                -halfW, minH, halfW,
                -halfW, maxH, halfW,
                -halfW, maxH, -halfW,
                -halfW, minH, -halfW,
                alpha);

        // Right
        fieldguide$drawDoubleSidedQuad(consumer, matrix,
                halfW, minH, -halfW,
                halfW, maxH, -halfW,
                halfW, maxH, halfW,
                halfW, minH, halfW,
                alpha);

        poseStack.popPose();
    }

    /**
     * Helper to draw a quad visible from both sides by drawing it twice with opposite winding orders.
     */
    @Unique
    private void fieldguide$drawDoubleSidedQuad(VertexConsumer consumer, Matrix4f matrix,
                                                float x1, float y1, float z1,
                                                float x2, float y2, float z2,
                                                float x3, float y3, float z3,
                                                float x4, float y4, float z4,
                                                float alpha) {
        // Outside face
        consumer.vertex(matrix, x1, y1, z1).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        consumer.vertex(matrix, x2, y2, z2).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        consumer.vertex(matrix, x3, y3, z3).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        consumer.vertex(matrix, x4, y4, z4).color(1.0F, 1.0F, 1.0F, alpha).endVertex();

        // Inside face
        consumer.vertex(matrix, x4, y4, z4).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        consumer.vertex(matrix, x3, y3, z3).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        consumer.vertex(matrix, x2, y2, z2).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
        consumer.vertex(matrix, x1, y1, z1).color(1.0F, 1.0F, 1.0F, alpha).endVertex();
    }
}