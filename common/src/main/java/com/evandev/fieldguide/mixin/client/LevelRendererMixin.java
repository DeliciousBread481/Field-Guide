package com.evandev.fieldguide.mixin.client;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.ModRenderTypes;
import com.evandev.fieldguide.client.render.TintedVertexConsumer;
import com.evandev.fieldguide.config.ModConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;
import java.util.Objects;

// TODO: should probably refactor this
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void renderScanOverlays(PoseStack poseStack, float partialTick, long finishTimeNano, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        ClientFieldGuideManager manager = ClientFieldGuideManager.getInstance();
        Minecraft mc = Minecraft.getInstance();

        Entity targetEntity = manager.getScanningEntity() != null ? manager.getScanningEntity() : manager.getFadingEntity();
        BlockPos targetBlock = (manager.getScanningTarget() instanceof Block && manager.getScanningPos() != null) ? manager.getScanningPos() : manager.getFadingPos();

        if (targetEntity == null && targetBlock == null) return;
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        Vec3 camPos = camera.getPosition();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        int colorInt = ModConfig.get().getScanOverlayColorInt();
        Color c = new Color(colorInt);
        float red = c.getRed() / 255.0F;
        float green = c.getGreen() / 255.0F;
        float blue = c.getBlue() / 255.0F;

        if (targetBlock != null) {
            float progress = manager.getScanningTarget() != null ? manager.getScanProgress(partialTick) : manager.getFadeProgress(partialTick);
            if (progress > 0.0f) {
                float fillHeight = manager.getScanningTarget() != null ? progress : 1.0f;
                float alpha = (float) (manager.getScanningTarget() != null ? ModConfig.get().scanOverlayAlpha : ModConfig.get().scanOverlayAlpha * progress);

                if (alpha > 0.01f) {
                    BlockState state = Objects.requireNonNull(mc.level).getBlockState(targetBlock);
                    if (!state.isAir()) {
                        Vec3 offset = state.getOffset(mc.level, targetBlock);
                        double x = targetBlock.getX() - camPos.x + offset.x;
                        double y = targetBlock.getY() - camPos.y + offset.y;
                        double z = targetBlock.getZ() - camPos.z + offset.z;

                        poseStack.pushPose();
                        poseStack.translate(x, y, z);

                        double shapeHeight = state.getShape(mc.level, targetBlock, CollisionContext.of(mc.player)).max(Direction.Axis.Y);
                        float localScanLimitY = (float) (shapeHeight * fillHeight);

                        if (ModRenderTypes.SCAN_BLOCK_SHADER != null) {
                            ModRenderTypes.SCAN_BLOCK_SHADER.getUniform("ScanLimitY").set(localScanLimitY);

                            Matrix4f inverseMat = new Matrix4f(poseStack.last().pose()).invert();
                            ModRenderTypes.SCAN_BLOCK_SHADER.getUniform("InverseModelViewMat").set(inverseMat);
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
            }
        }

        if (targetEntity != null) {
            float progress = manager.getScanningEntity() != null ? manager.getScanProgress(partialTick) : manager.getFadeProgress(partialTick);
            if (progress > 0.0f) {
                float fillHeight = manager.getScanningEntity() != null ? progress : 1.0f;
                float alpha = (float) (manager.getScanningEntity() != null ? ModConfig.get().scanOverlayAlpha : ModConfig.get().scanOverlayAlpha * progress);

                if (alpha > 0.01f) {
                    double x = Mth.lerp(partialTick, targetEntity.xOld, targetEntity.getX()) - camPos.x;
                    double y = Mth.lerp(partialTick, targetEntity.yOld, targetEntity.getY()) - camPos.y;
                    double z = Mth.lerp(partialTick, targetEntity.zOld, targetEntity.getZ()) - camPos.z;

                    poseStack.pushPose();
                    poseStack.translate(x, y, z);

                    double entityHeight = targetEntity.getBoundingBox().maxY - targetEntity.getBoundingBox().minY;
                    float localScanLimitY = (float) (entityHeight * fillHeight);

                    if (ModRenderTypes.SCAN_ENTITY_SHADER != null) {
                        ModRenderTypes.SCAN_ENTITY_SHADER.getUniform("ScanLimitY").set(localScanLimitY);

                        Matrix4f inverseMat = new Matrix4f(poseStack.last().pose()).invert();
                        ModRenderTypes.SCAN_ENTITY_SHADER.getUniform("InverseModelViewMat").set(inverseMat);
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
        }
    }
}