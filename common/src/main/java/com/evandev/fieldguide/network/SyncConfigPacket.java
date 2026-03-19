package com.evandev.fieldguide.network;

import com.evandev.fieldguide.config.ServerConfig;
import net.minecraft.network.FriendlyByteBuf;

public record SyncConfigPacket(String configJson) {

    public SyncConfigPacket(ServerConfig config) {
        this(config.toJson());
    }

    public SyncConfigPacket(FriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(configJson);
    }
}
