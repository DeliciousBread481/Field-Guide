package com.evandev.fieldguide.server;

import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.util.EntryResolver;
import com.evandev.fieldguide.util.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;

public class ScanVerifier {

    public static boolean verifyScan(ServerPlayer player, ResourceLocation entryId,
                                     ResourceLocation scannedTargetId, BlockPos targetBlockPos, int targetEntityId) {
        ModConfig config = ModConfig.get();

        if (config.disableScanning) return false;

        if (config.requireSpyglass) {
            if (!player.isHolding(s -> s.is(ModTags.Items.SPYGLASSES))) return false;
        }

        ResourceLocation categoryId = ServerFieldGuideManager.getInstance().getCategoryForEntryId(entryId);

        double maxDistSq = config.scanDistance * config.scanDistance;
        ServerLevel level = player.serverLevel();

        if (targetEntityId != 0) {
            if (!verifyEntityPresence(player, scannedTargetId, targetEntityId, level, maxDistSq, categoryId)) return false;
        } else if (targetBlockPos != null) {
            if (!verifyBlockPresence(player, scannedTargetId, targetBlockPos, level, maxDistSq, categoryId)) return false;
        } else {
            return false;
        }

        if (ServerFieldGuideManager.getInstance().getCategoryForEntryId(entryId) == null) {
            return false;
        }

        return targetBelongsToEntry(scannedTargetId, entryId);
    }

    private static boolean verifyEntityPresence(ServerPlayer player, ResourceLocation scannedTargetId,
                                                int entityId, ServerLevel level, double maxDistSq, ResourceLocation categoryId) {
        Entity entity = level.getEntity(entityId);
        if (entity == null || entity.isSpectator()) return false;
        if (player.distanceToSqr(entity) > maxDistSq) return false;
        if (!EntryResolver.isValidEntity(entity.getType(), categoryId)) return false;

        ResourceLocation actualTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return actualTypeId.equals(scannedTargetId);
    }

    private static boolean verifyBlockPresence(ServerPlayer player, ResourceLocation scannedTargetId,
                                               BlockPos blockPos, ServerLevel level, double maxDistSq, ResourceLocation categoryId) {
        if (!level.isLoaded(blockPos)) return false;

        double distSq = player.distanceToSqr(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
        if (distSq > maxDistSq) return false;

        Block block = level.getBlockState(blockPos).getBlock();
        if (!EntryResolver.isValidBlock(block, categoryId)) return false;

        ResourceLocation actualBlockId = BuiltInRegistries.BLOCK.getKey(block);
        return actualBlockId.equals(scannedTargetId);
    }

    private static boolean targetBelongsToEntry(ResourceLocation scannedTargetId, ResourceLocation entryId) {
        if (scannedTargetId.equals(entryId)) return true;

        ResourceLocation redirected = ServerFieldGuideManager.getInstance().getRedirects().get(scannedTargetId);
        if (redirected != null && redirected.equals(entryId)) return true;

        return ServerFieldGuideManager.getInstance().isTargetInEntry(scannedTargetId, entryId);
    }
}