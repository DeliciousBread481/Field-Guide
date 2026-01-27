package com.evandev.fieldguide.mixin.client;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.gui.screens.FieldGuideScreen;
import com.evandev.fieldguide.config.ModConfig;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends EffectRenderingInventoryScreen<InventoryMenu> {

    public InventoryScreenMixin(InventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addFieldGuideButton(CallbackInfo ci) {
        if (!ModConfig.get().showInventoryButton) {
            return;
        }

        int buttonSize = 20;
        int xPos = this.leftPos + ModConfig.get().inventoryButtonXOffset;
        int yPos = this.topPos + ModConfig.get().inventoryButtonYOffset;

        this.addRenderableWidget(new ImageButton(
                xPos,
                yPos,
                buttonSize,
                buttonSize,
                0,
                0,
                20,
                Constants.BUTTON_TEXTURE,
                20,
                40,
                (button) -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new FieldGuideScreen());
                    }
                }
        ));
    }
}