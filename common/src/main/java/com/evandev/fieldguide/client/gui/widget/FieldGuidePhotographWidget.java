package com.evandev.fieldguide.client.gui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.Tesselator;
import io.github.mortuusars.exposure.ExposureClient;
import io.github.mortuusars.exposure.item.PhotographItem;
import io.github.mortuusars.exposure.render.PhotographRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2i;

import java.util.function.Supplier;

public class FieldGuidePhotographWidget extends AbstractButton {
    private final Rect2i exposureArea;
    private final Supplier<ItemStack> photographGetter;
    private final Runnable onLeftClick;
    private final Runnable onRightClick;
    private final ResourceLocation backgroundTexture;

    public FieldGuidePhotographWidget(int x, int y, int width, int height, Rect2i exposureArea,
                                      ResourceLocation backgroundTexture,
                                      Supplier<ItemStack> photographGetter,
                                      Runnable onLeftClick, Runnable onRightClick,
                                      Component tooltipText) {
        super(x, y, width, height, Component.empty());
        this.exposureArea = exposureArea;
        this.backgroundTexture = backgroundTexture;
        this.photographGetter = photographGetter;
        this.onLeftClick = onLeftClick;
        this.onRightClick = onRightClick;

        this.setTooltip(Tooltip.create(tooltipText));
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.blit(backgroundTexture, this.getX(), this.getY(), 0, 0, this.width, this.height, this.width, this.height);

        ItemStack photograph = photographGetter.get();
        if (photograph.getItem() instanceof PhotographItem) {
            guiGraphics.pose().pushPose();
            float scale = exposureArea.getWidth() / (float) ExposureClient.getExposureRenderer().getSize();
            guiGraphics.pose().translate(exposureArea.getX(), exposureArea.getY(), 1);
            guiGraphics.pose().scale(scale, scale, scale);

            MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
            PhotographRenderer.render(photograph, false, false, guiGraphics.pose(),
                    bufferSource, LightTexture.FULL_BRIGHT, 255, 255, 255, 255);
            bufferSource.endBatch();
            guiGraphics.pose().popPose();
        }
    }

    @Override
    protected @NotNull ClientTooltipPositioner createTooltipPositioner() {
        return (screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight) -> {
            int x = mouseX + 12;
            int y = mouseY - 12;

            if (x + tooltipWidth > screenWidth) {
                x -= 28 + tooltipWidth;
            }
            if (y + tooltipHeight + 6 > screenHeight) {
                y = screenHeight - tooltipHeight - 6;
            }
            return new Vector2i(x, y);
        };
    }

    @Override
    public void onPress() {
        onLeftClick.run();
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        return this.active && this.visible
                && mouseX >= this.getX() && mouseY >= this.getY()
                && mouseX < this.getX() + this.width && mouseY < this.getY() + this.height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.clicked(mouseX, mouseY)) {
            if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                this.onPress();
                return true;
            } else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                this.onRightClick.run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0 && this.isHovered() && !photographGetter.get().isEmpty()) {
            this.onPress();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}