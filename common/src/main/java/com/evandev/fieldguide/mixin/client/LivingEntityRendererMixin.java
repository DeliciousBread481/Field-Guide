package com.evandev.fieldguide.mixin.client;

import com.evandev.fieldguide.client.ScanRenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
    private static void forceWhiteOverlay(LivingEntity entity, float partialTicks, CallbackInfoReturnable<Integer> cir) {
        if (ScanRenderState.isScanning()) {
            cir.setReturnValue(OverlayTexture.pack(15, 10));
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void forceTranslucentRenderType(LivingEntity entity, boolean bodyVisible, boolean translucent, boolean glowing, CallbackInfoReturnable<RenderType> cir) {
        if (ScanRenderState.isScanning()) {
            EntityRenderer<LivingEntity> renderer = (EntityRenderer<LivingEntity>) (Object) this;
            ResourceLocation texture = renderer.getTextureLocation(entity);

            cir.setReturnValue(RenderType.entityTranslucent(texture));
        }
    }
}