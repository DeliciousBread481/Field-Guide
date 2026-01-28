package com.evandev.fieldguide.platform;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.network.GrantContentPacket;
import com.evandev.fieldguide.network.RequestDropsPacket;
import com.evandev.fieldguide.network.SyncCategoriesPacket;
import com.evandev.fieldguide.network.SyncDropsPacket;
import com.evandev.fieldguide.platform.services.INetworkHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class FabricNetworkHelper implements INetworkHelper {
    public static final ResourceLocation REQUEST_DROPS_CHANNEL = new ResourceLocation(Constants.MOD_ID, "request_drops");
    public static final ResourceLocation SYNC_DROPS_CHANNEL = new ResourceLocation(Constants.MOD_ID, "sync_drops");
    public static final ResourceLocation SYNC_CATEGORIES_CHANNEL = new ResourceLocation(Constants.MOD_ID, "sync_categories");
    public static final ResourceLocation GRANT_CONTENT_CHANNEL = new ResourceLocation(Constants.MOD_ID, "grant_content");

    @Override
    public void sendToServer(Object packet) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        if (packet instanceof RequestDropsPacket req) {
            req.encode(buf);
            ClientPlayNetworking.send(REQUEST_DROPS_CHANNEL, buf);
        }
    }

    @Override
    public void sendToPlayer(Object packet, ServerPlayer player) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        if (packet instanceof SyncDropsPacket sync) {
            sync.encode(buf);
            ServerPlayNetworking.send(player, SYNC_DROPS_CHANNEL, buf);
        } else if (packet instanceof SyncCategoriesPacket syncCat) {
            syncCat.encode(buf);
            ServerPlayNetworking.send(player, SYNC_CATEGORIES_CHANNEL, buf);
        } else if (packet instanceof GrantContentPacket grant) {
            grant.encode(buf);
            ServerPlayNetworking.send(player, GRANT_CONTENT_CHANNEL, buf);
        }
    }
}