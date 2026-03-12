package com.evandev.fieldguide.network;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.server.ScanVerifier;
import com.evandev.fieldguide.server.progress.FieldGuideProgressManager;
import com.evandev.fieldguide.server.progress.PlayerFieldGuideProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class ScanUnlockPacket implements CustomPacketPayload {

    public static final Type<ScanUnlockPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "scan_unlock"));

    public static final StreamCodec<FriendlyByteBuf, ScanUnlockPacket> CODEC = StreamCodec.ofMember(
            ScanUnlockPacket::encode,
            ScanUnlockPacket::new
    );

    private final ResourceLocation entryId;
    private final ResourceLocation scannedTargetId;
    private final BlockPos targetBlockPos;
    private final int targetEntityId;

    public ScanUnlockPacket(
            ResourceLocation entryId,
            ResourceLocation scannedTargetId,
            BlockPos targetBlockPos,
            int targetEntityId
    ) {
        this.entryId = entryId;
        this.scannedTargetId = scannedTargetId;
        this.targetBlockPos = targetBlockPos;
        this.targetEntityId = targetEntityId;
    }

    public ScanUnlockPacket(FriendlyByteBuf buf) {
        this.entryId = buf.readResourceLocation();
        this.scannedTargetId = buf.readResourceLocation();
        this.targetBlockPos = buf.readNullable(b -> b.readBlockPos());
        this.targetEntityId = buf.readVarInt();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(entryId);
        buf.writeResourceLocation(scannedTargetId);
        buf.writeNullable(targetBlockPos, (b, pos) -> b.writeBlockPos(pos));
        buf.writeVarInt(targetEntityId);
    }

    public void handleServer(ServerPlayer player) {
        if (player == null) return;

        FieldGuideProgressManager manager = FieldGuideProgressManager.getInstance();
        PlayerFieldGuideProgress progress = manager.getProgress(player);
        if (progress == null) return;

        if (!manager.isValidEntry(entryId)) return;
        if (manager.isKillToUnlock(entryId)) return;
        if (progress.isUnlocked(entryId)) return;
        if (!ScanVerifier.verifyScan(player, entryId, scannedTargetId, targetBlockPos, targetEntityId)) return;

        progress.unlock(player, entryId);
    }
}