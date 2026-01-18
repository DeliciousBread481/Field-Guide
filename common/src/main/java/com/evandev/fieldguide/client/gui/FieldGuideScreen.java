package com.evandev.fieldguide.client.gui;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.gui.util.Bounds;
import com.evandev.fieldguide.client.gui.util.EntityRenderHelper;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.FieldGuideDataManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class FieldGuideScreen extends BookScreen {
    private static final int ITEMS_PER_PAGE = 9;
    private static final int ITEMS_PER_VIEW = ITEMS_PER_PAGE * 2;
    private static final int GRID_COLS = 3;
    private static final int CELL_SIZE = 40;
    private static final int GAP = 0;
    private static final int TAB_WIDTH = 24;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_GAP = 1;

    private final Map<EntityType<?>, Entity> entryCache = new HashMap<>();
    private final List<Category> sortedCategories = new ArrayList<>();

    private List<EntityType<?>> currentEntries = new ArrayList<>();

    private Category selectedCategory;
    private int currentPage = 0;

    private ImageButton prevPageButton;
    private ImageButton nextPageButton;

    public FieldGuideScreen() {
        super(Component.translatable("title.fieldguide.compendium"));
    }

    @Override
    protected void init() {
        super.init();

        this.sortedCategories.clear();
        this.sortedCategories.addAll(FieldGuideDataManager.getCategories().values());

        this.sortedCategories.sort(Comparator.comparingInt(Category::getTabIndex)
                .thenComparing(c -> c.getId().getPath()));

        if (selectedCategory == null && !sortedCategories.isEmpty()) {
            selectCategory(sortedCategories.get(0));
        } else if (selectedCategory != null) {
            selectCategory(selectedCategory);
        }

        this.prevPageButton = new ImageButton(
                this.leftPageBounds.left(),
                this.leftPageBounds.bottom() - 15,
                16, 16, 0, 0, 16,
                Constants.PREV_PAGE_TEXTURE, 16, 32,
                b -> prevPage()
        );

        this.nextPageButton = new ImageButton(
                this.rightPageBounds.right() - 16,
                this.rightPageBounds.bottom() - 15,
                16, 16, 0, 0, 16,
                Constants.NEXT_PAGE_TEXTURE, 16, 32,
                b -> nextPage()
        );

        this.addRenderableWidget(prevPageButton);
        this.addRenderableWidget(nextPageButton);

        initTabs();

        updatePageButtons();
    }

    private void initTabs() {
        int startY = this.bounds.top() + 10;

        for (int i = 0; i < sortedCategories.size(); i++) {
            Category category = sortedCategories.get(i);
            int yPos = startY + (i * (TAB_HEIGHT + TAB_GAP));
            int xPos = this.bounds.left() - TAB_WIDTH;

            TabButton tab = new TabButton(
                    xPos,
                    yPos,
                    TAB_WIDTH,
                    TAB_HEIGHT,
                    category,
                    this
            );

            this.addRenderableWidget(tab);
        }
    }

    /**
     * Public accessor to check state.
     */
    public Category getSelectedCategory() {
        return this.selectedCategory;
    }

    /**
     * Selects a category and refreshes the entry list.
     * Made public so TabButton can call it.
     */
    public void selectCategory(Category category) {
        this.selectedCategory = category;
        this.currentPage = 0;
        this.currentEntries = category.getEntities();

        if (!this.children().isEmpty()) {
            this.rebuildWidgets();
        }
    }

    /**
     * Calculates total spreads (pairs of pages) needed.
     */
    private int getTotalSpreads() {
        int count = currentEntries.size();
        if (count <= ITEMS_PER_PAGE) {
            return 1;
        }
        return 1 + (int) Math.ceil((double) (count - ITEMS_PER_PAGE) / ITEMS_PER_VIEW);
    }

    private void updatePageButtons() {
        int totalSpreads = getTotalSpreads();
        this.prevPageButton.visible = currentPage > 0;
        this.nextPageButton.visible = currentPage < totalSpreads - 1;
    }

    private void prevPage() {
        if (currentPage > 0) {
            currentPage--;
            updatePageButtons();
        }
    }

    private void nextPage() {
        if (currentPage < getTotalSpreads() - 1) {
            currentPage++;
            updatePageButtons();
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        RenderSystem.setShaderTexture(0, Constants.BOOK_TEXTURE);
        guiGraphics.blit(Constants.BOOK_TEXTURE, this.bounds.left(), this.bounds.top(), 0, 0, this.bounds.width(), this.bounds.height(), this.bounds.width(), this.bounds.height());

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (selectedCategory != null && currentPage == 0) {
            renderCategoryInfo(guiGraphics);
        }

        int totalSpreads = getTotalSpreads();
        int totalPagesStr = totalSpreads * 2;

        int leftPageNum = (currentPage + 1) * 2 - 1;
        int rightPageNum = (currentPage + 1) * 2;

        if (leftPageNum > 1) {
            renderPageNumber(leftPageNum, totalPagesStr, this.leftPageBounds, guiGraphics);
        }
        renderPageNumber(rightPageNum, totalPagesStr, this.rightPageBounds, guiGraphics);

        renderGrid(guiGraphics, mouseX, mouseY);
    }

    private void renderCategoryInfo(GuiGraphics guiGraphics) {
        Component title = Component.translatable("category.fieldguide." + selectedCategory.getId().getPath());
        int titleWidth = this.font.width(title);
        guiGraphics.drawString(this.font, title,
                this.leftPageBounds.x_center() - titleWidth / 2,
                this.leftPageBounds.top() + 15, 0x7A583C, false);

        int total = currentEntries.size();
        if (total > 0) {
            long unlocked = currentEntries.stream().filter(FieldGuideDataManager::isUnlocked).count();

            int barWidth = 100;
            int barHeight = 6;
            int x = this.leftPageBounds.x_center() - barWidth / 2;
            int y = this.leftPageBounds.bottom() - 40;

            guiGraphics.fill(x, y, x + barWidth, y + barHeight, 0xFFdbcbb0);

            int progressWidth = (int) ((float) unlocked / total * barWidth);
            guiGraphics.fill(x, y, x + progressWidth, y + barHeight, 0xFF7A583C);

            String progressText = unlocked + " of " + total;
            guiGraphics.drawCenteredString(this.font, progressText, this.leftPageBounds.x_center(), y + 10, 0x7A583C);
        }
    }

    /**
     * Helper to map a slot on the current spread to an actual item index.
     * Returns -1 if the slot is empty (e.g., the title page area).
     */
    private int getItemIndexForSlot(int slotIndex) {
        if (currentPage == 0) {
            if (slotIndex < ITEMS_PER_PAGE) {
                return -1;
            }
            return slotIndex - ITEMS_PER_PAGE;
        } else {
            int startItemIndex = ITEMS_PER_PAGE + (currentPage - 1) * ITEMS_PER_VIEW;
            return startItemIndex + slotIndex;
        }
    }

    private void renderGrid(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (int i = 0; i < ITEMS_PER_VIEW; i++) {
            int itemIndex = getItemIndexForSlot(i);

            if (itemIndex >= 0 && itemIndex < currentEntries.size()) {
                EntityType<?> type = currentEntries.get(itemIndex);
                int globalSlotIndex = currentPage * ITEMS_PER_VIEW + i;
                Bounds bounds = getGridCellBounds(globalSlotIndex);

                boolean unlocked = FieldGuideDataManager.isUnlocked(type);
                boolean hovered = bounds.contains(mouseX, mouseY);

                if (hovered) {
                    guiGraphics.blit(Constants.CELL_BACKGROUND_HOVER_TEXTURE, bounds.x(), bounds.y(), 0, 0, CELL_SIZE, CELL_SIZE, CELL_SIZE, CELL_SIZE);
                } else {
                    guiGraphics.blit(Constants.CELL_BACKGROUND_TEXTURE, bounds.x(), bounds.y(), 0, 0, CELL_SIZE, CELL_SIZE, CELL_SIZE, CELL_SIZE);
                }

                renderEntryInGrid(guiGraphics, type, bounds.x_center(), bounds.y_center(), 30, unlocked);

                if (FieldGuideDataManager.isNew(type)) {
                    Component newText = Component.translatable("fieldguide.new");
                    guiGraphics.drawCenteredString(this.font, newText, bounds.x_center(), bounds.bottom() - 8, 0x63B40C);
                }
            }
        }

        Lighting.setupForFlatItems();

        for (int i = 0; i < ITEMS_PER_VIEW; i++) {
            int itemIndex = getItemIndexForSlot(i);
            if (itemIndex >= 0 && itemIndex < currentEntries.size()) {
                int globalSlotIndex = currentPage * ITEMS_PER_VIEW + i;
                Bounds bounds = getGridCellBounds(globalSlotIndex);
                if (bounds.contains(mouseX, mouseY)) {
                    EntityType<?> type = currentEntries.get(itemIndex);
                    boolean unlocked = FieldGuideDataManager.isUnlocked(type);

                    if (unlocked) {
                        guiGraphics.renderTooltip(this.font, type.getDescription(), mouseX, mouseY);
                    } else {
                        guiGraphics.renderTooltip(this.font, Component.translatable("fieldguide.unknown"), mouseX, mouseY);
                    }
                }
            }
        }
    }

    private Bounds getGridCellBounds(int i) {
        Bounds pageBounds;
        int startIndex = currentPage * ITEMS_PER_VIEW;

        if (i < startIndex + ITEMS_PER_PAGE) {
            pageBounds = this.leftPageBounds;
        } else {
            pageBounds = this.rightPageBounds;
        }

        int startX = pageBounds.left() + 6;
        int startY = pageBounds.top() + 11;

        int localIndex = (i - startIndex) % ITEMS_PER_PAGE;
        int col = localIndex % GRID_COLS;
        int row = localIndex / GRID_COLS;

        int x = startX + (col * (CELL_SIZE + GAP));
        int y = startY + (row * (CELL_SIZE + GAP));

        return new Bounds(x, y, CELL_SIZE, CELL_SIZE);
    }

    private void renderPageNumber(int page, int total, Bounds bounds, GuiGraphics guiGraphics) {
        String str = page + " of " + total;
        guiGraphics.drawString(this.font, str, bounds.x_center() - font.width(str) / 2, bounds.bottom() - 16, 0xB2997D, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        for (int i = 0; i < ITEMS_PER_VIEW; i++) {
            int globalSlotIndex = currentPage * ITEMS_PER_VIEW + i;
            if (getGridCellBounds(globalSlotIndex).contains((int) mouseX, (int) mouseY)) {
                int itemIndex = getItemIndexForSlot(i);

                if (itemIndex >= 0 && itemIndex < currentEntries.size()) {
                    EntityType<?> type = currentEntries.get(itemIndex);

                    if (FieldGuideDataManager.isNew(type)) {
                        FieldGuideDataManager.markAsSeen(type);
                    }

                    Minecraft.getInstance().setScreen(new FieldGuideEntryScreen(this, type));
                    return true;
                }
            }
        }
        return false;
    }

    private void renderEntryInGrid(GuiGraphics guiGraphics, EntityType<?> type, int x, int y, int scale, boolean unlocked) {
        if (this.minecraft != null && this.minecraft.level != null) {
            Entity entity = entryCache.computeIfAbsent(type, t -> t.create(this.minecraft.level));
            if (entity instanceof LivingEntity living) {
                EntityRenderHelper.renderEntityNormalized(guiGraphics, living, x, y, CELL_SIZE - 8, CELL_SIZE - 8, scale, !unlocked);
            }
        }
    }
}