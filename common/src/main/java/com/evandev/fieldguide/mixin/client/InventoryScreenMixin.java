package com.evandev.fieldguide.mixin.client;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.client.gui.screens.BookScreen;
import com.evandev.fieldguide.client.gui.screens.FieldGuideCategoryScreen;
import com.evandev.fieldguide.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends EffectRenderingInventoryScreen<InventoryMenu> {

    @Unique
    private ImageButton fieldguide$guideButton;

    public InventoryScreenMixin(InventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addFieldGuideButton(CallbackInfo ci) {
        if (!ClientConfig.get().showInventoryButton) {
            return;
        }

        int xPos = this.leftPos + ClientConfig.get().inventoryButtonXOffset;
        int yPos = this.topPos + ClientConfig.get().inventoryButtonYOffset;

        this.fieldguide$guideButton = new ImageButton(
                xPos,
                yPos,
                20,
                18,
                0,
                0,
                18,
                Constants.INVENTORY_BUTTON_TEXTURE,
                20,
                36,
                (button) -> {
                    if (this.minecraft != null) {
                        if (!FieldGuideClient.canOpenGuide()) return;
                        String defaultMode = ClientConfig.get().defaultScreen;
                        if ("last_opened_screen".equals(defaultMode) && BookScreen.lastOpenedScreen != null) {
                            this.minecraft.setScreen(BookScreen.lastOpenedScreen);
                        } else {
                            this.minecraft.setScreen(new FieldGuideCategoryScreen());
                        }
                    }
                }
        );

        this.addRenderableWidget(this.fieldguide$guideButton);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void updateButtonPosition(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.fieldguide$guideButton != null && ClientConfig.get().showInventoryButton) {
            this.fieldguide$guideButton.setX(this.leftPos + ClientConfig.get().inventoryButtonXOffset);
            this.fieldguide$guideButton.setY(this.topPos + ClientConfig.get().inventoryButtonYOffset);
        }
    }
}
