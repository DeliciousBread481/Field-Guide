package com.evandev.fieldguide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.List;

public class SyncDropsPacket {
    private final ResourceLocation entryId;
    private final List<ItemStack> drops;

    public SyncDropsPacket(ResourceLocation entryId, List<ItemStack> drops) {
        this.entryId = entryId;
        this.drops = drops;
    }

    public SyncDropsPacket(FriendlyByteBuf buf) {
        this.entryId = buf.readResourceLocation();
        this.drops = buf.readCollection(java.util.ArrayList::new, FriendlyByteBuf::readItem);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(entryId);
        buf.writeCollection(drops, FriendlyByteBuf::writeItem);
    }

    public ResourceLocation getEntryId() {
        return entryId;
    }

    public List<ItemStack> getDrops() {
        return drops;
    }
}