package com.evandev.fieldguide.platform;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.FieldGuideMod;
import com.evandev.fieldguide.network.*;
import com.evandev.fieldguide.platform.services.INetworkHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ForgeNetworkHelper implements INetworkHelper {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SyncLootPacket.class, SyncLootPacket::encode, SyncLootPacket::new, FieldGuideMod::handleSyncLoot);
        CHANNEL.registerMessage(id++, SyncCategoriesPacket.class, SyncCategoriesPacket::encode, SyncCategoriesPacket::new, FieldGuideMod::handleSyncCategories);
        CHANNEL.registerMessage(id++, GrantContentPacket.class, GrantContentPacket::encode, GrantContentPacket::new, FieldGuideMod::handleGrantContent);
        CHANNEL.registerMessage(id++, ClaimXpPacket.class, ClaimXpPacket::encode, ClaimXpPacket::new, FieldGuideMod::handleClaimXp);
        CHANNEL.registerMessage(id++, ExportContentPacket.class, ExportContentPacket::encode, ExportContentPacket::new, FieldGuideMod::handleExportContent);
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