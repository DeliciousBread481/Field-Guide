package com.evandev.fieldguide.client.gui.toasts;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.config.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

public class FieldGuideToast implements Toast {
    private final Object entry;

    public FieldGuideToast(Object entry) {
        this.entry = entry;
    }

    @Override
    public @NotNull Visibility render(GuiGraphics guiGraphics, @NotNull ToastComponent toastComponent, long timeSinceLastVisible) {
        guiGraphics.blit(Constants.TOAST_TEXTURE, 0, 0, 0, 0, this.width(), this.height(), 160, 32);

        Component name = Component.translatable("fieldguide.unknown");
        if (entry instanceof EntityType<?> type) {
            name = type.getDescription();
        } else if (entry instanceof Block block) {
            name = block.getName();
        }

        Component discovered = Component.translatable("fieldguide.toast.discovered");
        //Component hint = Component.translatable("fieldguide.toast.view_hint", FieldGuideClient.OPEN_GUIDE_KEY.getTranslatedKeyMessage());

        guiGraphics.drawString(toastComponent.getMinecraft().font, name, 30, 7, ModConfig.get().getTextTitleColorInt(), false);
        guiGraphics.drawString(toastComponent.getMinecraft().font, discovered, 30, 17, 0xAF8C5C, false);

        guiGraphics.blit(Constants.TOAST_ICON, 9, 7, 0, 0, 16, 16, 16, 16);

        return timeSinceLastVisible >= 5000L ? Visibility.HIDE : Visibility.SHOW;
    }
}