package com.evandev.fieldguide.client.gui.screens;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.client.data.EntryVisual;
import com.evandev.fieldguide.client.gui.util.Bounds;
import com.evandev.fieldguide.client.gui.util.EntryRenderHelper;
import com.evandev.fieldguide.client.gui.widget.PageTurnButton;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.platform.Services;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
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
import net.minecraft.world.level.block.SoundType;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.*;

public class FieldGuideEntryScreen extends BookScreen {
    private final BookScreen parent;
    private final Object entry;
    private final List<ResourceLocation> spawnBiomes = new ArrayList<>();
    private final int biomesPerPage = 6;
    private Entity renderedEntity;
    private long lastClickTime = 0;
    private int currentBiomePage = 1;
    private ResourceLocation hoveredBiome;
    private ItemStack hoveredItem;
    private ImageButton prevBiomePageButton;
    private ImageButton nextBiomePageButton;

    public FieldGuideEntryScreen(BookScreen parent, Object entry) {
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
        spawnBiomes.clear();

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

        ResourceLocation entryId = ClientFieldGuideManager.getEntryId(entry);
        EntryVisual visual = entryId != null ? ClientFieldGuideManager.getInstance().getEntryVisual(entryId) : null;

        if (visual != null && visual.spawnBiomes != null) {
            spawnBiomes.addAll(visual.spawnBiomes);
        } else if (entry instanceof EntityType<?> entityType) {
            if (Services.PLATFORM.isModLoaded("immersiveoverlays")) {
                Registry<Biome> biomeRegistry = null;
                if (this.minecraft.level != null) {
                    biomeRegistry = this.minecraft.level.registryAccess().registryOrThrow(Registries.BIOME);
                }

                try {
                    if (biomeRegistry != null) {
                        for (var biomeEntry : biomeRegistry.entrySet()) {
                            ResourceLocation id = biomeEntry.getKey().location();
                            Biome biome = biomeEntry.getValue();

                            var spawns = biome.getMobSettings().getMobs(entityType.getCategory());

                            if (spawns.unwrap().stream().anyMatch(s -> s.type == entityType)) {
                                ResourceLocation texture = new ResourceLocation(id.getNamespace(), "textures/immersiveoverlays/" + id.getPath() + ".png");
                                if (this.minecraft.getResourceManager().getResource(texture).isPresent()) {
                                    spawnBiomes.add(id);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Constants.LOG.error("Failed to load spawn biomes for Field Guide", e);
                }
            }
        }

        // Add Navigation Buttons
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
    public void onTabClick(Category category) {
        Objects.requireNonNull(this.minecraft).setScreen(parent);
        parent.onTabClick(category);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == 0 && (renderedEntity != null || entry instanceof Block)) {
            int xPos = leftPageBounds.left() + leftPageBounds.width() / 2;
            int yPos = leftPageBounds.y_center();
            int halfSize = 50;

            if (mouseX >= xPos - halfSize && mouseX <= xPos + halfSize &&
                    mouseY >= yPos - halfSize && mouseY <= yPos + halfSize) {

                if (ClientFieldGuideManager.isUnlocked(entry)) {
                    if (entry instanceof EntityType<?> && renderedEntity != null) {
                        FieldGuideClient.playMobCry(this.renderedEntity);
                    } else if (entry instanceof Block block) {
                        if (this.minecraft != null) {
                            SoundType soundType = block.defaultBlockState().getSoundType();
                            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(soundType.getBreakSound(), 1.0F, 1.0F));
                        }
                    }
                    this.lastClickTime = System.currentTimeMillis();
                }
                return true;
            }
        }
        if (button == 0 && hoveredItem != null) {
            Minecraft.getInstance().setScreen(new FieldGuideScreen("=^" + hoveredItem.getHoverName().getString().toLowerCase(Locale.ROOT), this));
        }
        if (button == 0 && hoveredBiome != null) {
            Minecraft.getInstance().setScreen(new FieldGuideScreen("=!" + hoveredBiome, this));
        }
        return false;
    }

    private void renderAttributes(GuiGraphics guiGraphics, LivingEntity entity) {
        RenderSystem.setShaderTexture(0, Constants.ATTRIBUTES_TEXTURE);
        int iconSize = 9;
        int iconSpacing = 2;
        int gap = 8;

        int yPos = leftPageBounds.top() + 13;

        String health = String.valueOf((int) entity.getMaxHealth() / 2);
        String armor = String.valueOf(entity.getArmorValue());
        boolean showArmor = !armor.equals("0");

        int healthWidth = this.font.width(health) + iconSpacing + iconSize;
        int armorWidth = this.font.width(armor) + iconSpacing + iconSize;
        int totalWidth = healthWidth + (showArmor ? gap + armorWidth : 0);

        int xPos = this.leftPageBounds.x_center() - (totalWidth / 2);

        // Draw Health
        guiGraphics.blit(Constants.ATTRIBUTES_TEXTURE, xPos, yPos, 0, 0, iconSize, iconSize, 32, 32);
        guiGraphics.drawString(this.font, health, xPos + iconSize + iconSpacing, yPos + 1, ModConfig.get().getTextColorInt(), false);

        // Draw Frame
        guiGraphics.blit(Constants.HEALTH_FRAME_TEXTURE, xPos - 9, yPos - 4, 0, 0, 8, 16, 16, 16);
        guiGraphics.blit(Constants.HEALTH_FRAME_TEXTURE, xPos + totalWidth + 1, yPos - 4, 8, 0, 8, 16, 16, 16);

        if (showArmor) {
            // Draw Armor
            xPos = xPos + healthWidth + gap;
            guiGraphics.blit(Constants.ATTRIBUTES_TEXTURE, xPos, yPos, 0, iconSize, iconSize, iconSize, 32, 32);
            guiGraphics.drawString(this.font, armor, xPos + iconSize + iconSpacing, yPos + 1, ModConfig.get().getTextColorInt(), false);
        }
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
        guiGraphics.drawString(this.font, title, this.rightPageBounds.left() + 5, titleY, unlocked ? ModConfig.get().getTextTitleColorInt() : ModConfig.get().getTextMutedColorInt(), false);

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
                    hoveredBiome = biomeId;
                    tooltipText = Component.translatable("biome." + biomeId.getNamespace() + "." + biomeId.getPath());
                }
            }

            nextBiomePageButton.active = indexEnd < spawnBiomes.size();
            prevBiomePageButton.active = currentBiomePage > 1;

            if (spawnBiomes.size() > biomesPerPage) {
                nextBiomePageButton.visible = true;
                prevBiomePageButton.visible = true;

                // Progress Bar
                int pages = (spawnBiomes.size() + biomesPerPage - 1) / biomesPerPage;
                int sideMargin = 13;
                Bounds bounds = new Bounds(leftPageBounds.left() + sideMargin, biomeStartY + 18, leftPageBounds.width() - sideMargin * 2, 1);
                float progressStart = (float) (currentBiomePage - 1) / pages;
                float progressEnd = (float) currentBiomePage / pages;

                int barStart = (int) (bounds.left() + bounds.width() * progressStart);
                int barEnd = (int) (bounds.left() + bounds.width() * progressEnd);


                // Render Background
                guiGraphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 0xFFF9EED0);

                // Render Bar
                guiGraphics.fill(barStart, bounds.top(), barEnd, bounds.bottom(), 0xFFE0D2AE);
            }

        } else {
            nextBiomePageButton.visible = false;
            prevBiomePageButton.visible = false;
        }

        // Entity
        float bounce = 1.0f;
        long elapsed = System.currentTimeMillis() - lastClickTime;
        float duration = 150;
        if (elapsed < duration) {
            float t = elapsed / duration;
            bounce = 1.0f - 0.05f * (float) Math.sin(t * Math.PI);
        }

        int xPos = leftPageBounds.x_center();
        int yPos = leftPageBounds.y_center();

        if (entry instanceof EntityType && renderedEntity instanceof LivingEntity living) {
            if (unlocked) {
                EntryRenderHelper.renderEntityNormalized(guiGraphics, living, xPos, yPos, 100, 100, 80, false, 0, true, bounce);
                renderAttributes(guiGraphics, living);
            } else {
                EntryRenderHelper.renderEntityNormalized(guiGraphics, living, xPos, yPos, 100, 100, 80, true, ModConfig.get().getDetailsSilhouetteColorInt(), true, bounce);
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
            long discoveryTime = ClientFieldGuideManager.getInstance().getDiscoveryTime(entry);
            if (discoveryTime > 0) {
                // TODO: Add config for date format?
                String dateStr = new SimpleDateFormat("MMM dd, yyyy").format(new Date(discoveryTime));
                Component dateComp = Component.literal(dateStr);
                guiGraphics.drawString(this.font, dateComp, this.rightPageBounds.right() - this.font.width(dateComp), this.rightPageBounds.bottom() - 58, ModConfig.get().getTextMutedColorInt(), false);
            }

            guiGraphics.drawWordWrap(font, Component.literal(ClientFieldGuideManager.getEntryDescription(entry)), textX, textY, textAreaWidth, ModConfig.get().getTextColorInt());
        } else {
            guiGraphics.drawWordWrap(font, Component.literal(Component.translatable("fieldguide.description.locked").getString()), textX, textY, textAreaWidth, ModConfig.get().getTextMutedColorInt());
        }

        // Drops
        List<List<ItemStack>> dropLines = new ArrayList<>();
        int dropItemSize = 20;
        int dropSpacing = 1;
        int dropStartY;

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
            List<Component> tooltip = new ArrayList<>(Screen.getTooltipFromItem(Objects.requireNonNull(this.minecraft), tooltipStack));

            if (tooltipStack.hasTag() && Objects.requireNonNull(tooltipStack.getTag()).contains("FieldGuideDropChance")) {
                float chance = tooltipStack.getTag().getFloat("FieldGuideDropChance");
                tooltip.add(Component.literal(String.format(Locale.ROOT, "%.1f%%", chance)).withStyle(ChatFormatting.GRAY));
            }

            guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        } else if (tooltipText != null) {
            guiGraphics.renderTooltip(this.font, tooltipText, mouseX, mouseY);
        }
    }
}