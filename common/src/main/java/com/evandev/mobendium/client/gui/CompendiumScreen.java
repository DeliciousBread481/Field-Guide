package com.evandev.mobendium.client.gui;

import com.evandev.mobendium.Constants;
import com.evandev.mobendium.client.MobDataManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CompendiumScreen extends Screen {
    private static final int ITEMS_PER_PAGE = 9;
    private static final int GRID_COLS = 3;
    private static final int CELL_SIZE = 40; // Size of the box for each mob
    private static final int GAP = 10;

    private static final int BG_WIDTH = 300;
    private static final int BG_HEIGHT = 200;

    private int leftPos;
    private int topPos;
    private int currentPage = 0;
    private List<EntityType<?>> allEntities;

    public CompendiumScreen() {
        super(Component.translatable("title.mobendium.compendium"));
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - BG_WIDTH) / 2;
        this.topPos = (this.height - BG_HEIGHT) / 2;
        this.allEntities = MobDataManager.getValidEntities();

        addRenderableWidget(Button.builder(Component.literal("<"), b -> prevPage())
                .bounds(this.leftPos + 20, this.topPos + 150, 20, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal(">"), b -> nextPage())
                .bounds(this.leftPos + BG_WIDTH - 40, this.topPos + 150, 20, 20)
                .build());
    }

    private void prevPage() {
        if (currentPage > 0) currentPage--;
    }

    private void nextPage() {
        if ((currentPage + 1) * ITEMS_PER_PAGE < allEntities.size()) currentPage++;
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // Book background
        RenderSystem.setShaderTexture(0, Constants.BOOK_TEXTURE);
        guiGraphics.blit(Constants.BOOK_TEXTURE, leftPos, topPos, 0, 0, BG_WIDTH, BG_HEIGHT, BG_WIDTH, BG_HEIGHT);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Page number
        int totalPages = (int) Math.ceil((double) allEntities.size() / ITEMS_PER_PAGE);
        String pageStr = (currentPage + 1) + " of " + totalPages;
        guiGraphics.drawCenteredString(this.font, pageStr, this.width / 2, this.topPos + 155, 0x404040);

        // Mobs grid
        int startX = leftPos + 55;
        int startY = topPos + 30;

        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allEntities.size());

        for (int i = startIndex; i < endIndex; i++) {
            EntityType<?> type = allEntities.get(i);
            int localIndex = i - startIndex;
            int col = localIndex % GRID_COLS;
            int row = localIndex / GRID_COLS;

            int x = startX + (col * (CELL_SIZE + GAP));
            int y = startY + (row * (CELL_SIZE + GAP));

            boolean unlocked = MobDataManager.isUnlocked(type);
            boolean hovered = mouseX >= x && mouseX < x + CELL_SIZE && mouseY >= y && mouseY < y + CELL_SIZE;

            // Slot background/highlight
            if (hovered && unlocked) {
                guiGraphics.fill(x, y, x + CELL_SIZE, y + CELL_SIZE, 0x80FFFFFF);
            }

            renderMobInGrid(guiGraphics, type, x + CELL_SIZE / 2, y + CELL_SIZE - 5, 15, unlocked);

            // Tooltip on hover
            if (hovered) {
                if (unlocked) {
                    guiGraphics.renderTooltip(this.font, type.getDescription(), mouseX, mouseY);
                } else {
                    guiGraphics.renderTooltip(this.font, Component.translatable("mobendium.unknown"), mouseX, mouseY);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        int startX = leftPos + 55;
        int startY = topPos + 30;
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, allEntities.size());

        for (int i = startIndex; i < endIndex; i++) {
            int localIndex = i - startIndex;
            int col = localIndex % GRID_COLS;
            int row = localIndex / GRID_COLS;
            int x = startX + (col * (CELL_SIZE + GAP));
            int y = startY + (row * (CELL_SIZE + GAP));

            if (mouseX >= x && mouseX < x + CELL_SIZE && mouseY >= y && mouseY < y + CELL_SIZE) {
                EntityType<?> type = allEntities.get(i);
                if (MobDataManager.isUnlocked(type)) {
                    Minecraft.getInstance().setScreen(new MobDetailScreen(this, type));
                    return true;
                }
            }
        }
        return false;
    }

    private void renderMobInGrid(GuiGraphics guiGraphics, EntityType<?> type, int x, int y, int scale, boolean unlocked) {
        Entity entity = null;
        if (Minecraft.getInstance().level != null) {
            entity = type.create(Minecraft.getInstance().level);
        }
        if (entity instanceof LivingEntity living) {
            if (!unlocked) {
                // TODO: render silhouettes
                guiGraphics.drawCenteredString(font, "?", x, y - 10, 0x000000);
            } else {
                InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics, x, y, scale, 0, 0, living);
            }
        }
    }

}