package com.evandev.fieldguide.mixin.client;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.gui.CompendiumScreen;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public class PauseScreenMixin extends Screen {

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "createPauseMenu", at = @At("HEAD"))
    private void addFieldGuideButton(CallbackInfo ci) {

        // TODO: fix position
        int buttonSize = 20;
        int margin = 5;

        this.addRenderableWidget(new ImageButton(
                this.width - buttonSize - margin,
                margin,
                buttonSize,
                buttonSize,
                0,
                0,
                20,
                Constants.BUTTON_TEXTURE,
                20,
                40,
                (button) -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new CompendiumScreen());
                    }
                },
                Component.translatable("gui.fieldguide.open")
        ));
    }

}