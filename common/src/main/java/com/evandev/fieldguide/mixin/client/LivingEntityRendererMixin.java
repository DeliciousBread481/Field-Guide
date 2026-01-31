package com.evandev.fieldguide.mixin.client;

import com.evandev.fieldguide.client.ModRenderTypes;
import com.evandev.fieldguide.client.ScanRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends EntityModel<T>> extends EntityRenderer<T> {

    protected LivingEntityRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
    private static void modifyOverlayCoords(LivingEntity entity, float partialTicks, CallbackInfoReturnable<Integer> cir) {
        if (ScanRenderState.isScanning()) {
            cir.setReturnValue(OverlayTexture.pack(0, 10));
        }
    }

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void modifyRenderType(T entity, boolean bodyVisible, boolean translucent, boolean glowing, CallbackInfoReturnable<RenderType> cir) {
        if (ScanRenderState.isScanning()) {
            ResourceLocation texture = this.getTextureLocation(entity);
            cir.setReturnValue(ModRenderTypes.getScanRenderType(texture));
        }
    }
}