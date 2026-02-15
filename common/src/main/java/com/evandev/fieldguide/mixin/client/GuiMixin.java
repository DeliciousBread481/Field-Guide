package com.evandev.fieldguide.mixin.client;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(
            method = "render",
            at = @At("TAIL")
    )
    private void renderScanningIcon(GuiGraphics guiGraphics, float scopeScale, CallbackInfo ci) {
        ClientFieldGuideManager manager = ClientFieldGuideManager.getInstance();

        boolean outOfRange = manager.getOutOfRangeEntity() != null;
        boolean isFading = manager.getFadingTarget() != null;
        boolean hasTarget = manager.getScanningTarget() != null;

        if (hasTarget || isFading || outOfRange) {
            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            int textureSize = 32;
            int offset = 10;
            int x = (screenWidth - textureSize) / 2;
            int y = (screenHeight - textureSize) / 2 + offset;

            int frame;
            int totalFramesInTexture = 6;

            if (outOfRange) {
                frame = 5;
            } else if (isFading) {
                // Only show last frame after success
                frame = 4;
            } else {
                float progress = manager.getScanProgress(0);

                if (manager.getIsTickingDown()) {
                    return;
                }

                int animationFrames = 4;
                frame = (int) Math.min(Math.floor(progress * animationFrames), animationFrames - 1);
            }

            guiGraphics.blit(Constants.SCANNING_ICON_TEXTURE, x, y, 0, textureSize * frame, textureSize, textureSize, textureSize, textureSize * totalFramesInTexture);
        }
    }
}