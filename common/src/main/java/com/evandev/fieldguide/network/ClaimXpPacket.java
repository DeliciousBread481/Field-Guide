package com.evandev.fieldguide.network;

import com.evandev.fieldguide.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

public class ClaimXpPacket implements CustomPacketPayload {
    public static final Type<ClaimXpPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "claim_xp"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimXpPacket> CODEC = StreamCodec.ofMember(ClaimXpPacket::encode, ClaimXpPacket::new);

    private final int amount;

    public ClaimXpPacket(int amount) {
        this.amount = amount;
    }

    public ClaimXpPacket(RegistryFriendlyByteBuf buf) {
        this.amount = buf.readInt();
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeInt(amount);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handleServer(ServerPlayer player) {
        if (player != null && amount > 0) {
            player.giveExperiencePoints(amount);
        }
    }
}