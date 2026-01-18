package com.evandev.fieldguide.client.gui.screens;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.data.FieldGuideDataManager;
import com.evandev.fieldguide.client.gui.util.EntityRenderHelper;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public class FieldGuideEntryScreen extends BookScreen {
    private final Screen parent;
    private final EntityType<?> entityType;
    private Entity renderedEntity;

    public FieldGuideEntryScreen(Screen parent, EntityType<?> entityType) {
        super(FieldGuideDataManager.isUnlocked(entityType) ? entityType.getDescription() : Component.translatable("fieldguide.undiscovered"));
        this.parent = parent;
        this.entityType = entityType;
    }

    @Override
    protected void init() {
        super.init();

        if (this.minecraft != null && this.minecraft.level != null) {
            this.renderedEntity = entityType.create(this.minecraft.level);
        }

        this.addRenderableWidget(new ImageButton(
                this.bounds.left() - 2,
                this.bounds.top() + 27,
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

        boolean unlocked = FieldGuideDataManager.isUnlocked(entityType);
        Component title = unlocked ? entityType.getDescription() : Component.translatable("fieldguide.undiscovered");
        String description = unlocked ? FieldGuideDataManager.getEntityDescription(entityType) : Component.translatable("fieldguide.description.locked").getString();

        // Entity name
        guiGraphics.drawString(this.font, title, this.leftPageBounds.x_center() - font.width(title) / 2 + 1, this.leftPageBounds.top() + 14 + 1, 0xF6EACD, false);
        guiGraphics.drawString(this.font, title, this.leftPageBounds.x_center() - font.width(title) / 2, this.leftPageBounds.top() + 14, 0x7A583C, false);


        // Entity model
        int xPos = leftPageBounds.left() + leftPageBounds.width() / 2;
        int yPos = leftPageBounds.y_center();
        if (renderedEntity instanceof LivingEntity living) {
            if (unlocked) {
                EntityRenderHelper.renderEntityNormalized(guiGraphics, living, xPos, yPos, 100, 100, 80, false);
            } else {
                EntityRenderHelper.renderEntityNormalized(guiGraphics, living, xPos, yPos, 100, 100, 80, true, 0.0F, 0.0F, 0.0F);
            }
        }

        // Description
        int textX = this.rightPageBounds.left() + 11;
        int textY = this.rightPageBounds.top() + 17;
        int textAreaWidth = this.rightPageBounds.width() - 22;

        guiGraphics.drawWordWrap(font, Component.literal(description), textX, textY, textAreaWidth, 0x7A583C);
    }
}