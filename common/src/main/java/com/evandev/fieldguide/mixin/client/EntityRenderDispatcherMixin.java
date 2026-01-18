package com.evandev.fieldguide.mixin.client;

import com.evandev.fieldguide.data.FieldGuideDataManager;
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
        FieldGuideDataManager manager = FieldGuideDataManager.getInstance();
        boolean isScanning = manager.getScanningEntity() == entity;
        boolean isFading = manager.getFadingEntity() == entity;

        if (!isScanning && !isFading) return;

        float progress = 1.0f;
        float alpha = 0.4f;

        if (isScanning) {
            progress = manager.getScanProgress();
        } else {
            alpha *= manager.getFadeProgress();
        }

        float width = entity.getBbWidth();
        float height = entity.getBbHeight();
        float currentHeight = height * progress;
        float w2 = width / 2.0F;

        poseStack.pushPose();
        poseStack.translate(x, y, z);

        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        fieldguide$addQuad(consumer, matrix, -w2, w2, 0, 0, -w2, w2, alpha);
        fieldguide$addQuad(consumer, matrix, -w2, w2, currentHeight, currentHeight, -w2, w2, alpha);
        fieldguide$addQuad(consumer, matrix, -w2, w2, 0, currentHeight, w2, w2, alpha);
        fieldguide$addQuad(consumer, matrix, -w2, w2, 0, currentHeight, -w2, -w2, alpha);
        fieldguide$addQuad(consumer, matrix, -w2, -w2, 0, currentHeight, -w2, w2, alpha);
        fieldguide$addQuad(consumer, matrix, w2, w2, 0, currentHeight, -w2, w2, alpha);

        poseStack.popPose();
    }

    @Unique
    private void fieldguide$addQuad(VertexConsumer consumer, Matrix4f matrix, float xMin, float xMax, float yMin, float yMax, float zMin, float zMax, float alpha) {
        float r = 1.0f;
        float g = 1.0f;
        float b = 1.0f;

        consumer.vertex(matrix, xMin, yMin, zMin).color(r, g, b, alpha).endVertex();
        consumer.vertex(matrix, xMin, yMax, zMin).color(r, g, b, alpha).endVertex();
        consumer.vertex(matrix, xMax, yMax, zMax).color(r, g, b, alpha).endVertex();
        consumer.vertex(matrix, xMax, yMin, zMax).color(r, g, b, alpha).endVertex();
    }
}