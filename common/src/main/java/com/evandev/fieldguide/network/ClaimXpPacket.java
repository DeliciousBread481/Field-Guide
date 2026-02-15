package com.evandev.fieldguide.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class ClaimXpPacket {
    private final int amount;

    public ClaimXpPacket(int amount) {
        this.amount = amount;
    }

    public ClaimXpPacket(FriendlyByteBuf buf) {
        this.amount = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(amount);
    }

    public int getAmount() {
        return amount;
    }

    public void handleServer(ServerPlayer player) {
        if (player != null && amount > 0) {
            player.giveExperiencePoints(amount);
        }
    }
}