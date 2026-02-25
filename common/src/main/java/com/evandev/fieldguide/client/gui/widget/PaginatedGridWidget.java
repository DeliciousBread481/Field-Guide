package com.evandev.fieldguide.client.gui.widget;

import com.evandev.fieldguide.Constants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;

public class PaginatedGridWidget<T> extends AbstractWidget {
    private final List<T> items;
    private final int itemsPerPage;
    private final int itemSize;
    private final int spacing;
    private final ImageButton prevButton;
    private final ImageButton nextButton;
    private final ItemRenderer<T> itemRenderer;
    private final Consumer<T> onClick;
    private int currentPage = 1;

    public PaginatedGridWidget(int x, int y, int width, int height, int itemsPerPage, int itemSize, int spacing, List<T> items, ItemRenderer<T> itemRenderer, Consumer<T> onClick) {
        super(x, y, width, height, Component.empty());
        this.items = items;
        this.itemsPerPage = itemsPerPage;
        this.itemSize = itemSize;
        this.spacing = spacing;
        this.itemRenderer = itemRenderer;
        this.onClick = onClick;
        int buttonSize = 16;
        int buttonYOffset = (itemSize - buttonSize) / 2;

        this.prevButton = new ImageButton(x, y + buttonYOffset, buttonSize, buttonSize, 0, 16, 16, Constants.WIDGETS_TEXTURE, b -> setPage(currentPage - 1));
        this.nextButton = new ImageButton(x + width - 16, y + buttonYOffset, buttonSize, buttonSize, 16, 16, 16, Constants.WIDGETS_TEXTURE, b -> setPage(currentPage + 1));
        updateButtons();
    }

    private int getTotalPages() {
        return (int) Math.ceil((double) items.size() / itemsPerPage);
    }

    private void setPage(int page) {
        this.currentPage = Math.max(1, Math.min(getTotalPages(), page));
        updateButtons();
    }

    private void updateButtons() {
        this.prevButton.visible = getTotalPages() > 1;
        this.nextButton.visible = getTotalPages() > 1;
        this.prevButton.active = currentPage > 1;
        this.nextButton.active = currentPage < getTotalPages();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (prevButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (nextButton.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == 0 && onClick != null && !items.isEmpty()) {
            int indexStart = itemsPerPage * (currentPage - 1);
            int indexEnd = Math.min(items.size(), itemsPerPage * currentPage);
            int itemsToDraw = indexEnd - indexStart;

            int totalWidth = itemsToDraw * itemSize + Math.max(0, itemsToDraw - 1) * spacing;
            int currentX = this.getX() + (this.width / 2) - (totalWidth / 2);

            for (int i = indexStart; i < indexEnd; i++) {
                if (mouseX >= currentX && mouseX <= currentX + itemSize && mouseY >= this.getY() && mouseY <= this.getY() + itemSize) {
                    onClick.accept(items.get(i));
                    return true;
                }
                currentX += itemSize + spacing;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.isHoveredOrFocused()) {
            setPage(currentPage - (int) Math.signum(delta));
            return true;
        }
        return false;
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (items.isEmpty()) return;

        int indexStart = itemsPerPage * (currentPage - 1);
        int indexEnd = Math.min(items.size(), itemsPerPage * currentPage);
        int itemsToDraw = indexEnd - indexStart;

        int totalWidth = itemsToDraw * itemSize + Math.max(0, itemsToDraw - 1) * spacing;
        int currentX = this.getX() + (this.width / 2) - (totalWidth / 2);

        for (int i = indexStart; i < indexEnd; i++) {
            itemRenderer.render(guiGraphics, items.get(i), currentX, this.getY(), mouseX, mouseY);
            currentX += itemSize + spacing;
        }

        prevButton.render(guiGraphics, mouseX, mouseY, partialTick);
        nextButton.render(guiGraphics, mouseX, mouseY, partialTick);

        if (getTotalPages() > 1) {
            int barY = this.getY() + itemSize + 2;
            int barWidth = (itemSize + spacing) * itemsPerPage - spacing;
            int barStartX = this.getX() + (width / 2) - (barWidth / 2);

            float progressStart = (float) (currentPage - 1) / getTotalPages();
            float progressEnd = (float) currentPage / getTotalPages();

            guiGraphics.fill(barStartX, barY, barStartX + barWidth, barY + 1, 0xFFF9EED0);
            guiGraphics.fill(barStartX + (int) (barWidth * progressStart), barY, barStartX + (int) (barWidth * progressEnd), barY + 1, 0xFFE0D2AE);
        }
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
    }

    public interface ItemRenderer<T> {
        void render(GuiGraphics graphics, T item, int x, int y, int mouseX, int mouseY);
    }
}