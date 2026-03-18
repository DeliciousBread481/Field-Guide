package com.evandev.fieldguide.platform.services;

import net.minecraft.server.level.ServerPlayer;

public interface INetworkHelper {
    void sendToServer(Object packet);
    void sendToPlayer(Object packet, ServerPlayer player);
}
