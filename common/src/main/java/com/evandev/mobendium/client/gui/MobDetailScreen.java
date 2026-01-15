package com.evandev.mobendium.client.gui;

import com.evandev.mobendium.Constants;
import com.evandev.mobendium.client.MobDataManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public class MobDetailScreen extends Screen {
    private static final int BG_WIDTH = 300;
    private static final int BG_HEIGHT = 200;

    private final Screen parent;
    private final EntityType<?> entityType;
    private Entity renderedEntity;
    private int leftPos;
    private int topPos;

    public MobDetailScreen(Screen parent, EntityType<?> entityType) {
        super(entityType.getDescription());
        this.parent = parent;
        this.entityType = entityType;
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - BG_WIDTH) / 2;
        this.topPos = (this.height - BG_HEIGHT) / 2;

        if (this.minecraft != null && this.minecraft.level != null) {
            this.renderedEntity = entityType.create(this.minecraft.level);
        }

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.minecraft.setScreen(parent))
                .bounds(this.leftPos + 10, this.topPos + 10, 40, 20)
                .build());
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // Book background
        RenderSystem.setShaderTexture(0, Constants.BOOK_TEXTURE);
        guiGraphics.blit(Constants.BOOK_TEXTURE, leftPos, topPos, 0, 0, BG_WIDTH, BG_HEIGHT, BG_WIDTH, BG_HEIGHT);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Entity name
        guiGraphics.drawCenteredString(font, entityType.getDescription(), leftPos + (BG_WIDTH / 4), topPos + 20, 0x000000);

        // Entity model
        if (renderedEntity instanceof LivingEntity living) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics, leftPos + 65, topPos + 120, 40,
                    (float) (leftPos + 65) - mouseX,
                    (float) (topPos + 120 - 50) - mouseY,
                    living);
        }

        // Description
        String description = MobDataManager.getEntityDescription(entityType);
        int textX = leftPos + 135;
        int textY = topPos + 25;
        int textAreaWidth = 100;

        guiGraphics.drawWordWrap(font, Component.literal(description), textX, textY, textAreaWidth, 0x000000);
    }

}