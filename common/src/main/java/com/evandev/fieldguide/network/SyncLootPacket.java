package com.evandev.fieldguide.network;

import com.evandev.fieldguide.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record SyncLootPacket(Map<ResourceLocation, List<ItemStack>> lootCache,
                             boolean clearCache) implements CustomPacketPayload {
    public static final Type<SyncLootPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_loot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncLootPacket> CODEC = StreamCodec.ofMember(SyncLootPacket::encode, SyncLootPacket::new);

    public SyncLootPacket {
        lootCache = lootCache != null ? lootCache : new HashMap<>();
    }

    public SyncLootPacket(RegistryFriendlyByteBuf buf) {
        this(decodeCache(buf), buf.readBoolean());
    }

    private static Map<ResourceLocation, List<ItemStack>> decodeCache(RegistryFriendlyByteBuf buf) {
        int mapSize = buf.readVarInt();
        Map<ResourceLocation, List<ItemStack>> map = new HashMap<>(mapSize);

        for (int i = 0; i < mapSize; i++) {
            ResourceLocation key = buf.readResourceLocation();

            int listSize = buf.readVarInt();
            List<ItemStack> list = new ArrayList<>(listSize);
            for (int j = 0; j < listSize; j++) {
                list.add(ItemStack.STREAM_CODEC.decode(buf));
            }

            map.put(key, list);
        }

        return map;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.lootCache.size());

        for (Map.Entry<ResourceLocation, List<ItemStack>> entry : this.lootCache.entrySet()) {
            buf.writeResourceLocation(entry.getKey());

            List<ItemStack> list = entry.getValue();
            buf.writeVarInt(list.size());
            for (ItemStack item : list) {
                ItemStack.STREAM_CODEC.encode(buf, item);
            }
        }

        buf.writeBoolean(this.clearCache);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}