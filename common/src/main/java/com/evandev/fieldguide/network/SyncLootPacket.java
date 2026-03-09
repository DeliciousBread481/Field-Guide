package com.evandev.fieldguide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyncLootPacket {
    private final Map<ResourceLocation, List<ItemStack>> lootCache;
    private final boolean clearCache;

    public SyncLootPacket(Map<ResourceLocation, List<ItemStack>> lootCache, boolean clearCache) {
        this.lootCache = lootCache;
        this.clearCache = clearCache;
    }

    public SyncLootPacket(FriendlyByteBuf buf) {
        this.lootCache = buf.readMap(
                HashMap::new,
                FriendlyByteBuf::readResourceLocation,
                b -> b.readList(FriendlyByteBuf::readItem)
        );
        this.clearCache = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeMap(
                this.lootCache,
                FriendlyByteBuf::writeResourceLocation,
                (b, list) -> b.writeCollection(list, FriendlyByteBuf::writeItem)
        );
        buf.writeBoolean(this.clearCache);
    }

    public Map<ResourceLocation, List<ItemStack>> getLootCache() {
        return lootCache;
    }

    public boolean isClearCache() {
        return clearCache;
    }
}