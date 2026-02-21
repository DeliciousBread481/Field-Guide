package com.evandev.fieldguide.client.gui.toasts;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.gui.util.EntryRenderHelper;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.data.CompositeFieldGuideEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

public class FieldGuideToast implements Toast {
    private final Object entry;
    private Entity cachedEntity = null;
    private boolean entityInitialized = false;

    public FieldGuideToast(Object entry) {
        this.entry = entry;
    }

    @Override
    public @NotNull Visibility render(GuiGraphics guiGraphics, @NotNull ToastComponent toastComponent, long timeSinceLastVisible) {
        guiGraphics.blit(Constants.TOAST_TEXTURE, 0, 0, 0, 0, this.width(), this.height(), 160, 32);

        Component name = ClientFieldGuideManager.getEntryName(entry);
        Component discovered = Component.translatable("fieldguide.toast.discovered");

        guiGraphics.drawString(toastComponent.getMinecraft().font, name, 30, 7, ModConfig.get().getTextTitleColorInt(), false);
        guiGraphics.drawString(toastComponent.getMinecraft().font, discovered, 30, 17, 0xAF8C5C, false);

        int iconX = 17;
        int iconY = 15;
        Object coreEntry = this.entry instanceof CompositeFieldGuideEntry composite ? composite.displayEntry() : this.entry;

        if (!entityInitialized && coreEntry instanceof EntityType<?> type) {
            cachedEntity = type.create(Minecraft.getInstance().level);
            entityInitialized = true;
        }

        if (this.entry instanceof CompositeFieldGuideEntry composite && composite.displayEntry() instanceof Block block) {
            if (composite.structureNbt() != null) {
                EntryRenderHelper.renderStructure(guiGraphics, composite, iconX, iconY, 16, false, false, 1.0F);
            } else {
                EntryRenderHelper.renderBlock(guiGraphics, block, iconX, iconY, 8.0F, false, false, 1.0F);
            }
        } else if (coreEntry instanceof EntityType<?>) {
            if (cachedEntity instanceof LivingEntity living) {
                EntryRenderHelper.renderEntityNormalized(guiGraphics, living, iconX, iconY, 16, 16, 16, false, 0, false, 1.0F);
            } else {
                guiGraphics.blit(Constants.TOAST_ICON, 9, 7, 0, 0, 16, 16, 16, 16);
            }
        } else if (coreEntry instanceof Block block) {
            EntryRenderHelper.renderBlock(guiGraphics, block, iconX, iconY, 8.0F, false, false, 1.0F);
        } else {
            guiGraphics.blit(Constants.TOAST_ICON, 9, 7, 0, 0, 16, 16, 16, 16);
        }

        return timeSinceLastVisible >= 5000L ? Visibility.HIDE : Visibility.SHOW;
    }
}