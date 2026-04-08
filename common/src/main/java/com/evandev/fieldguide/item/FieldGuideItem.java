package com.evandev.fieldguide.item;

import com.evandev.fieldguide.config.ServerConfig;
import com.evandev.fieldguide.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.function.Consumer;

public class FieldGuideItem extends Item {

    public FieldGuideItem(Properties properties) {
        super(properties);
    }

    public @NonNull InteractionResult use(@NonNull Level level, @NonNull Player player, @NonNull InteractionHand hand) {

        if (!ServerConfig.get().enableFieldGuideItem) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            Services.getClient().openFieldGuide();
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull TooltipDisplay display, @NotNull Consumer<Component> tooltip, @NotNull TooltipFlag tooltipFlag) {
        if (ServerConfig.get().enableFieldGuideItem) {
            tooltip.accept(Component.translatable("item.fieldguide.field_guide.tooltip").withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, context, display, tooltip, tooltipFlag);
    }
}
