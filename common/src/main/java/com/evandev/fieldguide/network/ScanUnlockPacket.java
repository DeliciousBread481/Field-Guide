package com.evandev.fieldguide.network;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.server.ScanVerifier;
import com.evandev.fieldguide.server.ServerFieldGuideManager;
import com.evandev.fieldguide.server.progress.FieldGuideProgressManager;
import com.evandev.fieldguide.server.progress.PlayerFieldGuideProgress;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ScanUnlockPacket {
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
        this.targetBlockPos = buf.readNullable(FriendlyByteBuf::readBlockPos);
        this.targetEntityId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(entryId);
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
        if (progress.isUnlocked(entryId)) return;
        if (!ScanVerifier.verifyScan(player, entryId, scannedTargetId, targetBlockPos, targetEntityId)) return;

        long gameTime = player.serverLevel().dayTime();
        progress.unlock(entryId.toString(), gameTime);

        ModConfig config = ModConfig.get();
        if (config.grantXpOnScan && config.xpAmountOnScan > 0) {
            player.giveExperiencePoints(config.xpAmountOnScan);
        }

        executeCommands(player, entryId);
    }

    private void executeCommands(ServerPlayer player, ResourceLocation entryId) {
        ModConfig config = ModConfig.get();
        List<String> commandsToRun = new ArrayList<>(config.globalScanCommands);

        String idStr = entryId.toString();
        if (config.entryScanCommands.containsKey(idStr)) {
            commandsToRun.addAll(config.entryScanCommands.get(idStr));
        }

        ResourceLocation categoryId = ServerFieldGuideManager.getInstance().getCategoryForEntryId(entryId);
        if (categoryId != null && config.categoryScanCommands.containsKey(categoryId.toString())) {
            commandsToRun.addAll(config.categoryScanCommands.get(categoryId.toString()));
        }

        if (!commandsToRun.isEmpty()) {
            CommandSourceStack sourceStack = createRewardSourceStack(player, entryId);
            for (String cmd : commandsToRun) {
                player.getServer().getCommands().performPrefixedCommand(sourceStack, cmd);
            }
        }
    }

    private static CommandSourceStack createRewardSourceStack(ServerPlayer player, ResourceLocation entryId) {
        String sourceName = Constants.MOD_ID + "/" + entryId;
        return new CommandSourceStack(
                new CommandSource() {
                    @Override
                    public void sendSystemMessage(@NotNull Component component) {
                        Constants.LOG.info("[SCAN] {}", component.getString());
                    }

                    @Override
                    public boolean acceptsSuccess() {
                        return true;
                    }

                    @Override
                    public boolean acceptsFailure() {
                        return true;
                    }

                    @Override
                    public boolean shouldInformAdmins() {
                        return false;
                    }
                },
                player.position(), player.getRotationVector(), player.serverLevel(),
                2, sourceName, Component.literal(sourceName), player.getServer(), player
        );
    }
}
