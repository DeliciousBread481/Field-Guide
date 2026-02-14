package com.evandev.fieldguide.client.gui.widget;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.data.CategoryVisual;
import com.evandev.fieldguide.client.gui.screens.BookScreen;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class TabButton extends ImageButton {
    private final Category category;
    private final BookScreen parent;
    private final float r, g, b;
    private final CategoryVisual visual;

    public TabButton(int x, int y, int width, int height, Category category, BookScreen parent) {
        super(x, y, width, height, 0, 0, 0, Constants.TAB_TEXTURE, 24, 40, (btn) -> parent.onTabClick(category));
        this.category = category;
        this.parent = parent;

        this.visual = ClientFieldGuideManager.getInstance().getCategoryVisual(category.getId());

        int colorInt = visual.getColorInt();
        this.r = ((colorInt >> 16) & 0xFF) / 255.0F;
        this.g = ((colorInt >> 8) & 0xFF) / 255.0F;
        this.b = (colorInt & 0xFF) / 255.0F;

        this.setTooltip(Tooltip.create(Component.translatable("category.fieldguide." + category.getId().getPath())));
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean isSelected = (category == parent.getSelectedCategory());
        int vOffset = isSelected ? 24 : 0;

        // Tab tinting
        // guiGraphics.setColor(r, g, b, 1.0F);

        guiGraphics.blit(Constants.TAB_TEXTURE, this.getX(), this.getY(), 0, vOffset, this.width, this.height, 24, 48);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        int iconX = this.getX() + 3;
        int iconY = this.getY() + 3;
        if (isSelected) iconX = iconX + 1;

        guiGraphics.blit(visual.icon, iconX, iconY, 0, 0, 16, 16, 16, 16);
    }

    @Override
    public void playDownSound(SoundManager handler) {
        handler.play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }
}