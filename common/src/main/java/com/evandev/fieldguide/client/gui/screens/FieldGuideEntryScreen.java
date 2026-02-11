package com.evandev.fieldguide.client.gui.screens;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.client.gui.util.Bounds;
import com.evandev.fieldguide.client.gui.util.EntryRenderHelper;
import com.evandev.fieldguide.client.gui.widget.PageTurnButton;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.platform.Services;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class FieldGuideEntryScreen extends BookScreen {
    private final Screen parent;
    private final Object entry;
    private final List<ResourceLocation> spawnBiomes = new ArrayList<>();
    private Entity renderedEntity;
    private long lastClickTime = 0;
    private boolean isCommonSpawn = false;
    private int currentBiomePage = 1;
    private final int biomesPerPage = 6;
    private ResourceLocation hoveredBiome;
    private ItemStack hoveredItem;
    private ImageButton prevBiomePageButton;
    private ImageButton nextBiomePageButton;

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

            if (Services.PLATFORM.isModLoaded("immersiveoverlays")) {
                spawnBiomes.clear();
                Registry<Biome> biomeRegistry = null;
                if (this.minecraft.level != null) {
                    biomeRegistry = this.minecraft.level.registryAccess().registryOrThrow(Registries.BIOME);
                }

                try {
                    if (biomeRegistry != null) {
                        for (var entry : biomeRegistry.entrySet()) {
                            ResourceLocation id = entry.getKey().location();
                            Biome biome = entry.getValue();

                            var spawns = biome.getMobSettings().getMobs(type.getCategory());

                            if (spawns.unwrap().stream().anyMatch(s -> s.type == type)) {
                                ResourceLocation texture = new ResourceLocation(id.getNamespace(), "textures/immersiveoverlays/" + id.getPath() + ".png");
                                if (this.minecraft.getResourceManager().getResource(texture).isPresent()) {
                                    spawnBiomes.add(id);
                                }
                            }
                        }
                    }

                    // Disabled while testing pagination. Does this still make sense to keep?
//                    if (spawnBiomes.size() > 16) {
//                        this.isCommonSpawn = true;
//                        spawnBiomes.clear();
//                        spawnBiomes.add(new ResourceLocation("minecraft", "plains"));  // TODO: generic icon?
//                    }

                } catch (Exception e) {
                    Constants.LOG.error("Failed to load spawn biomes for Field Guide", e);
                }
            }
        }

        this.addRenderableWidget(new PageTurnButton(
                this.bounds.right() + 9 - 24,
                this.bounds.top() + 26,
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

        this.nextBiomePageButton = new ImageButton(
                this.leftPageBounds.right() - 13,
                this.leftPageBounds.bottom() - 24,
                16,
                16,
                16,
                0,
                16,
                Constants.BIOME_PAGINATION_BUTTONS_TEXTURE,
                32,
                48,
                b -> currentBiomePage = (int) Math.min(Math.ceil((double) spawnBiomes.size() / biomesPerPage), currentBiomePage + 1)
        );
        this.prevBiomePageButton = new ImageButton(
                this.leftPageBounds.left() - 3,
                this.leftPageBounds.bottom() - 24,
                16,
                16,
                0,
                0,
                16,
                Constants.BIOME_PAGINATION_BUTTONS_TEXTURE,
                32,
                48,
                b -> currentBiomePage = Math.max(1, currentBiomePage - 1)
        );
        nextBiomePageButton.visible = false;
        prevBiomePageButton.visible = false;
        this.addRenderableWidget(nextBiomePageButton);
        this.addRenderableWidget(prevBiomePageButton);
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
        if (button == 0 && hoveredItem != null) {
            Minecraft.getInstance().setScreen(new FieldGuideScreen("=^" + hoveredItem.getHoverName().getString().toLowerCase(Locale.ROOT), this));
        }
        if (button == 0 && hoveredBiome != null) {
            Minecraft.getInstance().setScreen(new FieldGuideScreen("=!" + hoveredBiome.toString(), this));
        }
        return false;
    }

    private void renderAttributeTextAndIcon(GuiGraphics guiGraphics, int iconOffset, String text, Bounds bounds) {
        int iconSize = 9;
        int iconSpacing = 4;
        int width = iconSize + iconSpacing + font.width(text);

        // Center within given bounds
        int y = bounds.y_center() - iconSize / 2;
        int x = bounds.x_center() - width / 2;

        guiGraphics.blit(Constants.ATTRIBUTES_TEXTURE, x, y, 0, iconOffset, iconSize, iconSize, 32, 32);
        guiGraphics.drawString(this.font, text, x + iconSize + iconSpacing, y + 1, Constants.TEXT_COLOR, false);
    }

    private void renderAttributes(GuiGraphics guiGraphics, LivingEntity entity) {
        RenderSystem.setShaderTexture(0, Constants.ATTRIBUTES_TEXTURE);

        // Health
        String health = String.valueOf((int)entity.getMaxHealth() / 2);
        Bounds healthBounds = new Bounds(leftPageBounds.left(), leftPageBounds.bottom() - 48, leftPageBounds.width() / 2, 20);
        renderAttributeTextAndIcon(guiGraphics, 0, health, healthBounds);

        // Armor
        String armor = String.valueOf(entity.getArmorValue());
        Bounds armorBounds = new Bounds(leftPageBounds.x_center(), leftPageBounds.bottom() - 48, leftPageBounds.width() / 2, 20);
        renderAttributeTextAndIcon(guiGraphics, 9, armor, armorBounds);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        hoveredBiome = null;
        hoveredItem = null;

        // Book Backgrounds
        RenderSystem.setShaderTexture(0, Constants.BOOK_TEXTURE);
        guiGraphics.blit(Constants.BOOK_TEXTURE, this.bounds.left(), this.bounds.top(), 0, 0, this.bounds.width(), this.bounds.height(), this.bounds.width(), this.bounds.height());
        guiGraphics.blit(Constants.DETAILS_PAGE_TEXTURE, this.bounds.left(), this.bounds.top(), 0, 0, this.bounds.width(), this.bounds.height(), this.bounds.width(), this.bounds.height());

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        boolean unlocked = ClientFieldGuideManager.isUnlocked(entry);
        Component title = getTitleForEntry(entry);

        ItemStack tooltipStack = null;
        Component tooltipText = null;

        // Entry Name
        int titleY = this.leftPageBounds.top() + 8;
        guiGraphics.drawString(this.font, title, this.rightPageBounds.left() + 5, titleY, unlocked ? Constants.TEXT_TITLE_COLOR : Constants.TEXT_MUTED_COLOR, false);

        // Spawn Biomes
        int biomeIconSize = 16;
        int biomeSpacing = 0;
        int biomeStartY = this.leftPageBounds.bottom() - 24;

        if (unlocked && !spawnBiomes.isEmpty()) {
            int indexStart = biomesPerPage * (currentBiomePage - 1);
            int indexEnd = Math.min(spawnBiomes.size(), biomesPerPage * currentBiomePage);
            int totalWidth = (indexEnd - indexStart) * (biomeIconSize + biomeSpacing);

            int startX = this.leftPageBounds.x_center() - totalWidth / 2;

            for (int i = indexStart; i < indexEnd; i++) {
                ResourceLocation biomeId = spawnBiomes.get(i);
                ResourceLocation texture = new ResourceLocation(biomeId.getNamespace(), "textures/immersiveoverlays/" + biomeId.getPath() + ".png");
                int x = startX + ((i - indexStart) * (biomeIconSize + biomeSpacing));

                guiGraphics.blit(texture, x, biomeStartY, 0, 0, biomeIconSize, biomeIconSize, biomeIconSize, biomeIconSize);

                if (Bounds.isMouseOver(mouseX, mouseY, x, biomeStartY, biomeIconSize, biomeIconSize)) {
                    if (isCommonSpawn) {
                        tooltipText = Component.translatable("fieldguide.tooltip.common_spawn");
                    } else {
                        hoveredBiome = biomeId;
                        tooltipText = Component.translatable("biome." + biomeId.getNamespace() + "." + biomeId.getPath());
                    }
                }
            }

            nextBiomePageButton.active = indexEnd < spawnBiomes.size();
            prevBiomePageButton.active = currentBiomePage > 1;
            nextBiomePageButton.visible = spawnBiomes.size() > biomesPerPage;
            prevBiomePageButton.visible = spawnBiomes.size() > biomesPerPage;

        } else {
            nextBiomePageButton.visible = false;
            prevBiomePageButton.visible = false;
        }

        // Entity
        float bounce = 1.0f;
        long elapsed = System.currentTimeMillis() - lastClickTime;
        if (elapsed < 200) {
            float t = elapsed / 200f;
            bounce = 1.0f + 0.15f * (float) Math.sin(t * Math.PI);
        }

        int xPos = leftPageBounds.x_center();
        int yPos = leftPageBounds.y_center() - 20;

        if (entry instanceof EntityType && renderedEntity instanceof LivingEntity living) {
            if (unlocked) {
                EntryRenderHelper.renderEntityNormalized(guiGraphics, living, xPos, yPos, 100, 100, 80, false, 0, true, bounce);
                renderAttributes(guiGraphics, living);
            } else {
                EntryRenderHelper.renderEntityNormalized(guiGraphics, living, xPos, yPos, 100, 100, 80, true, Constants.DETAILS_SILHOUETTE_COLOR, true, bounce);
            }
        } else if (entry instanceof Block block) {
            EntryRenderHelper.renderBlock(guiGraphics, block, xPos, yPos, 30.0F, !unlocked, true, bounce);
        }

        // Description
        // TODO: Add pagination for long descriptions
        int textX = this.rightPageBounds.left() + 5;
        int textY = this.rightPageBounds.top() + 25;
        int textAreaWidth = this.rightPageBounds.width() - 10;
        if (unlocked) {
            guiGraphics.drawWordWrap(font, Component.literal(ClientFieldGuideManager.getEntryDescription(entry)), textX, textY, textAreaWidth, Constants.TEXT_COLOR);
        } else {
            guiGraphics.drawWordWrap(font, Component.literal(Component.translatable("fieldguide.description.locked").getString()), textX, textY, textAreaWidth, Constants.TEXT_MUTED_COLOR);
        }

        // Drops
        List<List<ItemStack>> dropLines = new ArrayList<>();
        int dropItemSize = 20;
        int dropSpacing = 1;
        int dropStartY = 0;

        if (unlocked) {
            List<ItemStack> drops = ClientFieldGuideManager.getInstance().getDrops(entry);

            if (!drops.isEmpty()) {
                int maxLineWidth = (dropItemSize + dropSpacing) * 6;
                List<ItemStack> currentLine = new ArrayList<>();
                int currentWidth = 0;

                for (ItemStack stack : drops) {
                    int needed = (currentLine.isEmpty() ? 0 : dropSpacing) + dropItemSize;
                    if (currentWidth + needed > maxLineWidth) {
                        dropLines.add(currentLine);
                        currentLine = new ArrayList<>();
                        currentWidth = 0;
                    }
                    currentWidth += (currentLine.isEmpty() ? 0 : dropSpacing) + dropItemSize;
                    currentLine.add(stack);
                }
                dropLines.add(currentLine);

                int dropsHeight = 2 * dropItemSize + (dropLines.size() - 1) * dropSpacing;
                int bottomAnchor = this.rightPageBounds.bottom() - 6;
                dropStartY = bottomAnchor - dropsHeight;

                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);

                // Backgrounds and Items
                int currentY = dropStartY;
                for (List<ItemStack> line : dropLines) {
                    int startX = this.rightPageBounds.left();

                    for (ItemStack stack : line) {
                        guiGraphics.renderItem(stack, startX + 2, currentY + 2);
                        guiGraphics.renderItemDecorations(this.font, stack, startX + 2, currentY + 2);

                        if (Bounds.isMouseOver(mouseX, mouseY, startX, currentY, dropItemSize, dropItemSize)) {
                            tooltipStack = stack;
                            hoveredItem = stack;
                        }

                        startX += dropItemSize + dropSpacing;
                    }
                    currentY += dropItemSize + dropSpacing;
                }
            }
        }

        // Render Tooltip
        if (tooltipStack != null) {
            guiGraphics.renderTooltip(this.font, tooltipStack, mouseX, mouseY);
        } else if (tooltipText != null) {
            guiGraphics.renderTooltip(this.font, tooltipText, mouseX, mouseY);
        }
    }
}