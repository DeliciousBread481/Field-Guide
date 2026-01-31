package com.evandev.fieldguide.client.gui.screens;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.client.gui.util.EntryRenderHelper;
import com.evandev.fieldguide.config.ModConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FieldGuideEntryScreen extends BookScreen {
    private final Screen parent;
    private final Object entry;
    private Entity renderedEntity;

    private long lastClickTime = 0;

    public FieldGuideEntryScreen(Screen parent, Object entry) {
        super(getTitleForEntry(entry));
        this.parent = parent;
        this.entry = entry;
    }

    private static Component getTitleForEntry(Object entry) {
        if (ClientFieldGuideManager.isUnlocked(entry) || ModConfig.get().showUndiscoveredNames) {
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
                this.bounds.left() - 10,
                this.bounds.top() + 31,
                24,
                24,
                0,
                0,
                24,
                Constants.BACK_TEXTURE,
                24,
                24 * 2,
                b -> Objects.requireNonNull(this.minecraft).setScreen(parent)
        ));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == 0 && renderedEntity != null) {
            int xPos = leftPageBounds.left() + leftPageBounds.width() / 2;
            int yPos = leftPageBounds.y_center();
            int halfSize = 50;

            if (mouseX >= xPos - halfSize && mouseX <= xPos + halfSize &&
                    mouseY >= yPos - halfSize && mouseY <= yPos + halfSize) {

                if (ClientFieldGuideManager.isUnlocked(entry)) {
                    FieldGuideClient.playMobCry(this.renderedEntity);

                    this.lastClickTime = System.currentTimeMillis();
                }
                return true;
            }
        }
        return false;
    }

    private void renderAttributes(GuiGraphics guiGraphics, LivingEntity entity, int x, int y) {
        RenderSystem.setShaderTexture(0, Constants.ATTRIBUTES_TEXTURE);

        float maxHealth = entity.getMaxHealth();
        int armor = entity.getArmorValue();

        int iconSize = 9;
        int spacing = 1;
        int rowHeight = iconSize + 1;
        int iconsPerRow = 10;
        int healthPerRow = iconsPerRow * 2;


        int totalHeartRows = Mth.ceil(maxHealth / (float) healthPerRow);
        int visibleHeartRows = Math.min(totalHeartRows, 2);

        int currentBottomY = y - iconSize;

        int x1 = x + (iconsPerRow * (iconSize + spacing)) + 2;
        for (int row = 0; row < visibleHeartRows; row++) {
            int drawY = currentBottomY - (row * rowHeight);

            boolean isOverflowRow = (row == visibleHeartRows - 1) && (totalHeartRows > visibleHeartRows);

            int heartsToRender;
            if (isOverflowRow) {
                heartsToRender = iconsPerRow;
            } else {
                float healthInThisRowRange = maxHealth - (row * healthPerRow);
                heartsToRender = Mth.ceil(Math.min(healthPerRow, Math.max(0, healthInThisRowRange)) / 2.0F);
            }

            for (int i = 0; i < heartsToRender; ++i) {
                int drawX = x + i * (iconSize + spacing);
                int u = 0;

                if (!isOverflowRow) {
                    double currentIconHp = (row * healthPerRow) + (i * 2) + 1;
                    if (currentIconHp == (int) maxHealth) {
                        u = iconSize;
                    }
                }

                guiGraphics.blit(Constants.ATTRIBUTES_TEXTURE, drawX, drawY, u, 0, iconSize, iconSize, 32, 32);
            }

            if (isOverflowRow) {
                String multiplier = "x" + totalHeartRows;
                guiGraphics.drawString(this.font, multiplier, x1, drawY + 1, Constants.TEXT_COLOR, false);
            }
        }

        if (armor > 0) {
            int armorDrawY = currentBottomY - (visibleHeartRows * rowHeight);
            int totalArmorRows = Mth.ceil(armor / 20.0f);

            boolean isArmorOverflow = totalArmorRows > 1;
            int armorIconsToRender = isArmorOverflow ? iconsPerRow : Mth.ceil(armor / 2.0F);

            for (int i = 0; i < armorIconsToRender; ++i) {
                int drawX = x + i * (iconSize + spacing);

                int u = 0;

                if (!isArmorOverflow) {
                    if (i * 2 + 1 == armor) {
                        u = iconSize;
                    }
                }

                guiGraphics.blit(Constants.ATTRIBUTES_TEXTURE, drawX, armorDrawY, u, iconSize, iconSize, iconSize, 32, 32);
            }

            if (isArmorOverflow) {
                String multiplier = "x" + totalArmorRows;
                guiGraphics.drawString(this.font, multiplier, x1, armorDrawY + 1, Constants.TEXT_COLOR, false);
            }
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // Book background
        RenderSystem.setShaderTexture(0, Constants.BOOK_TEXTURE);
        guiGraphics.blit(Constants.BOOK_TEXTURE, this.bounds.left(), this.bounds.top(), 0, 0, this.bounds.width(), this.bounds.height(), this.bounds.width(), this.bounds.height());
        guiGraphics.blit(Constants.PAGE_DETAILS_TEXTURE, this.bounds.left(), this.bounds.top(), 0, 0, this.bounds.width(), this.bounds.height(), this.bounds.width(), this.bounds.height());

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        boolean unlocked = ClientFieldGuideManager.isUnlocked(entry);
        Component title = getTitleForEntry(entry);
        String description = unlocked ? ClientFieldGuideManager.getEntryDescription(entry) : Component.translatable("fieldguide.description.locked").getString();

        // Entry name
        guiGraphics.drawString(this.font, title, this.leftPageBounds.x_center() - font.width(title) / 2 + 1, this.leftPageBounds.top() + 14 + 1, Constants.TEXT_SHADOW_COLOR, false);
        guiGraphics.drawString(this.font, title, this.leftPageBounds.x_center() - font.width(title) / 2, this.leftPageBounds.top() + 14, Constants.TEXT_COLOR, false);

        // Bounce
        float bounce = 1.0f;
        long elapsed = System.currentTimeMillis() - lastClickTime;
        if (elapsed < 200) {
            float t = elapsed / 200f;
            bounce = 1.0f + 0.15f * (float) Math.sin(t * Math.PI);
        }

        int xPos = leftPageBounds.left() + leftPageBounds.width() / 2;
        int yPos = leftPageBounds.y_center();

        if (entry instanceof EntityType && renderedEntity instanceof LivingEntity living) {
            if (unlocked) {
                EntryRenderHelper.renderEntityNormalized(guiGraphics, living, xPos, yPos, 100, 100, 80, false, 0, true, bounce);
                renderAttributes(guiGraphics, living, leftPageBounds.left() + 15, leftPageBounds.bottom() - 14);
            } else {
                EntryRenderHelper.renderEntityNormalized(guiGraphics, living, xPos, yPos, 100, 100, 80, true, Constants.DETAILS_SILHOUETTE_COLOR, true, bounce);
            }
        } else if (entry instanceof Block block) {
            EntryRenderHelper.renderBlock(guiGraphics, block, xPos, yPos, 30.0F, !unlocked, true, bounce);
        }

        // Description
        int textX = this.rightPageBounds.left() + 11;
        int textY = this.rightPageBounds.top() + 17;
        int textAreaWidth = this.rightPageBounds.width() - 22;

        guiGraphics.drawWordWrap(font, Component.literal(description), textX, textY, textAreaWidth, Constants.TEXT_COLOR);

        // Drops
        if (unlocked) {
            List<ItemStack> drops = ClientFieldGuideManager.getInstance().getDrops(entry);

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

                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);

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