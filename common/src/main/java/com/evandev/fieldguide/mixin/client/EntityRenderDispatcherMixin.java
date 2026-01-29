package com.evandev.fieldguide.mixin.client;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.ScanRenderState;
import com.evandev.fieldguide.client.gui.util.ScissorBox;
import com.evandev.fieldguide.config.ModConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "render", at = @At("RETURN"))
    private void renderScanOverlay(Entity entity, double x, double y, double z, float rotationYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (ScanRenderState.isScanning() || !(entity instanceof LivingEntity)) return;

        if (buffer instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch();
        }

        ClientFieldGuideManager manager = ClientFieldGuideManager.getInstance();
        boolean isScanning = manager.getScanningEntity() == entity;
        boolean isFading = manager.getFadingEntity() == entity;

        if (!isScanning && !isFading) return;

        float progress = isScanning ? manager.getScanProgress(partialTicks) : manager.getFadeProgress(partialTicks);
        if (progress <= 0.0f) return;

        double baseAlpha = ModConfig.get().scanOverlayAlpha;
        float fillHeight = isScanning ? progress : 1.0f;
        float alpha = (float) (isScanning ? baseAlpha : baseAlpha * progress);

        if (alpha <= 0.01f) return;

        ScanRenderState.setScanning(true);
        poseStack.pushPose();
        poseStack.translate(x, y, z);

        try {
            fieldguide$renderWhiteSilhouette(entity, partialTicks, poseStack, packedLight, fillHeight, alpha);
        } finally {
            poseStack.popPose();
            ScanRenderState.setScanning(false);
        }
    }

    @Unique
    private void fieldguide$renderWhiteSilhouette(Entity entity, float partialTicks, PoseStack poseStack, int light, float fillPercent, float alpha) {
        EntityRenderer<? super Entity> renderer = ((EntityRenderDispatcher) (Object) this).getRenderer(entity);
        boolean useScissor = fillPercent < 1.0f;

        if (useScissor) {
            double entityHeight = entity.getBoundingBox().maxY - entity.getBoundingBox().minY;
            double limitY = entityHeight * fillPercent;
            ScissorBox scissor = fieldguide$calculateScissor(poseStack, limitY);

            if (scissor != null) {
                RenderSystem.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
            } else {
                return;
            }
        }

        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1.0f, -1.0f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int colorInt = ModConfig.get().getScanOverlayColorInt();
        Color c = new Color(colorInt);
        RenderSystem.setShaderColor(c.getRed() / 255.0F, c.getGreen() / 255.0F, c.getBlue() / 255.0F, alpha);

        try {
            MultiBufferSource.BufferSource immediate = Minecraft.getInstance().renderBuffers().bufferSource();
            float yaw = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());

            renderer.render(entity, yaw, partialTicks, poseStack, immediate, light);
            immediate.endBatch();
        } catch (Exception ignored) {
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        RenderSystem.disablePolygonOffset();

        if (useScissor) {
            RenderSystem.disableScissor();
        }
    }

    @Unique
    private ScissorBox fieldguide$calculateScissor(PoseStack poseStack, double limitY) {
        Minecraft mc = Minecraft.getInstance();
        Matrix4f modelView = poseStack.last().pose();
        Matrix4f projection = RenderSystem.getProjectionMatrix();

        Vector4f bottomPos = new Vector4f(0, 0, 0, 1.0f);
        Vector4f topPos = new Vector4f(0, (float) limitY, 0, 1.0f);

        bottomPos.mul(modelView);
        bottomPos.mul(projection);

        topPos.mul(modelView);
        topPos.mul(projection);

        // Check if points are behind the camera
        if (bottomPos.w() <= 0 && topPos.w() <= 0) return null;

        Vector3f ndcTop = new Vector3f(topPos.x() / topPos.w(), topPos.y() / topPos.w(), topPos.z() / topPos.w());

        int winWidth = mc.getWindow().getWidth();
        int winHeight = mc.getWindow().getHeight();

        int yEnd = (int) ((ndcTop.y() + 1) * 0.5f * winHeight);
        int scissorHeight = Math.max(0, yEnd);

        if (scissorHeight > winHeight) scissorHeight = winHeight;

        return new ScissorBox(0, 0, winWidth, scissorHeight);
    }
}