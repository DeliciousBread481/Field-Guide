package com.evandev.fieldguide.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class FieldGuideSearchBox extends EditBox {
    private static final int BORDER_COLOR_NORMAL = 0xFF7A583C;
    private static final int BORDER_COLOR_FOCUSED = 0xFFC6A678;
    private static final int BACKGROUND_COLOR = 0xFF000000;
    private static final int PLACEHOLDER_COLOR = 0x888888;

    public FieldGuideSearchBox(Font font, int x, int y, int width, int height, Consumer<String> onSearch) {
        super(font, x, y, width, height, Component.translatable("gui.fieldguide.search"));
        this.setMaxLength(50);
        this.setBordered(false);
        this.setVisible(true);
        this.setTextColor(0xFFFFFF);
        this.setResponder(onSearch);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int boxX = this.getX() - 4;
        int boxY = this.getY() - 4;
        int boxW = this.width + 8;
        int boxH = this.height + 4;

        int borderColor = this.isFocused() ? BORDER_COLOR_FOCUSED : BORDER_COLOR_NORMAL;

        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, BACKGROUND_COLOR);
        guiGraphics.renderOutline(boxX, boxY, boxW, boxH, borderColor);

        if (this.getValue().isEmpty()) {
            guiGraphics.drawString(Minecraft.getInstance().font, Component.translatable("gui.fieldguide.search"), this.getX(), this.getY(), PLACEHOLDER_COLOR, false);
        }

        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
    }
}