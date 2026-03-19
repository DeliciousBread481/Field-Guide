package com.evandev.fieldguide.server;

import com.evandev.fieldguide.compat.cobblemon.FieldGuideCobblemonCompat;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.util.EntryResolver;
import com.evandev.fieldguide.util.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ScanVerifier {

    public static boolean verifyScan(ServerPlayer player, ResourceLocation entryId,
                                     ResourceLocation scannedTargetId, BlockPos targetBlockPos, int targetEntityId) {
        ModConfig config = ModConfig.get();

        if (config.disableScanning) return false;

        boolean hasSpyglass = player.isScoping() ||
                player.isHolding(s -> s.is(ModTags.Items.SPYGLASSES));

        double activeScanDist;
        if (hasSpyglass && config.enableSpyglassScanning) {
            activeScanDist = config.spyglassScanDistance;
        } else if (config.enableNakedEyeScanning) {
            activeScanDist = config.nakedEyeScanDistance;
        } else {
            return false;
        }

        double maxDistSq = activeScanDist * activeScanDist;
        ServerLevel level = player.serverLevel();

        ResourceLocation categoryId = ServerFieldGuideManager.getInstance().getCategoryForEntryId(entryId);
        if (categoryId == null) {
            return false;
        }

        if (targetEntityId != 0) {
            if (!verifyEntityPresence(player, scannedTargetId, targetEntityId, level, maxDistSq, categoryId))
                return false;
            Entity entity = level.getEntity(targetEntityId);
            if (Services.PLATFORM.isModLoaded("cobblemon") && FieldGuideCobblemonCompat.isPokemon(entity)) {
                ResourceLocation pokemonEntryId = FieldGuideCobblemonCompat.getPokemonEntryId(entity);
                return pokemonEntryId.equals(entryId);
            }
        } else if (targetBlockPos != null) {
            if (!verifyBlockPresence(player, scannedTargetId, targetBlockPos, level, maxDistSq, categoryId))
                return false;
        } else {
            return false;
        }

        return targetBelongsToEntry(scannedTargetId, entryId);
    }

    private static boolean verifyEntityPresence(ServerPlayer player, ResourceLocation scannedTargetId,
                                                int entityId, ServerLevel level, double maxDistSq, ResourceLocation categoryId) {
        Entity entity = level.getEntity(entityId);
        if (entity == null || entity.isSpectator()) return false;
        if (player.distanceToSqr(entity) > maxDistSq) return false;

        if (entity instanceof ItemEntity itemEntity) {
            Item item = itemEntity.getItem().getItem();
            if (!EntryResolver.isValidItem(item, categoryId)) return false;
            ResourceLocation actualItemId = EntryResolver.getEntryId(item, true);
            return actualItemId.equals(scannedTargetId);
        }

        if (!EntryResolver.isValidEntity(entity.getType(), categoryId)) return false;

        ResourceLocation actualTypeId = EntryResolver.getEntryId(entity.getType(), true);
        return actualTypeId.equals(scannedTargetId);
    }

    private static boolean verifyBlockPresence(ServerPlayer player, ResourceLocation scannedTargetId,
                                               BlockPos blockPos, ServerLevel level, double maxDistSq, ResourceLocation categoryId) {
        if (!level.isLoaded(blockPos)) return false;

        double distSq = player.distanceToSqr(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
        if (distSq > maxDistSq) return false;

        Block block = level.getBlockState(blockPos).getBlock();
        if (!EntryResolver.isValidBlock(block, categoryId)) return false;

        ResourceLocation actualBlockId = EntryResolver.getEntryId(block, true);
        return actualBlockId.equals(scannedTargetId);
    }

    private static boolean targetBelongsToEntry(ResourceLocation scannedTargetId, ResourceLocation entryId) {
        if (scannedTargetId.equals(entryId)) return true;

        ResourceLocation redirected = ServerFieldGuideManager.getInstance().getRedirects().get(scannedTargetId);
        if (redirected != null && redirected.equals(entryId)) return true;

        return ServerFieldGuideManager.getInstance().isTargetInEntry(scannedTargetId, entryId);
    }
}
