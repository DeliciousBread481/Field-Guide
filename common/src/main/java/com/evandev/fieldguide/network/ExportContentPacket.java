package com.evandev.fieldguide.network;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.gui.util.IconCacheManager;
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
        if ("reload_cache".equals(type)) {
            ClientFieldGuideManager.clearCache();
            IconCacheManager.clearCache();
        } else {
            ClientFieldGuideManager.getInstance().exportToLang(type);
        }
    }
}