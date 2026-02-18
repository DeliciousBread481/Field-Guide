package com.evandev.fieldguide.client.gui.screens;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.client.data.JournalPage;
import com.evandev.fieldguide.client.gui.util.Bounds;
import com.evandev.fieldguide.client.gui.widget.FieldGuideSearchBox;
import com.evandev.fieldguide.client.gui.widget.PageTurnButton;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.data.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class FieldGuideJournalScreen extends BookScreen {
    private static final int SEARCH_WIDTH = 140;
    private static final int SEARCH_HEIGHT = 20;
    public static int lastOpenedJournalPage = 0;
    final int MAX_LINES = 13;
    private int currentSpread;
    private String journalTitle = "";
    private String leftTitle = "";
    private String rightTitle = "";
    private String leftContent = "";
    private String rightContent = "";
    private FieldGuideSearchBox searchBox;

    // 0 = none, 1 = Journal Title, 2 = Left Title, 3 = Right Title, 4 = Left Content, 5 = Right Content
    private int editingElement = 0;
    private int cursor = 0;
    private int selection = 0;

    private int textXLeft, textXRight, textY, textAreaWidth;

    private PageTurnButton prevButton;
    private PageTurnButton nextButton;

    public FieldGuideJournalScreen(Category category, int pageIndex) {
        super(Component.literal("Journal"));
        this.setSelectedCategory(category);
        this.currentSpread = pageIndex;
        FieldGuideJournalScreen.lastOpenedJournalPage = pageIndex;
    }

    @Override
    protected void init() {
        super.init();

        textXLeft = this.leftPageBounds.left() + 5;
        textXRight = this.rightPageBounds.left() + 5;
        int titleY = this.leftPageBounds.top() + 8;
        int dateY = titleY + this.font.lineHeight + 2;
        textY = dateY + this.font.lineHeight + 6;
        textAreaWidth = this.rightPageBounds.width() - 10;

        loadPageData();

        this.prevButton = this.addRenderableWidget(new PageTurnButton(
                this.bounds.left() + 15, this.leftPageBounds.bottom() - 15,
                16, 16, 0, 0, 16, Constants.PREV_PAGE_TEXTURE, 16, 32,
                b -> {
                    if (currentSpread > 0) {
                        savePageData();
                        currentSpread--;
                        loadPageData();
                        updateButtonVisibility();
                    }
                }
        ));

        // Next Page
        this.nextButton = this.addRenderableWidget(new PageTurnButton(
                this.bounds.right() - 14 - 16, this.rightPageBounds.bottom() - 15,
                16, 16, 0, 0, 16, Constants.NEXT_PAGE_TEXTURE, 16, 32,
                b -> {
                    savePageData();
                    currentSpread++;
                    loadPageData();
                    updateButtonVisibility();
                }
        ));

        updateButtonVisibility();
        int searchX = this.width / 2 - SEARCH_WIDTH / 2;
        int searchY = this.bounds.bottom() + 5;

        this.searchBox = new FieldGuideSearchBox(this.font, searchX, searchY, SEARCH_WIDTH, SEARCH_HEIGHT, "", this::onSearchChanged);
        this.addRenderableWidget(this.searchBox);
    }


    private void onSearchChanged(String query) {
        if (!query.isEmpty() && this.minecraft != null) {
            FieldGuideScreen searchScreen = new FieldGuideScreen(query, this);
            searchScreen.setInitialSearchFocus(true);
            this.minecraft.setScreen(searchScreen);

            if (searchScreen.getSearchBox() != null) {
                searchScreen.getSearchBox().setCursorPosition(query.length());
            }
        }
    }

    private void updateButtonVisibility() {
        if (this.prevButton != null) {
            this.prevButton.visible = this.currentSpread > 0;
        }
    }

    private void loadPageData() {
        ClientFieldGuideManager manager = ClientFieldGuideManager.getInstance();
        journalTitle = manager.getJournalTitle();
        if (journalTitle == null) journalTitle = "";

        int targetSize = currentSpread == 0 ? 1 : currentSpread * 2 + 1;
        while (manager.getJournalPages().size() < targetSize) {
            manager.getJournalPages().add(new JournalPage("", "", System.currentTimeMillis()));
        }

        if (currentSpread > 0) {
            JournalPage lPage = manager.getJournalPages().get(currentSpread * 2 - 1);
            leftTitle = lPage.title;
            leftContent = lPage.content;
        }

        JournalPage rPage = manager.getJournalPages().get(currentSpread == 0 ? 0 : currentSpread * 2);
        rightTitle = rPage.title;
        rightContent = rPage.content;

        FieldGuideJournalScreen.lastOpenedJournalPage = currentSpread;
        editingElement = 0;
    }

    private void savePageData() {
        ClientFieldGuideManager manager = ClientFieldGuideManager.getInstance();
        manager.setJournalTitle(journalTitle);

        if (currentSpread > 0) {
            JournalPage lPage = manager.getJournalPages().get(currentSpread * 2 - 1);
            lPage.title = leftTitle;
            lPage.content = leftContent;
        }

        JournalPage rPage = manager.getJournalPages().get(currentSpread == 0 ? 0 : currentSpread * 2);
        rPage.title = rightTitle;
        rPage.content = rightContent;

        manager.saveJournal();
    }

    private void cleanupEmptyPages() {
        savePageData();
        List<JournalPage> pages = ClientFieldGuideManager.getInstance().getJournalPages();

        for (int i = pages.size() - 1; i > 0; i--) {
            JournalPage p = pages.get(i);
            if (p.title.trim().isEmpty() && p.content.trim().isEmpty()) {
                pages.remove(i);
            } else {
                break;
            }
        }
        ClientFieldGuideManager.getInstance().saveJournal();
    }

    @Override
    public void removed() {
        cleanupEmptyPages();
        super.removed();
    }

    @Override
    public void onTabClick(Category category) {
        if (category.getId().getPath().equals("intro")) return;
        cleanupEmptyPages();
        Objects.requireNonNull(this.minecraft).setScreen(new FieldGuideScreen(category, 0));
    }

    private String getActiveText() {
        return switch (editingElement) {
            case 1 -> journalTitle;
            case 2 -> leftTitle;
            case 3 -> rightTitle;
            case 4 -> leftContent;
            case 5 -> rightContent;
            default -> "";
        };
    }

    private void setActiveText(String text) {
        switch (editingElement) {
            case 1 -> journalTitle = text;
            case 2 -> leftTitle = text;
            case 3 -> rightTitle = text;
            case 4 -> leftContent = text;
            case 5 -> rightContent = text;
        }
    }

    private boolean isMultiline() {
        return editingElement == 4 || editingElement == 5;
    }

    private void applyTextChange(String proposedText, int newCursorPos) {
        if (editingElement == 1) {
            if (this.font.width(proposedText) <= this.font.width("M".repeat(13))) {
                setActiveText(proposedText);
                cursor = newCursorPos;
                selection = cursor;
            }
            return;
        }

        if (editingElement == 2 || editingElement == 3) {
            if (this.font.width(proposedText) <= this.font.width("M".repeat(20))) {
                setActiveText(proposedText);
                cursor = newCursorPos;
                selection = cursor;
            }
            return;
        }

        if (this.font.split(Component.literal(proposedText), textAreaWidth).size() <= MAX_LINES) {
            setActiveText(proposedText);
            cursor = newCursorPos;
            selection = cursor;
            return;
        }

        int splitIndex = getSplitIndexForMaxLines(proposedText);
        String keep = proposedText.substring(0, splitIndex);
        String spill = proposedText.substring(splitIndex);

        setActiveText(keep);

        boolean cursorMovedToSpill = newCursorPos > splitIndex;
        int remainingCursor = cursorMovedToSpill ? newCursorPos - splitIndex : newCursorPos;

        if (!cursorMovedToSpill) {
            cursor = newCursorPos;
            selection = cursor;
        }

        handleSpill(spill, editingElement == 4 ? 5 : -1, cursorMovedToSpill, remainingCursor);
    }

    private void handleSpill(String spill, int targetElement, boolean moveCursor, int remainingCursor) {
        if (targetElement == 5) {
            String newRight = spill + rightContent;
            if (this.font.split(Component.literal(newRight), textAreaWidth).size() <= MAX_LINES) {
                rightContent = newRight;
                if (moveCursor) {
                    editingElement = 5;
                    cursor = remainingCursor;
                    selection = cursor;
                }
            } else {
                int splitIndex = getSplitIndexForMaxLines(newRight);
                rightContent = newRight.substring(0, splitIndex);
                String nextSpill = newRight.substring(splitIndex);

                if (moveCursor && remainingCursor <= splitIndex) {
                    editingElement = 5;
                    cursor = remainingCursor;
                    selection = cursor;
                    moveCursor = false;
                } else if (moveCursor) {
                    remainingCursor -= splitIndex;
                }
                handleSpill(nextSpill, -1, moveCursor, remainingCursor);
            }
        } else if (targetElement == -1) {
            ClientFieldGuideManager manager = ClientFieldGuideManager.getInstance();
            int nextPageIdx = (currentSpread == 0 ? 0 : currentSpread * 2) + 1;

            int safetyLimit = 0;
            while (!spill.isEmpty() && safetyLimit < 100) {
                safetyLimit++;
                while (manager.getJournalPages().size() <= nextPageIdx) {
                    manager.getJournalPages().add(new JournalPage("", "", System.currentTimeMillis()));
                }
                JournalPage nextPage = manager.getJournalPages().get(nextPageIdx);
                String merged = spill + nextPage.content;

                if (this.font.split(Component.literal(merged), textAreaWidth).size() <= MAX_LINES) {
                    nextPage.content = merged;
                    spill = "";
                    if (moveCursor) {
                        savePageData();
                        currentSpread = (nextPageIdx + 1) / 2;
                        loadPageData();
                        editingElement = (nextPageIdx % 2 == 1) ? 4 : 5;
                        cursor = remainingCursor;
                        selection = cursor;
                        moveCursor = false;
                        updateButtonVisibility(); // Make sure visibility stays accurate on spillover
                    }
                } else {
                    int splitIndex = getSplitIndexForMaxLines(merged);
                    nextPage.content = merged.substring(0, splitIndex);
                    String nextSpill = merged.substring(splitIndex);

                    if (moveCursor && remainingCursor <= splitIndex) {
                        savePageData();
                        currentSpread = (nextPageIdx + 1) / 2;
                        loadPageData();
                        editingElement = (nextPageIdx % 2 == 1) ? 4 : 5;
                        cursor = remainingCursor;
                        selection = cursor;
                        moveCursor = false;
                        updateButtonVisibility(); // Make sure visibility stays accurate on spillover
                    } else if (moveCursor) {
                        remainingCursor -= splitIndex;
                    }
                    spill = nextSpill;
                }
                nextPageIdx++;
            }
            manager.saveJournal();
        }
    }

    private int getSplitIndexForMaxLines(String text) {
        int low = 0;
        int high = text.length();
        int best = 0;

        while (low <= high) {
            int mid = (low + high) / 2;
            String sub = text.substring(0, mid);
            if (this.font.split(Component.literal(sub), textAreaWidth).size() <= MAX_LINES) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private void deleteSelection() {
        if (selection != cursor) {
            int start = Math.min(cursor, selection);
            int end = Math.max(cursor, selection);
            String text = getActiveText();
            applyTextChange(text.substring(0, start) + text.substring(end), start);
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

    private int[] getCoordsForIndex(int index, String text, int startX, int startY) {
        index = Math.max(0, Math.min(index, text.length()));
        String before = text.substring(0, index);
        List<FormattedCharSequence> lines = this.font.split(Component.literal(before), textAreaWidth);
        int lineIdx = Math.max(0, lines.size() - 1);
        int cx = startX;
        if (!lines.isEmpty()) {
            cx += this.font.width(lines.get(lines.size() - 1));
        }
        return new int[]{cx, startY + lineIdx * this.font.lineHeight, lineIdx};
    }

    private void moveCursorLine(int dir, String text, int startX, int startY) {
        int[] currentCoords = getCoordsForIndex(cursor, text, startX, startY);
        int targetLine = currentCoords[2] + dir;

        if (targetLine < 0) {
            cursor = 0;
            if (!Screen.hasShiftDown()) selection = cursor;
            return;
        }

        List<FormattedCharSequence> allLines = this.font.split(Component.literal(text), textAreaWidth);
        if (targetLine >= allLines.size()) {
            cursor = text.length();
            if (!Screen.hasShiftDown()) selection = cursor;
            return;
        }

        int bestPos = 0;
        double bestDist = Double.MAX_VALUE;

        for (int i = 0; i <= text.length(); i++) {
            int[] coords = getCoordsForIndex(i, text, startX, startY);
            if (coords[2] == targetLine) {
                double xDist = Math.abs(coords[0] - currentCoords[0]);
                if (xDist < bestDist) {
                    bestDist = xDist;
                    bestPos = i;
                }
            }
        }
        cursor = bestPos;
        if (!Screen.hasShiftDown()) selection = cursor;
    }

    private void setCursorSingleLine(double mouseX, String text, int startX) {
        int relativeX = (int) (mouseX - startX);
        cursor = this.font.plainSubstrByWidth(text, relativeX).length();
        if (!Screen.hasShiftDown()) selection = cursor;
    }

    private void setCursorMultiLine(double mouseX, double mouseY, String text, int startX, int startY) {
        int targetLine = Math.max(0, (int) (mouseY - startY) / this.font.lineHeight);
        List<FormattedCharSequence> allLines = this.font.split(Component.literal(text), textAreaWidth);
        if (targetLine >= allLines.size()) {
            cursor = text.length();
            if (!Screen.hasShiftDown()) selection = cursor;
            return;
        }

        int bestPos = 0;
        double bestDist = Double.MAX_VALUE;

        for (int i = 0; i <= text.length(); i++) {
            int[] coords = getCoordsForIndex(i, text, startX, startY);
            if (coords[2] == targetLine) {
                double xDist = Math.abs(coords[0] - mouseX);
                if (xDist < bestDist) {
                    bestDist = xDist;
                    bestPos = i;
                }
            }
        }
        cursor = bestPos;
        if (!Screen.hasShiftDown()) selection = cursor;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.searchBox != null) {
            this.searchBox.setFocused(this.searchBox.isMouseOver(mouseX, mouseY));
            if (this.searchBox.isFocused()) {
                this.editingElement = 0;
            }
        }

        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        int prevFocus = editingElement;
        editingElement = 0;

        int titleY = this.leftPageBounds.top() + 8;

        // Journal Title
        if (currentSpread == 0) {
            int jtWidth = Math.max(100, this.font.width(journalTitle.isEmpty() ? "Journal" : journalTitle));
            int jtX = this.leftPageBounds.x_center() - jtWidth / 2;
            int jtY = this.leftPageBounds.top() + 36;
            if (Bounds.isMouseOver(mouseX, mouseY, jtX, jtY, jtWidth, this.font.lineHeight)) {
                editingElement = 1;
                setCursorSingleLine(mouseX, journalTitle, jtX);
                this.setFocused(null);
                return true;
            }
        }

        // Left Title
        if (currentSpread > 0) {
            int ltWidth = Math.max(100, this.font.width(leftTitle));
            if (Bounds.isMouseOver(mouseX, mouseY, textXLeft, titleY, ltWidth, this.font.lineHeight)) {
                editingElement = 2;
                setCursorSingleLine(mouseX, leftTitle, textXLeft);
                this.setFocused(null);
                return true;
            }
        }

        // Right Title
        int rtWidth = Math.max(100, this.font.width(rightTitle));
        if (Bounds.isMouseOver(mouseX, mouseY, textXRight, titleY, rtWidth, this.font.lineHeight)) {
            editingElement = 3;
            setCursorSingleLine(mouseX, rightTitle, textXRight);
            this.setFocused(null);
            return true;
        }

        // Left Content
        if (currentSpread > 0) {
            if (mouseX >= textXLeft && mouseX <= textXLeft + textAreaWidth && mouseY >= textY && mouseY <= this.leftPageBounds.bottom() - 20) {
                editingElement = 4;
                setCursorMultiLine(mouseX, mouseY, leftContent, textXLeft, textY);
                this.setFocused(null);
                return true;
            }
        }

        // Right Content
        if (mouseX >= textXRight && mouseX <= textXRight + textAreaWidth && mouseY >= textY && mouseY <= this.rightPageBounds.bottom() - 20) {
            editingElement = 5;
            setCursorMultiLine(mouseX, mouseY, rightContent, textXRight, textY);
            this.setFocused(null);
            return true;
        }

        if (prevFocus != 0) {
            savePageData();
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (editingElement > 0) {
            String text = getActiveText();
            String proposedText;
            int newCursor;

            if (selection != cursor) {
                int start = Math.min(cursor, selection);
                int end = Math.max(cursor, selection);
                proposedText = text.substring(0, start) + codePoint + text.substring(end);
                newCursor = start + 1;
            } else {
                proposedText = text.substring(0, cursor) + codePoint + text.substring(cursor);
                newCursor = cursor + 1;
            }

            applyTextChange(proposedText, newCursor);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.searchBox.setFocused(false);
                return true;
            }
            return this.searchBox.keyPressed(keyCode, scanCode, modifiers);
        }

        if (editingElement > 0) {
            String text = getActiveText();

            if (keyCode == GLFW.GLFW_KEY_SPACE) {
                return true;
            }

            if (Screen.isSelectAll(keyCode)) {
                selection = 0;
                cursor = text.length();
                return true;
            }
            if (Screen.isCopy(keyCode)) {
                if (selection != cursor) {
                    int start = Math.min(cursor, selection);
                    int end = Math.max(cursor, selection);
                    Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(start, end));
                }
                return true;
            }
            if (Screen.isCut(keyCode)) {
                if (selection != cursor) {
                    int start = Math.min(cursor, selection);
                    int end = Math.max(cursor, selection);
                    Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(start, end));
                    deleteSelection();
                }
                return true;
            }
            if (Screen.isPaste(keyCode)) {
                String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (!clipboard.isEmpty()) {
                    int start = Math.min(cursor, selection);
                    int end = Math.max(cursor, selection);
                    String proposedText = text.substring(0, start) + clipboard + text.substring(end);
                    applyTextChange(proposedText, start + clipboard.length());
                }
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (selection != cursor) {
                    deleteSelection();
                } else if (cursor > 0) {
                    String proposedText = text.substring(0, cursor - 1) + text.substring(cursor);
                    applyTextChange(proposedText, cursor - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                if (selection != cursor) {
                    deleteSelection();
                } else if (cursor < text.length()) {
                    String proposedText = text.substring(0, cursor) + text.substring(cursor + 1);
                    applyTextChange(proposedText, cursor);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                if (Screen.hasControlDown()) {
                    cursor = getWordPosition(text, cursor, -1);
                } else if (cursor > 0) {
                    cursor--;
                }
                if (!Screen.hasShiftDown()) selection = cursor;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                if (Screen.hasControlDown()) {
                    cursor = getWordPosition(text, cursor, 1);
                } else if (cursor < text.length()) {
                    cursor++;
                }
                if (!Screen.hasShiftDown()) selection = cursor;
                return true;
            }

            if (isMultiline()) {
                int startX = editingElement == 4 ? textXLeft : textXRight;
                if (keyCode == GLFW.GLFW_KEY_UP) {
                    moveCursorLine(-1, text, startX, textY);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_DOWN) {
                    moveCursorLine(1, text, startX, textY);
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    int start = Math.min(cursor, selection);
                    int end = Math.max(cursor, selection);
                    String proposedText = text.substring(0, start) + "\n" + text.substring(end);
                    applyTextChange(proposedText, start + 1);
                    return true;
                }
            } else {
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    editingElement = 0;
                    savePageData();
                    return true;
                }
            }

            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                editingElement = 0;
                savePageData();
                this.onClose();
                return true;
            }

            if (FieldGuideClient.OPEN_GUIDE_KEY.matches(keyCode, scanCode) || (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode))) {
                return false;
            }

            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        guiGraphics.blit(Constants.BOOK_TEXTURE, this.bounds.left(), this.bounds.top(), 0, 0, this.bounds.width(), this.bounds.height(), this.bounds.width(), this.bounds.height());

        if (currentSpread == 0) {
            guiGraphics.blit(Constants.JOURNAL_TITLE_PAGE_TEXTURE, this.bounds.left(), this.bounds.top(), 0, 0, this.bounds.width(), this.bounds.height(), this.bounds.width(), this.bounds.height());
            guiGraphics.blit(Constants.JOURNAL_LINES_TEXTURE, this.rightPageBounds.left(), this.rightPageBounds.top(), 0, 0, this.rightPageBounds.width(), this.rightPageBounds.height(), this.rightPageBounds.width(), this.rightPageBounds.height());
        } else {
            guiGraphics.blit(Constants.JOURNAL_LINES_TEXTURE, this.leftPageBounds.left(), this.leftPageBounds.top(), 0, 0, this.leftPageBounds.width(), this.leftPageBounds.height(), this.leftPageBounds.width(), this.leftPageBounds.height());
            guiGraphics.blit(Constants.JOURNAL_LINES_TEXTURE, this.rightPageBounds.left(), this.rightPageBounds.top(), 0, 0, this.rightPageBounds.width(), this.rightPageBounds.height(), this.rightPageBounds.width(), this.rightPageBounds.height());
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int leftPageNum = currentSpread * 2;
        int rightPageNum = currentSpread * 2 + 1;

        if (currentSpread > 0) {
            String leftPageStr = leftPageNum + "";
            guiGraphics.drawString(this.font, leftPageStr, this.leftPageBounds.x_center() - this.font.width(leftPageStr) / 2, this.leftPageBounds.bottom() - 11, ModConfig.get().getPageNumberColorInt(), false);
        }
        String rightPageStr = rightPageNum + "";
        guiGraphics.drawString(this.font, rightPageStr, this.rightPageBounds.x_center() - this.font.width(rightPageStr) / 2, this.rightPageBounds.bottom() - 11, ModConfig.get().getPageNumberColorInt(), false);

        int titleY = this.leftPageBounds.top() + 8;
        int dateY = titleY + this.font.lineHeight + 2;
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy");

        if (currentSpread == 0) {
            int jtX = this.leftPageBounds.x_center() - this.font.width(journalTitle) / 2;
            int jtY = this.leftPageBounds.top() + 36;
            renderTextLine(guiGraphics, journalTitle, jtX, jtY, 1);
        } else {
            renderTextLine(guiGraphics, leftTitle, textXLeft, titleY, 2);
            JournalPage lPage = ClientFieldGuideManager.getInstance().getJournalPages().get(currentSpread * 2 - 1);
            String lDateStr = dateFormat.format(new Date(lPage.timestamp));
            guiGraphics.drawString(this.font, lDateStr, textXLeft, dateY, ModConfig.get().getTextMutedColorInt(), false);

            renderPageContent(guiGraphics, leftContent, textXLeft, textY, 4);
        }

        renderTextLine(guiGraphics, rightTitle, textXRight, titleY, 3);
        JournalPage rPage = ClientFieldGuideManager.getInstance().getJournalPages().get(currentSpread == 0 ? 0 : currentSpread * 2);
        String rDateStr = dateFormat.format(new Date(rPage.timestamp));
        guiGraphics.drawString(this.font, rDateStr, textXRight, dateY, ModConfig.get().getTextMutedColorInt(), false);

        renderPageContent(guiGraphics, rightContent, textXRight, textY, 5);
    }

    private void renderTextLine(GuiGraphics guiGraphics, String text, int x, int y, int elementId) {
        if (editingElement == elementId) {
            if (selection != cursor) {
                int start = Math.min(cursor, selection);
                int end = Math.max(cursor, selection);
                int selStartX = x + this.font.width(text.substring(0, start));
                int selEndX = x + this.font.width(text.substring(0, end));
                guiGraphics.fill(selStartX, y, selEndX, y + this.font.lineHeight, 0x550000FF);
            }
        }

        guiGraphics.drawString(this.font, text, x, y, ModConfig.get().getTextTitleColorInt(), false);

        if (editingElement == elementId && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = x + this.font.width(text.substring(0, cursor));
            String cursorChar = (cursor == text.length()) ? "_" : "|";
            guiGraphics.drawString(this.font, cursorChar, cursorX, y, ModConfig.get().getTextTitleColorInt(), false);
        }
    }

    private void renderPageContent(GuiGraphics guiGraphics, String text, int x, int y, int elementId) {
        List<FormattedCharSequence> lines = this.font.split(Component.literal(text), textAreaWidth);
        for (int i = 0; i < lines.size(); i++) {
            guiGraphics.drawString(this.font, lines.get(i), x, y + i * this.font.lineHeight, ModConfig.get().getTextColorInt(), false);
        }

        if (editingElement == elementId) {
            if (selection != cursor) {
                int start = Math.min(cursor, selection);
                int end = Math.max(cursor, selection);

                int[] startCoords = getCoordsForIndex(start, text, x, y);
                int[] endCoords = getCoordsForIndex(end, text, x, y);

                for (int line = startCoords[2]; line <= endCoords[2]; line++) {
                    int lineStartX = x;
                    if (line == startCoords[2]) lineStartX = startCoords[0];

                    int lineEndX = x + this.font.width(lines.get(Math.min(line, Math.max(0, lines.size() - 1))));
                    if (line == endCoords[2]) lineEndX = endCoords[0];

                    guiGraphics.fill(lineStartX, y + line * this.font.lineHeight, lineEndX, y + (line + 1) * this.font.lineHeight, 0x550000FF);
                }
            }

            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                int[] coords = getCoordsForIndex(cursor, text, x, y);
                String cursorChar = (cursor == text.length()) ? "_" : "|";
                guiGraphics.drawString(this.font, cursorChar, coords[0], y + coords[2] * this.font.lineHeight, ModConfig.get().getTextColorInt(), false);
            }
        }
    }
}