package com.evandev.fieldguide.compat.exposure;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.gui.screens.FieldGuideEntryScreen;
import com.evandev.fieldguide.client.gui.screens.FieldGuidePhotographScreen;
import com.evandev.fieldguide.client.gui.widget.FieldGuidePhotographWidget;
import com.evandev.fieldguide.client.progress.ProgressManager;
import com.mojang.blaze3d.vertex.Tesselator;
import io.github.mortuusars.exposure.ExposureClient;
import io.github.mortuusars.exposure.gui.screen.ItemListScreen;
import io.github.mortuusars.exposure.item.PhotographItem;
import io.github.mortuusars.exposure.render.PhotographRenderProperties;
import io.github.mortuusars.exposure.render.PhotographRenderer;
import io.github.mortuusars.exposure.util.ItemAndStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ExposureCompat {
    private static final ResourceLocation ADD_PHOTO_ICON = new ResourceLocation("fieldguide", "textures/gui/exposure/add_photo.png");

    public static void setupExposureWidgets(FieldGuideEntryScreen screen, Object entry) {
        if (!ClientFieldGuideManager.isUnlocked(entry)) return;

        int leftX = screen.getLeftPageBounds().left();
        int leftY = screen.getLeftPageBounds().top();
        int leftWidth = screen.getLeftPageBounds().width();
        int leftHeight = screen.getLeftPageBounds().height();

        int iconSize = 16;
        int iconX = leftX + leftWidth - iconSize - 12;
        int iconY = leftY + 12;

        ItemStack existingPhoto = ProgressManager.getInstance().getPhotograph(entry);

        if (existingPhoto.isEmpty()) {
            ImageButton addPhotoButton = new ImageButton(iconX, iconY, iconSize, iconSize, 0, 0, iconSize, ADD_PHOTO_ICON, 16, 32, btn -> {
                openPhotographSelector(screen, entry);
            }, Component.translatable("gui.fieldguide.add_photograph"));

            addPhotoButton.setTooltip(Tooltip.create(Component.translatable("gui.fieldguide.add_photograph")));
            screen.addWidgetPublic(addPhotoButton);
        } else {
            int photoWidth = 108;
            int photoHeight = 108;
            int photoX = leftX + (leftWidth / 2) - (photoWidth / 2);
            int photoY = leftY + (leftHeight / 2) - (photoHeight / 2) - 15;

            Rect2i exposureArea = new Rect2i(photoX + 6, photoY + 6, photoWidth - 12, photoHeight - 12);

            Component tooltipText = Component.empty()
                    .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("Left Click").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("] or [").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("Scroll Up").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("] to View\n").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("Right Click").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("] to Remove").withStyle(ChatFormatting.DARK_GRAY));



            FieldGuidePhotographWidget photoWidget = new FieldGuidePhotographWidget(
                    photoX, photoY, photoWidth, photoHeight,
                    exposureArea,
                    () -> ProgressManager.getInstance().getPhotograph(entry),

                    () -> Minecraft.getInstance().setScreen(new FieldGuidePhotographScreen(screen, List.of(new ItemAndStack<>(existingPhoto)))),

                    () -> {
                        ProgressManager.getInstance().setPhotograph(entry, ItemStack.EMPTY);
                        Minecraft.getInstance().setScreen(new FieldGuideEntryScreen(screen.getParentScreen(), entry));
                    },
                    tooltipText
            );

            screen.addWidgetPublic(photoWidget);
        }
    }

    public static void renderPhotographInGrid(GuiGraphics guiGraphics, int x, int y, int width, int height, ItemStack photograph) {
        if (photograph.getItem() instanceof PhotographItem) {
            PhotographRenderProperties renderProperties = PhotographRenderProperties.get(photograph);
            Rect2i exposureArea = new Rect2i(x + 4, y + 4, width - 8, height - 8);
            int textureSize = 64;
            int sliceSize = width / 2;

            // Paper
            guiGraphics.blit(renderProperties.getPaperTexture(), x, y, 0, 0, 0, sliceSize, sliceSize, textureSize, textureSize);
            guiGraphics.blit(renderProperties.getPaperTexture(), x + sliceSize, y, 0, textureSize - sliceSize, 0, sliceSize, sliceSize, textureSize, textureSize);
            guiGraphics.blit(renderProperties.getPaperTexture(), x, y + sliceSize, 0, 0, textureSize - sliceSize, sliceSize, sliceSize, textureSize, textureSize);
            guiGraphics.blit(renderProperties.getPaperTexture(), x + sliceSize, y + sliceSize, 0, textureSize - sliceSize, textureSize - sliceSize, sliceSize, sliceSize, textureSize, textureSize);

            // Exposure
            guiGraphics.pose().pushPose();
            float scale = exposureArea.getWidth() / (float) ExposureClient.getExposureRenderer().getSize();
            guiGraphics.pose().translate(exposureArea.getX(), exposureArea.getY(), 1);
            guiGraphics.pose().scale(scale, scale, scale);

            MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
            PhotographRenderer.render(photograph, false, false, guiGraphics.pose(),
                    bufferSource, LightTexture.FULL_BRIGHT, 255, 255, 255, 255);
            bufferSource.endBatch();
            guiGraphics.pose().popPose();

            // Paper overlay
            if (renderProperties.hasPaperOverlayTexture()) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0, 0, 2);
                guiGraphics.blit(renderProperties.getPaperOverlayTexture(), x, y, 0, 0, 0, sliceSize, sliceSize, textureSize, textureSize);
                guiGraphics.blit(renderProperties.getPaperOverlayTexture(), x + sliceSize, y, 0, textureSize - sliceSize, 0, sliceSize, sliceSize, textureSize, textureSize);
                guiGraphics.blit(renderProperties.getPaperOverlayTexture(), x, y + sliceSize, 0, 0, textureSize - sliceSize, sliceSize, sliceSize, textureSize, textureSize);
                guiGraphics.blit(renderProperties.getPaperOverlayTexture(), x + sliceSize, y + sliceSize, 0, textureSize - sliceSize, textureSize - sliceSize, sliceSize, sliceSize, textureSize, textureSize);
                guiGraphics.pose().popPose();
            }
        }
    }

    private static void openPhotographSelector(FieldGuideEntryScreen parent, Object entry) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        List<ItemStack> photographs = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof PhotographItem) {
                photographs.add(stack);
            }
        }

        Minecraft.getInstance().setScreen(new PhotographSelectionScreen(parent, photographs, stack -> {
            ProgressManager.getInstance().setPhotograph(entry, stack.copy());
            Minecraft.getInstance().setScreen(new FieldGuideEntryScreen(parent.getParentScreen(), entry));
        }));
    }

    public static boolean hasPhotograph(Object entry) {
        return !ProgressManager.getInstance().getPhotograph(entry).isEmpty();
    }

    private static class PhotographSelectionScreen extends ItemListScreen {
        private final Consumer<ItemStack> onSelect;

        public PhotographSelectionScreen(Screen parent, List<ItemStack> items, Consumer<ItemStack> onSelect) {
            super(parent, Component.translatable("gui.fieldguide.select_photograph"), items);
            this.onSelect = onSelect;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && hoveredSlot != null && hoveredSlot.hasItem()) {
                onSelect.accept(hoveredSlot.getItem());
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }
}