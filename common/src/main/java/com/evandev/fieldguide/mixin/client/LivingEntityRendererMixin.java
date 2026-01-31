package com.evandev.fieldguide.mixin.client;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.ScanRenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Unique
    private static final ResourceLocation FIELDGUIDE$WHITE_TEXTURE = new ResourceLocation(Constants.MOD_ID, "textures/misc/overlay.png");

    @Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
    private static void modifyOverlayCoords(LivingEntity entity, float partialTicks, CallbackInfoReturnable<Integer> cir) {
        if (ScanRenderState.isScanning()) {
            cir.setReturnValue(OverlayTexture.pack(0, 10));
        }
    }

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void modifyRenderType(LivingEntity entity, boolean bodyVisible, boolean translucent, boolean glowing, CallbackInfoReturnable<RenderType> cir) {
        if (ScanRenderState.isScanning()) {
            cir.setReturnValue(RenderType.entityTranslucent(FIELDGUIDE$WHITE_TEXTURE));
        }
    }
}