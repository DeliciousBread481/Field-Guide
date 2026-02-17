package com.evandev.fieldguide.client.gui.screens;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.client.data.EntryVisual;
import com.evandev.fieldguide.client.gui.util.Bounds;
import com.evandev.fieldguide.client.gui.util.EntryRenderHelper;
import com.evandev.fieldguide.client.gui.widget.FieldGuideSearchBox;
import com.evandev.fieldguide.client.gui.widget.PageTurnButton;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.platform.Services;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.*;

public class FieldGuideEntryScreen extends BookScreen {
    private final BookScreen parent;
    private final Object entry;
    private final List<ResourceLocation> spawnBiomes = new ArrayList<>();
    private final int biomesPerPage = 6;
    private final int maxVisibleLines = 9;
    private Entity renderedEntity;
    private long lastClickTime = 0;
    private int currentBiomePage = 1;
    private ResourceLocation hoveredBiome;
    private ItemStack hoveredItem;
    private ImageButton prevBiomePageButton;
    private ImageButton nextBiomePageButton;
    private FieldGuideSearchBox searchBox;

    private int scrollOffset = 0;
    private boolean isDraggingScrollbar = false;
    private String editableDescription = "";
    private boolean isEditingDescription = false;
    private int cursorPos = 0;
    private int selectionPos = 0;

    private int lootScrollOffset = 0;
    private String editableName = "";
    private boolean isEditingName = false;
    private int nameCursorPos = 0;
    private int nameSelectionPos = 0;

    private int textX, textY, textAreaWidth, textAreaHeight;

    public FieldGuideEntryScreen(BookScreen parent, Object entry) {
        super(getTitleForEntry(entry));
        this.parent = parent;
        this.entry = entry;
    }

    private static Component getTitleForEntry(Object entry) {
        if (ClientFieldGuideManager.isUnlocked(entry) || ModConfig.get().showUndiscoveredNames) {
            return ClientFieldGuideManager.getEntryName(entry);
        }
        return Component.translatable("fieldguide.undiscovered");
    }

    @Override
    protected void init() {
        Category category = ClientFieldGuideManager.getInstance().getCategoryForEntry(entry);
        if (category != null) {
            this.setSelectedCategory(category);
        } else if (parent != null) {
            this.setSelectedCategory(parent.getSelectedCategory());
        }

        super.init();
        spawnBiomes.clear();

        if (ClientFieldGuideManager.isUnlocked(entry)) {
            editableDescription = ClientFieldGuideManager.getEntryDescription(entry);
            cursorPos = editableDescription.length();
            selectionPos = cursorPos;

            editableName = ClientFieldGuideManager.getEntryName(entry).getString();
            nameCursorPos = editableName.length();
            nameSelectionPos = nameCursorPos;
        }

        textX = this.rightPageBounds.left() + 5;
        textY = this.rightPageBounds.top() + 25;
        textAreaWidth = this.rightPageBounds.width() - 10;
        textAreaHeight = this.rightPageBounds.height() - 85;

        if (entry instanceof EntityType<?> type) {
            if (this.minecraft != null && this.minecraft.level != null) {
                try {
                    this.renderedEntity = type.create(this.minecraft.level);
                } catch (Exception e) {
                    Constants.LOG.error("Failed to create entity preview: {}", type.getDescription().getString(), e);
                    this.renderedEntity = null;
                }
            }
        }

        ResourceLocation entryId = ClientFieldGuideManager.getEntryId(entry);
        EntryVisual visual = entryId != null ? ClientFieldGuideManager.getInstance().getEntryVisual(entryId) : null;

        if (visual != null && visual.spawnBiomes != null) {
            spawnBiomes.addAll(visual.spawnBiomes);
        } else if (entry instanceof EntityType<?> entityType) {
            if (Services.PLATFORM.isModLoaded("immersiveoverlays")) {
                Registry<Biome> biomeRegistry = null;
                if (this.minecraft.level != null) {
                    biomeRegistry = this.minecraft.level.registryAccess().registryOrThrow(Registries.BIOME);
                }

                try {
                    if (biomeRegistry != null) {
                        for (var biomeEntry : biomeRegistry.entrySet()) {
                            ResourceLocation id = biomeEntry.getKey().location();
                            Biome biome = biomeEntry.getValue();

                            var spawns = biome.getMobSettings().getMobs(entityType.getCategory());

                            if (spawns.unwrap().stream().anyMatch(s -> s.type == entityType)) {
                                ResourceLocation texture = new ResourceLocation(id.getNamespace(), "textures/immersiveoverlays/" + id.getPath() + ".png");
                                if (this.minecraft.getResourceManager().getResource(texture).isPresent()) {
                                    spawnBiomes.add(id);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Constants.LOG.error("Failed to load spawn biomes for Field Guide", e);
                }
            }
        }

        // Navigation Buttons
        this.addRenderableWidget(new PageTurnButton(
                this.bounds.right() + 9 - 24,
                this.bounds.top() + 26,
                24,
                24,
                0,
                0,
                24,
                Constants.BACK_TEXTURE,
                24,
                24 * 2,
                b -> Objects.requireNonNull(this.minecraft).setScreen(parent)
        ));

        PageTurnButton prevEntryButton = new PageTurnButton(
                this.bounds.left() + 15,
                this.leftPageBounds.bottom() - 15,
                16, 16, 0, 0, 16,
                Constants.PREV_PAGE_TEXTURE, 16, 32,
                b -> prevEntry()
        );

        PageTurnButton nextEntryButton = new PageTurnButton(
                this.bounds.right() - 14 - 16,
                this.rightPageBounds.bottom() - 15,
                16, 16, 0, 0, 16,
                Constants.NEXT_PAGE_TEXTURE, 16, 32,
                b -> nextEntry()
        );

        Category currentCat = this.getSelectedCategory();
        if (currentCat != null) {
            List<Object> entries = ClientFieldGuideManager.getInstance().getEntriesForCategory(currentCat);
            int index = entries.indexOf(this.entry);
            prevEntryButton.visible = index > 0;
            nextEntryButton.visible = index >= 0 && index < entries.size() - 1;
        } else {
            prevEntryButton.visible = false;
            nextEntryButton.visible = false;
        }

        this.addRenderableWidget(prevEntryButton);
        this.addRenderableWidget(nextEntryButton);

        // Biome Buttons
        this.nextBiomePageButton = new ImageButton(
                this.leftPageBounds.right() - 13,
                this.leftPageBounds.bottom() - 24,
                16,
                16,
                16,
                0,
                16,
                Constants.BIOME_PAGINATION_BUTTONS_TEXTURE,
                32,
                48,
                b -> currentBiomePage = (int) Math.min(Math.ceil((double) spawnBiomes.size() / biomesPerPage), currentBiomePage + 1)
        );

        this.prevBiomePageButton = new ImageButton(
                this.leftPageBounds.left() - 3,
                this.leftPageBounds.bottom() - 24,
                16,
                16,
                0,
                0,
                16,
                Constants.BIOME_PAGINATION_BUTTONS_TEXTURE,
                32,
                48,
                b -> currentBiomePage = Math.max(1, currentBiomePage - 1)
        );

        nextBiomePageButton.visible = false;
        prevBiomePageButton.visible = false;
        this.addRenderableWidget(nextBiomePageButton);
        this.addRenderableWidget(prevBiomePageButton);

        int searchX = this.width / 2 - 140 / 2;
        int searchY = this.bounds.bottom() + 5;
        this.searchBox = new FieldGuideSearchBox(this.font, searchX, searchY, 140, 20, "", this::onSearchChanged);
        this.addRenderableWidget(this.searchBox);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int dropItemSize = 20;
        int maxVisibleDropLines = 2;
        int dropsAreaHeight = maxVisibleDropLines * dropItemSize + (maxVisibleDropLines - 1);
        int dropsAreaY = this.rightPageBounds.bottom() - 6 - dropsAreaHeight;

        if (mouseX >= this.rightPageBounds.left() && mouseX <= this.rightPageBounds.right() && mouseY >= dropsAreaY && mouseY <= this.rightPageBounds.bottom() - 6) {
            lootScrollOffset = Math.max(0, lootScrollOffset - (int) Math.signum(delta));
            return true;
        }

        if (isEditingDescription || (mouseX >= textX && mouseX <= textX + textAreaWidth && mouseY >= textY && mouseY <= textY + textAreaHeight)) {
            List<FormattedCharSequence> lines = this.font.split(Component.literal(editableDescription), textAreaWidth);
            int totalLines = lines.size();

            if (totalLines > maxVisibleLines) {
                scrollOffset = Math.max(0, Math.min(scrollOffset - (int) Math.signum(delta), totalLines - maxVisibleLines));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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

    private void prevEntry() {
        Category currentCat = this.getSelectedCategory();
        if (currentCat == null) return;
        List<Object> entries = ClientFieldGuideManager.getInstance().getEntriesForCategory(currentCat);
        int index = entries.indexOf(this.entry);
        if (index > 0 && this.minecraft != null) {
            this.minecraft.setScreen(new FieldGuideEntryScreen(parent, entries.get(index - 1)));
        }
    }

    private void nextEntry() {
        Category currentCat = this.getSelectedCategory();
        if (currentCat == null) return;
        List<Object> entries = ClientFieldGuideManager.getInstance().getEntriesForCategory(currentCat);
        int index = entries.indexOf(this.entry);
        if (index >= 0 && index < entries.size() - 1 && this.minecraft != null) {
            this.minecraft.setScreen(new FieldGuideEntryScreen(parent, entries.get(index + 1)));
        }
    }

    private void scrollToCursor(int targetLine) {
        if (targetLine < scrollOffset) {
            scrollOffset = targetLine;
        } else if (targetLine >= scrollOffset + maxVisibleLines) {
            scrollOffset = targetLine - maxVisibleLines + 1;
        }
    }

    private void saveDescriptionIfChanged() {
        if (ClientFieldGuideManager.isUnlocked(entry)) {
            String originalDescription = ClientFieldGuideManager.getEntryDescription(entry);
            if (originalDescription == null) originalDescription = "";
            if (!editableDescription.equals(originalDescription)) {
                ClientFieldGuideManager.setCustomDescription(entry, editableDescription);
            }
        }
    }

    private void saveNameIfChanged() {
        if (ClientFieldGuideManager.isUnlocked(entry)) {
            ClientFieldGuideManager.setCustomName(entry, editableName);
        }
    }

    @Override
    public void removed() {
        if (isEditingDescription) {
            saveDescriptionIfChanged();
            isEditingDescription = false;
        }
        if (isEditingName) {
            saveNameIfChanged();
            isEditingName = false;
        }
    }

    @Override
    public void onTabClick(Category category) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
            parent.onTabClick(category);
        }
    }

    private int[] getCoordsForIndex(int index) {
        index = Math.max(0, Math.min(index, editableDescription.length()));
        String before = editableDescription.substring(0, index);
        List<FormattedCharSequence> lines = this.font.split(Component.literal(before), textAreaWidth);
        int lineIdx = lines.size() - 1;
        if (lineIdx < 0) lineIdx = 0;
        int cx = textX;
        if (!lines.isEmpty()) {
            cx += this.font.width(lines.get(lines.size() - 1));
        }
        return new int[]{cx, textY + lineIdx * this.font.lineHeight, lineIdx};
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

    private void moveCursorLine(int dir) {
        int[] currentCoords = getCoordsForIndex(cursorPos);
        int targetLine = currentCoords[2] + dir;

        if (targetLine < 0) {
            cursorPos = 0;
            if (!Screen.hasShiftDown()) selectionPos = cursorPos;
            return;
        }

        List<FormattedCharSequence> allLines = this.font.split(Component.literal(editableDescription), textAreaWidth);
        if (targetLine >= allLines.size()) {
            cursorPos = editableDescription.length();
            if (!Screen.hasShiftDown()) selectionPos = cursorPos;
            return;
        }

        int bestPos = 0;
        double bestDist = Double.MAX_VALUE;

        for (int i = 0; i <= editableDescription.length(); i++) {
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
        if (!Screen.hasShiftDown()) {
            selectionPos = cursorPos;
        }
    }

    private void setCursorPosFromMouse(double mouseX, double mouseY) {
        int relativeY = (int) (mouseY - textY);
        int targetLine = (relativeY / this.font.lineHeight) + scrollOffset;
        if (targetLine < 0) targetLine = 0;

        List<FormattedCharSequence> allLines = this.font.split(Component.literal(editableDescription), textAreaWidth);
        if (targetLine >= allLines.size()) {
            cursorPos = editableDescription.length();
            if (!Screen.hasShiftDown()) selectionPos = cursorPos;
            return;
        }

        int bestPos = 0;
        double bestDist = Double.MAX_VALUE;

        for (int i = 0; i <= editableDescription.length(); i++) {
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
        if (!Screen.hasShiftDown()) {
            selectionPos = cursorPos;
        }
    }

    private void deleteSelection() {
        if (selectionPos != cursorPos) {
            int start = Math.min(cursorPos, selectionPos);
            int end = Math.max(cursorPos, selectionPos);
            editableDescription = editableDescription.substring(0, start) + editableDescription.substring(end);
            cursorPos = start;
            selectionPos = start;
        }
    }

    private void deleteNameSelection() {
        if (nameSelectionPos != nameCursorPos) {
            int start = Math.min(nameCursorPos, nameSelectionPos);
            int end = Math.max(nameCursorPos, nameSelectionPos);
            editableName = editableName.substring(0, start) + editableName.substring(end);
            nameCursorPos = start;
            nameSelectionPos = start;
        }
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<FormattedCharSequence> lines = this.font.split(Component.literal(editableDescription), textAreaWidth);
        if (lines.size() > maxVisibleLines && button == 0) {
            int scrollbarX = textX + textAreaWidth + 2;
            int scrollbarY = textY;
            int scrollbarHeight = (maxVisibleLines * this.font.lineHeight) - 2;
            int hitPadding = 4;

            if (mouseX >= scrollbarX - hitPadding && mouseX <= scrollbarX + 2 + hitPadding &&
                    mouseY >= scrollbarY && mouseY <= scrollbarY + scrollbarHeight) {
                isDraggingScrollbar = true;
                updateScrollFromMouse(mouseY);
                return true;
            }
        }

        if (this.searchBox != null) {
            if (this.searchBox.isMouseOver(mouseX, mouseY)) {
                this.searchBox.setFocused(true);
                if (isEditingDescription) {
                    isEditingDescription = false;
                    saveDescriptionIfChanged();
                }
                if (isEditingName) {
                    isEditingName = false;
                    saveNameIfChanged();
                }
            } else {
                this.searchBox.setFocused(false);
            }
        }

        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (ClientFieldGuideManager.isUnlocked(entry)) {
            int titleY = this.leftPageBounds.top() + 8;
            int titleX = this.rightPageBounds.left() + 5;
            int titleWidth = this.font.width(editableName.isEmpty() ? getTitleForEntry(entry).getString() : editableName);

            if (mouseX >= titleX && mouseX <= titleX + Math.max(titleWidth, 50) && mouseY >= titleY && mouseY <= titleY + this.font.lineHeight) {
                isEditingName = true;
                if (isEditingDescription) {
                    isEditingDescription = false;
                    saveDescriptionIfChanged();
                }
                if (this.searchBox != null) this.searchBox.setFocused(false);

                int relativeX = (int) (mouseX - titleX);
                nameCursorPos = this.font.plainSubstrByWidth(editableName, relativeX).length();
                if (!Screen.hasShiftDown()) nameSelectionPos = nameCursorPos;
                return true;
            } else if (isEditingName) {
                isEditingName = false;
                saveNameIfChanged();
            }

            if (mouseX >= textX && mouseX <= textX + textAreaWidth && mouseY >= textY && mouseY <= textY + textAreaHeight) {
                isEditingDescription = true;
                if (this.searchBox != null) this.searchBox.setFocused(false);
                setCursorPosFromMouse(mouseX, mouseY);
                return true;
            } else if (isEditingDescription) {
                isEditingDescription = false;
                saveDescriptionIfChanged();
            }
        }

        if (button == 0 && (renderedEntity != null || entry instanceof Block)) {
            int xPos = leftPageBounds.left() + leftPageBounds.width() / 2;
            int yPos = leftPageBounds.y_center();
            int halfSize = 50;

            if (mouseX >= xPos - halfSize && mouseX <= xPos + halfSize &&
                    mouseY >= yPos - halfSize && mouseY <= yPos + halfSize) {

                if (ClientFieldGuideManager.isUnlocked(entry)) {
                    if (entry instanceof EntityType<?> && renderedEntity != null) {
                        FieldGuideClient.playMobCry(this.renderedEntity);
                    } else if (entry instanceof Block block) {
                        if (this.minecraft != null) {
                            SoundType soundType = block.defaultBlockState().getSoundType();
                            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(soundType.getBreakSound(), 1.0F, 1.0F));
                        }
                    }
                    this.lastClickTime = System.currentTimeMillis();
                }
                return true;
            }
        }
        if (button == 0 && hoveredItem != null) {
            Minecraft.getInstance().setScreen(new FieldGuideScreen("=^" + hoveredItem.getHoverName().getString().toLowerCase(Locale.ROOT), this));
        }
        if (button == 0 && hoveredBiome != null) {
            Minecraft.getInstance().setScreen(new FieldGuideScreen("=!" + hoveredBiome, this));
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (isEditingDescription) {
            String proposedText;
            if (selectionPos != cursorPos) {
                int start = Math.min(cursorPos, selectionPos);
                int end = Math.max(cursorPos, selectionPos);
                proposedText = editableDescription.substring(0, start) + codePoint + editableDescription.substring(end);
            } else {
                proposedText = editableDescription.substring(0, cursorPos) + codePoint + editableDescription.substring(cursorPos);
            }

            editableDescription = proposedText;
            if (selectionPos != cursorPos) {
                cursorPos = Math.min(cursorPos, selectionPos) + 1;
            } else {
                cursorPos++;
            }
            selectionPos = cursorPos;
            scrollToCursor(getCoordsForIndex(cursorPos)[2]);
            return true;
        }

        if (isEditingName) {
            String proposedText;
            if (nameSelectionPos != nameCursorPos) {
                int start = Math.min(nameCursorPos, nameSelectionPos);
                int end = Math.max(nameCursorPos, nameSelectionPos);
                proposedText = editableName.substring(0, start) + codePoint + editableName.substring(end);
            } else {
                proposedText = editableName.substring(0, nameCursorPos) + codePoint + editableName.substring(nameCursorPos);
            }

            editableName = proposedText;
            if (nameSelectionPos != nameCursorPos) {
                nameCursorPos = Math.min(nameCursorPos, nameSelectionPos) + 1;
            } else {
                nameCursorPos++;
            }
            nameSelectionPos = nameCursorPos;
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
            if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) return true;
            return true;
        }

        if (isEditingName) {
            if (Screen.isSelectAll(keyCode)) {
                nameSelectionPos = 0;
                nameCursorPos = editableName.length();
                return true;
            }
            if (Screen.isCopy(keyCode)) {
                if (nameSelectionPos != nameCursorPos) {
                    int start = Math.min(nameCursorPos, nameSelectionPos);
                    int end = Math.max(nameCursorPos, nameSelectionPos);
                    Minecraft.getInstance().keyboardHandler.setClipboard(editableName.substring(start, end));
                }
                return true;
            }
            if (Screen.isCut(keyCode)) {
                if (nameSelectionPos != nameCursorPos) {
                    int start = Math.min(nameCursorPos, nameSelectionPos);
                    int end = Math.max(nameCursorPos, nameSelectionPos);
                    Minecraft.getInstance().keyboardHandler.setClipboard(editableName.substring(start, end));
                    deleteNameSelection();
                }
                return true;
            }
            if (Screen.isPaste(keyCode)) {
                String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (!clipboard.isEmpty()) {
                    int start = Math.min(nameCursorPos, nameSelectionPos);
                    int end = Math.max(nameCursorPos, nameSelectionPos);
                    editableName = editableName.substring(0, start) + clipboard + editableName.substring(end);
                    nameCursorPos = start + clipboard.length();
                    nameSelectionPos = nameCursorPos;
                }
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (nameSelectionPos != nameCursorPos) {
                    deleteNameSelection();
                } else if (nameCursorPos > 0) {
                    editableName = editableName.substring(0, nameCursorPos - 1) + editableName.substring(nameCursorPos);
                    nameCursorPos--;
                    nameSelectionPos = nameCursorPos;
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                if (nameSelectionPos != nameCursorPos) {
                    deleteNameSelection();
                } else if (nameCursorPos < editableName.length()) {
                    editableName = editableName.substring(0, nameCursorPos) + editableName.substring(nameCursorPos + 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                if (Screen.hasControlDown()) {
                    nameCursorPos = getWordPosition(editableName, nameCursorPos, -1);
                } else if (nameCursorPos > 0) {
                    nameCursorPos--;
                }
                if (!Screen.hasShiftDown()) nameSelectionPos = nameCursorPos;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                if (Screen.hasControlDown()) {
                    nameCursorPos = getWordPosition(editableName, nameCursorPos, 1);
                } else if (nameCursorPos < editableName.length()) {
                    nameCursorPos++;
                }
                if (!Screen.hasShiftDown()) nameSelectionPos = nameCursorPos;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                isEditingName = false;
                saveNameIfChanged();
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    this.onClose();
                }
                return true;
            }

            if (FieldGuideClient.OPEN_GUIDE_KEY.matches(keyCode, scanCode) || (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode))) {
                return false;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (isEditingDescription) {
            if (Screen.isSelectAll(keyCode)) {
                selectionPos = 0;
                cursorPos = editableDescription.length();
                return true;
            }
            if (Screen.isCopy(keyCode)) {
                if (selectionPos != cursorPos) {
                    int start = Math.min(cursorPos, selectionPos);
                    int end = Math.max(cursorPos, selectionPos);
                    Minecraft.getInstance().keyboardHandler.setClipboard(editableDescription.substring(start, end));
                }
                return true;
            }
            if (Screen.isCut(keyCode)) {
                if (selectionPos != cursorPos) {
                    int start = Math.min(cursorPos, selectionPos);
                    int end = Math.max(cursorPos, selectionPos);
                    Minecraft.getInstance().keyboardHandler.setClipboard(editableDescription.substring(start, end));
                    deleteSelection();
                }
                return true;
            }
            if (Screen.isPaste(keyCode)) {
                String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (!clipboard.isEmpty()) {
                    int start = Math.min(cursorPos, selectionPos);
                    int end = Math.max(cursorPos, selectionPos);

                    editableDescription = editableDescription.substring(0, start) + clipboard + editableDescription.substring(end);
                    cursorPos = start + clipboard.length();
                    selectionPos = cursorPos;
                    scrollToCursor(getCoordsForIndex(cursorPos)[2]);
                }
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (selectionPos != cursorPos) {
                    deleteSelection();
                } else if (cursorPos > 0) {
                    editableDescription = editableDescription.substring(0, cursorPos - 1) + editableDescription.substring(cursorPos);
                    cursorPos--;
                    selectionPos = cursorPos;
                    scrollToCursor(getCoordsForIndex(cursorPos)[2]);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                if (selectionPos != cursorPos) {
                    deleteSelection();
                    scrollToCursor(getCoordsForIndex(cursorPos)[2]);
                } else if (cursorPos < editableDescription.length()) {
                    editableDescription = editableDescription.substring(0, cursorPos) + editableDescription.substring(cursorPos + 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                if (Screen.hasControlDown()) {
                    cursorPos = getWordPosition(editableDescription, cursorPos, -1);
                } else if (cursorPos > 0) {
                    cursorPos--;
                }
                scrollToCursor(getCoordsForIndex(cursorPos)[2]);
                if (!Screen.hasShiftDown()) selectionPos = cursorPos;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                if (Screen.hasControlDown()) {
                    cursorPos = getWordPosition(editableDescription, cursorPos, 1);
                } else if (cursorPos < editableDescription.length()) {
                    cursorPos++;
                }
                scrollToCursor(getCoordsForIndex(cursorPos)[2]);
                if (!Screen.hasShiftDown()) selectionPos = cursorPos;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                moveCursorLine(-1);
                scrollToCursor(getCoordsForIndex(cursorPos)[2]);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                moveCursorLine(1);
                scrollToCursor(getCoordsForIndex(cursorPos)[2]);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                int start = Math.min(cursorPos, selectionPos);
                int end = Math.max(cursorPos, selectionPos);

                editableDescription = editableDescription.substring(0, start) + "\n" + editableDescription.substring(end);
                cursorPos = start + 1;
                selectionPos = cursorPos;
                scrollToCursor(getCoordsForIndex(cursorPos)[2]);
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.onClose();
                return true;
            }

            if (FieldGuideClient.OPEN_GUIDE_KEY.matches(keyCode, scanCode) || (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode))) {
                return false;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void renderAttributes(GuiGraphics guiGraphics, LivingEntity entity) {
        RenderSystem.setShaderTexture(0, Constants.ATTRIBUTES_TEXTURE);
        int iconSize = 9;
        int iconSpacing = 2;
        int gap = 8;

        int yPos = leftPageBounds.top() + 13;

        String health = String.valueOf((int) entity.getMaxHealth() / 2);
        String armor = String.valueOf(entity.getArmorValue());
        boolean showArmor = !armor.equals("0");

        int healthWidth = this.font.width(health) + iconSpacing + iconSize;
        int armorWidth = this.font.width(armor) + iconSpacing + iconSize;
        int totalWidth = healthWidth + (showArmor ? gap + armorWidth : 0);

        int xPos = this.leftPageBounds.x_center() - (totalWidth / 2);

        // Draw Health
        guiGraphics.blit(Constants.ATTRIBUTES_TEXTURE, xPos, yPos, 0, 0, iconSize, iconSize, 32, 32);
        guiGraphics.drawString(this.font, health, xPos + iconSize + iconSpacing, yPos + 1, ModConfig.get().getTextColorInt(), false);

        // Draw Frame
        guiGraphics.blit(Constants.HEALTH_FRAME_TEXTURE, xPos - 9, yPos - 4, 0, 0, 8, 16, 16, 16);
        guiGraphics.blit(Constants.HEALTH_FRAME_TEXTURE, xPos + totalWidth + 1, yPos - 4, 8, 0, 8, 16, 16, 16);

        if (showArmor) {
            // Draw Armor
            xPos = xPos + healthWidth + gap;
            guiGraphics.blit(Constants.ATTRIBUTES_TEXTURE, xPos, yPos, 0, iconSize, iconSize, iconSize, 32, 32);
            guiGraphics.drawString(this.font, armor, xPos + iconSize + iconSpacing, yPos + 1, ModConfig.get().getTextColorInt(), false);
        }
    }

    private void updateScrollFromMouse(double mouseY) {
        List<FormattedCharSequence> lines = this.font.split(Component.literal(editableDescription), textAreaWidth);
        int totalLines = lines.size();

        if (totalLines > maxVisibleLines) {
            int scrollbarY = textY;
            int scrollbarHeight = (maxVisibleLines * this.font.lineHeight) - 2;

            int thumbHeight = Math.max(4, (int) ((float) maxVisibleLines / totalLines * scrollbarHeight));

            float progress = (float) (mouseY - scrollbarY - (thumbHeight / 2.0f)) / (scrollbarHeight - thumbHeight);
            progress = Math.max(0.0f, Math.min(1.0f, progress));

            scrollOffset = (int) (progress * (totalLines - maxVisibleLines) + 0.5f);
            scrollOffset = Math.max(0, Math.min(scrollOffset, totalLines - maxVisibleLines));
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        hoveredBiome = null;
        hoveredItem = null;

        // Book Backgrounds
        RenderSystem.setShaderTexture(0, Constants.BOOK_TEXTURE);
        guiGraphics.blit(Constants.BOOK_TEXTURE, this.bounds.left(), this.bounds.top(), 0, 0, this.bounds.width(), this.bounds.height(), this.bounds.width(), this.bounds.height());
        guiGraphics.blit(Constants.DETAILS_PAGE_TEXTURE, this.bounds.left(), this.bounds.top(), 0, 0, this.bounds.width(), this.bounds.height(), this.bounds.width(), this.bounds.height());

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        boolean unlocked = ClientFieldGuideManager.isUnlocked(entry);
        Component title = getTitleForEntry(entry);

        ItemStack tooltipStack = null;
        Component tooltipText = null;

        // Entry Name
        int titleY = this.leftPageBounds.top() + 8;
        int titleX = this.rightPageBounds.left() + 5;
        if (unlocked) {
            if (isEditingName) {
                if (nameSelectionPos != nameCursorPos) {
                    int start = Math.min(nameCursorPos, nameSelectionPos);
                    int end = Math.max(nameCursorPos, nameSelectionPos);
                    int selStartX = titleX + this.font.width(editableName.substring(0, start));
                    int selEndX = titleX + this.font.width(editableName.substring(0, end));
                    guiGraphics.fill(selStartX, titleY, selEndX, titleY + this.font.lineHeight, 0x550000FF);
                }
                guiGraphics.drawString(this.font, editableName, titleX, titleY, ModConfig.get().getTextTitleColorInt(), false);
                if ((System.currentTimeMillis() / 500) % 2 == 0) {
                    int cursorX = titleX + this.font.width(editableName.substring(0, nameCursorPos));
                    String cursorChar = (nameCursorPos == editableName.length()) ? "_" : "|";
                    guiGraphics.drawString(this.font, cursorChar, cursorX, titleY, ModConfig.get().getTextTitleColorInt(), false);
                }
            } else {
                guiGraphics.drawString(this.font, editableName.isEmpty() ? title : Component.literal(editableName), titleX, titleY, ModConfig.get().getTextTitleColorInt(), false);
            }
        } else {
            guiGraphics.drawString(this.font, title, titleX, titleY, ModConfig.get().getTextMutedColorInt(), false);
        }

        // Mob Alignment Icons
        if ((unlocked || ModConfig.get().showUndiscoveredNames) && entry instanceof EntityType<?> type && renderedEntity instanceof LivingEntity) {
            ResourceLocation icon;
            Component typeComponent;

            if (renderedEntity instanceof NeutralMob) {
                icon = Constants.NEUTRAL_ICON;
                typeComponent = Component.translatable("fieldguide.alignment.neutral");
            } else if (type.getCategory() == MobCategory.MONSTER) {
                icon = Constants.HOSTILE_ICON;
                typeComponent = Component.translatable("fieldguide.alignment.hostile");
            } else {
                icon = Constants.PASSIVE_ICON;
                typeComponent = Component.translatable("fieldguide.alignment.passive");
            }

            int iconX = this.rightPageBounds.right() - 12;
            int iconY = titleY - 2;

            RenderSystem.enableBlend();
            guiGraphics.blit(icon, iconX, iconY, 0, 0, 12, 12, 12, 12);
            RenderSystem.disableBlend();

            if (Bounds.isMouseOver(mouseX, mouseY, iconX, iconY, 12, 12)) {
                tooltipText = typeComponent;
            }
        }

        // Spawn Biomes
        int biomeIconSize = 16;
        int biomeSpacing = 0;
        int biomeStartY = this.leftPageBounds.bottom() - 24;

        if (unlocked && !spawnBiomes.isEmpty()) {
            int indexStart = biomesPerPage * (currentBiomePage - 1);
            int indexEnd = Math.min(spawnBiomes.size(), biomesPerPage * currentBiomePage);
            int totalWidth = (indexEnd - indexStart) * (biomeIconSize + biomeSpacing);

            int startX = this.leftPageBounds.x_center() - totalWidth / 2;

            for (int i = indexStart; i < indexEnd; i++) {
                ResourceLocation biomeId = spawnBiomes.get(i);
                ResourceLocation texture = new ResourceLocation(biomeId.getNamespace(), "textures/immersiveoverlays/" + biomeId.getPath() + ".png");
                int x = startX + ((i - indexStart) * (biomeIconSize + biomeSpacing));

                guiGraphics.blit(texture, x, biomeStartY, 0, 0, biomeIconSize, biomeIconSize, biomeIconSize, biomeIconSize);

                if (Bounds.isMouseOver(mouseX, mouseY, x, biomeStartY, biomeIconSize, biomeIconSize)) {
                    hoveredBiome = biomeId;
                    tooltipText = Component.translatable("biome." + biomeId.getNamespace() + "." + biomeId.getPath());
                }
            }

            nextBiomePageButton.active = indexEnd < spawnBiomes.size();
            prevBiomePageButton.active = currentBiomePage > 1;

            if (spawnBiomes.size() > biomesPerPage) {
                nextBiomePageButton.visible = true;
                prevBiomePageButton.visible = true;

                // Progress Bar
                int pages = (spawnBiomes.size() + biomesPerPage - 1) / biomesPerPage;
                int sideMargin = 13;
                Bounds bounds = new Bounds(leftPageBounds.left() + sideMargin, biomeStartY + 18, leftPageBounds.width() - sideMargin * 2, 1);
                float progressStart = (float) (currentBiomePage - 1) / pages;
                float progressEnd = (float) currentBiomePage / pages;

                int barStart = (int) (bounds.left() + bounds.width() * progressStart);
                int barEnd = (int) (bounds.left() + bounds.width() * progressEnd);

                // Render Background
                guiGraphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 0xFFF9EED0);

                // Render Bar
                guiGraphics.fill(barStart, bounds.top(), barEnd, bounds.bottom(), 0xFFE0D2AE);
            }

        } else {
            nextBiomePageButton.visible = false;
            prevBiomePageButton.visible = false;
        }

        // Entity
        float bounce = 1.0f;
        long elapsed = System.currentTimeMillis() - lastClickTime;
        float duration = 150;
        if (elapsed < duration) {
            float t = elapsed / duration;
            bounce = 1.0f - 0.05f * (float) Math.sin(t * Math.PI);
        }

        int xPos = leftPageBounds.x_center();
        int yPos = leftPageBounds.y_center();

        if (entry instanceof EntityType && renderedEntity instanceof LivingEntity living) {
            if (unlocked) {
                EntryRenderHelper.renderEntityNormalized(guiGraphics, living, xPos, yPos, 100, 100, 80, false, 0, true, bounce);
                renderAttributes(guiGraphics, living);
            } else {
                EntryRenderHelper.renderEntityNormalized(guiGraphics, living, xPos, yPos, 100, 100, 80, true, ModConfig.get().getDetailsSilhouetteColorInt(), true, bounce);
            }
        } else if (entry instanceof Block block) {
            EntryRenderHelper.renderBlock(guiGraphics, block, xPos, yPos, 30.0F, !unlocked, true, bounce);
        }

        // Description
        if (unlocked) {
            String dateStr = "";
            long discoveryTime = ClientFieldGuideManager.getInstance().getDiscoveryTime(entry);
            long gameTime = ClientFieldGuideManager.getInstance().getDiscoveryGameTime(entry);

            if (ModConfig.get().useRealWorldDate && discoveryTime > 0) {
                dateStr = new SimpleDateFormat("MMM dd, yyyy").format(new Date(discoveryTime));
            } else if (gameTime > 0) {
                long day = gameTime / 24000L + 1;
                int timeOfDay = (int) (gameTime % 24000L);

                String timeKey;
                if (timeOfDay >= 23000 || timeOfDay < 2000) timeKey = "morning";
                else if (timeOfDay < 9000) timeKey = "noon";
                else if (timeOfDay < 13000) timeKey = "evening";
                else if (timeOfDay < 22000) timeKey = "midnight";
                else timeKey = "morning";

                String timeStr = I18n.get("fieldguide.time." + timeKey);
                dateStr = I18n.get("fieldguide.date.in_game", timeStr, day);
            } else if (discoveryTime > 0) {
                dateStr = new SimpleDateFormat("MMM dd, yyyy").format(new Date(discoveryTime));
            }

            if (!dateStr.isEmpty()) {
                Component dateComp = Component.literal(dateStr);
                guiGraphics.drawString(this.font, dateComp, this.rightPageBounds.right() - this.font.width(dateComp), this.rightPageBounds.bottom() - 58, ModConfig.get().getTextMutedColorInt(), false);
            }

            List<FormattedCharSequence> lines = this.font.split(Component.literal(editableDescription), textAreaWidth);
            int totalLines = lines.size();
            scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, totalLines - maxVisibleLines)));

            // Render Text
            for (int i = 0; i < maxVisibleLines && (i + scrollOffset) < totalLines; i++) {
                guiGraphics.drawString(this.font, lines.get(i + scrollOffset), textX, textY + i * this.font.lineHeight, ModConfig.get().getTextColorInt(), false);
            }

            // Render Scrollbar
            if (totalLines > maxVisibleLines) {
                int scrollbarX = textX + textAreaWidth + 2;
                int scrollbarY = textY;
                int scrollbarHeight = (maxVisibleLines * this.font.lineHeight) - 2;
                int scrollbarWidth = 2;

                float progress = (float) scrollOffset / (totalLines - maxVisibleLines);
                int thumbHeight = Math.max(4, (int) ((float) maxVisibleLines / totalLines * scrollbarHeight));
                int thumbY = scrollbarY + (int) (progress * (scrollbarHeight - thumbHeight));

                int hitPadding = 4;
                boolean isHovered = (mouseX >= scrollbarX - hitPadding && mouseX <= scrollbarX + scrollbarWidth + hitPadding &&
                        mouseY >= scrollbarY && mouseY <= scrollbarY + scrollbarHeight);

                int thumbColor = (isDraggingScrollbar || isHovered) ? 0xFF8B5A2B : 0xFFBC986A;

                guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + scrollbarWidth, scrollbarY + scrollbarHeight, 0xFFF9EED0);
                guiGraphics.fill(scrollbarX, thumbY, scrollbarX + scrollbarWidth, thumbY + thumbHeight, thumbColor);
            }

            if (isEditingDescription) {
                // Render selection highlight
                if (selectionPos != cursorPos) {
                    int start = Math.min(cursorPos, selectionPos);
                    int end = Math.max(cursorPos, selectionPos);

                    int[] startCoords = getCoordsForIndex(start);
                    int[] endCoords = getCoordsForIndex(end);

                    for (int line = startCoords[2]; line <= endCoords[2]; line++) {
                        if (line < scrollOffset || line >= scrollOffset + maxVisibleLines) continue;

                        int visibleLine = line - scrollOffset;
                        int lineStartX = textX;
                        if (line == startCoords[2]) lineStartX = startCoords[0];

                        int lineEndX = textX + this.font.width(lines.get(line));
                        if (line == endCoords[2]) lineEndX = endCoords[0];

                        guiGraphics.fill(lineStartX, textY + visibleLine * this.font.lineHeight, lineEndX, textY + (visibleLine + 1) * this.font.lineHeight, 0x550000FF);
                    }
                }

                // Render caret
                if ((System.currentTimeMillis() / 500) % 2 == 0) {
                    int[] coords = getCoordsForIndex(cursorPos);
                    int cursorLine = coords[2];
                    if (cursorLine >= scrollOffset && cursorLine < scrollOffset + maxVisibleLines) {
                        int visibleLine = cursorLine - scrollOffset;
                        String cursorChar = (cursorPos == editableDescription.length()) ? "_" : "|";
                        guiGraphics.drawString(this.font, cursorChar, coords[0], textY + visibleLine * this.font.lineHeight, ModConfig.get().getTextColorInt(), false);
                    }
                }
            }
        } else {
            ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
            String lockedKey = "fieldguide.description.locked";

            if (id != null) {
                String specificKey = "fieldguide.description.locked." + id.getNamespace() + "." + id.getPath();
                String shortKey = "fieldguide.description.locked." + id.getPath();

                if (I18n.exists(specificKey)) {
                    lockedKey = specificKey;
                } else if (I18n.exists(shortKey)) {
                    lockedKey = shortKey;
                } else if (entry instanceof EntityType<?> type) {
                    var key = BuiltInRegistries.ENTITY_TYPE.getResourceKey(type);
                    boolean requiresKill = false;
                    if (key.isPresent()) {
                        var holder = BuiltInRegistries.ENTITY_TYPE.getHolder(key.get());
                        TagKey<EntityType<?>> killTag = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("fieldguide", "kill_to_unlock"));
                        if (holder.isPresent() && holder.get().is(killTag)) {
                            requiresKill = true;
                        }
                    }

                    if (requiresKill) {
                        lockedKey = "fieldguide.description.locked.kill";
                    } else if (!ModConfig.get().requireSpyglass) {
                        lockedKey = "fieldguide.description.locked.no_spyglass";
                    }
                } else if (!ModConfig.get().requireSpyglass) {
                    lockedKey = "fieldguide.description.locked.no_spyglass";
                }
            }

            guiGraphics.drawWordWrap(font, Component.translatable(lockedKey), textX, textY, textAreaWidth, ModConfig.get().getTextMutedColorInt());
        }

        // Drops
        List<List<ItemStack>> dropLines = new ArrayList<>();
        int dropItemSize = 20;
        int dropSpacing = 1;
        int scrollbarY;

        if (unlocked) {
            List<ItemStack> drops = ClientFieldGuideManager.getInstance().getDrops(entry);

            if (!drops.isEmpty()) {
                int maxLineWidth = (dropItemSize + dropSpacing) * 6;
                List<ItemStack> currentLine = new ArrayList<>();
                int currentWidth = 0;

                for (ItemStack stack : drops) {
                    int needed = (currentLine.isEmpty() ? 0 : dropSpacing) + dropItemSize;
                    if (currentWidth + needed > maxLineWidth) {
                        dropLines.add(currentLine);
                        currentLine = new ArrayList<>();
                        currentWidth = 0;
                    }
                    currentWidth += (currentLine.isEmpty() ? 0 : dropSpacing) + dropItemSize;
                    currentLine.add(stack);
                }
                dropLines.add(currentLine);

                int maxVisibleDropLines = 2;
                lootScrollOffset = Math.max(0, Math.min(lootScrollOffset, Math.max(0, dropLines.size() - maxVisibleDropLines)));

                int maxDropsAreaHeight = maxVisibleDropLines * dropItemSize + Math.max(0, maxVisibleDropLines - 1) * dropSpacing;
                int bottomAnchor = this.rightPageBounds.bottom() - 6;
                scrollbarY = bottomAnchor - maxDropsAreaHeight;

                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);

                // Backgrounds and Items
                int currentY = scrollbarY;
                for (int i = lootScrollOffset; i < Math.min(lootScrollOffset + maxVisibleDropLines, dropLines.size()); i++) {
                    List<ItemStack> line = dropLines.get(i);
                    int startX = this.rightPageBounds.left();

                    for (ItemStack stack : line) {
                        guiGraphics.renderItem(stack, startX + 2, currentY + 2);
                        guiGraphics.renderItemDecorations(this.font, stack, startX + 2, currentY + 2);

                        if (Bounds.isMouseOver(mouseX, mouseY, startX, currentY, dropItemSize, dropItemSize)) {
                            tooltipStack = stack;
                            hoveredItem = stack;
                        }

                        startX += dropItemSize + dropSpacing;
                    }
                    currentY += dropItemSize + dropSpacing;
                }
            }
        }

        // Render Tooltip
        if (tooltipStack != null) {
            List<Component> tooltip = new ArrayList<>(Screen.getTooltipFromItem(Objects.requireNonNull(this.minecraft), tooltipStack));

            if (tooltipStack.hasTag() && Objects.requireNonNull(tooltipStack.getTag()).contains("FieldGuideDropChance")) {
                float chance = tooltipStack.getTag().getFloat("FieldGuideDropChance");
                tooltip.add(Component.literal(String.format(Locale.ROOT, "%.1f%%", chance)).withStyle(ChatFormatting.GRAY));
            }

            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        } else if (tooltipText != null) {
            guiGraphics.renderTooltip(this.font, tooltipText, mouseX, mouseY);
        }
    }
}