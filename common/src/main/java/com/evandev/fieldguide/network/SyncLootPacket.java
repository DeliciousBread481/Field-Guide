package com.evandev.fieldguide.network;

import com.evandev.fieldguide.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record SyncLootPacket(Map<Identifier, List<ItemStack>> lootCache,
                             boolean clearCache) implements CustomPacketPayload {

    public static final Type<SyncLootPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sync_loot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncLootPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.<RegistryFriendlyByteBuf, Identifier, List<ItemStack>, Map<Identifier, List<ItemStack>>>map(
                    HashMap::new,
                    Identifier.STREAM_CODEC,
                    ItemStack.LIST_STREAM_CODEC
            ),
            SyncLootPacket::lootCache,
            ByteBufCodecs.BOOL,
            SyncLootPacket::clearCache,
            SyncLootPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}