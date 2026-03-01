package com.evandev.fieldguide.mixin.client;

import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.platform.Services;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void renderScanningIcon(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!Services.PLATFORM.getPlatformName().equals("Forge")) {
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
            FieldGuideClient.renderScanningIcon(guiGraphics, partialTick);
        }
    }
}