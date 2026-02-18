package com.evandev.fieldguide.network;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import net.minecraft.network.FriendlyByteBuf;

public class ExportContentPacket {
    private final String type;

    public ExportContentPacket(String type) {
        this.type = type;
    }

    public ExportContentPacket(FriendlyByteBuf buf) {
        this.type = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(type);
    }

    public void handleClient() {
        ClientFieldGuideManager.getInstance().exportToLang(type);
    }
}