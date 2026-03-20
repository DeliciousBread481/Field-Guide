package com.evandev.fieldguide.client.gui.screens;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.FieldGuideLimits;
import com.evandev.fieldguide.api.*;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.client.data.EntryVisual;
import com.evandev.fieldguide.client.gui.util.Bounds;
import com.evandev.fieldguide.client.gui.util.EntryRenderHelper;
import com.evandev.fieldguide.client.gui.widget.*;
import com.evandev.fieldguide.client.manager.ClientCategoryManager;
import com.evandev.fieldguide.client.progress.ProgressManager;
import com.evandev.fieldguide.compat.cobblemon.FieldGuideCobblemonCompat;
import com.evandev.fieldguide.compat.exposure.ClientExposureCompat;
import com.evandev.fieldguide.config.ClientConfig;
import com.evandev.fieldguide.config.ServerConfig;
import com.evandev.fieldguide.network.RipOutPacket;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.util.FieldGuideVariantManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FieldGuideEntryScreen extends BookScreen {
    private final FieldGuideCategoryScreen parent;
    private final Object entry;
    private final List<ResourceLocation> spawnBiomes = new ArrayList<>();
    private final List<AbstractWidget> exposureWidgets = new ArrayList<>();
    private String initialVariant = null;
    private Entity renderedEntity;
    private long lastClickTime = 0;
    private int currentVariantIndex = 0;
    private List<VariantDef> entityVariants = new ArrayList<>();
    private PageTurnButton prevVariantButton;
    private PageTurnButton nextVariantButton;
    private VariantOverviewWidget variantOverviewWidget;
    private ImageButton overviewToggleButton;

    public FieldGuideEntryScreen(FieldGuideCategoryScreen parent, Object entry) {
        super(getTitleForEntry(entry));
        this.parent = parent;
        this.entry = entry;
    }

    private static Component getTitleForEntry(Object entry) {
        if (ClientFieldGuideManager.isUnlocked(entry) || ServerConfig.get().showUndiscoveredNames) {
            return ClientFieldGuideManager.getEntryName(entry);
        }
        return Component.translatable("fieldguide.undiscovered");
    }

    private static @NotNull String getTimeKey(long gameTime) {
        long timeOfDay = gameTime % 24000L;

        String timeKey = "fieldguide.time.day";

        if (timeOfDay >= 4500 && timeOfDay < 7500) {
            timeKey = "fieldguide.time.noon";
        } else if (timeOfDay >= 16500 && timeOfDay < 19500) {
            timeKey = "fieldguide.time.midnight";
        } else if (timeOfDay >= 13000 && timeOfDay < 23000) {
            timeKey = "fieldguide.time.night";
        }
        return timeKey;
    }

    private boolean isCobblemon(Object entry) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        return id != null && id.getNamespace().equals("fieldguide") && id.getPath().startsWith("cobblemon/");
    }

    public void setInitialVariant(String variantId) {
        this.initialVariant = variantId;
    }

    public FieldGuideCategoryScreen getParentScreen() {
        return parent;
    }

    public Bounds getLeftPageBounds() {
        return leftPageBounds;
    }

    public void addExposureWidget(AbstractWidget widget) {
        this.addRenderableWidget(widget);
        this.exposureWidgets.add(widget);
    }

    private void refreshExposureWidgets() {
        if (Services.PLATFORM.isModLoaded("exposure")) {
            for (AbstractWidget widget : exposureWidgets) {
                this.removeWidget(widget);
            }
            exposureWidgets.clear();

            String variantId = (!entityVariants.isEmpty() && currentVariantIndex < entityVariants.size()) ? entityVariants.get(currentVariantIndex).id() : null;
            ClientExposureCompat.setupExposureWidgets(this, entry, variantId);
        }
        updateWidgetVisibility();
    }

    private void updateWidgetVisibility() {
        boolean overviewVisible = this.variantOverviewWidget != null && this.variantOverviewWidget.isVisible();

        for (AbstractWidget widget : exposureWidgets) {
            widget.visible = !overviewVisible;
        }

        if (this.overviewToggleButton != null) {
            if (overviewVisible) {
                this.overviewToggleButton.visible = false;
            } else {
                String variantId = (!entityVariants.isEmpty() && currentVariantIndex < entityVariants.size()) ? entityVariants.get(currentVariantIndex).id() : null;
                boolean hasPhoto = !ProgressManager.getInstance().getPhotograph(entry, variantId).isEmpty();
                this.overviewToggleButton.visible = !hasPhoto;
            }
        }

        if (this.prevVariantButton != null) {
            this.prevVariantButton.visible = !overviewVisible && currentVariantIndex > 0;
        }
        if (this.nextVariantButton != null) {
            this.nextVariantButton.visible = !overviewVisible && currentVariantIndex < entityVariants.size() - 1;
        }
    }

    @Override
    protected void init() {
        Category category = ClientFieldGuideManager.getInstance().getCategoryForEntry(entry);
        if (category != null) this.setSelectedCategory(category);
        else if (parent != null) this.setSelectedCategory(parent.getSelectedCategory());

        super.init();
        spawnBiomes.clear();
        boolean unlocked = ClientFieldGuideManager.isUnlocked(entry);
        if (ClientFieldGuideManager.isNew(entry)) {
            ClientFieldGuideManager.markAsSeen(entry);
        }
        setupTextWidgets(unlocked);
        setupEntityPreview();
        loadSpawnBiomes();
        setupBiomeWidget(unlocked);
        setupDropWidget(unlocked);
        setupNavigationButtons();
        refreshExposureWidgets();

        if (unlocked && ServerConfig.get().enableTearingOutPages) {
            this.addRenderableWidget(new PageTurnButton(this.bounds.right() - 13, this.bounds.top() + 54, 24, 24, 24, 144, 24, Constants.WIDGETS_TEXTURE, (btn) -> {
                ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
                if (id != null) {
                    Services.NETWORK.sendToServer(new RipOutPacket(id));
                    this.onClose();
                }
            })).setTooltip(Tooltip.create(Component.translatable("gui.fieldguide.rip_out.tooltip")));
        }

        if (this.entityVariants.size() > 1 && this.renderedEntity instanceof LivingEntity living) {
            this.overviewToggleButton = new ImageButton(this.leftPageBounds.left() + 10, this.leftPageBounds.top() + 10, 16, 16, 0, 0, 16, Constants.OVERVIEW_ICON, 16, 32, (btn) -> {
                if (this.variantOverviewWidget != null) {
                    this.variantOverviewWidget.toggleVisibility();
                }
            });
            this.overviewToggleButton.setTooltip(Tooltip.create(Component.translatable("gui.fieldguide.variant_selector.tooltip")));
            this.addRenderableWidget(this.overviewToggleButton);

            int widgetWidth = 142;
            int widgetHeight = 166;
            int widgetX = this.leftPageBounds.left() + (this.leftPageBounds.width() / 2) - (widgetWidth / 2);
            int widgetY = this.leftPageBounds.top() + (this.leftPageBounds.height() / 2) - (widgetHeight / 2);

            this.variantOverviewWidget = new VariantOverviewWidget(widgetX, widgetY, widgetWidth, widgetHeight, this.entry, living, this.entityVariants, this::setVariantIndex, this::updateWidgetVisibility);
            this.addRenderableWidget(this.variantOverviewWidget);

            updateWidgetVisibility();
        }
    }

    private void setupTextWidgets(boolean unlocked) {
        int textX = this.rightPageBounds.left() + 6;
        int titleY = this.leftPageBounds.top() + 8;
        int textY = this.rightPageBounds.top() + 38;
        int textAreaWidth = this.rightPageBounds.width() - 12;
        int textAreaHeight = this.rightPageBounds.height() - 74;

        if (unlocked) {
            String initialName = ClientFieldGuideManager.getEntryName(entry).getString();
            if (!ServerConfig.get().disableEditingNames) {
                this.addRenderableWidget(new BookTextFieldWidget(this.font, textX, titleY, textAreaWidth, font.lineHeight, initialName, ClientConfig.get().getTextTitleColorInt(), textAreaWidth, FieldGuideLimits.MAX_ENTRY_NAME_LENGTH,
                        newName -> ClientFieldGuideManager.setCustomName(entry, newName)));
            }

            String initialDesc = ClientFieldGuideManager.getEntryDescription(entry);
            if (!ServerConfig.get().disableEditingDescriptions) {
                this.addRenderableWidget(new BookTextAreaWidget(this.font, textX, textY, textAreaWidth, textAreaHeight, 10, ClientConfig.get().getTextColorInt(), true, FieldGuideLimits.MAX_ENTRY_DESCRIPTION_LENGTH, initialDesc,
                        newDesc -> ClientFieldGuideManager.setCustomDescription(entry, newDesc)));
            }
        }
    }

    private void setupEntityPreview() {
        if (this.minecraft == null || this.minecraft.level == null) return;

        Object renderEntry = entry instanceof CompositeFieldGuideEntry composite ? composite.displayEntry() : entry;

        if (isCobblemon(entry)) {
            ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
            this.renderedEntity = FieldGuideCobblemonCompat.getDummyPokemon(id, this.minecraft.level);
        } else if (renderEntry instanceof EntityType<?> type) {
            try {
                this.renderedEntity = type.create(this.minecraft.level);
            } catch (Exception ignored) {
            }
        }

        if (this.renderedEntity != null) {
            this.entityVariants = FieldGuideVariantManager.getVariants(this.renderedEntity);
            if (this.entityVariants.size() > 1) {
                int centerX = leftPageBounds.x_center();
                int centerY = leftPageBounds.y_center() - 15;

                this.prevVariantButton = new PageTurnButton(centerX - 70, centerY - 8, 16, 16, 0, 16, 16, Constants.WIDGETS_TEXTURE, b -> cycleVariant(-1));
                this.nextVariantButton = new PageTurnButton(centerX + 54, centerY - 8, 16, 16, 16, 16, 16, Constants.WIDGETS_TEXTURE, b -> cycleVariant(1));

                this.addRenderableWidget(prevVariantButton);
                this.addRenderableWidget(nextVariantButton);

                VariantProvider<Mob> provider = FieldGuideVariantManager.getProvider(this.renderedEntity);
                if (provider != null) {
                    VariantDef current = provider.getCurrent((Mob) this.renderedEntity);
                    for (int i = 0; i < this.entityVariants.size(); i++) {
                        if (this.entityVariants.get(i).id().equals(this.initialVariant)) {
                            this.currentVariantIndex = i;
                            provider.apply((Mob) this.renderedEntity, this.entityVariants.get(i));
                            break;
                        } else if (this.initialVariant == null && this.entityVariants.get(i).id().equals(current.id())) {
                            this.currentVariantIndex = i;
                            this.initialVariant = this.entityVariants.get(i).id();
                            break;
                        }
                    }
                }
            }
        }
    }

    private void cycleVariant(int dir) {
        if (entityVariants.isEmpty() || renderedEntity == null || !(renderedEntity instanceof Mob)) return;
        currentVariantIndex = (currentVariantIndex + dir + entityVariants.size()) % entityVariants.size();
        this.initialVariant = entityVariants.get(currentVariantIndex).id();

        VariantProvider<Mob> provider = FieldGuideVariantManager.getProvider(renderedEntity);
        if (provider != null) {
            provider.apply((Mob) renderedEntity, entityVariants.get(currentVariantIndex));

            if (isCobblemon(entry) && this.minecraft != null) {
                ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
                this.renderedEntity = FieldGuideCobblemonCompat.getDummyPokemon(id, this.minecraft.level);
            }
        }
        refreshExposureWidgets();
        lastClickTime = System.currentTimeMillis();
        this.updateWidgetVisibility();
    }

    private void setVariantIndex(int index) {
        if (entityVariants.isEmpty() || renderedEntity == null || !(renderedEntity instanceof Mob)) return;
        if (index >= 0 && index < entityVariants.size()) {
            currentVariantIndex = index;
            this.initialVariant = entityVariants.get(currentVariantIndex).id();
            VariantProvider<Mob> provider = FieldGuideVariantManager.getProvider(renderedEntity);
            if (provider != null) {
                provider.apply((Mob) renderedEntity, entityVariants.get(currentVariantIndex));

                if (isCobblemon(entry) && this.minecraft != null) {
                    ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
                    this.renderedEntity = FieldGuideCobblemonCompat.getDummyPokemon(id, this.minecraft.level);
                }
            }
            refreshExposureWidgets();
            lastClickTime = System.currentTimeMillis();
        }
    }

    private void loadSpawnBiomes() {
        Object coreEntry = entry instanceof CompositeFieldGuideEntry composite ? composite.displayEntry() : entry;
        if (!(coreEntry instanceof EntityType<?>) && !(coreEntry instanceof Block)) return;

        EntryVisual visual = ClientFieldGuideManager.getInstance().getEntryVisual(entry);

        if (visual != null && visual.spawnBiomes != null) {
            spawnBiomes.addAll(visual.spawnBiomes);
        }

        ResourceLocation entryId = ClientFieldGuideManager.getEntryId(entry);
        if (entryId != null) {
            ClientCategoryManager categoryManager = ClientCategoryManager.getInstance();
            for (String removal : categoryManager.getBiomeRemovals()) {
                String[] parts = removal.split("\\|");
                if (parts.length == 2 && categoryManager.isBiomeMatch(entry, new ResourceLocation(parts[1]))) {
                    spawnBiomes.remove(new ResourceLocation(parts[1]));
                }
            }

            for (String addition : categoryManager.getBiomeAdditions()) {
                String[] parts = addition.split("\\|");
                if (parts.length == 2 && categoryManager.isBiomeMatch(entry, new ResourceLocation(parts[1]))) {
                    ResourceLocation biomeId = new ResourceLocation(parts[1]);

                    if (Services.PLATFORM.isModLoaded("immersiveoverlays")) {
                        ResourceLocation texture = new ResourceLocation(biomeId.getNamespace(), "textures/immersiveoverlays/" + biomeId.getPath() + ".png");
                        if (this.minecraft != null && this.minecraft.getResourceManager().getResource(texture).isEmpty()) {
                            continue;
                        }
                    }

                    if (!spawnBiomes.contains(biomeId)) {
                        spawnBiomes.add(biomeId);
                    }
                }
            }
        }
    }

    private void setupBiomeWidget(boolean unlocked) {
        if (ServerConfig.get().disableBiomeDisplay || !Services.PLATFORM.isModLoaded("immersiveoverlays")) return;

        if (unlocked && !spawnBiomes.isEmpty()) {
            int itemSize = 20;
            this.addRenderableWidget(new PaginatedGridWidget<>(this.rightPageBounds.left() + 2, this.rightPageBounds.bottom() - 33, this.rightPageBounds.width() - 4, itemSize, 5, itemSize, 0, spawnBiomes, (graphics, item, x, y, mouseX, mouseY) -> {
                ResourceLocation texture = new ResourceLocation(item.getNamespace(), "textures/immersiveoverlays/" + item.getPath() + ".png");

                if (Minecraft.getInstance().getResourceManager().getResource(texture).isPresent()) {
                    boolean mouseOver = Bounds.isMouseOver(mouseX, mouseY, x, y, itemSize, itemSize);
                    int backgroundOffset = mouseOver ? itemSize : 0;
                    graphics.blit(Constants.WIDGETS_TEXTURE, x, y, 20, 64 + backgroundOffset, itemSize, itemSize);
                    int offset = (itemSize - 16) / 2;
                    graphics.blit(texture, x + offset, y + offset, 0, 0, 16, 16, 16, 16);
                    if (Bounds.isMouseOver(mouseX, mouseY, x + offset, y + offset, 16, 16)) {
                        graphics.renderTooltip(this.font, Component.translatable("biome." + item.getNamespace() + "." + item.getPath()), mouseX, mouseY);
                    }
                }
            }, item -> {
                if (this.minecraft != null) this.minecraft.setScreen(new FieldGuideCategoryScreen("=!" + item, this));
            }));
        }
    }

    private void setupDropWidget(boolean unlocked) {
        if (ServerConfig.get().disableLootDisplay) return;

        List<ItemStack> drops = unlocked ? ClientFieldGuideManager.getInstance().getDrops(entry) : List.of();

        if (unlocked && drops.isEmpty() && isCobblemon(entry)) {
            drops = FieldGuideCobblemonCompat.getCobblemonDrops(entry);
        }

        if (!drops.isEmpty()) {
            int dropItemSize = 20;
            this.addRenderableWidget(new PaginatedGridWidget<>(this.leftPageBounds.left() + 2, this.leftPageBounds.bottom() - 33, this.leftPageBounds.width() - 4, dropItemSize, 5, dropItemSize, 0, drops, (graphics, stack, x, y, mouseX, mouseY) -> {
                RenderSystem.enableDepthTest();
                boolean mouseOver = Bounds.isMouseOver(mouseX, mouseY, x, y, dropItemSize, dropItemSize) && (this.variantOverviewWidget == null || !this.variantOverviewWidget.isMouseOver(mouseX, mouseY));
                int backgroundOffset = mouseOver ? dropItemSize : 0;
                graphics.blit(Constants.WIDGETS_TEXTURE, x, y, 0, 64 + backgroundOffset, dropItemSize, dropItemSize);
                int offset = (dropItemSize - 16) / 2;
                graphics.renderItem(stack, x + offset, y + offset);
                graphics.renderItemDecorations(this.font, stack, x + offset, y + offset, "");
                if (mouseOver) {
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
                    this.minecraft.setScreen(new FieldGuideCategoryScreen("=^" + stack.getHoverName().getString().toLowerCase(Locale.ROOT), this));
            }));
        }
    }

    private void setupNavigationButtons() {
        this.addRenderableWidget(new PageTurnButton(this.bounds.right() - 13, this.bounds.top() + 26, 24, 24, 24, 144, 24, Constants.WIDGETS_TEXTURE, b -> {
            if (this.minecraft != null) this.minecraft.setScreen(parent);
        }));

        List<Object> entries = parent.getCurrentEntries();

        if (!entries.isEmpty()) {
            int index = entries.indexOf(this.entry);

            PageTurnButton prevEntryButton = new PageTurnButton(this.bounds.left() + 15, this.leftPageBounds.bottom() - 15, 16, 16, 32, 16, 16, Constants.WIDGETS_TEXTURE, b -> {
                if (index > 0 && this.minecraft != null)
                    this.minecraft.setScreen(new FieldGuideEntryScreen(parent, entries.get(index - 1)));
            });
            PageTurnButton nextEntryButton = new PageTurnButton(this.bounds.right() - 30, this.rightPageBounds.bottom() - 15, 16, 16, 48, 16, 16, Constants.WIDGETS_TEXTURE, b -> {
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
                FieldGuideCategoryScreen searchScreen = new FieldGuideCategoryScreen(q, this);
                searchScreen.setInitialSearchFocus(true);
                this.minecraft.setScreen(searchScreen);
            }
        }));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.variantOverviewWidget != null && this.variantOverviewWidget.isVisible()) {
            if (this.variantOverviewWidget.mouseClicked(mouseX, mouseY, button)) return true;
        }

        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        Object clickEntry = entry instanceof CompositeFieldGuideEntry composite ? composite.displayEntry() : entry;

        if ((button == 0 || button == 1) && (renderedEntity != null || clickEntry instanceof Block || clickEntry instanceof Item)) {
            int xPos = leftPageBounds.left() + leftPageBounds.width() / 2;
            int yPos = leftPageBounds.y_center() - 18;
            if (mouseX >= xPos - 50 && mouseX <= xPos + 50 && mouseY >= yPos - 50 && mouseY <= yPos + 50) {
                if (ClientFieldGuideManager.isUnlocked(entry)) {
                    if (button == 0) {
                        EntryVisual visual = ClientFieldGuideManager.getInstance().getEntryVisual(entry);

                        if (visual != null && visual.customSound != null && this.minecraft != null) {
                            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(visual.customSound), 1.0F, 1.0F));
                        } else if ((isCobblemon(entry) || clickEntry instanceof EntityType<?>) && renderedEntity != null) {
                            FieldGuideClient.playMobCry(this.renderedEntity);
                        } else if (clickEntry instanceof Block block && this.minecraft != null) {
                            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(block.defaultBlockState().getSoundType().getBreakSound(), 1.0F, 1.0F));
                        } else if (clickEntry instanceof Item && this.minecraft != null) {
                            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(Constants.ITEM_PICKUP_SOUND), 1.0F, 1.0F));
                        }
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

        boolean unlocked = ClientFieldGuideManager.isUnlocked(entry);

        int titleY = this.leftPageBounds.top() + 8;
        int titleX = this.rightPageBounds.left() + 6;
        int textX = this.rightPageBounds.left() + 6;
        int textAreaWidth = this.rightPageBounds.width() - 10;

        if (!unlocked) {
            guiGraphics.drawString(this.font, getTitleForEntry(entry), titleX, titleY, ClientConfig.get().getTextMutedColorInt(), false);
            guiGraphics.drawWordWrap(font, Component.translatable("fieldguide.description.locked"), textX, titleY + 30, textAreaWidth, ClientConfig.get().getTextMutedColorInt());
        } else {
            long discoveryTime = ProgressManager.getInstance().getDiscoveryTime(entry);
            if (discoveryTime > 0) {
                Component dateComponent;

                if (ClientConfig.get().useRealWorldDate) {
                    String realDate = new SimpleDateFormat("MMM dd, yyyy")
                            .format(new Date(discoveryTime));

                    dateComponent = Component.literal(realDate);
                } else {
                    long gameTime = ProgressManager.getInstance().getDiscoveryGameTime(entry);
                    long days = gameTime / 24000L + 1;
                    String timeKey = getTimeKey(gameTime);

                    dateComponent = Component.translatable("fieldguide.date.in_game", days, Component.translatable(timeKey));
                }
                guiGraphics.drawString(this.font, dateComponent, titleX, titleY + this.font.lineHeight + 2, ClientConfig.get().getTextMutedColorInt(), false);
            }

            if (ServerConfig.get().disableEditingNames) {
                guiGraphics.drawString(this.font, ClientFieldGuideManager.getEntryName(entry), titleX, titleY, ClientConfig.get().getTextTitleColorInt(), false);
            }
            if (ServerConfig.get().disableEditingDescriptions) {
                int textY = this.rightPageBounds.top() + 38;
                guiGraphics.drawWordWrap(font, Component.literal(ClientFieldGuideManager.getEntryDescription(entry)), textX, textY, textAreaWidth, ClientConfig.get().getTextColorInt());
            }
        }

        float bounce = 1.0f;
        long elapsed = System.currentTimeMillis() - lastClickTime;
        if (elapsed < 150) bounce = 1.0f - 0.05f * (float) Math.sin((elapsed / 150.0f) * Math.PI);

        int xPos = leftPageBounds.x_center();
        int yPos = leftPageBounds.y_center() - 15;

        boolean hideEntity = Services.PLATFORM.isModLoaded("exposure") && ClientExposureCompat.hasPhotograph(entry);
        Object renderEntry = entry instanceof CompositeFieldGuideEntry composite ? composite.displayEntry() : entry;

        if (entry instanceof CompositeFieldGuideEntry composite && composite.displayEntry() instanceof Block block) {
            if (!hideEntity) {
                if (composite.structureNbt() != null || (composite.stackedBlocks() != null && !composite.stackedBlocks().isEmpty())) {
                    EntryRenderHelper.renderStructure(guiGraphics, composite, xPos, yPos, 112, unlocked, true, bounce);
                } else {
                    EntryRenderHelper.renderBlock(guiGraphics, block, xPos, yPos, 40.0F, unlocked, true, bounce);
                }
            }
        } else if (entry instanceof VirtualFieldGuideEntry virt && virt.virtualType().equals("cobblemon")) {
            if (!hideEntity) {
                EntryRenderHelper.renderCobblemon(guiGraphics, virt, xPos, yPos, 112, 112, unlocked, true, bounce);
            }
            Entity dummy = FieldGuideCobblemonCompat.getDummyPokemon(virt.id(), Minecraft.getInstance().level);
            if (unlocked && dummy instanceof LivingEntity living) {
                renderAttributes(guiGraphics, living);
                renderAlignment(guiGraphics, living, mouseX, mouseY);
            }
        } else if (entry instanceof VirtualFieldGuideEntry virt && virt.virtualType().equals("tutorial")) {
            if (!hideEntity) {
                EntryRenderHelper.renderTutorial(guiGraphics, virt, xPos, yPos, 112, 112, unlocked, true, bounce);
            }
        } else if (renderEntry instanceof EntityType && renderedEntity != null) {
            boolean variantUnlocked = unlocked;
            if (unlocked && !entityVariants.isEmpty() && !ServerConfig.get().unlockAllVariants) {
                variantUnlocked = ClientFieldGuideManager.isVariantUnlocked(entry, entityVariants.get(currentVariantIndex).id());
            }

            if (!hideEntity) {
                EntryRenderHelper.renderEntityNormalized(guiGraphics, renderedEntity, xPos, yPos, 112, 112, variantUnlocked, true, bounce);
            }

            if (unlocked && renderedEntity instanceof LivingEntity living) {
                renderAttributes(guiGraphics, living);
                renderAlignment(guiGraphics, living, mouseX, mouseY);
            }
        } else if (renderEntry instanceof Block block) {
            if (!hideEntity) {
                EntryRenderHelper.renderBlock(guiGraphics, block, xPos, yPos, 40.0F, unlocked, true, bounce);
            }
        } else if (renderEntry instanceof Item item) {
            if (!hideEntity) {
                EntryRenderHelper.renderItem(guiGraphics, item, xPos, yPos, 60.0F, unlocked, true, bounce);
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderAlignment(GuiGraphics guiGraphics, LivingEntity entity, int mouseX, int mouseY) {
        ResourceLocation icon;
        Component typeComponent;

        if (entity instanceof NeutralMob) {
            icon = Constants.NEUTRAL_ICON;
            typeComponent = Component.translatable("fieldguide.alignment.neutral");
        } else if (entry instanceof EntityType<?> type && type.getCategory() == MobCategory.MONSTER) {
            icon = Constants.HOSTILE_ICON;
            typeComponent = Component.translatable("fieldguide.alignment.hostile");
        } else {
            icon = Constants.PASSIVE_ICON;
            typeComponent = Component.translatable("fieldguide.alignment.passive");
        }

        int titleY = this.leftPageBounds.top() + 8;
        int iconX = this.rightPageBounds.right() - 14;
        int iconY = titleY - 3;

        RenderSystem.enableBlend();
        guiGraphics.blit(icon, iconX, iconY, 0, 0, 12, 12, 12, 12);
        RenderSystem.disableBlend();

        if (Bounds.isMouseOver(mouseX, mouseY, iconX, iconY, 12, 12)) {
            guiGraphics.renderTooltip(this.font, typeComponent, mouseX, mouseY);
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

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 2);

        guiGraphics.blitNineSliced(Constants.WIDGETS_TEXTURE, xPos - 4, yPos - 3, totalWidth + 8, 16, 6, 16, 16, 48, 0);

        guiGraphics.blit(Constants.ATTRIBUTES_TEXTURE, xPos, yPos, 0, 0, iconSize, iconSize, 32, 32);
        guiGraphics.drawString(this.font, health, xPos + iconSize + iconSpacing, yPos + 1, ClientConfig.get().getTextColorInt(), false);

        if (showArmor) {
            xPos = xPos + healthWidth + gap;
            guiGraphics.blit(Constants.ATTRIBUTES_TEXTURE, xPos, yPos, 0, iconSize, iconSize, iconSize, 32, 32);
            guiGraphics.drawString(this.font, armor, xPos + iconSize + iconSpacing, yPos + 1, ClientConfig.get().getTextColorInt(), false);
        }
        guiGraphics.pose().popPose();
    }
}
