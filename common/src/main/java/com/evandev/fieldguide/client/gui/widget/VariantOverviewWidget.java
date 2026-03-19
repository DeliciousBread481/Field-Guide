package com.evandev.fieldguide.client.gui.widget;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.api.VariantDef;
import com.evandev.fieldguide.api.VariantProvider;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.gui.util.EntryRenderHelper;
import com.evandev.fieldguide.compat.cobblemon.FieldGuideCobblemonCompat;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.util.FieldGuideVariantManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class VariantOverviewWidget extends AbstractWidget {

    private final Object entry;
    private final LivingEntity renderedEntity;
    private final List<VariantDef> variants;
    private final Consumer<Integer> onVariantSelected;
    private final Runnable onToggle;
    private final int maxPages;
    private final ImageButton closeButton;
    private final PageTurnButton leftButton;
    private final PageTurnButton rightButton;
    private int currentPage = 0;
    private Component currentTitleText;

    public VariantOverviewWidget(int x, int y, int width, int height, Object entry, LivingEntity renderedEntity, List<VariantDef> variants, Consumer<Integer> onVariantSelected, Runnable onToggle) {
        super(x, y, width, height, Component.empty());
        this.entry = entry;
        this.renderedEntity = renderedEntity;
        this.variants = variants;
        this.onVariantSelected = onVariantSelected;
        this.onToggle = onToggle;
        this.maxPages = (int) Math.ceil(variants.size() / 9.0);
        this.currentTitleText = Component.translatable("gui.fieldguide.variants");
        this.visible = false;

        this.closeButton = new ImageButton(x + width - 21, y + 11, 10, 10, 0, 0, 10, Constants.CLOSE_ICON, 10, 20, (btn) -> this.toggleVisibility());

        this.leftButton = new PageTurnButton(x + (width / 2) - 24, y + height - 20, 16, 16, 0, 16, 16, Constants.WIDGETS_TEXTURE, (btn) -> {
            if (currentPage > 0) currentPage--;
        });
        this.rightButton = new PageTurnButton(x + (width / 2) + 8, y + height - 20, 16, 16, 16, 16, 16, Constants.WIDGETS_TEXTURE, (btn) -> {
            if (currentPage < maxPages - 1) currentPage++;
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (!this.visible) return false;

        if (scrollDelta > 0 && this.currentPage > 0) {
            this.currentPage--;
            return true;
        } else if (scrollDelta < 0 && this.currentPage < maxPages - 1) {
            this.currentPage++;
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    @Override
    public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);

        graphics.blit(Constants.VARIANT_WIDGET_TEXTURE, this.getX(), this.getY(), 0, 0, this.width, this.height, this.width, this.height);

        this.currentTitleText = Component.translatable("gui.fieldguide.variants");

        int startIdx = currentPage * 9;
        int endIdx = Math.min(startIdx + 9, variants.size());

        int spacingX = 36;
        int spacingY = 40;
        int startX = this.getX() + (this.width / 2) - spacingX;
        int startY = this.getY() + 40;

        VariantProvider<Mob> provider = null;
        VariantDef originalVariant = null;

        if (renderedEntity instanceof Mob mob) {
            provider = FieldGuideVariantManager.getProvider(mob);
            if (provider != null) {
                originalVariant = provider.getCurrent(mob);
            }
        }

        for (int i = startIdx; i < endIdx; i++) {
            int gridIndex = i - startIdx;
            int row = gridIndex / 3;
            int col = gridIndex % 3;

            int itemX = startX + (col * spacingX);
            int itemY = startY + (row * spacingY);

            VariantDef variant = variants.get(i);
            boolean isUnlocked = ClientFieldGuideManager.isVariantUnlocked(entry, variant.id());

            LivingEntity renderEntity = renderedEntity;

            if (provider != null && renderedEntity instanceof Mob mob) {
                if (Services.PLATFORM.isModLoaded("cobblemon") && FieldGuideCobblemonCompat.isPokemon(renderedEntity)) {
                    ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
                    if (id != null && Minecraft.getInstance().level != null) {
                        renderEntity = FieldGuideCobblemonCompat.getDummyVariant(id, variant.id(), Minecraft.getInstance().level);
                    }
                } else {
                    provider.apply(mob, variant);
                }
            }

            EntryRenderHelper.renderEntityNormalized(graphics, renderEntity, itemX, itemY + 8, 30, 30, isUnlocked, false, 1.0f);

            if (mouseX >= itemX - 16 && mouseX <= itemX + 16 && mouseY >= itemY - 16 && mouseY <= itemY + 16) {
                if (isUnlocked) {
                    String name = variant.id();
                    if (name.contains(":")) name = name.substring(name.indexOf(':') + 1);

                    name = Arrays.stream(name.split("_"))
                            .map(s -> s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase())
                            .collect(Collectors.joining(" "));
                    this.currentTitleText = Component.literal(name);
                } else {
                    this.currentTitleText = Component.literal("???");
                }
            }
        }

        if (provider != null && renderedEntity instanceof Mob mob && originalVariant != null) {
            provider.apply(mob, originalVariant);
        }

        int titleWidth = Minecraft.getInstance().font.width(this.currentTitleText);
        graphics.drawString(Minecraft.getInstance().font, this.currentTitleText, this.getX() + (this.width / 2) - (titleWidth / 2), this.getY() + 13, ModConfig.get().getTextTitleColorInt(), false);

        this.closeButton.render(graphics, mouseX, mouseY, partialTicks);
        if (this.currentPage > 0) this.leftButton.render(graphics, mouseX, mouseY, partialTicks);
        if (this.currentPage < maxPages - 1) this.rightButton.render(graphics, mouseX, mouseY, partialTicks);

        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible) return false;

        if (this.closeButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (this.currentPage > 0 && this.leftButton.mouseClicked(mouseX, mouseY, button)) return true;
        if (this.currentPage < maxPages - 1 && this.rightButton.mouseClicked(mouseX, mouseY, button)) return true;

        int startIdx = currentPage * 9;
        int endIdx = Math.min(startIdx + 9, variants.size());
        int spacingX = 36;
        int spacingY = 40;
        int startX = this.getX() + (this.width / 2) - spacingX;
        int startY = this.getY() + 40;

        for (int i = startIdx; i < endIdx; i++) {
            int gridIndex = i - startIdx;
            int row = gridIndex / 3;
            int col = gridIndex % 3;

            int itemX = startX + (col * spacingX);
            int itemY = startY + (row * spacingY);

            if (mouseX >= itemX - 16 && mouseX <= itemX + 16 && mouseY >= itemY - 16 && mouseY <= itemY + 16) {
                VariantDef variant = variants.get(i);
                if (ClientFieldGuideManager.isVariantUnlocked(entry, variant.id())) {
                    this.onVariantSelected.accept(i);
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    this.visible = false;
                }
                return true;
            }
        }

        if (mouseX >= this.getX() && mouseX <= this.getX() + this.width && mouseY >= this.getY() && mouseY <= this.getY() + this.height) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
    }
}
