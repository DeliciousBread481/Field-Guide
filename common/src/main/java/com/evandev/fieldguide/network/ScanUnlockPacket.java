package com.evandev.fieldguide.network;

import com.evandev.fieldguide.server.ScanVerifier;
import com.evandev.fieldguide.server.progress.FieldGuideProgressManager;
import com.evandev.fieldguide.server.progress.FieldGuideTriggers;
import com.evandev.fieldguide.server.progress.PlayerFieldGuideProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class ScanUnlockPacket {
    private final ResourceLocation entryId;
    private final String variantId;
    private final ResourceLocation scannedTargetId;
    private final BlockPos targetBlockPos;
    private final int targetEntityId;

    public ScanUnlockPacket(
            ResourceLocation entryId,
            String variantId,
            ResourceLocation scannedTargetId,
            BlockPos targetBlockPos,
            int targetEntityId
    ) {
        this.entryId = entryId;
        this.variantId = variantId != null ? variantId : "";
        this.scannedTargetId = scannedTargetId;
        this.targetBlockPos = targetBlockPos;
        this.targetEntityId = targetEntityId;
    }

    public ScanUnlockPacket(FriendlyByteBuf buf) {
        this.entryId = buf.readResourceLocation();
        this.variantId = buf.readUtf();
        this.scannedTargetId = buf.readResourceLocation();
        this.targetBlockPos = buf.readNullable(FriendlyByteBuf::readBlockPos);
        this.targetEntityId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(entryId);
        buf.writeUtf(variantId);
        buf.writeResourceLocation(scannedTargetId);
        buf.writeNullable(targetBlockPos, FriendlyByteBuf::writeBlockPos);
        buf.writeVarInt(targetEntityId);
    }

    public void handleServer(ServerPlayer player) {
        if (player == null) return;

        FieldGuideProgressManager manager = FieldGuideProgressManager.getInstance();
        PlayerFieldGuideProgress progress = manager.getProgress(player);
        if (progress == null) return;

        if (!manager.isValidEntry(entryId)) return;
        if (manager.isKillToUnlock(entryId)) return;

        if (!ScanVerifier.verifyScan(player, entryId, scannedTargetId, targetBlockPos, targetEntityId)) return;

        if (targetEntityId != 0) {
            Entity entity = player.serverLevel().getEntity(targetEntityId);
            if (entity != null) {
                FieldGuideTriggers.SCAN_ENTITY.trigger(player, entity);
            }
        }

        progress.unlock(player, entryId, variantId);
    }
}
