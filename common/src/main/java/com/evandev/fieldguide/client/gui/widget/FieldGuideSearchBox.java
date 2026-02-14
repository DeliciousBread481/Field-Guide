package com.evandev.fieldguide.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class FieldGuideSearchBox extends EditBox {
    private static final int BORDER_COLOR_NORMAL = 0xFF51291D;
    private static final int BORDER_COLOR_FOCUSED = 0xFFDDC69B;
    private static final int BACKGROUND_COLOR = 0xFF050302;
    private static final int PLACEHOLDER_COLOR = 0xC7A875;

    public FieldGuideSearchBox(Font font, int x, int y, int width, int height, String value, Consumer<String> onSearch) {
        super(font, x, y, width, height, Component.translatable("gui.fieldguide.search"));
        this.setMaxLength(50);
        this.setBordered(true);
        this.setVisible(true);
        this.setTextColor(0xFCF3DD);
        this.setValue(value);
        this.setResponder(onSearch);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int boxX = this.getX() - 2;
        int boxY = this.getY() - 2;
        int boxW = this.width + 4;
        int boxH = this.height + 4;

        int borderColor = this.isFocused() ? BORDER_COLOR_FOCUSED : BORDER_COLOR_NORMAL;

        guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, BACKGROUND_COLOR);

        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);

        if (this.getValue().isEmpty()) {
            guiGraphics.drawString(Minecraft.getInstance().font, Component.translatable("gui.fieldguide.search"), this.getX() + 4, this.getY() + 6, PLACEHOLDER_COLOR, false);
        }

        guiGraphics.renderOutline(this.getX() - 1, this.getY() - 1, this.width + 2, this.height + 2, borderColor);
    }
}