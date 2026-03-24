package com.evandev.fieldguide.item;

import com.evandev.fieldguide.server.progress.FieldGuideProgressManager;
import com.evandev.fieldguide.server.progress.PlayerFieldGuideProgress;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PageItem extends Item {

    public PageItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        CompoundTag tag = stack.getTag();

        if (tag != null && tag.contains("EntryId")) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                ResourceLocation entryId = new ResourceLocation(tag.getString("EntryId"));
                PlayerFieldGuideProgress progress = FieldGuideProgressManager.getInstance().getProgress(serverPlayer);

                if (progress != null) {
                    // Unlock base entry
                    progress.unlock(serverPlayer, entryId, null, false);

                    // Unlock variants
                    if (tag.contains("Variants")) {
                        ListTag variants = tag.getList("Variants", Tag.TAG_STRING);
                        CompoundTag variantNames = tag.getCompound("CustomVariantNames");
                        for (int i = 0; i < variants.size(); i++) {
                            String variantId = variants.getString(i);
                            progress.unlock(serverPlayer, entryId, variantId, false);
                            if (variantNames.contains(variantId)) {
                                progress.setCustomName(entryId + "#" + variantId, variantNames.getString(variantId));
                            }
                        }
                    }

                    // Restore custom data
                    if (tag.contains("CustomName"))
                        progress.setCustomName(entryId.toString(), tag.getString("CustomName"));
                    if (tag.contains("CustomDescription"))
                        progress.setCustomDescription(entryId.toString(), tag.getString("CustomDescription"));
                    if (tag.contains("Photograph"))
                        progress.setEntryPhotograph(entryId.toString(), tag.getString("Photograph"));
                    if (tag.contains("DiscoveryTime"))
                        progress.setDiscoveryTime(entryId.toString(), tag.getLong("DiscoveryTime"));
                    if (tag.contains("DiscoveryGameTime"))
                        progress.setDiscoveryGameTime(entryId.toString(), tag.getLong("DiscoveryGameTime"));

                    progress.markEntryForResync(entryId.toString());

                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);

                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag isAdvanced) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("EntryName")) {
            tooltipComponents.add(Component.translatable("item.fieldguide.page.entry", Component.Serializer.fromJson(tag.getString("EntryName"))).withStyle(ChatFormatting.GOLD));
        }
        if (tag != null && tag.contains("Author")) {
            tooltipComponents.add(Component.translatable("item.fieldguide.page.author", tag.getString("Author")).withStyle(ChatFormatting.GRAY));
        }
        tooltipComponents.add(Component.translatable("item.fieldguide.page.tooltip").withStyle(ChatFormatting.BLUE));
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
    }
}
