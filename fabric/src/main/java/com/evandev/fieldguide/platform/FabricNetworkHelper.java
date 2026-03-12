package com.evandev.fieldguide.platform;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.network.*;
import com.evandev.fieldguide.platform.services.INetworkHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class FabricNetworkHelper implements INetworkHelper {
    public static final ResourceLocation SYNC_CATEGORIES_CHANNEL = new ResourceLocation(Constants.MOD_ID, "sync_categories");
    public static final ResourceLocation PROGRESS_UPDATE_CHANNEL = new ResourceLocation(Constants.MOD_ID, "progress_update");
    public static final ResourceLocation SYNC_LOOT_CHANNEL = new ResourceLocation(Constants.MOD_ID, "sync_loot");
    public static final ResourceLocation EXPORT_CONTENT_CHANNEL = new ResourceLocation(Constants.MOD_ID, "export_content");
    public static final ResourceLocation SCAN_UNLOCK_CHANNEL = new ResourceLocation(Constants.MOD_ID, "scan_unlock");
    public static final ResourceLocation MARK_SEEN_CHANNEL = new ResourceLocation(Constants.MOD_ID, "mark_seen");
    public static final ResourceLocation UPDATE_ENTRY_DATA_CHANNEL = new ResourceLocation(Constants.MOD_ID, "update_entry_data");
    public static final ResourceLocation UPDATE_JOURNAL_CHANNEL = new ResourceLocation(Constants.MOD_ID, "update_journal");

    @Override
    public void sendToServer(Object packet) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        if (packet instanceof ScanUnlockPacket scanPacket) {
            scanPacket.encode(buf);
            ClientPlayNetworking.send(SCAN_UNLOCK_CHANNEL, buf);
        } else if (packet instanceof MarkSeenPacket seenPacket) {
            seenPacket.encode(buf);
            ClientPlayNetworking.send(MARK_SEEN_CHANNEL, buf);
        } else if (packet instanceof UpdateEntryDataPacket entryDataPacket) {
            entryDataPacket.encode(buf);
            ClientPlayNetworking.send(UPDATE_ENTRY_DATA_CHANNEL, buf);
        } else if (packet instanceof UpdateJournalPacket journalPacket) {
            journalPacket.encode(buf);
            ClientPlayNetworking.send(UPDATE_JOURNAL_CHANNEL, buf);
        }
    }

    @Override
    public void sendToPlayer(Object packet, ServerPlayer player) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        if (packet instanceof SyncCategoriesPacket syncCat) {
            syncCat.encode(buf);
            ServerPlayNetworking.send(player, SYNC_CATEGORIES_CHANNEL, buf);
        } else if (packet instanceof ProgressUpdatePacket progressUpdate) {
            progressUpdate.encode(buf);
            ServerPlayNetworking.send(player, PROGRESS_UPDATE_CHANNEL, buf);
        } else if (packet instanceof SyncLootPacket syncLoot) {
            syncLoot.encode(buf);
            ServerPlayNetworking.send(player, SYNC_LOOT_CHANNEL, buf);
        } else if (packet instanceof ExportContentPacket export) {
            export.encode(buf);
            ServerPlayNetworking.send(player, EXPORT_CONTENT_CHANNEL, buf);
        }
    }
}