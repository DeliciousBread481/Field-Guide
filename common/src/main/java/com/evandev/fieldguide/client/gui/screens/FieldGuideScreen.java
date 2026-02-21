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
import com.evandev.fieldguide.data.CompositeFieldGuideEntry;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class FieldGuideScreen extends BookScreen {
    private static final int ITEMS_PER_PAGE = 9;
    private static final int ITEMS_PER_VIEW = ITEMS_PER_PAGE * 2;

    private static final int GRID_COLS = 3;
    private static final int CELL_SIZE = 40;
    private static final int GAP = 1;

    private static final int SEARCH_WIDTH = 140;
    private static final int SEARCH_HEIGHT = 20;
    public static int lastOpenedJournalPage = 0;
    private static ResourceLocation lastOpenedCategory = null;
    private final Map<EntityType<?>, Entity> entryCache = new HashMap<>();
    public boolean isSearching = false;
    private boolean initialSearchFocus = false;
    private List<Object> currentEntries = new ArrayList<>();
    private List<Object> recentEntries = new ArrayList<>();
    private int currentPage = 0;
    private Screen parent = null;
    private String searchQuery = "";
    private PageTurnButton prevPageButton;
    private PageTurnButton nextPageButton;
    private PageTurnButton backButton;
    private FieldGuideSearchBox searchBox;

    public FieldGuideScreen() {
        super(Component.translatable("title.fieldguide.field_guide"));
    }

    public FieldGuideScreen(Category initialCategory) {
        this();
        this.setSelectedCategory(initialCategory);
    }

    public FieldGuideScreen(Category initialCategory, int initialPage) {
        this(initialCategory);
        this.currentPage = initialPage;
    }

    public FieldGuideScreen(String searchQuery, Screen parent) {
        this();
        this.searchQuery = searchQuery;
        this.parent = parent;
    }

    public static int getPageForEntry(Category category, Object entry) {
        List<Object> entries = ClientFieldGuideManager.getInstance().getEntriesForCategory(category);
        int index = entries.indexOf(entry);
        if (index < 0) return 0;
        return 1 + index / ITEMS_PER_VIEW;
    }

    public void setInitialSearchFocus(boolean focus) {
        this.initialSearchFocus = focus;
    }

    public List<Object> getCurrentEntries() {
        return this.currentEntries;
    }

    @Override
    protected void init() {
        super.init();

        if (this.getSelectedCategory() == null) {
            if (lastOpenedCategory != null) {
                this.setSelectedCategory(ClientFieldGuideManager.getCategories().get(lastOpenedCategory));
            }

            if (this.getSelectedCategory() == null) {
                Category intro = ClientFieldGuideManager.getCategories().values().stream()
                        .filter(cat -> cat.getId().getPath().equals("intro"))
                        .findFirst()
                        .orElse(null);

                if (intro != null) {
                    this.setSelectedCategory(intro);
                } else if (!this.getSortedCategories().isEmpty()) {
                    this.setSelectedCategory(this.getSortedCategories().get(0));
                }
            }
        }

        this.isSearching = !this.searchQuery.trim().isEmpty();

        if (!this.isSearching && this.getSelectedCategory() != null && this.getSelectedCategory().getId().getPath().equals("intro")) {
            Objects.requireNonNull(this.minecraft).setScreen(new FieldGuideJournalScreen(this.getSelectedCategory(), lastOpenedJournalPage));
            return;
        }

        lastOpenedCategory = this.getSelectedCategory().getId();

        // Pagination Buttons
        this.prevPageButton = new PageTurnButton(
                this.bounds.left() + 15,
                this.leftPageBounds.bottom() - 15,
                16, 16, 0, 0, 16,
                Constants.PREV_PAGE_TEXTURE, 16, 32,
                b -> prevPage()
        );

        this.nextPageButton = new PageTurnButton(
                this.bounds.right() - 14 - 16,
                this.rightPageBounds.bottom() - 15,
                16, 16, 0, 0, 16,
                Constants.NEXT_PAGE_TEXTURE, 16, 32,
                b -> nextPage()
        );

        this.addRenderableWidget(prevPageButton);
        this.addRenderableWidget(nextPageButton);

        // Back Button
        this.backButton = new PageTurnButton(
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
                b -> {
                    if (parent != null) {
                        Objects.requireNonNull(this.minecraft).setScreen(parent);
                    } else {
                        this.searchBox.setValue("");
                    }
                }
        );
        backButton.visible = false;
        this.addRenderableWidget(backButton);

        // Search Bar
        int searchX = this.width / 2 - SEARCH_WIDTH / 2;
        int searchY = this.bounds.bottom() + 5;

        this.searchBox = new FieldGuideSearchBox(this.font, searchX, searchY, SEARCH_WIDTH, SEARCH_HEIGHT, this.searchQuery, this::onSearchChanged);
        if (this.initialSearchFocus) {
            this.searchBox.setInitialFocus();
            this.setFocused(this.searchBox);
        }
        this.addRenderableWidget(this.searchBox);

        if (this.isSearching) {
            this.setSelectedCategory(null);
            this.getEntriesForSearchQuery();
        } else {
            this.getEntriesForSelectedCategory();
        }

        int totalSpreads = getTotalSpreads();
        if (this.currentPage >= totalSpreads) {
            this.currentPage = Math.max(0, totalSpreads - 1);
        }

        goToPage(this.currentPage);
    }

    private void onSearchChanged(String query) {
        if (query.equals(this.searchQuery)) return;

        this.isSearching = !query.trim().isEmpty();
        this.searchQuery = query;

        if (!isSearching) {
            Category category = ClientFieldGuideManager.getCategories().get(lastOpenedCategory);
            if (category != null && category.getId().getPath().equals("intro")) {
                Objects.requireNonNull(this.minecraft).setScreen(new FieldGuideJournalScreen(category, lastOpenedJournalPage));
                return;
            }
            this.setSelectedCategory(category);
            if (category != null) {
                this.getEntriesForSelectedCategory();
            }
        } else {
            this.setSelectedCategory(null);
            this.getEntriesForSearchQuery();
        }

        this.goToPage(0);
    }

    private void getEntriesForSelectedCategory() {
        Category category = this.getSelectedCategory();
        if (category != null) {
            this.currentEntries = ClientFieldGuideManager.getInstance().getEntriesForCategory(category);
            this.recentEntries = ClientFieldGuideManager.getInstance().getRecentEntries(category, 9);
        } else {
            this.currentEntries = null;
            this.recentEntries = null;
        }
    }

    private void getEntriesForSearchQuery() {
        this.currentEntries = ClientFieldGuideManager.getInstance().searchEntries(this.searchQuery);
        this.recentEntries = null;
    }

    @Override
    public void onTabClick(Category category) {
        lastOpenedCategory = category.getId();

        if (category.getId().getPath().equals("intro")) {
            Objects.requireNonNull(this.minecraft).setScreen(new FieldGuideJournalScreen(category, lastOpenedJournalPage));
            return;
        }

        this.setSelectedCategory(category);
        this.getEntriesForSelectedCategory();
        this.goToPage(0);

        if (this.searchBox != null) {
            this.searchBox.setValue("");
            this.isSearching = false;
        }

        if (!this.children().isEmpty()) {
            this.rebuildWidgets();
        }
    }

    private int getTotalSpreads() {
        int count = currentEntries.size();
        if (isSearching) {
            if (count == 0) return 1;
            return (int) Math.ceil((double) count / ITEMS_PER_VIEW);
        }

        if (count == 0) return 1;
        return 1 + (int) Math.ceil((double) count / ITEMS_PER_VIEW);
    }

    private void updatePageButtons() {
        int totalSpreads = getTotalSpreads();
        this.prevPageButton.visible = currentPage > 0;
        this.nextPageButton.visible = currentPage < totalSpreads - 1;
    }

    private void goToPage(int page) {
        this.currentPage = page;
        this.updatePageButtons();
    }

    private void prevPage() {
        if (currentPage > 0) {
            this.goToPage(currentPage - 1);
        }
    }

    private void nextPage() {
        if (currentPage < getTotalSpreads() - 1) {
            this.goToPage(currentPage + 1);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.searchBox != null) this.searchBox.setFocused(this.searchBox.isMouseOver(mouseX, mouseY));
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (!isSearching && currentPage == 0) {
            for (int i = 0; i < recentEntries.size(); i++) {
                int slotIndex = ITEMS_PER_PAGE + i;
                Bounds bounds = getGridCellBounds(slotIndex);

                if (bounds.contains((int) mouseX, (int) mouseY)) {
                    Object entry = recentEntries.get(i);
                    handleEntryClick(entry);
                    return true;
                }
            }
            return false;
        }

        for (int i = 0; i < ITEMS_PER_VIEW; i++) {
            if (!isSearching && currentPage == 0) continue;

            Bounds cellBounds = getGridCellBoundsLocal(i);

            if (cellBounds.contains((int) mouseX, (int) mouseY)) {
                int itemIndex = getItemIndexForSlot(i);
                if (itemIndex >= 0 && itemIndex < currentEntries.size()) {
                    handleEntryClick(currentEntries.get(itemIndex));
                    return true;
                }
            }
        }
        return false;
    }

    private void handleEntryClick(Object entry) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));

        ResourceLocation entryId = ClientFieldGuideManager.getEntryId(entry);
        EntryVisual visual = entryId != null ? ClientFieldGuideManager.getInstance().getEntryVisual(entryId) : null;
        Object coreEntry = entry instanceof CompositeFieldGuideEntry composite ? composite.displayEntry() : entry;

        if (coreEntry instanceof EntityType<?> type) {
            Entity entity = entryCache.get(type);
            if (entity == null && Objects.requireNonNull(this.minecraft).level != null) {
                try {
                    entity = type.create(this.minecraft.level);
                    entryCache.put(type, entity);
                } catch (Exception ignored) {
                }
            }
            if (entity != null && ClientFieldGuideManager.isUnlocked(entry)) {
                if (visual != null && visual.customSound != null) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(visual.customSound), 1.0F, 1.0F));
                } else {
                    FieldGuideClient.playMobCry(entity);
                }
            }
        } else if (coreEntry instanceof Block && ClientFieldGuideManager.isUnlocked(entry)) {
            if (visual != null && visual.customSound != null) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(visual.customSound), 1.0F, 1.0F));
            }
        }

        Minecraft.getInstance().setScreen(new FieldGuideEntryScreen(this, entry));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.searchBox.setFocused(false);
                return true;
            }
            if (this.searchBox.keyPressed(keyCode, scanCode, modifiers)) return true;
            return true;
        }

        if (this.isSearching && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (parent != null) {
                Minecraft.getInstance().setScreen(parent);
            } else {
                this.searchBox.setValue("");
            }
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        RenderSystem.setShaderTexture(0, Constants.BOOK_TEXTURE);
        guiGraphics.blit(Constants.BOOK_TEXTURE, this.bounds.left(), this.bounds.top(), 0, 0, this.bounds.width(), this.bounds.height(), this.bounds.width(), this.bounds.height());

        // Page Numbers
        int leftPageNum, rightPageNum;
        leftPageNum = (currentPage + 1) * 2 - 1;
        rightPageNum = (currentPage + 1) * 2;

        if (!isSearching) {
            leftPageNum -= 2;
            rightPageNum -= 2;
        }

        if (currentPage > 0 || isSearching) {
            int titleY = this.rightPageBounds.top() + 8;
            guiGraphics.blit(Constants.LIST_PAGE_TEXTURE, this.bounds.left(), this.bounds.top(), 0, 0, this.bounds.width(), this.bounds.height(), this.bounds.width(), this.bounds.height());
            renderPageNumber(leftPageNum, this.leftPageBounds, guiGraphics);
            if (isSearching) {
                if (currentEntries.size() > leftPageNum * ITEMS_PER_PAGE) {
                    renderPageNumber(rightPageNum, this.rightPageBounds, guiGraphics);
                }

                // Biome Title
                if (searchQuery.startsWith("=!")) {
                    ResourceLocation biomeId = ResourceLocation.tryParse(searchQuery.substring(2));
                    if (biomeId != null) {
                        ResourceLocation texture = new ResourceLocation(biomeId.getNamespace(), "textures/immersiveoverlays/" + biomeId.getPath() + ".png");
                        int iconSize = 16;
                        int iconY = titleY - 5;
                        int iconX = this.leftPageBounds.left() + 3;
                        guiGraphics.blit(texture, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
                        Component searchTitle = Component.translatable("biome." + biomeId.getNamespace() + "." + biomeId.getPath());
                        guiGraphics.drawString(this.font, searchTitle, iconX + iconSize + 3, titleY, ModConfig.get().getTextColorInt(), false);
                    }
                } else if (searchQuery.startsWith("=^")) {
                    String dropQuery = searchQuery.substring(2).toLowerCase(Locale.ROOT);
                    ItemStack displayStack = ItemStack.EMPTY;
                    String dropName = searchQuery.substring(2);

                    for (Object entry : currentEntries) {
                        List<ItemStack> drops = ClientFieldGuideManager.getInstance().getDrops(entry);
                        for (ItemStack stack : drops) {
                            if (stack.getHoverName().getString().toLowerCase(Locale.ROOT).equals(dropQuery)) {
                                displayStack = stack;
                                dropName = stack.getHoverName().getString();
                                break;
                            }
                        }
                        if (!displayStack.isEmpty()) break;
                    }

                    int iconSize = 16;
                    int iconY = titleY - 5;
                    int iconX = this.leftPageBounds.left() + 3;

                    if (!displayStack.isEmpty()) {
                        guiGraphics.renderItem(displayStack, iconX, iconY);
                    } else {
                        StringBuilder titleCase = new StringBuilder();
                        for (String word : dropName.split("\\s+")) {
                            if (!word.isEmpty()) {
                                titleCase.append(Character.toUpperCase(word.charAt(0)))
                                        .append(word.substring(1).toLowerCase(Locale.ROOT))
                                        .append(" ");
                            }
                        }
                        dropName = titleCase.toString().trim();
                    }

                    guiGraphics.drawString(this.font, dropName, iconX + iconSize + 3, titleY, ModConfig.get().getTextColorInt(), false);
                } else {
                    guiGraphics.drawString(this.font, searchQuery, this.leftPageBounds.left() + 6, titleY, ModConfig.get().getTextMutedColorInt(), false);
                }


            } else {
                int startIdx = (currentPage - 1) * ITEMS_PER_VIEW;
                if (currentEntries.size() > startIdx + ITEMS_PER_PAGE) {
                    renderPageNumber(rightPageNum, this.rightPageBounds, guiGraphics);
                }

                // Category Title
                Component title = Component.translatable("category.fieldguide." + this.getSelectedCategory().getId().getPath());
                guiGraphics.drawString(this.font, title, this.leftPageBounds.left() + 6, titleY, ModConfig.get().getTextMutedColorInt(), false);
            }
        }

        if (!isSearching && this.getSelectedCategory() != null && currentPage == 0) {
            renderCategoryInfo(guiGraphics);
            renderRecentDiscoveries(guiGraphics, mouseX, mouseY);
        } else if (isSearching) {
            if (currentEntries.isEmpty()) {
                Component noResults = Component.translatable("gui.fieldguide.no_results");
                guiGraphics.drawString(this.font, noResults, this.leftPageBounds.x_center() - this.font.width(noResults) / 2, this.leftPageBounds.y_center() - (font.lineHeight / 2), ModConfig.get().getTextMutedColorInt(), false);
            }
        }

        // Back Button
        this.backButton.visible = this.isSearching;
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Grid
        if (currentPage > 0 || isSearching) {
            renderGrid(guiGraphics, mouseX, mouseY);
        }
    }

    private void renderCategoryInfo(GuiGraphics guiGraphics) {
        guiGraphics.blit(Constants.TITLE_PAGE_TEXTURE, this.bounds.left(), this.bounds.top(), 0, 0, this.bounds.width(), this.bounds.height(), this.bounds.width(), this.bounds.height());

        Category category = this.getSelectedCategory();
        Component title = Component.translatable("category.fieldguide." + category.getId().getPath());
        int titleY = this.leftPageBounds.top() + 36;
        List<FormattedCharSequence> lines = this.font.split(title, 70);

        if (lines.size() > 2) {
            titleY -= font.lineHeight;
        }

        for (FormattedCharSequence line : lines) {
            int lineWidth = this.font.width(line);
            int lineX = this.leftPageBounds.x_center() - lineWidth / 2;
            guiGraphics.drawString(this.font, line, lineX, titleY, ModConfig.get().getTextTitleColorInt(), false);
            titleY += font.lineHeight;
        }

        int total = currentEntries.size();
        if (total > 0) {
            long unlocked = currentEntries.stream().filter(ClientFieldGuideManager::isUnlocked).count();
            int y = this.leftPageBounds.bottom() - 38;
            int x = this.leftPageBounds.x_center();
            int xOffset = 14;

            String countText = String.valueOf(unlocked);
            String totalText = String.valueOf(total);

            guiGraphics.drawString(this.font, countText, x - xOffset - font.width(countText) / 2, y, ModConfig.get().getTextColorInt(), false);
            guiGraphics.drawString(this.font, totalText, x + xOffset - font.width(totalText) / 2, y, ModConfig.get().getTextColorInt(), false);

            // Progress Bar
            if (unlocked > 0) {
                int barWidth = 83;
                int barHeight = 7;
                int barX = x - barWidth / 2 - 1;
                int barY = y + 15;

                int progressWidth = (int) ((float) unlocked / total * barWidth);
                progressWidth = Math.max(progressWidth, 6);
                guiGraphics.blitNineSliced(Constants.PROGRESS_BAR_TEXTURE, barX, barY, progressWidth, barHeight, 3, 7, 7, 0, 0);
            }
        }
    }

    private void renderRecentDiscoveries(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Title
        Component title = Component.literal("Recent Discoveries"); // Fallback
        int titleY = this.rightPageBounds.top() + 8;
        guiGraphics.drawString(this.font, title, this.rightPageBounds.x_center() - font.width(title) / 2, titleY, ModConfig.get().getTextMutedColorInt(), false);

        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            if (i >= recentEntries.size()) break;
            Object entry = recentEntries.get(i);
            int slotIndex = ITEMS_PER_PAGE + i;
            Bounds bounds = getGridCellBoundsLocal(slotIndex);

            boolean hovered = bounds.contains(mouseX, mouseY);
            if (hovered) {
                guiGraphics.blit(Constants.CELL_BACKGROUND_HOVER_TEXTURE, bounds.x(), bounds.y(), 0, 0, CELL_SIZE, CELL_SIZE, CELL_SIZE, CELL_SIZE);
            } else {
                guiGraphics.blit(Constants.CELL_BACKGROUND_TEXTURE, bounds.x(), bounds.y(), 0, 0, CELL_SIZE, CELL_SIZE, CELL_SIZE, CELL_SIZE);
            }

            renderEntryInGrid(guiGraphics, entry, bounds.x_center(), bounds.y_center(), 30, true);

            if (ClientFieldGuideManager.isNew(entry)) {
                renderNewLabel(guiGraphics, bounds);
            }
        }

        // Tooltips
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            if (i >= recentEntries.size()) break;
            Object entry = recentEntries.get(i);
            int slotIndex = ITEMS_PER_PAGE + i;
            Bounds bounds = getGridCellBoundsLocal(slotIndex);
            if (bounds.contains(mouseX, mouseY)) {
                renderEntryTooltip(guiGraphics, entry, mouseX, mouseY, true);
            }
        }
    }

    private int getItemIndexForSlot(int slotIndex) {
        if (isSearching) return (currentPage * ITEMS_PER_VIEW) + slotIndex;
        if (currentPage == 0) return -1;

        int startItemIndex = (currentPage - 1) * ITEMS_PER_VIEW;
        return startItemIndex + slotIndex;
    }

    private void renderGrid(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (int i = 0; i < ITEMS_PER_VIEW; i++) {
            int itemIndex = getItemIndexForSlot(i);
            if (itemIndex >= 0 && itemIndex < currentEntries.size()) {
                Object entry = currentEntries.get(itemIndex);
                Bounds bounds = getGridCellBoundsLocal(i);
                boolean unlocked = ClientFieldGuideManager.isUnlocked(entry);
                boolean hovered = bounds.contains(mouseX, mouseY);

                if (hovered) {
                    guiGraphics.blit(Constants.CELL_BACKGROUND_HOVER_TEXTURE, bounds.x(), bounds.y(), 0, 0, CELL_SIZE, CELL_SIZE, CELL_SIZE, CELL_SIZE);
                } else {
                    guiGraphics.blit(Constants.CELL_BACKGROUND_TEXTURE, bounds.x(), bounds.y(), 0, 0, CELL_SIZE, CELL_SIZE, CELL_SIZE, CELL_SIZE);
                }

                renderEntryInGrid(guiGraphics, entry, bounds.x_center(), bounds.y_center(), 30, unlocked);

                if (ClientFieldGuideManager.isNew(entry)) {
                    renderNewLabel(guiGraphics, bounds);
                }
            }
        }

        for (int i = 0; i < ITEMS_PER_VIEW; i++) {
            int itemIndex = getItemIndexForSlot(i);
            if (itemIndex >= 0 && itemIndex < currentEntries.size()) {
                Bounds bounds = getGridCellBoundsLocal(i);
                if (bounds.contains(mouseX, mouseY)) {
                    Object entry = currentEntries.get(itemIndex);
                    boolean unlocked = ClientFieldGuideManager.isUnlocked(entry);
                    renderEntryTooltip(guiGraphics, entry, mouseX, mouseY, unlocked);
                }
            }
        }
    }

    private void renderNewLabel(GuiGraphics guiGraphics, Bounds bounds) {
        Component newText = Component.translatable("fieldguide.new");
        int textWidth = this.font.width(newText);
        int textX = bounds.x_center() - textWidth / 2;
        int textY = bounds.bottom() - 8;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 200);
        guiGraphics.drawString(this.font, newText, textX, textY, ModConfig.get().getTextNewColorInt(), false);
        guiGraphics.pose().popPose();
    }

    private void renderEntryTooltip(GuiGraphics guiGraphics, Object entry, int mouseX, int mouseY, boolean unlocked) {
        if (unlocked || ModConfig.get().showUndiscoveredNames) {
            Component name = ClientFieldGuideManager.getEntryName(entry);

            List<Component> tooltip = new ArrayList<>();
            tooltip.add(name);

            if (this.minecraft != null && this.minecraft.options.advancedItemTooltips) {
                ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);

                if (id != null) {
                    tooltip.add(Component.literal(id.toString()).withStyle(ChatFormatting.DARK_GRAY));
                }
            }

            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        } else {
            guiGraphics.renderTooltip(this.font, Component.translatable("fieldguide.unknown"), mouseX, mouseY);
        }
    }

    private Bounds getGridCellBounds(int globalSlotIndex) {
        return getGridCellBoundsLocal(globalSlotIndex % ITEMS_PER_VIEW);
    }

    private Bounds getGridCellBoundsLocal(int i) {
        Bounds pageBounds;
        if (i < ITEMS_PER_PAGE) pageBounds = this.leftPageBounds;
        else pageBounds = this.rightPageBounds;

        int startX = pageBounds.left() + 6;
        int startY = pageBounds.top() + 24;
        int localIndex = i % ITEMS_PER_PAGE;
        int col = localIndex % GRID_COLS;
        int row = localIndex / GRID_COLS;
        int x = startX + (col * (CELL_SIZE + GAP));
        int y = startY + (row * (CELL_SIZE + GAP));
        return new Bounds(x, y, CELL_SIZE, CELL_SIZE);
    }

    private void renderPageNumber(int page, Bounds bounds, GuiGraphics guiGraphics) {
        String str = page + "";
        guiGraphics.drawString(this.font, str, bounds.x_center() - font.width(str) / 2, bounds.bottom() - 11, ModConfig.get().getPageNumberColorInt(), false);
    }

    private void renderEntryInGrid(GuiGraphics guiGraphics, Object entry, int x, int y, int scale, boolean unlocked) {
        Object coreEntry = entry instanceof CompositeFieldGuideEntry composite ? composite.displayEntry() : entry;

        if (entry instanceof CompositeFieldGuideEntry composite && composite.displayEntry() instanceof Block block) {
            boolean isTree = block.getName().getString().endsWith(" Sapling") || block.getName().getString().endsWith(" Fungus") || block.getName().getString().endsWith(" Propagule") || block.getName().getString().endsWith(" Mushroom");
            if (isTree) {
                EntryRenderHelper.renderStructure(guiGraphics, composite, x, y, CELL_SIZE - 4, !unlocked, false, 1.0F);
            } else {
                EntryRenderHelper.renderBlock(guiGraphics, block, x, y, 15.0F, !unlocked, false, 1.0F);
            }
        } else if (coreEntry instanceof EntityType<?> type) {
            if (this.minecraft != null && this.minecraft.level != null) {
                Entity entity = entryCache.get(type);
                if (entity == null && !entryCache.containsKey(type)) {
                    if (this.minecraft.level != null) {
                        try {
                            entity = type.create(this.minecraft.level);
                            if (entity != null) {
                                entryCache.put(type, entity);
                            }
                        } catch (Exception e) {
                            Constants.LOG.error("Failed to create entity for guide: {}", type.getDescription().getString());
                            entryCache.put(type, null);
                        }
                    }
                }

                if (entity instanceof LivingEntity living) {
                    EntryRenderHelper.renderEntityNormalized(guiGraphics, living, x, y, CELL_SIZE - 8, CELL_SIZE - 8, scale, !unlocked, ModConfig.get().getListSilhouetteColorInt(), false, 1.0F);
                }
            }
        } else if (coreEntry instanceof Block block) {
            EntryRenderHelper.renderBlock(guiGraphics, block, x, y, 15.0F, !unlocked, false, 1.0F);
        }
    }
}