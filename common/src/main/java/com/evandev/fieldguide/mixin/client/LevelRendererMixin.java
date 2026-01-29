package com.evandev.fieldguide.mixin.client;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.gui.util.ScissorBox;
import com.evandev.fieldguide.config.ModConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;
import java.util.Objects;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void renderScanOverlay(PoseStack poseStack, float partialTick, long finishTimeNano, boolean renderBlockOutline, Camera camera, net.minecraft.client.renderer.GameRenderer gameRenderer, net.minecraft.client.renderer.LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        ClientFieldGuideManager manager = ClientFieldGuideManager.getInstance();
        BlockPos pos = null;
        boolean isScanning = false;

        if (manager.getScanningTarget() instanceof Block && manager.getScanningPos() != null) {
            pos = manager.getScanningPos();
            isScanning = true;
        } else if (manager.getFadingTarget() instanceof Block && manager.getFadingPos() != null) {
            pos = manager.getFadingPos();
        }

        if (pos == null) return;

        float progress = isScanning ? manager.getScanProgress(partialTick) : manager.getFadeProgress(partialTick);
        if (progress <= 0.0f) return;

        double baseAlpha = ModConfig.get().scanOverlayAlpha;
        float fillHeight = isScanning ? progress : 1.0f;
        float alpha = (float) (isScanning ? baseAlpha : baseAlpha * progress);

        if (alpha <= 0.01f) return;

        Minecraft mc = Minecraft.getInstance();
        BlockState state = Objects.requireNonNull(mc.level).getBlockState(pos);
        if (state.isAir()) return;

        Vec3 offset = state.getOffset(mc.level, pos);

        Vec3 camPos = camera.getPosition();
        double x = pos.getX() - camPos.x + offset.x;
        double y = pos.getY() - camPos.y + offset.y;
        double z = pos.getZ() - camPos.z + offset.z;

        poseStack.pushPose();
        poseStack.translate(x, y, z);

        boolean useScissor = fillHeight < 1.0f;
        if (useScissor) {
            double shapeHeight = state.getShape(mc.level, pos, CollisionContext.of(Objects.requireNonNull(mc.player))).max(net.minecraft.core.Direction.Axis.Y);
            if (shapeHeight <= 0.001) shapeHeight = 1.0;

            double limitY = shapeHeight * fillHeight;
            ScissorBox scissor = fieldguide$calculateScissor(poseStack, limitY);

            if (scissor != null) {
                RenderSystem.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
            } else {
                poseStack.popPose();
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
            MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

            mc.getBlockRenderer().renderSingleBlock(state, poseStack, bufferSource, 15728880, OverlayTexture.pack(15, 10));

            bufferSource.endBatch();
        } catch (Exception ignored) {
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        RenderSystem.disablePolygonOffset();

        if (useScissor) {
            RenderSystem.disableScissor();
        }

        poseStack.popPose();
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