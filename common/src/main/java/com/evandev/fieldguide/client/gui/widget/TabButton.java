package com.evandev.fieldguide.client.gui.widget;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.gui.screens.FieldGuideScreen;
import com.evandev.fieldguide.data.Category;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class TabButton extends ImageButton {
    private final Category category;
    private final FieldGuideScreen parent;
//    private final ItemStack iconStack;
    private final float r, g, b;

    public TabButton(int x, int y, int width, int height, Category category, FieldGuideScreen parent) {
        super(x, y, width, height, 0, 0, 0, Constants.TAB_TEXTURE, 24, 40, (btn) -> parent.selectCategory(category));
        this.category = category;
        this.parent = parent;

        int colorInt = parseColor(category.getTabColor());
        this.r = ((colorInt >> 16) & 0xFF) / 255.0F;
        this.g = ((colorInt >> 8) & 0xFF) / 255.0F;
        this.b = (colorInt & 0xFF) / 255.0F;

//        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(category.getTabIcon()));
//        this.iconStack = stack.isEmpty() ? new ItemStack(Items.BARRIER) : stack;

        this.setTooltip(Tooltip.create(Component.translatable("category.fieldguide." + category.getId().getPath())));
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean isSelected = (category == parent.getSelectedCategory());

//        RenderSystem.setShaderColor(r, g, b, 1.0F);

        int vOffset = isSelected ? 24 : 0;

        guiGraphics.blit(Constants.TAB_TEXTURE, this.getX(), this.getY(), 0, vOffset, this.width, this.height, 24, 48);
//        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        int iconX = this.getX() + 4;
        int iconY = this.getY() + 3;
        if (isSelected) {
            iconY = iconY - 1;
        }
        guiGraphics.blit(category.getTabIcon(), iconX, iconY, 0, 0, 16, 16, 16, 16);
//        guiGraphics.renderItem(iconStack, iconX, iconY);
    }

    private int parseColor(String hex) {
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }
}