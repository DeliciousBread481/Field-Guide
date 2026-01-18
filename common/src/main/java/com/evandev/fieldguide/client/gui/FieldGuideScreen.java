package com.evandev.fieldguide.client.gui;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.data.FieldGuideDataManager;
import com.evandev.fieldguide.client.gui.util.Bounds;
import com.evandev.fieldguide.client.gui.util.EntityRenderHelper;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FieldGuideScreen extends BookScreen {
    private static final int ITEMS_PER_PAGE = 18;
    private static final int GRID_COLS = 3;
    private static final int CELL_SIZE = 40;
    private static final int GAP = 0;
    private final Map<EntityType<?>, Entity> entityCache = new HashMap<>();
    private int currentPage = 0;
    private List<EntityType<?>> allEntities;
    private ImageButton prevPageButton;
    private ImageButton nextPageButton;

    public FieldGuideScreen() {
        super(Component.translatable("title.fieldguide.compendium"));
    }

    @Override
    protected void init() {
        super.init();
        this.allEntities = FieldGuideDataManager.getValidEntities();

        this.prevPageButton = new ImageButton(
                this.leftPageBounds.left(),
                this.leftPageBounds.bottom() - 15,
                16,
                16,
                0,
                0,
                16,
                Constants.PREV_PAGE_TEXTURE,
                16,
                16 * 2,
                b -> prevPage()
        );

        this.nextPageButton = new ImageButton(
                this.rightPageBounds.right() - 16,
                this.rightPageBounds.bottom() - 15,
                16,
                16,
                0,
                0,
                16,
                Constants.NEXT_PAGE_TEXTURE,
                16,
                16 * 2,
                b -> nextPage()
        );

        this.addRenderableWidget(prevPageButton);
        this.addRenderableWidget(nextPageButton);
        updatePageButtons();
    }

    private void updatePageButtons() {
        this.prevPageButton.visible = currentPage > 0;
        this.nextPageButton.visible = (currentPage + 1) * ITEMS_PER_PAGE < allEntities.size();
    }

    private void prevPage() {
        if (currentPage > 0) {
            currentPage--;
            updatePageButtons();
        }
    }

    private void nextPage() {
        if ((currentPage + 1) * ITEMS_PER_PAGE < allEntities.size()) {
            currentPage++;
            updatePageButtons();
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // Book background
        RenderSystem.setShaderTexture(0, Constants.BOOK_TEXTURE);
        guiGraphics.blit(Constants.BOOK_TEXTURE, this.bounds.left(), this.bounds.top(), 0, 0, this.bounds.width(), this.bounds.height(), this.bounds.width(), this.bounds.height());

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Page numbers
        int totalPages = (int) Math.ceil((double) allEntities.size() / ITEMS_PER_PAGE);

        renderPageNumber((currentPage + 1) * 2 - 1, totalPages * 2, this.leftPageBounds, guiGraphics);
        renderPageNumber((currentPage + 1) * 2, totalPages * 2, this.rightPageBounds, guiGraphics);

        // Mobs grid
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allEntities.size());

        for (int i = startIndex; i < endIndex; i++) {
            EntityType<?> type = allEntities.get(i);
            Bounds bounds = getGridCellBounds(i);
            boolean unlocked = FieldGuideDataManager.isUnlocked(type);
            boolean hovered = bounds.contains(mouseX, mouseY);

            // Render background
            if (hovered) {
                guiGraphics.blit(Constants.CELL_BACKGROUND_HOVER_TEXTURE, bounds.x(), bounds.y(), 0, 0, CELL_SIZE, CELL_SIZE, CELL_SIZE, CELL_SIZE);
            } else {
                guiGraphics.blit(Constants.CELL_BACKGROUND_TEXTURE, bounds.x(), bounds.y(), 0, 0, CELL_SIZE, CELL_SIZE, CELL_SIZE, CELL_SIZE);
            }

            // Render mob
            renderMobInGrid(guiGraphics, type, bounds.x_center(), bounds.y_center(), 30, unlocked);

            // Render "new" tooltip
            if (FieldGuideDataManager.isNew(type)) {
                Component newText = Component.translatable("fieldguide.new");
                guiGraphics.drawCenteredString(this.font, newText, bounds.x_center(), bounds.bottom() - 8, 0x63B40C);
            }
        }

        Lighting.setupForFlatItems();

        // Tooltip on hover
        for (int i = startIndex; i < endIndex; i++) {
            Bounds bounds = getGridCellBounds(i);
            if (bounds.contains(mouseX, mouseY)) {
                EntityType<?> type = allEntities.get(i);
                boolean unlocked = FieldGuideDataManager.isUnlocked(type);

                if (unlocked) {
                    guiGraphics.renderTooltip(this.font, type.getDescription(), mouseX, mouseY);
                } else {
                    guiGraphics.renderTooltip(this.font, Component.translatable("fieldguide.unknown"), mouseX, mouseY);
                }
            }
        }
    }

    private Bounds getGridCellBounds(int i) {
        Bounds pageBounds;
        int startIndex = currentPage * ITEMS_PER_PAGE;
        if (i < startIndex + ITEMS_PER_PAGE / 2) {
            pageBounds = this.leftPageBounds;
        } else {
            pageBounds = this.rightPageBounds;
        }
        int startX = pageBounds.left() + 6;
        int startY = pageBounds.top() + 11;
        int localIndex = (i - startIndex) % (ITEMS_PER_PAGE / 2);
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
            this.setFocused(null);
            return true;
        }

        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allEntities.size());

        for (int i = startIndex; i < endIndex; i++) {
            if (getGridCellBounds(i).contains((int) mouseX, (int) mouseY)) {
                EntityType<?> type = allEntities.get(i);

                if (FieldGuideDataManager.isNew(type)) {
                    FieldGuideDataManager.markAsSeen(type);
                }

                Minecraft.getInstance().setScreen(new FieldGuideEntryScreen(this, type));
                return true;
            }
        }
        return false;
    }

    private void renderMobInGrid(GuiGraphics guiGraphics, EntityType<?> type, int x, int y, int scale, boolean unlocked) {
        if (this.minecraft != null && this.minecraft.level != null) {
            Entity entity = entityCache.computeIfAbsent(type, t -> t.create(this.minecraft.level));
            if (entity instanceof LivingEntity living) {
                EntityRenderHelper.renderEntityNormalized(guiGraphics, living, x, y, CELL_SIZE - 8, CELL_SIZE - 8, scale, !unlocked);
            }
        }
    }
}