package com.evandev.fieldguide.network;

import com.evandev.fieldguide.entry.EntryResolutionHelper;
import com.evandev.fieldguide.item.ModItems;
import com.evandev.fieldguide.server.progress.FieldGuideProgressManager;
import com.evandev.fieldguide.server.progress.PlayerFieldGuideProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Optional;

public class CopyPagePacket {
    private final ResourceLocation entryId;

    public CopyPagePacket(ResourceLocation entryId) {
        this.entryId = entryId;
    }

    public CopyPagePacket(FriendlyByteBuf buf) {
        this.entryId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(entryId);
    }

    public void handleServer(ServerPlayer player) {
        if (player == null) return;
        PlayerFieldGuideProgress progress = FieldGuideProgressManager.getInstance().getProgress(player);
        if (progress == null) return;

        String idStr = entryId.toString();
        if (!progress.isUnlocked(idStr)) return;

        // Check for paper
        boolean hasPaper = false;
        if (player.isCreative()) {
            hasPaper = true;
        } else {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(Items.PAPER)) {
                    hasPaper = true;
                    break;
                }
            }
        }

        if (!hasPaper) return;

        // Gather data
        List<String> variants = progress.getUnlockedVariants(idStr);
        String customName = progress.getCustomName(idStr);
        String customDescription = progress.getCustomDescription(idStr);
        String photograph = progress.getEntryPhotograph(idStr);
        long discoveryTime = progress.getDiscoveryTime(idStr);
        long discoveryGameTime = progress.getDiscoveryGameTime(idStr);

        // Give item
        ItemStack page = new ItemStack(ModItems.PAGE.get());
        CompoundTag tag = page.getOrCreateTag();
        tag.putString("EntryId", idStr);
        tag.putString("Author", player.getScoreboardName());

        Component nameComponent = null;
        Optional<Object> entry = EntryResolutionHelper.resolveSingleEntry(entryId, null, null);
        if (entry.isPresent()) {
            Object obj = entry.get();
            if (obj instanceof EntityType<?> type) nameComponent = type.getDescription();
            else if (obj instanceof Block block) nameComponent = block.getName();
            else if (obj instanceof Item item) nameComponent = item.getDescription();
        }

        if (nameComponent == null) {
            if (entryId.getNamespace().equals("fieldguide") && entryId.getPath().startsWith("cobblemon/")) {
                String species = entryId.getPath().substring("cobblemon/".length());
                int underscore = species.lastIndexOf('_');
                if (underscore != -1) species = species.substring(0, underscore);
                nameComponent = Component.translatable("cobblemon.species." + species + ".name");
            } else {
                nameComponent = Component.translatable(getTranslationKey(entryId));
            }
        }

        tag.putString("EntryName", Component.Serializer.toJson(nameComponent));

        if (!variants.isEmpty()) {
            ListTag variantsTag = new ListTag();
            for (String v : variants) {
                variantsTag.add(StringTag.valueOf(v));
            }
            tag.put("Variants", variantsTag);
        }

        if (customName != null) tag.putString("CustomName", customName);
        if (customDescription != null) tag.putString("CustomDescription", customDescription);
        if (photograph != null) tag.putString("Photograph", photograph);
        tag.putLong("DiscoveryTime", discoveryTime);
        tag.putLong("DiscoveryGameTime", discoveryGameTime);

        if (!player.getInventory().add(page)) {
            player.drop(page, false);
        }

        // Remove paper
        if (!player.isCreative()) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(Items.PAPER)) {
                    stack.shrink(1);
                    break;
                }
            }
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private String getTranslationKey(ResourceLocation id) {
        return "fieldguide.entry." + id.getNamespace() + "." + id.getPath().replace("/", ".");
    }
}
