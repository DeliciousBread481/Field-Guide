package com.evandev.fieldguide.mixin.client;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.ModRenderTypes;
import com.evandev.fieldguide.client.gui.util.ScissorBox;
import com.evandev.fieldguide.client.gui.util.ScissorBoxHelper;
import com.evandev.fieldguide.client.render.TintedVertexConsumer;
import com.evandev.fieldguide.config.ModConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
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

            double visualHeight = Math.max(shapeHeight, 1.0);

            double limitY = visualHeight * fillHeight;
            ScissorBox scissor = ScissorBoxHelper.calculateScissor(poseStack, limitY);

            if (scissor != null) {
                RenderSystem.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
            } else {
                poseStack.popPose();
                return;
            }
        }

        int colorInt = ModConfig.get().getScanOverlayColorInt();
        Color c = new Color(colorInt);
        float red = c.getRed() / 255.0F;
        float green = c.getGreen() / 255.0F;
        float blue = c.getBlue() / 255.0F;

        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1.0f, -1.0f);

        try {
            MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

            RenderType targetType;
            targetType = ModRenderTypes.getScanRenderType(InventoryMenu.BLOCK_ATLAS);

            MultiBufferSource tintedSource = requestedType -> new TintedVertexConsumer(
                    bufferSource.getBuffer(targetType),
                    red, green, blue, alpha
            );

            mc.getBlockRenderer().renderSingleBlock(
                    state,
                    poseStack,
                    tintedSource,
                    15728880,
                    OverlayTexture.pack(0, 10)
            );

            bufferSource.endBatch();
        } catch (Exception ignored) {
        }

        RenderSystem.disablePolygonOffset();
        if (useScissor) RenderSystem.disableScissor();
        poseStack.popPose();
    }
}