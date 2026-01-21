package com.evandev.fieldguide.client.gui.screens;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.gui.util.Bounds;
import com.evandev.fieldguide.client.gui.util.EntityRenderHelper;
import com.evandev.fieldguide.client.gui.widget.TabButton;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.FieldGuideDataManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
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
    private static final int TAB_HEIGHT = 24;
    private static final int TAB_GAP = 0;

    private static ResourceLocation lastOpenedCategory = null;
    private static int lastOpenedPage = 0;

    private final Map<EntityType<?>, Entity> entryCache = new HashMap<>();
    private final List<Category> sortedCategories = new ArrayList<>();

    private List<EntityType<?>> currentEntries = new ArrayList<>();

    private Category selectedCategory;
    private int currentPage = 0;

    private ImageButton prevPageButton;
    private ImageButton nextPageButton;

    public FieldGuideScreen() {
        super(Component.translatable("title.fieldguide.field_guide"));
    }

    /**
     * Constructor to open a specific category immediately.
     */
    public FieldGuideScreen(Category initialCategory) {
        this();
        this.selectedCategory = initialCategory;
    }

    /**
     * Constructor to open a specific category and page.
     */
    public FieldGuideScreen(Category initialCategory, int initialPage) {
        this(initialCategory);
        this.currentPage = initialPage;
    }

    /**
     * Helper to calculate which page an entity is on.
     */
    public static int getPageForEntry(Category category, EntityType<?> entry) {
        int index = category.getEntities().indexOf(entry);
        if (index < 0) return 0;

        if (index < ITEMS_PER_PAGE) {
            return 0;
        }

        return 1 + (index - ITEMS_PER_PAGE) / ITEMS_PER_VIEW;
    }

    @Override
    protected void init() {
        super.init();

        this.sortedCategories.clear();
        this.sortedCategories.addAll(FieldGuideDataManager.getCategories().values());

        this.sortedCategories.sort(Comparator.comparingInt(Category::getTabIndex)
                .thenComparing(c -> c.getId().getPath()));

        if (this.selectedCategory != null) {
            Category c = this.selectedCategory;
            int savedPage = this.currentPage;

            this.selectedCategory = null;
            selectCategory(c);

            this.currentPage = savedPage;
            lastOpenedPage = savedPage;
        } else {
            Category categoryToOpen = null;
            int pageToRestore = 0;

            if (lastOpenedCategory != null) {
                categoryToOpen = FieldGuideDataManager.getCategories().get(lastOpenedCategory);
                if (categoryToOpen != null) {
                    pageToRestore = lastOpenedPage;
                }
            }

            if (categoryToOpen == null && !sortedCategories.isEmpty()) {
                categoryToOpen = sortedCategories.get(0);
            }

            if (categoryToOpen != null) {
                selectCategory(categoryToOpen);

                this.currentPage = pageToRestore;

                int totalSpreads = getTotalSpreads();
                if (this.currentPage >= totalSpreads) {
                    this.currentPage = Math.max(0, totalSpreads - 1);
                }

                lastOpenedPage = this.currentPage;
            }
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
        int startX = this.bounds.left() + 25;

        for (int i = 0; i < sortedCategories.size(); i++) {
            Category category = sortedCategories.get(i);
            int xPos = startX + (i * (TAB_WIDTH + TAB_GAP));
            int yPos = this.bounds.top();

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
     */
    public void selectCategory(Category category) {
        if (this.selectedCategory == category) return;

        this.selectedCategory = category;
        this.currentPage = 0;

        this.currentEntries = category.getEntities();

        lastOpenedCategory = this.selectedCategory.getId();
        lastOpenedPage = this.currentPage;

        if (!this.children().isEmpty()) {
            this.rebuildWidgets();
        }

        if (this.prevPageButton != null) {
            updatePageButtons();
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
            lastOpenedPage = currentPage;
            updatePageButtons();
        }
    }

    private void nextPage() {
        if (currentPage < getTotalSpreads() - 1) {
            currentPage++;
            lastOpenedPage = currentPage;
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
        guiGraphics.blit(Constants.TITLE_PAGE_TEXTURE, this.leftPageBounds.left(), this.leftPageBounds.top(), 0, 0, this.leftPageBounds.width(), this.leftPageBounds.height(), this.leftPageBounds.width(), this.leftPageBounds.height());

        Component title = Component.translatable("category.fieldguide." + selectedCategory.getId().getPath());

        int titleY = this.leftPageBounds.top() + 60;
        List<FormattedCharSequence> lines = this.font.split(title, 70);

        for (FormattedCharSequence line : lines) {
            int lineWidth = this.font.width(line);
            int lineX = this.leftPageBounds.x_center() - lineWidth / 2;

            guiGraphics.drawString(this.font, line, lineX + 1, titleY + 1, Constants.TEXT_SHADOW_COLOR, false);
            guiGraphics.drawString(this.font, line, lineX, titleY, Constants.TEXT_COLOR, false);

            titleY += this.font.lineHeight;
        }

        int total = currentEntries.size();
        if (total > 0) {
            long unlocked = currentEntries.stream().filter(FieldGuideDataManager::isUnlocked).count();

            int barWidth = 92;
            int barHeight = 2;
            int x = this.leftPageBounds.x_center() - barWidth / 2;
            int y = this.leftPageBounds.bottom() - 46;

            int progressWidth = (int) ((float) unlocked / total * barWidth);
            guiGraphics.fill(x, y, x + progressWidth, y + barHeight, 0xFF7A583C);

            String countText = String.valueOf(unlocked);
            String ofText = " of ";
            String totalText = String.valueOf(total);

            int totalWidth = font.width(countText) + font.width(ofText) + font.width(totalText);
            int textX = this.leftPageBounds.x_center() - totalWidth / 2;
            int textY = y + 10;

            guiGraphics.drawString(this.font, countText, textX, textY, Constants.TEXT_COLOR, false);
            textX += font.width(countText);

            guiGraphics.drawString(this.font, ofText, textX, textY, Constants.TEXT_MUTED_COLOR, false);
            textX += font.width(ofText);

            guiGraphics.drawString(this.font, totalText, textX, textY, Constants.TEXT_COLOR, false);
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
                    int textWidth = this.font.width(newText);
                    int textX = bounds.x_center() - textWidth / 2;
                    int textY = bounds.bottom() - 8;

                    guiGraphics.drawString(this.font, newText, textX, textY, 0x63B40C, false);
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
        guiGraphics.drawString(this.font, str, bounds.x_center() - font.width(str) / 2, bounds.bottom() - 16, Constants.PAGE_NUMBER_COLOR, false);
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

                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));

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