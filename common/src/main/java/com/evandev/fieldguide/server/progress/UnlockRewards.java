package com.evandev.fieldguide.server.progress;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.server.ServerFieldGuideManager;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class UnlockRewards {

    static void grant(ServerPlayer player, ResourceLocation entryId) {
        try {
            ModConfig config = ModConfig.get();
            if (config.grantXpOnScan && config.xpAmountOnScan > 0) {
                player.giveExperiencePoints(config.xpAmountOnScan);
            }

            executeCommands(player, entryId, config);
        } catch (Exception e) {
            Constants.LOG.error("Failed to grant unlock rewards for entry {} to {}", entryId, player.getName().getString(), e);
        }
    }

    private static void executeCommands(ServerPlayer player, ResourceLocation entryId, ModConfig config) {
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
                Objects.requireNonNull(player.getServer()).getCommands().performPrefixedCommand(sourceStack, cmd);
            }
        }
    }

    private static CommandSourceStack createRewardSourceStack(ServerPlayer player, ResourceLocation entryId) {
        String sourceName = Constants.MOD_ID + "/" + entryId;
        return new CommandSourceStack(
                new CommandSource() {
                    @Override
                    public void sendSystemMessage(@NotNull Component component) {
                        Constants.LOG.info("[UNLOCK] {}", component.getString());
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
                2, sourceName, Component.literal(sourceName), Objects.requireNonNull(player.getServer()), player
        );
    }
}
