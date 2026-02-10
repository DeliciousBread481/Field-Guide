package com.evandev.fieldguide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SyncLootPacket {
    private final Map<ResourceLocation, List<ItemStack>> lootCache;

    public SyncLootPacket(Map<ResourceLocation, List<ItemStack>> lootCache) {
        this.lootCache = lootCache;
    }

    public SyncLootPacket(FriendlyByteBuf buf) {
        this.lootCache = buf.readMap(
                FriendlyByteBuf::readResourceLocation,
                b -> b.readList(FriendlyByteBuf::readItem)
        );
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeMap(
                lootCache,
                FriendlyByteBuf::writeResourceLocation,
                (b, list) -> b.writeCollection(list, FriendlyByteBuf::writeItem)
        );
    }

    public Map<ResourceLocation, List<ItemStack>> getLootCache() {
        return lootCache;
    }
}