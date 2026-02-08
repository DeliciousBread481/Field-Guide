package com.evandev.fieldguide.mixin.client;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(
            method = "renderSpyglassOverlay",
            at = @At("TAIL")
    )
    private void renderScanningIcon(GuiGraphics guiGraphics, float scopeScale, CallbackInfo ci) {
        ClientFieldGuideManager manager = ClientFieldGuideManager.getInstance();
        if (manager.getScanningTarget() != null) {
            float progress = manager.getScanProgress(0);

            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();

            int frames = 4;
            int frame = (int)Math.min(Math.floor(progress * frames), frames - 1);

            int textureSize = 32;
            int offset = 32;
            int x = (screenWidth - textureSize) / 2;
            int y = (screenHeight - textureSize) / 2 + offset;
            guiGraphics.blit(Constants.SCANNING_ICON_TEXTURE, x, y, 0, textureSize * frame, textureSize, textureSize, textureSize, textureSize * frames);
        }
    }
}
