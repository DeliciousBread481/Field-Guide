package com.evandev.fieldguide.client.gui.screens;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.gui.util.EntryRenderHelper;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.data.FieldGuideDataManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

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
        if (FieldGuideDataManager.isUnlocked(entry) || ModConfig.get().showUndiscoveredNames) {
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
                try {
                    this.renderedEntity = type.create(this.minecraft.level);
                } catch (Exception e) {
                    Constants.LOG.error("Failed to create entity preview: {}", type.getDescription().getString(), e);
                    this.renderedEntity = null;
                }
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
                EntryRenderHelper.renderEntityNormalized(guiGraphics, living, xPos, yPos, 100, 100, 80, false, 0, true);
            } else {
                EntryRenderHelper.renderEntityNormalized(guiGraphics, living, xPos, yPos, 100, 100, 80, true, Constants.DETAILS_SILHOUETTE_COLOR, true);
            }
        } else if (entry instanceof Block block) {
            EntryRenderHelper.renderBlock(guiGraphics, block, xPos, yPos, 30.0F, !unlocked, true);
        }

        // Description
        int textX = this.rightPageBounds.left() + 11;
        int textY = this.rightPageBounds.top() + 17;
        int textAreaWidth = this.rightPageBounds.width() - 22;

        guiGraphics.drawWordWrap(font, Component.literal(description), textX, textY, textAreaWidth, Constants.TEXT_COLOR);

        // Drops
        if (unlocked) {
            List<ItemStack> drops = FieldGuideDataManager.getInstance().getDrops(entry);

            if (!drops.isEmpty()) {
                int itemSize = 18;
                int spacing = 2;
                int maxLineWidth = this.rightPageBounds.width() - 20;

                List<List<ItemStack>> lines = new ArrayList<>();
                List<ItemStack> currentLine = new ArrayList<>();
                int currentWidth = 0;

                for (ItemStack stack : drops) {
                    int needed = (currentLine.isEmpty() ? 0 : spacing) + itemSize;
                    if (currentWidth + needed > maxLineWidth) {
                        lines.add(currentLine);
                        currentLine = new ArrayList<>();
                        currentWidth = 0;
                    }
                    currentWidth += (currentLine.isEmpty() ? 0 : spacing) + itemSize;
                    currentLine.add(stack);
                }
                lines.add(currentLine);

                int totalBlockHeight = lines.size() * itemSize + (lines.size() - 1) * spacing;
                int startY = this.rightPageBounds.bottom() - 15 - totalBlockHeight;
                int originalStartY = startY;

                for (List<ItemStack> line : lines) {
                    int lineWidth = line.size() * itemSize + (line.size() - 1) * spacing;
                    int startX = this.rightPageBounds.x_center() - (lineWidth / 2);

                    for (ItemStack stack : line) {
                        guiGraphics.blit(Constants.ITEM_BACKGROUND_TEXTURE, startX, startY, 0, 0, itemSize, itemSize, itemSize, itemSize);
                        guiGraphics.renderItem(stack, startX + 1, startY + 1);
                        guiGraphics.renderItemDecorations(this.font, stack, startX + 1, startY + 1);
                        startX += itemSize + spacing;
                    }
                    startY += itemSize + spacing;
                }

                startY = originalStartY;
                for (List<ItemStack> line : lines) {
                    int lineWidth = line.size() * itemSize + (line.size() - 1) * spacing;
                    int startX = this.rightPageBounds.x_center() - (lineWidth / 2);

                    for (ItemStack stack : line) {
                        if (mouseX >= startX && mouseX < startX + itemSize && mouseY >= startY && mouseY < startY + itemSize) {
                            guiGraphics.renderTooltip(this.font, stack, mouseX, mouseY);
                        }
                        startX += itemSize + spacing;
                    }
                    startY += itemSize + spacing;
                }
            }
        }
    }
}