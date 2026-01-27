package com.evandev.fieldguide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class RequestDropsPacket {
    private final ResourceLocation entryId;

    public RequestDropsPacket(ResourceLocation entryId) {
        this.entryId = entryId;
    }

    public RequestDropsPacket(FriendlyByteBuf buf) {
        this.entryId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(entryId);
    }

    public ResourceLocation getEntryId() {
        return entryId;
    }
}