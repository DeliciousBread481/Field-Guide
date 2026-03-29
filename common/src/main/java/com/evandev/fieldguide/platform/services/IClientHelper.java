package com.evandev.fieldguide.platform.services;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;

public interface IClientHelper {

    /**
     * Opens the Field Guide UI.
     */
    void openFieldGuide();

    /**
     * Pre-render hook for entities in the Field Guide.
     */
    default void preRenderEntity(Entity entity) {
    }

    /**
     * Custom render hook for entities. Return true to skip default rendering.
     */
    default boolean renderEntity(Entity entity, double x, double y, double z, float yRot, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        return false;
    }

    /**
     * Post-render hook for entities in the Field Guide.
     */
    default void postRenderEntity(Entity entity) {
    }
}
