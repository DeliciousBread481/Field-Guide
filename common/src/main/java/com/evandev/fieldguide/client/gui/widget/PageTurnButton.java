package com.evandev.fieldguide.client.gui.widget;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;

public class PageTurnButton extends ImageButton {
    private boolean playSound = true;
    private SoundEvent customSound = SoundEvents.BOOK_PAGE_TURN;

    public PageTurnButton(int x, int y, int width, int height, int xTexStart, int yTexStart, int yDiffTex, ResourceLocation resourceLocation, Button.OnPress onPress) {
        super(x, y, width, height, xTexStart, yTexStart, yDiffTex, resourceLocation, onPress);
    }

    public PageTurnButton setCustomSound(SoundEvent sound) {
        this.customSound = sound;
        return this;
    }

    public PageTurnButton setPlaySound(boolean playSound) {
        this.playSound = playSound;
        return this;
    }

    @Override
    public void playDownSound(@NotNull SoundManager handler) {
        if (playSound) {
            handler.play(SimpleSoundInstance.forUI(this.customSound, 1.0F));
        }
    }
}