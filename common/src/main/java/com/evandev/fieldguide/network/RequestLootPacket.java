package com.evandev.fieldguide.network;

import com.evandev.fieldguide.server.ServerFieldGuideManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class RequestLootPacket {
    private final ResourceLocation entryId;

    public RequestLootPacket(ResourceLocation entryId) {
        this.entryId = entryId;
    }

    public RequestLootPacket(FriendlyByteBuf buf) {
        this.entryId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.entryId);
    }

    public void handleServer(ServerPlayer player) {
        ServerFieldGuideManager.getInstance().syncLootToPlayer(player, entryId);
    }
}
