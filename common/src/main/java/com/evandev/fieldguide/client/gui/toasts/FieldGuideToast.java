package com.evandev.fieldguide.client.gui.toasts;

import com.evandev.fieldguide.Constants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.NotNull;

public class FieldGuideToast implements Toast {
    private final EntityType<?> type;

    public FieldGuideToast(EntityType<?> type) {
        this.type = type;
    }

    @Override
    public @NotNull Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        guiGraphics.blit(Constants.TOAST_TEXTURE, 0, 0, 0, 0, this.width(), this.height(), 160, 32);

        guiGraphics.drawString(toastComponent.getMinecraft().font, Component.translatable("fieldguide.toast.unlocked"), 30, 7, -11534256, false);
        guiGraphics.drawString(toastComponent.getMinecraft().font, type.getDescription(), 30, 18, -16777216, false);

        return timeSinceLastVisible >= 5000L ? Visibility.HIDE : Visibility.SHOW;
    }
}