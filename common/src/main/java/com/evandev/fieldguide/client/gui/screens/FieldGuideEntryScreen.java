package com.evandev.fieldguide.client.gui.screens;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.gui.util.EntryRenderHelper;
import com.evandev.fieldguide.data.FieldGuideDataManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class FieldGuideEntryScreen extends BookScreen {
    private final Screen parent;
    private final Object entry;
    private Entity renderedEntity;

    public FieldGuideEntryScreen(Screen parent, Object entry) {
        super(getTitleForEntry(entry));
        this.parent = parent;
        this.entry = entry;
    }

    private static Component getTitleForEntry(Object entry) {
        if (FieldGuideDataManager.isUnlocked(entry)) {
            if (entry instanceof EntityType<?> type) return type.getDescription();
            if (entry instanceof Block block) return block.getName();
        }
        return Component.translatable("fieldguide.undiscovered");
    }

    @Override
    protected void init() {
        super.init();

        if (entry instanceof EntityType<?> type) {
            if (this.minecraft != null && this.minecraft.level != null) {
                this.renderedEntity = type.create(this.minecraft.level);
            }
        }

        this.addRenderableWidget(new ImageButton(
                this.bounds.left() - 4,
                this.bounds.top() + 84,
                23,
                23,
                0,
                0,
                23,
                Constants.BACK_TEXTURE,
                23,
                23 * 2,
                b -> this.minecraft.setScreen(parent)
        ));
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // Book background
        RenderSystem.setShaderTexture(0, Constants.BOOK_TEXTURE);
        guiGraphics.blit(Constants.BOOK_TEXTURE, this.bounds.left(), this.bounds.top(), 0, 0, this.bounds.width(), this.bounds.height(), this.bounds.width(), this.bounds.height());
        guiGraphics.blit(Constants.PAGE_DETAILS_TEXTURE, this.bounds.left(), this.bounds.top(), 0, 0, this.bounds.width(), this.bounds.height(), this.bounds.width(), this.bounds.height());

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        boolean unlocked = FieldGuideDataManager.isUnlocked(entry);
        Component title = getTitleForEntry(entry);
        String description = unlocked ? FieldGuideDataManager.getEntryDescription(entry) : Component.translatable("fieldguide.description.locked").getString();

        // Entry name
        guiGraphics.drawString(this.font, title, this.leftPageBounds.x_center() - font.width(title) / 2 + 1, this.leftPageBounds.top() + 14 + 1, Constants.TEXT_SHADOW_COLOR, false);
        guiGraphics.drawString(this.font, title, this.leftPageBounds.x_center() - font.width(title) / 2, this.leftPageBounds.top() + 14, Constants.TEXT_COLOR, false);


        // Entry model
        int xPos = leftPageBounds.left() + leftPageBounds.width() / 2;
        int yPos = leftPageBounds.y_center();

        if (entry instanceof EntityType && renderedEntity instanceof LivingEntity living) {
            if (unlocked) {
                EntryRenderHelper.renderEntityNormalized(guiGraphics, living, xPos, yPos, 100, 100, 80, false);
            } else {
                EntryRenderHelper.renderEntityNormalized(guiGraphics, living, xPos, yPos, 100, 100, 80, true, Constants.DETAILS_SILHOUETTE_COLOR);
            }
        } else if (entry instanceof Block block) {
            EntryRenderHelper.renderBlockItem(guiGraphics, block, xPos, yPos, 4.0F, !unlocked);
        }

        // Description
        int textX = this.rightPageBounds.left() + 11;
        int textY = this.rightPageBounds.top() + 17;
        int textAreaWidth = this.rightPageBounds.width() - 22;

        guiGraphics.drawWordWrap(font, Component.literal(description), textX, textY, textAreaWidth, Constants.TEXT_COLOR);
    }
}