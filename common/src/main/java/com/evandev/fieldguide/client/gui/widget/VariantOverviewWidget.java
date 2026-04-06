package com.evandev.fieldguide.client.gui.widget;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.api.variant.VariantDef;
import com.evandev.fieldguide.api.variant.VariantProvider;
import com.evandev.fieldguide.client.ClientConstants;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.gui.util.Bounds;
import com.evandev.fieldguide.client.gui.util.EntryRenderHelper;
import com.evandev.fieldguide.client.progress.ProgressManager;
import com.evandev.fieldguide.variant.FieldGuideVariantManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class VariantOverviewWidget extends AbstractWidget {

    private final Object entry;
    private final LivingEntity renderedEntity;
    private final List<VariantDef> variants;
    private final Consumer<Integer> onVariantSelected;
    private final Runnable onToggle;
    private final int maxPages;
    private final PageTurnButton leftButton;
    private final PageTurnButton rightButton;
    private final float[] hoverScales = new float[9];
    private int currentPage = 0;
    private long lastRenderTime = 0;

    public VariantOverviewWidget(int x, int y, int width, int height, Object entry, LivingEntity renderedEntity, List<VariantDef> variants, Consumer<Integer> onVariantSelected, Runnable onToggle) {
        super(x, y, width, height, Component.empty());
        this.entry = entry;
        this.renderedEntity = renderedEntity;
        this.variants = variants;
        this.onVariantSelected = onVariantSelected;
        this.onToggle = onToggle;
        this.maxPages = (int) Math.ceil(variants.size() / 9.0);
        this.visible = false;

        Arrays.fill(hoverScales, 1.0f);

        this.leftButton = new PageTurnButton(x + (width / 2) - 24, y + height - 20, 16, 16, ClientConstants.PREV_SPRITES, (btn) -> {
            if (currentPage > 0) {
                currentPage--;
                Arrays.fill(hoverScales, 1.0f);
            }
        });
        this.rightButton = new PageTurnButton(x + (width / 2) + 8, y + height - 20, 16, 16, ClientConstants.NEXT_SPRITES, (btn) -> {
            if (currentPage < maxPages - 1) {
                currentPage++;
                Arrays.fill(hoverScales, 1.0f);
            }
        });
    }

    public void toggleVisibility() {
        this.visible = !this.visible;
        if (this.onToggle != null) this.onToggle.run();
    }

    public boolean isVisible() {
        return this.visible;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.visible && mouseX >= this.getX() && mouseX <= this.getX() + this.width && mouseY >= this.getY() && mouseY <= this.getY() + this.height;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!this.visible) return false;

        if (scrollY > 0 && this.currentPage > 0) {
            this.currentPage--;
            return true;
        } else if (scrollY < 0 && this.currentPage < maxPages - 1) {
            this.currentPage++;
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) return;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(0f, 0f);

        guiGraphics.blit(Constants.VARIANT_WIDGET_TEXTURE, this.getX(), this.getY(), 0, 0, this.width, this.height, this.width, this.height);

        Component tooltipText = null;

        int startIdx = currentPage * 9;
        int endIdx = Math.min(startIdx + 9, variants.size());

        VariantProvider<Mob> provider = null;
        VariantDef originalVariant = null;

        if (renderedEntity instanceof Mob mob) {
            provider = FieldGuideVariantManager.getProvider(mob);
            if (provider != null) {
                originalVariant = provider.getCurrent(mob);
            }
        }

        long currentTime = System.currentTimeMillis();
        float deltaTime = lastRenderTime > 0 ? (currentTime - lastRenderTime) / 1000.0f : 0.0f;
        lastRenderTime = currentTime;

        for (int i = startIdx; i < endIdx; i++) {
            int gridIndex = i - startIdx;
            Bounds bounds = getGridCellBoundsLocal(gridIndex);
            boolean hovered = bounds.contains(mouseX, mouseY);
            VariantDef variant = variants.get(i);
            boolean isUnlocked = ClientFieldGuideManager.isVariantUnlocked(entry, variant.id());

            LivingEntity renderEntity = renderedEntity;

            if (provider != null && renderedEntity instanceof Mob mob) {
                /*if (Services.PLATFORM.isModLoaded("cobblemon") && FieldGuideCobblemonCompat.isPokemon(renderedEntity)) {
                    Identifier id = ClientFieldGuideManager.getEntryId(entry);
                    if (id != null && Minecraft.getInstance().level != null) {
                        renderEntity = ClientFieldGuideCobblemonCompat.getDummyVariant(id, variant.id(), Minecraft.getInstance().level);
                    }*/
                provider.apply(mob, variant);
            }

            guiGraphics.pose().pushMatrix();

            int centerX = bounds.x_center();
            int centerY = bounds.y_center();
            guiGraphics.pose().translate((float) centerX, (float) centerY);
            float currentScale = hoverScales[gridIndex];
            guiGraphics.pose().scale(currentScale, currentScale);

            guiGraphics.pose().translate((float) -centerX, (float) -centerY);

            boolean renderedPhoto = false;
            /* if (isUnlocked && Services.PLATFORM.isModLoaded("exposure") && ClientConfig.get().exposureShowPhotographsInGrid) {
                ItemStack existingPhoto = ProgressManager.getInstance().getPhotograph(entry, variant.id());
                if (!existingPhoto.isEmpty()) {
                    ClientExposureCompat.renderPhotographInGrid(guiGraphics, centerX - (bounds.width() / 2), centerY - (bounds.height() / 2), bounds.width(), bounds.height(), existingPhoto);
                    renderedPhoto = true;
                } else if (ServerConfig.get().keepSilhouetteWhenUnlocked) {
                    ClientExposureCompat.renderMissingPhotoBackground(guiGraphics, centerX - (bounds.width() / 2), centerY - (bounds.height() / 2), bounds.width(), bounds.height());
                }
            }*/

            if (!renderedPhoto) {
                EntryRenderHelper.renderEntityNormalized(guiGraphics, renderEntity, centerX, centerY, bounds.width(), bounds.height(), isUnlocked, false, 1.0f, false);
            }

            if (provider != null && renderedEntity instanceof Mob mob && originalVariant != null) {
                //boolean isCobblemon = Services.PLATFORM.isModLoaded("cobblemon") && FieldGuideCobblemonCompat.isPokemon(renderedEntity);
                //if (!isCobblemon) {
                provider.apply(mob, originalVariant);
                //}
            }

            float targetScale = (hovered && isUnlocked) ? 1.05f : 1.0f;
            if (deltaTime > 0) {
                float speed = 10.0f;
                float factor = 1.0f - (float) Math.pow(0.01, deltaTime * speed);
                hoverScales[gridIndex] = hoverScales[gridIndex] + (targetScale - hoverScales[gridIndex]) * factor;
            } else {
                hoverScales[gridIndex] = targetScale;
            }

            guiGraphics.pose().popMatrix();

            if (hovered) {
                if (isUnlocked) {
                    String customVariantName = ProgressManager.getInstance().getCustomName(ClientFieldGuideManager.getEntryId(entry).toString() + "#" + variant.id());
                    tooltipText = customVariantName != null ? Component.literal(customVariantName) : FieldGuideVariantManager.getVariantDisplayName(variant);
                } else {
                    tooltipText = Component.literal("???");
                }
            }
        }

        if (provider != null && renderedEntity instanceof Mob mob && originalVariant != null) {
            //boolean isCobblemon = Services.PLATFORM.isModLoaded("cobblemon") && FieldGuideCobblemonCompat.isPokemon(renderedEntity);
            //if (!isCobblemon) {
            provider.apply(mob, originalVariant);
            //}
        }

        if (tooltipText != null) {
            guiGraphics.setTooltipForNextFrame(Minecraft.getInstance().font, tooltipText, mouseX, mouseY);
        }

        if (this.currentPage > 0) this.leftButton.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        if (this.currentPage < maxPages - 1)
            this.rightButton.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (!this.visible) return false;

        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (this.currentPage > 0 && this.leftButton.mouseClicked(event, doubleClick)) return true;
        if (this.currentPage < maxPages - 1 && this.rightButton.mouseClicked(event, doubleClick)) return true;

        int startIdx = currentPage * 9;
        int endIdx = Math.min(startIdx + 9, variants.size());

        for (int i = startIdx; i < endIdx; i++) {
            int gridIndex = i - startIdx;
            Bounds bounds = getGridCellBoundsLocal(gridIndex);

            if (bounds.contains((int) mouseX, (int) mouseY)) {
                VariantDef variant = variants.get(i);
                if (ClientFieldGuideManager.isVariantUnlocked(entry, variant.id())) {
                    this.visible = false;
                    this.onVariantSelected.accept(i);
                    if (this.onToggle != null) this.onToggle.run();
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
                }
                return true;
            }
        }

        if (mouseX >= this.getX() && mouseX <= this.getX() + this.width && mouseY >= this.getY() && mouseY <= this.getY() + this.height) {
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
    }

    private Bounds getGridCellBoundsLocal(int i) {
        int cell_size = 40;
        int gap = 1;
        int startX = this.getX() + 6;
        int startY = this.getY() + 6;
        int localIndex = i % 9;
        int col = localIndex % 3;
        int row = localIndex / 3;
        int x = startX + (col * (cell_size + gap));
        int y = startY + (row * (cell_size + gap));
        return new Bounds(x, y, cell_size, cell_size);
    }
}