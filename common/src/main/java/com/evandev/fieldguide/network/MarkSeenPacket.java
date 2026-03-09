package com.evandev.fieldguide.network;

import com.evandev.fieldguide.server.progress.FieldGuideProgressManager;
import com.evandev.fieldguide.server.progress.PlayerFieldGuideProgress;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class MarkSeenPacket {
    private final ResourceLocation entryId;

    public MarkSeenPacket(ResourceLocation entryId) {
        this.entryId = entryId;
    }

    public MarkSeenPacket(FriendlyByteBuf buf) {
        this.entryId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(entryId);
    }

    public void handleServer(ServerPlayer player) {
        if (player == null) return;
        PlayerFieldGuideProgress progress = FieldGuideProgressManager.getInstance().getProgress(player);
        if (progress == null) return;

        progress.markSeen(entryId.toString());
    }
}
