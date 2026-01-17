package com.evandev.fieldguide.client.gui;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.MobDataManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public class MobDetailScreen extends BookScreen {
    private final Screen parent;
    private final EntityType<?> entityType;
    private Entity renderedEntity;

    public MobDetailScreen(Screen parent, EntityType<?> entityType) {
        super(entityType.getDescription());
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
                23*2,
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

        // Entity name
        guiGraphics.drawString(this.font, entityType.getDescription(), this.leftPageBounds.left() + this.leftPageBounds.width() / 2 - font.width(entityType.getDescription()) / 2, this.leftPageBounds.top() + 14, 0x7A583C, false);

        // Entity model
        int xPos = leftPageBounds.left() + leftPageBounds.width()/2;
        int yPos = leftPageBounds.bottom() - 27;
        if (renderedEntity instanceof LivingEntity living) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics, xPos, yPos, 40,
                    (float) xPos - mouseX,
                    (float) yPos - mouseY,
                    living);
        }

        // Description
        String description = MobDataManager.getEntityDescription(entityType);
        int textX = this.rightPageBounds.left() + 11;
        int textY = this.rightPageBounds.top() + 17;
        int textAreaWidth = this.rightPageBounds.width() - 22;

        guiGraphics.drawWordWrap(font, Component.literal(description), textX, textY, textAreaWidth, 0x7A583C);
    }

}