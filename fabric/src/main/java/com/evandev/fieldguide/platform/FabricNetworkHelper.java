package com.evandev.fieldguide.platform;

import com.evandev.fieldguide.platform.services.INetworkHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public class FabricNetworkHelper implements INetworkHelper {
    @Override
    public void sendToServer(Object packet) {
        if (packet instanceof CustomPacketPayload payload) {
            ClientPlayNetworking.send(payload);
        }
    }

    @Override
    public void sendToPlayer(Object packet, ServerPlayer player) {
        if (packet instanceof CustomPacketPayload payload) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}