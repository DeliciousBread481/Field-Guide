package com.evandev.fieldguide.client.gui.screens;

import io.github.mortuusars.exposure.gui.screen.PhotographScreen;
import io.github.mortuusars.exposure.item.PhotographItem;
import io.github.mortuusars.exposure.util.ItemAndStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

public class FieldGuidePhotographScreen extends PhotographScreen {
    private final Screen parentScreen;

    public FieldGuidePhotographScreen(Screen parentScreen, List<ItemAndStack<PhotographItem>> photographs) {
        super(photographs);
        this.parentScreen = parentScreen;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parentScreen);
    }
}