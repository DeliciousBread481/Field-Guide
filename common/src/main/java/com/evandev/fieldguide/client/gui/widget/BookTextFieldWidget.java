package com.evandev.fieldguide.client.gui.widget;

import com.evandev.fieldguide.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class BookTextFieldWidget extends AbstractWidget {
    private final Font font;
    private final int textColor;
    private final int highlightColor = 0x550000FF;
    private final Consumer<String> onChanged;
    private final int maxLength;
    private String text;
    private int cursorPos;
    private int selectionPos;
    private boolean centered = false;

    public BookTextFieldWidget(Font font, int x, int y, int width, int height, String text, int textColor, int maxLength, Consumer<String> onChanged) {
        super(x, y, width, height, Component.empty());
        this.font = font;
        this.textColor = textColor;
        this.text = text == null ? "" : text;
        this.maxLength = maxLength;
        this.onChanged = onChanged;
        this.cursorPos = this.text.length();
        this.selectionPos = this.cursorPos;
    }

    public BookTextFieldWidget setCentered(boolean centered) {
        this.centered = centered;
        return this;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
        this.cursorPos = Math.min(this.text.length(), this.cursorPos);
        this.selectionPos = Math.min(this.text.length(), this.selectionPos);
    }

    private void tryUpdateText(String newText, int newCursorPos, int newSelectionPos) {
        if (font.width(newText) <= maxLength) {
            this.text = newText;
            this.cursorPos = newCursorPos;
            this.selectionPos = newSelectionPos;
            this.onChanged.accept(this.text);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.isMouseOver(mouseX, mouseY)) {
            this.setFocused(true);
            int renderX = this.centered ? this.getX() + (this.width - this.font.width(text)) / 2 : this.getX();
            int relativeX = (int) (mouseX - renderX);
            cursorPos = this.font.plainSubstrByWidth(text, Math.max(0, relativeX)).length();
            if (!Screen.hasShiftDown()) selectionPos = cursorPos;
            return true;
        }
        this.setFocused(false);
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!this.isFocused()) return false;
        String proposedText;
        int newCursor;
        if (selectionPos != cursorPos) {
            int start = Math.min(cursorPos, selectionPos);
            int end = Math.max(cursorPos, selectionPos);
            proposedText = text.substring(0, start) + codePoint + text.substring(end);
            newCursor = start + 1;
        } else {
            proposedText = text.substring(0, cursorPos) + codePoint + text.substring(cursorPos);
            newCursor = cursorPos + 1;
        }

        tryUpdateText(proposedText, newCursor, newCursor);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.isFocused()) return false;

        if (Screen.isSelectAll(keyCode)) {
            selectionPos = 0;
            cursorPos = text.length();
            return true;
        }
        if (Screen.isCopy(keyCode)) {
            if (selectionPos != cursorPos) {
                int start = Math.min(cursorPos, selectionPos);
                int end = Math.max(cursorPos, selectionPos);
                Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(start, end));
            }
            return true;
        }
        if (Screen.isCut(keyCode)) {
            if (selectionPos != cursorPos) {
                int start = Math.min(cursorPos, selectionPos);
                int end = Math.max(cursorPos, selectionPos);
                Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(start, end));
                deleteSelection();
            }
            return true;
        }
        if (Screen.isPaste(keyCode)) {
            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (!clipboard.isEmpty()) {
                int start = Math.min(cursorPos, selectionPos);
                int end = Math.max(cursorPos, selectionPos);
                String proposedText = text.substring(0, start) + clipboard + text.substring(end);
                tryUpdateText(proposedText, start + clipboard.length(), start + clipboard.length());
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (selectionPos != cursorPos) {
                deleteSelection();
            } else if (cursorPos > 0) {
                String proposedText = text.substring(0, cursorPos - 1) + text.substring(cursorPos);
                tryUpdateText(proposedText, cursorPos - 1, cursorPos - 1);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (selectionPos != cursorPos) {
                deleteSelection();
            } else if (cursorPos < text.length()) {
                String proposedText = text.substring(0, cursorPos) + text.substring(cursorPos + 1);
                tryUpdateText(proposedText, cursorPos, cursorPos);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if (Screen.hasControlDown()) cursorPos = getWordPosition(text, cursorPos, -1);
            else if (cursorPos > 0) cursorPos--;
            if (!Screen.hasShiftDown()) selectionPos = cursorPos;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (Screen.hasControlDown()) cursorPos = getWordPosition(text, cursorPos, 1);
            else if (cursorPos < text.length()) cursorPos++;
            if (!Screen.hasShiftDown()) selectionPos = cursorPos;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.setFocused(false);
            return true;
        }
        return false;
    }

    private void deleteSelection() {
        if (selectionPos != cursorPos) {
            int start = Math.min(cursorPos, selectionPos);
            int end = Math.max(cursorPos, selectionPos);
            String proposedText = text.substring(0, start) + text.substring(end);
            tryUpdateText(proposedText, start, start);
        }
    }

    private int getWordPosition(String text, int cursor, int dir) {
        int pos = cursor;
        if (dir < 0) {
            while (pos > 0 && Character.isWhitespace(text.charAt(pos - 1))) pos--;
            while (pos > 0 && !Character.isWhitespace(text.charAt(pos - 1))) pos--;
        } else {
            int len = text.length();
            while (pos < len && Character.isWhitespace(text.charAt(pos))) pos++;
            while (pos < len && !Character.isWhitespace(text.charAt(pos))) pos++;
        }
        return pos;
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        cursorPos = Math.max(0, Math.min(cursorPos, text.length()));
        selectionPos = Math.max(0, Math.min(selectionPos, text.length()));

        int renderX = this.centered ? this.getX() + (this.width - this.font.width(text)) / 2 : this.getX();

        if (this.isFocused() && selectionPos != cursorPos) {
            int start = Math.min(cursorPos, selectionPos);
            int end = Math.max(cursorPos, selectionPos);
            int selStartX = renderX + this.font.width(text.substring(0, start));
            int selEndX = renderX + this.font.width(text.substring(0, end));
            guiGraphics.fill(selStartX, this.getY(), selEndX, this.getY() + this.font.lineHeight, highlightColor);
        }

        guiGraphics.drawString(this.font, text, renderX, this.getY(), textColor, false);


        if (this.isFocused()) {
            int cursorX = renderX + this.font.width(text.substring(0, cursorPos));
            this.renderCursor(guiGraphics, cursorX, this.getY());
        }
    }

    private void renderCursor(GuiGraphics guiGraphics, int x, int y) {
        if ((System.currentTimeMillis() / 400) % 2 == 0) {
            if (cursorPos == text.length()) {
                guiGraphics.drawString(this.font, "_", x, y, ModConfig.get().getTextCursorColorInt(), false);
            } else {
                int cursorWidth = 1;
                int cursorHeight = this.font.lineHeight;
                guiGraphics.fill(x, y - 1, x + cursorWidth, y - 1 + cursorHeight, ModConfig.get().getTextCursorColorInt());
            }
        }
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
    }
}