package com.evandev.fieldguide.client.gui.screens;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.client.data.EntryVisual;
import com.evandev.fieldguide.client.gui.util.Bounds;
import com.evandev.fieldguide.client.gui.util.EntryRenderHelper;
import com.evandev.fieldguide.client.gui.widget.*;
import com.evandev.fieldguide.client.progress.ProgressManager;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.platform.Services;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FieldGuideEntryScreen extends BookScreen {
    private final BookScreen parent;
    private final Object entry;
    private final List<ResourceLocation> spawnBiomes = new ArrayList<>();
    private Entity renderedEntity;
    private long lastClickTime = 0;

    public FieldGuideEntryScreen(BookScreen parent, Object entry) {
        super(getTitleForEntry(entry));
        this.parent = parent;
        this.entry = entry;
    }

    private static Component getTitleForEntry(Object entry) {
        if (ClientFieldGuideManager.isUnlocked(entry) || ModConfig.get().showUndiscoveredNames) {
            return ClientFieldGuideManager.getEntryName(entry);
        }
        return Component.translatable("fieldguide.undiscovered");
    }

    @Override
    protected void init() {
        Category category = ClientFieldGuideManager.getInstance().getCategoryForEntry(entry);
        if (category != null) this.setSelectedCategory(category);
        else if (parent != null) this.setSelectedCategory(parent.getSelectedCategory());

        super.init();
        spawnBiomes.clear();
        boolean unlocked = ClientFieldGuideManager.isUnlocked(entry);

        setupTextWidgets(unlocked);
        setupEntityPreview();
        loadSpawnBiomes();
        setupBiomeWidget(unlocked);
        setupDropWidget(unlocked);
        setupNavigationButtons();
    }

    private void setupTextWidgets(boolean unlocked) {
        int textX = this.rightPageBounds.left() + 6;
        int titleY = this.leftPageBounds.top() + 8;
        int textY = this.rightPageBounds.top() + 38;
        int textAreaWidth = this.rightPageBounds.width() - 12;
        int textAreaHeight = this.rightPageBounds.height() - 98;

        if (unlocked) {
            String initialName = ClientFieldGuideManager.getEntryName(entry).getString();
            if (!ModConfig.get().disableEditingNames) {
                this.addRenderableWidget(new BookTextFieldWidget(this.font, textX, titleY, Math.max(font.width(initialName), 50), font.lineHeight, initialName, ModConfig.get().getTextTitleColorInt(), 50,
                        newName -> ClientFieldGuideManager.setCustomName(entry, newName)));
            }

            String initialDesc = ClientFieldGuideManager.getEntryDescription(entry);
            if (!ModConfig.get().disableEditingDescriptions) {
                this.addRenderableWidget(new BookTextAreaWidget(this.font, textX, textY, textAreaWidth, textAreaHeight, 10, ModConfig.get().getTextColorInt(), true, initialDesc,
                        newDesc -> ClientFieldGuideManager.setCustomDescription(entry, newDesc)));
            }
        }
    }

    private void setupEntityPreview() {
        if (entry instanceof EntityType<?> type && this.minecraft != null && this.minecraft.level != null) {
            try {
                this.renderedEntity = type.create(this.minecraft.level);
            } catch (Exception ignored) {
            }
        }
    }

    private void loadSpawnBiomes() {
        ResourceLocation entryId = ClientFieldGuideManager.getEntryId(entry);
        EntryVisual visual = entryId != null ? ClientFieldGuideManager.getInstance().getEntryVisual(entryId) : null;

        if (visual != null && visual.spawnBiomes != null) {
            spawnBiomes.addAll(visual.spawnBiomes);
        } else if (entry instanceof EntityType<?> entityType && Services.PLATFORM.isModLoaded("immersiveoverlays") && this.minecraft != null && this.minecraft.level != null) {
            Registry<Biome> biomeRegistry = this.minecraft.level.registryAccess().registryOrThrow(Registries.BIOME);
            for (var biomeEntry : biomeRegistry.entrySet()) {
                var spawns = biomeEntry.getValue().getMobSettings().getMobs(entityType.getCategory());
                if (spawns.unwrap().stream().anyMatch(s -> s.type == entityType)) {
                    ResourceLocation id = biomeEntry.getKey().location();
                    ResourceLocation texture = new ResourceLocation(id.getNamespace(), "textures/immersiveoverlays/" + id.getPath() + ".png");
                    if (this.minecraft.getResourceManager().getResource(texture).isPresent()) spawnBiomes.add(id);
                }
            }
        }
    }

    private void setupBiomeWidget(boolean unlocked) {
        if (ModConfig.get().disableBiomeDisplay) return;

        if (unlocked && !spawnBiomes.isEmpty()) {
            int itemSize = 18;
            this.addRenderableWidget(new PaginatedGridWidget<>(this.rightPageBounds.left() + 2, this.rightPageBounds.bottom() - 33, this.rightPageBounds.width() - 4, itemSize, 5, itemSize, 1, spawnBiomes, (graphics, item, x, y, mouseX, mouseY) -> {
                ResourceLocation texture = new ResourceLocation(item.getNamespace(), "textures/immersiveoverlays/" + item.getPath() + ".png");
                graphics.blit(Constants.BIOME_BACKGROUND_TEXTURE, x, y, 0, 0, itemSize, itemSize, itemSize, itemSize);
                int offset = (itemSize - 16) / 2;
                graphics.blit(texture, x + offset, y + offset, 0, 0, 16, 16, 16, 16);
                if (Bounds.isMouseOver(mouseX, mouseY, x + offset, y + offset, 16, 16)) {
                    graphics.renderTooltip(this.font, Component.translatable("biome." + item.getNamespace() + "." + item.getPath()), mouseX, mouseY);
                }
            }, item -> {
                if (this.minecraft != null) this.minecraft.setScreen(new FieldGuideScreen("=!" + item, this));
            }));
        }
    }

    private void setupDropWidget(boolean unlocked) {
        if (ModConfig.get().disableLootDisplay) return;
        List<ItemStack> drops = unlocked ? ClientFieldGuideManager.getInstance().getDrops(entry) : List.of();
        if (!drops.isEmpty()) {
            int dropItemSize = 20;
            this.addRenderableWidget(new PaginatedGridWidget<>(this.leftPageBounds.left() + 2, this.leftPageBounds.bottom() - 33, this.leftPageBounds.width() - 4, dropItemSize, 5, dropItemSize, 0, drops, (graphics, stack, x, y, mouseX, mouseY) -> {
                RenderSystem.enableDepthTest();
                graphics.blit(Constants.ITEM_BACKGROUND_TEXTURE, x, y, 0, 0, dropItemSize, dropItemSize, dropItemSize, dropItemSize);
                int offset = (dropItemSize - 16) / 2;
                graphics.renderItem(stack, x + offset, y + offset);
                graphics.renderItemDecorations(this.font, stack, x + offset, y + offset, "");
                if (Bounds.isMouseOver(mouseX, mouseY, x, y, dropItemSize, dropItemSize)) {
                    Minecraft mc = Minecraft.getInstance();
                    List<Component> tooltip = new ArrayList<>(Screen.getTooltipFromItem(mc, stack));
                    CompoundTag tag = stack.getTag();
                    if (tag != null && tag.contains("FieldGuideDropChance")) {
                        tooltip.add(Component.literal(String.format(Locale.ROOT, "%.2f%%", tag.getFloat("FieldGuideDropChance"))).withStyle(ChatFormatting.GRAY));
                    }
                    graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
                }
            }, stack -> {
                if (this.minecraft != null)
                    this.minecraft.setScreen(new FieldGuideScreen("=^" + stack.getHoverName().getString().toLowerCase(Locale.ROOT), this));
            }));
        }
    }

    private void setupNavigationButtons() {
        this.addRenderableWidget(new PageTurnButton(this.bounds.right() - 15, this.bounds.top() + 26, 24, 24, 0, 0, 24, Constants.BACK_TEXTURE, 24, 48, b -> {
            if (this.minecraft != null) this.minecraft.setScreen(parent);
        }));

        Category currentCat = this.getSelectedCategory();
        if (currentCat != null) {
            List<Object> entries = ClientFieldGuideManager.getInstance().getEntriesForCategory(currentCat);
            int index = entries.indexOf(this.entry);

            PageTurnButton prevEntryButton = new PageTurnButton(this.bounds.left() + 15, this.leftPageBounds.bottom() - 15, 16, 16, 0, 0, 16, Constants.PREV_PAGE_TEXTURE, 16, 32, b -> {
                if (index > 0 && this.minecraft != null)
                    this.minecraft.setScreen(new FieldGuideEntryScreen(parent, entries.get(index - 1)));
            });
            PageTurnButton nextEntryButton = new PageTurnButton(this.bounds.right() - 30, this.rightPageBounds.bottom() - 15, 16, 16, 0, 0, 16, Constants.NEXT_PAGE_TEXTURE, 16, 32, b -> {
                if (index >= 0 && index < entries.size() - 1 && this.minecraft != null)
                    this.minecraft.setScreen(new FieldGuideEntryScreen(parent, entries.get(index + 1)));
            });

            prevEntryButton.visible = index > 0;
            nextEntryButton.visible = index >= 0 && index < entries.size() - 1;
            this.addRenderableWidget(prevEntryButton);
            this.addRenderableWidget(nextEntryButton);
        }

        this.addRenderableWidget(new FieldGuideSearchBox(this.font, this.width / 2 - 70, this.bounds.bottom() + 5, 140, 20, "", q -> {
            if (!q.isEmpty() && this.minecraft != null) {
                FieldGuideScreen searchScreen = new FieldGuideScreen(q, this);
                searchScreen.setInitialSearchFocus(true);
                this.minecraft.setScreen(searchScreen);
            }
        }));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == 0 && (renderedEntity != null || entry instanceof Block)) {
            int xPos = leftPageBounds.left() + leftPageBounds.width() / 2;
            int yPos = leftPageBounds.y_center();
            if (mouseX >= xPos - 50 && mouseX <= xPos + 50 && mouseY >= yPos - 50 && mouseY <= yPos + 50) {
                if (ClientFieldGuideManager.isUnlocked(entry)) {
                    ResourceLocation entryId = ClientFieldGuideManager.getEntryId(entry);
                    EntryVisual visual = entryId != null ? ClientFieldGuideManager.getInstance().getEntryVisual(entryId) : null;
                    if (visual != null && visual.customSound != null && this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(visual.customSound), 1.0F, 1.0F));
                    } else if (entry instanceof EntityType<?> && renderedEntity != null) {
                        FieldGuideClient.playMobCry(this.renderedEntity);
                    } else if (entry instanceof Block block && this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(block.defaultBlockState().getSoundType().getBreakSound(), 1.0F, 1.0F));
                    }
                    this.lastClickTime = System.currentTimeMillis();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.getFocused() instanceof AbstractWidget widget && widget.isFocused()) {
            if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
            if (FieldGuideClient.OPEN_GUIDE_KEY.matches(keyCode, scanCode)) return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onTabClick(Category category) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
            parent.onTabClick(category);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        RenderSystem.setShaderTexture(0, Constants.BOOK_TEXTURE);
        guiGraphics.blit(Constants.BOOK_TEXTURE, this.bounds.left(), this.bounds.top(), 0, 0, this.bounds.width(), this.bounds.height(), this.bounds.width(), this.bounds.height());
        guiGraphics.blit(Constants.DETAILS_PAGE_TEXTURE, this.bounds.left(), this.bounds.top(), 0, 0, this.bounds.width(), this.bounds.height(), this.bounds.width(), this.bounds.height());

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        boolean unlocked = ClientFieldGuideManager.isUnlocked(entry);
        int titleY = this.leftPageBounds.top() + 8;
        int titleX = this.rightPageBounds.left() + 6;
        int textX = this.rightPageBounds.left() + 6;
        int textAreaWidth = this.rightPageBounds.width() - 10;

        if (!unlocked) {
            guiGraphics.drawString(this.font, getTitleForEntry(entry), titleX, titleY, ModConfig.get().getTextMutedColorInt(), false);
            guiGraphics.drawWordWrap(font, Component.translatable("fieldguide.description.locked"), textX, titleY + 30, textAreaWidth, ModConfig.get().getTextMutedColorInt());
        } else {
            long discoveryTime = ProgressManager.getInstance().getDiscoveryTime(entry);
            if (discoveryTime > 0) {
                String dateStr = new SimpleDateFormat("MMM dd, yyyy").format(new Date(discoveryTime));
                guiGraphics.drawString(this.font, Component.literal(dateStr), titleX, titleY + this.font.lineHeight + 2, ModConfig.get().getTextMutedColorInt(), false);
            }

            if (ModConfig.get().disableEditingNames) {
                guiGraphics.drawString(this.font, ClientFieldGuideManager.getEntryName(entry), titleX, titleY, ModConfig.get().getTextTitleColorInt(), false);
            }
            if (ModConfig.get().disableEditingDescriptions) {
                int textY = this.rightPageBounds.top() + 38;
                guiGraphics.drawWordWrap(font, Component.literal(ClientFieldGuideManager.getEntryDescription(entry)), textX, textY, textAreaWidth, ModConfig.get().getTextColorInt());
            }
        }

        float bounce = 1.0f;
        long elapsed = System.currentTimeMillis() - lastClickTime;
        if (elapsed < 150) bounce = 1.0f - 0.05f * (float) Math.sin((elapsed / 150.0f) * Math.PI);

        int xPos = leftPageBounds.x_center();
        int yPos = leftPageBounds.y_center() - 18;

        if (entry instanceof EntityType && renderedEntity instanceof LivingEntity living) {
            EntryRenderHelper.renderEntityNormalized(guiGraphics, living, xPos, yPos, 100, 100, 80, !unlocked, ModConfig.get().getDetailsSilhouetteColorInt(), true, bounce);
            if (unlocked) renderAttributes(guiGraphics, living);
        } else if (entry instanceof Block block) {
            EntryRenderHelper.renderBlock(guiGraphics, block, xPos, yPos, 30.0F, !unlocked, true, bounce);
        }
    }

    private void renderAttributes(GuiGraphics guiGraphics, LivingEntity entity) {
        RenderSystem.setShaderTexture(0, Constants.ATTRIBUTES_TEXTURE);
        int iconSize = 9;
        int iconSpacing = 2;
        int gap = 8;

        int yPos = leftPageBounds.bottom() - 47 - iconSize;

        String health = String.valueOf((int) entity.getMaxHealth() / 2);
        String armor = String.valueOf(entity.getArmorValue());
        boolean showArmor = !armor.equals("0");

        int healthWidth = this.font.width(health) + iconSpacing + iconSize;
        int armorWidth = this.font.width(armor) + iconSpacing + iconSize;
        int totalWidth = healthWidth + (showArmor ? gap + armorWidth : 0);

        int xPos = this.leftPageBounds.x_center() - (totalWidth / 2);

        guiGraphics.blit(Constants.ATTRIBUTES_TEXTURE, xPos, yPos, 0, 0, iconSize, iconSize, 32, 32);
        guiGraphics.drawString(this.font, health, xPos + iconSize + iconSpacing, yPos + 1, ModConfig.get().getTextColorInt(), false);

        if (showArmor) {
            xPos = xPos + healthWidth + gap;
            guiGraphics.blit(Constants.ATTRIBUTES_TEXTURE, xPos, yPos, 0, iconSize, iconSize, iconSize, 32, 32);
            guiGraphics.drawString(this.font, armor, xPos + iconSize + iconSpacing, yPos + 1, ModConfig.get().getTextColorInt(), false);
        }
    }
}