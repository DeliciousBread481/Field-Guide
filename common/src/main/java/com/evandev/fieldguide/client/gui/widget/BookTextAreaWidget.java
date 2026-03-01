package com.evandev.fieldguide.client.gui.widget;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BookTextAreaWidget extends AbstractWidget {
    private final Font font;
    private final int maxVisibleLines;
    private final int textColor;
    private final boolean scrollable;
    private final Consumer<String> onChanged;
    private final List<Integer> lineStarts = new ArrayList<>();
    private String text;
    private int cursorPos;
    private int selectionPos;
    private int scrollOffset = 0;
    private boolean isDraggingScrollbar = false;
    private Consumer<String> onSpillover;

    public BookTextAreaWidget(Font font, int x, int y, int width, int height, int maxVisibleLines, int textColor, boolean scrollable, String initialText, Consumer<String> onChanged) {
        super(x, y, width, height, Component.empty());
        this.font = font;
        this.maxVisibleLines = maxVisibleLines;
        this.textColor = textColor;
        this.scrollable = scrollable;
        this.text = initialText == null ? "" : initialText;
        this.computeLineStarts();
        this.onChanged = onChanged;
        this.cursorPos = this.text.length();
        this.selectionPos = this.cursorPos;
    }

    public void setOnSpillover(Consumer<String> onSpillover) {
        this.onSpillover = onSpillover;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
        this.computeLineStarts();
        this.cursorPos = Math.min(this.cursorPos, text.length());
        this.selectionPos = this.cursorPos;
    }

    private void computeLineStarts() {
        lineStarts.clear();
        lineStarts.add(0);
        int lineStart = 0;
        int lastSpace = -1;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lineStart = i + 1;
                lastSpace = -1;
                lineStarts.add(lineStart);
                continue;
            }
            if (c == ' ') {
                lastSpace = i;
            }
            String currentSub = text.substring(lineStart, i + 1);
            if (this.font.width(currentSub) > this.width) {
                if (lastSpace != -1 && lastSpace >= lineStart) {
                    lineStart = lastSpace + 1;
                } else {
                    lineStart = i;
                }
                lastSpace = -1;
                lineStarts.add(lineStart);
            }
        }
    }

    private void updateText(String proposedText, int newCursorPos) {
        if (scrollable) {
            this.text = proposedText;
            this.computeLineStarts();
            this.cursorPos = newCursorPos;
            this.selectionPos = this.cursorPos;
            this.onChanged.accept(this.text);
            scrollToCursor();
            return;
        }

        if (this.font.split(Component.literal(proposedText), this.width).size() <= maxVisibleLines) {
            this.text = proposedText;
            this.computeLineStarts();
            this.cursorPos = newCursorPos;
            this.selectionPos = this.cursorPos;
            this.onChanged.accept(this.text);
        } else if (onSpillover != null) {
            int splitIndex = getSplitIndexForMaxLines(proposedText);
            this.text = proposedText.substring(0, splitIndex);
            this.computeLineStarts();
            String spill = proposedText.substring(splitIndex);

            boolean cursorMovedToSpill = newCursorPos > splitIndex;
            if (!cursorMovedToSpill) {
                this.cursorPos = newCursorPos;
                this.selectionPos = this.cursorPos;
            }
            this.onChanged.accept(this.text);

            if (cursorMovedToSpill) {
                this.setFocused(false);
            }
            onSpillover.accept(spill);
        }
    }

    private int getSplitIndexForMaxLines(String text) {
        int low = 0, high = text.length(), best = 0;
        while (low <= high) {
            int mid = (low + high) / 2;
            if (this.font.split(Component.literal(text.substring(0, mid)), this.width).size() <= maxVisibleLines) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (scrollable && button == 0 && isScrollbarHovered(mouseX, mouseY)) {
            isDraggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }

        if (this.isMouseOver(mouseX, mouseY)) {
            this.setFocused(true);
            setCursorPosFromMouse(mouseX, mouseY);
            return true;
        }
        this.setFocused(false);
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingScrollbar) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDraggingScrollbar) {
            isDraggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!scrollable || !this.isMouseOver(mouseX, mouseY)) return false;
        List<FormattedCharSequence> lines = this.font.split(Component.literal(text), this.width);
        if (lines.size() > maxVisibleLines) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int) Math.signum(scrollY), lines.size() - maxVisibleLines));
            return true;
        }
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
        updateText(proposedText, newCursor);
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
                updateText(text.substring(0, start) + clipboard + text.substring(end), start + clipboard.length());
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (selectionPos != cursorPos) {
                deleteSelection();
            } else if (cursorPos > 0) {
                updateText(text.substring(0, cursorPos - 1) + text.substring(cursorPos), cursorPos - 1);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (selectionPos != cursorPos) {
                deleteSelection();
            } else if (cursorPos < text.length()) {
                updateText(text.substring(0, cursorPos) + text.substring(cursorPos + 1), cursorPos);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if (Screen.hasControlDown()) cursorPos = getWordPosition(text, cursorPos, -1);
            else if (cursorPos > 0) cursorPos--;
            if (!Screen.hasShiftDown()) selectionPos = cursorPos;
            scrollToCursor();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (Screen.hasControlDown()) cursorPos = getWordPosition(text, cursorPos, 1);
            else if (cursorPos < text.length()) cursorPos++;
            if (!Screen.hasShiftDown()) selectionPos = cursorPos;
            scrollToCursor();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            moveCursorLine(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            moveCursorLine(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            int start = Math.min(cursorPos, selectionPos);
            int end = Math.max(cursorPos, selectionPos);
            updateText(text.substring(0, start) + "\n" + text.substring(end), start + 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.setFocused(false);
            return true;
        }
        return false;
    }

    private void deleteSelection() {
        if (selectionPos != cursorPos) {
            int start = Math.min(cursorPos, selectionPos);
            int end = Math.max(cursorPos, selectionPos);
            updateText(text.substring(0, start) + text.substring(end), start);
        }
    }

    private int getWordPosition(String text, int cPos, int dir) {
        int pos = cPos;
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

    private int[] getCoordsForIndex(int index) {
        index = Math.max(0, Math.min(index, text.length()));

        int targetLine = 0;
        for (int i = 0; i < lineStarts.size(); i++) {
            if (index >= lineStarts.get(i)) {
                targetLine = i;
            } else {
                break;
            }
        }

        int startOfTargetLine = lineStarts.get(targetLine);
        String textBeforeCursorOnLine = text.substring(startOfTargetLine, index);
        int cx = this.getX() + this.font.width(textBeforeCursorOnLine);

        return new int[]{cx, this.getY() + targetLine * this.font.lineHeight, targetLine};
    }

    private void moveCursorLine(int dir) {
        int[] currentCoords = getCoordsForIndex(cursorPos);
        int targetLine = currentCoords[2] + dir;
        List<FormattedCharSequence> allLines = this.font.split(Component.literal(text), this.width);

        if (targetLine < 0) {
            cursorPos = 0;
        } else if (targetLine >= allLines.size()) {
            cursorPos = text.length();
        } else {
            int bestPos = 0;
            double bestDist = Double.MAX_VALUE;
            for (int i = 0; i <= text.length(); i++) {
                int[] coords = getCoordsForIndex(i);
                if (coords[2] == targetLine) {
                    double xDist = Math.abs(coords[0] - currentCoords[0]);
                    if (xDist < bestDist) {
                        bestDist = xDist;
                        bestPos = i;
                    }
                }
            }
            cursorPos = bestPos;
        }
        if (!Screen.hasShiftDown()) selectionPos = cursorPos;
        scrollToCursor();
    }

    private void setCursorPosFromMouse(double mouseX, double mouseY) {
        int relativeY = (int) (mouseY - this.getY());
        int targetLine = (relativeY / this.font.lineHeight) + scrollOffset;
        targetLine = Math.max(0, targetLine);

        List<FormattedCharSequence> allLines = this.font.split(Component.literal(text), this.width);
        if (targetLine >= allLines.size()) {
            cursorPos = text.length();
            if (!Screen.hasShiftDown()) selectionPos = cursorPos;
            return;
        }

        int bestPos = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i <= text.length(); i++) {
            int[] coords = getCoordsForIndex(i);
            if (coords[2] == targetLine) {
                double xDist = Math.abs(coords[0] - mouseX);
                if (xDist < bestDist) {
                    bestDist = xDist;
                    bestPos = i;
                }
            }
        }
        cursorPos = bestPos;
        if (!Screen.hasShiftDown()) selectionPos = cursorPos;
    }

    private void scrollToCursor() {
        if (!scrollable) return;
        int targetLine = getCoordsForIndex(cursorPos)[2];
        if (targetLine < scrollOffset) scrollOffset = targetLine;
        else if (targetLine >= scrollOffset + maxVisibleLines) scrollOffset = targetLine - maxVisibleLines + 1;
    }

    private boolean isScrollbarHovered(double mouseX, double mouseY) {
        List<FormattedCharSequence> lines = this.font.split(Component.literal(text), this.width);
        if (lines.size() <= maxVisibleLines) return false;
        int scrollbarX = this.getX() + this.width + 2;
        int scrollbarHeight = (maxVisibleLines * this.font.lineHeight) - 2;
        int hitPadding = 4;
        return mouseX >= scrollbarX - hitPadding && mouseX <= scrollbarX + 2 + hitPadding && mouseY >= this.getY() && mouseY <= this.getY() + scrollbarHeight;
    }

    private void updateScrollFromMouse(double mouseY) {
        List<FormattedCharSequence> lines = this.font.split(Component.literal(text), this.width);
        int totalLines = lines.size();
        if (totalLines > maxVisibleLines) {
            int scrollbarHeight = (maxVisibleLines * this.font.lineHeight) - 2;
            int thumbHeight = Math.max(4, (int) ((float) maxVisibleLines / totalLines * scrollbarHeight));
            float progress = (float) (mouseY - this.getY() - (thumbHeight / 2.0f)) / (scrollbarHeight - thumbHeight);
            progress = Math.max(0.0f, Math.min(1.0f, progress));
            scrollOffset = (int) (progress * (totalLines - maxVisibleLines) + 0.5f);
        }
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        List<FormattedCharSequence> lines = this.font.split(Component.literal(text), this.width);
        int totalLines = lines.size();
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, totalLines - maxVisibleLines)));

        for (int i = 0; i < maxVisibleLines && (i + scrollOffset) < totalLines; i++) {
            guiGraphics.drawString(this.font, lines.get(i + scrollOffset), this.getX(), this.getY() + i * this.font.lineHeight, textColor, false);
        }

        if (this.isFocused()) {
            if (selectionPos != cursorPos) {
                int start = Math.min(cursorPos, selectionPos);
                int end = Math.max(cursorPos, selectionPos);
                int[] startCoords = getCoordsForIndex(start);
                int[] endCoords = getCoordsForIndex(end);

                for (int line = startCoords[2]; line <= endCoords[2]; line++) {
                    if (scrollable && (line < scrollOffset || line >= scrollOffset + maxVisibleLines)) continue;
                    int visibleLine = line - scrollOffset;
                    int lineStartX = (line == startCoords[2]) ? startCoords[0] : this.getX();
                    int lineEndX = (line == endCoords[2]) ? endCoords[0] : this.getX() + this.font.width(lines.get(line));
                    guiGraphics.fill(lineStartX, this.getY() + visibleLine * this.font.lineHeight, lineEndX, this.getY() + (visibleLine + 1) * this.font.lineHeight, 0x550000FF);
                }
            }

            int[] coords = getCoordsForIndex(cursorPos);
            int cursorLine = coords[2];
            if (!scrollable || (cursorLine >= scrollOffset && cursorLine < scrollOffset + maxVisibleLines)) {
                int visibleLine = cursorLine - scrollOffset;
                this.renderCursor(guiGraphics, coords[0], this.getY() + visibleLine * this.font.lineHeight);
            }
        }

        if (scrollable && totalLines > maxVisibleLines) {
            int scrollbarY = this.getY() - 1;
            int scrollbarX = this.getX() + this.width + 1;
            int scrollbarHeight = (maxVisibleLines * this.font.lineHeight);
            float progress = (float) scrollOffset / (totalLines - maxVisibleLines);
            int thumbHeight = Math.max(4, (int) ((float) maxVisibleLines / totalLines * scrollbarHeight));
            int thumbY = scrollbarY + (int) (progress * (scrollbarHeight - thumbHeight));

            ResourceLocation trackSprite = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "widget/scrollbar_track");
            ResourceLocation thumbSprite = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "widget/scrollbar_thumb" + (isDraggingScrollbar || isScrollbarHovered(mouseX, mouseY) ? "_hovered" : ""));

            guiGraphics.blitSprite(trackSprite, scrollbarX, scrollbarY, 4, scrollbarHeight);
            guiGraphics.blitSprite(thumbSprite, scrollbarX, thumbY, 4, thumbHeight);
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