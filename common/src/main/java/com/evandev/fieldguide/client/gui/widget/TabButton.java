package com.evandev.fieldguide.client.gui.widget;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.api.Category;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.gui.screens.BookScreen;
import com.evandev.fieldguide.entry.EntryResolver;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class TabButton extends ImageButton {
    private final Category category;
    private final BookScreen parent;

    public TabButton(int x, int y, int width, int height, Category category, BookScreen parent) {
        super(x, y, width, height, 0, 144, 0, Constants.WIDGETS_TEXTURE, (btn) -> parent.onTabClick(category));
        this.category = category;
        this.parent = parent;

        Component tooltipText = category.getId().getPath().equals("intro")
                ? Component.literal(ClientFieldGuideManager.getInstance().getJournalTitle())
                : Component.translatable("category.fieldguide." + category.getId().getPath());

        this.setTooltip(Tooltip.create(tooltipText));
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean isSelected = (category == parent.getSelectedCategory());
        int vOffset = isSelected ? 24 : 0;

        guiGraphics.blit(Constants.WIDGETS_TEXTURE, this.getX(), this.getY(), 0, 144 + vOffset, this.width, this.height);

        int iconX = this.getX() + 3;
        int iconY = this.getY() + 4;
        if (isSelected) iconX = iconX + 1;

        ResourceLocation icon = category.getIcon();
        Item item = BuiltInRegistries.ITEM.get(EntryResolver.getRawId(icon));
        if (item != BuiltInRegistries.ITEM.get(BuiltInRegistries.ITEM.getDefaultKey())) {
            guiGraphics.renderItem(new ItemStack(item), iconX, iconY);
        } else {
            guiGraphics.blit(icon, iconX, iconY, 0, 0, 16, 16, 16, 16);
        }
    }

    @Override
    public void playDownSound(SoundManager handler) {
        handler.play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }
}
