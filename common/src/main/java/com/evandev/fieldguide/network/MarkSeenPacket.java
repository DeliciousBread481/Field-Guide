package com.evandev.fieldguide.network;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.server.progress.FieldGuideProgressManager;
import com.evandev.fieldguide.server.progress.PlayerFieldGuideProgress;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

public class MarkSeenPacket implements CustomPacketPayload {
    public static final Type<MarkSeenPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "mark_seen"));
    public static final StreamCodec<FriendlyByteBuf, MarkSeenPacket> CODEC = StreamCodec.ofMember(MarkSeenPacket::encode, MarkSeenPacket::new);

    private final Identifier entryId;

    public MarkSeenPacket(Identifier entryId) {
        this.entryId = entryId;
    }

    public MarkSeenPacket(FriendlyByteBuf buf) {
        this.entryId = buf.readIdentifier();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeIdentifier(entryId);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handleServer(ServerPlayer player) {
        if (player == null) return;
        PlayerFieldGuideProgress progress = FieldGuideProgressManager.getInstance().getProgress(player);
        if (progress == null) return;

        progress.markSeen(entryId.toString());
    }
}