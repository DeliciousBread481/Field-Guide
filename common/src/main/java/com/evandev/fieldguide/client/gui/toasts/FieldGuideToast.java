package com.evandev.fieldguide.client.gui.toasts;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.FieldGuideClient;
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

        Component title = Component.translatable("fieldguide.toast.added", type.getDescription());
        Component hint = Component.translatable("fieldguide.toast.view_hint", FieldGuideClient.OPEN_GUIDE_KEY.getTranslatedKeyMessage());

        guiGraphics.drawString(toastComponent.getMinecraft().font, title, 30, 7, -11534256, false);
        guiGraphics.drawString(toastComponent.getMinecraft().font, hint, 30, 18, -16777216, false);

        return timeSinceLastVisible >= 5000L ? Visibility.HIDE : Visibility.SHOW;
    }
}