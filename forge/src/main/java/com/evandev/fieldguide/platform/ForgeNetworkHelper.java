package com.evandev.fieldguide.platform;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.FieldGuideMod;
import com.evandev.fieldguide.network.*;
import com.evandev.fieldguide.platform.services.INetworkHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class ForgeNetworkHelper implements INetworkHelper {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Constants.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SyncLootPacket.class, SyncLootPacket::encode, SyncLootPacket::new, FieldGuideMod::handleSyncLoot, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, SyncConfigPacket.class, SyncConfigPacket::encode, SyncConfigPacket::new, FieldGuideMod::handleSyncConfig, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, SyncCategoriesPacket.class, SyncCategoriesPacket::encode, SyncCategoriesPacket::new, FieldGuideMod::handleSyncCategories, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ProgressUpdatePacket.class, ProgressUpdatePacket::encode, ProgressUpdatePacket::new, FieldGuideMod::handleProgressUpdate, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ExportContentPacket.class, ExportContentPacket::encode, ExportContentPacket::new, FieldGuideMod::handleExportContent, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ScanUnlockPacket.class, ScanUnlockPacket::encode, ScanUnlockPacket::new, FieldGuideMod::handleScanUnlock, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, MarkSeenPacket.class, MarkSeenPacket::encode, MarkSeenPacket::new, FieldGuideMod::handleMarkSeen, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, UpdateEntryDataPacket.class, UpdateEntryDataPacket::encode, UpdateEntryDataPacket::new, FieldGuideMod::handleUpdateEntryData, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, UpdateJournalPacket.class, UpdateJournalPacket::encode, UpdateJournalPacket::new, FieldGuideMod::handleUpdateJournal, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, CopyPagePacket.class, CopyPagePacket::encode, CopyPagePacket::new, FieldGuideMod::handleRipOut, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    @Override
    public void sendToServer(Object packet) {
        CHANNEL.send(PacketDistributor.SERVER.noArg(), packet);
    }

    @Override
    public void sendToPlayer(Object packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
