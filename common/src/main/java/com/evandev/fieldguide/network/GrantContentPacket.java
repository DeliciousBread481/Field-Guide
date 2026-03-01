package com.evandev.fieldguide.network;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.data.Category;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class GrantContentPacket implements CustomPacketPayload {
    public static final Type<GrantContentPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "grant_content"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GrantContentPacket> CODEC = StreamCodec.ofMember(GrantContentPacket::encode, GrantContentPacket::new);

    private final Action action;
    private final TypeEnum type;
    private final ResourceLocation id;

    public GrantContentPacket(Action action, TypeEnum type, ResourceLocation id) {
        this.action = action;
        this.type = type;
        this.id = id;
    }

    public GrantContentPacket(RegistryFriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.type = buf.readEnum(TypeEnum.class);
        this.id = buf.readBoolean() ? buf.readResourceLocation() : null;
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeEnum(type);
        buf.writeBoolean(id != null);
        if (id != null) buf.writeResourceLocation(id);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public Action getAction() {
        return action;
    }

    public TypeEnum getGrantType() {
        return type;
    }

    public ResourceLocation getId() {
        return id;
    }

    public void handleClient() {
        ClientFieldGuideManager manager = ClientFieldGuideManager.getInstance();
        if (action == Action.GRANT) {
            handleGrant(manager);
        } else {
            handleRevoke(manager);
        }
    }

    private void handleGrant(ClientFieldGuideManager manager) {
        switch (type) {
            case EVERYTHING -> {
                for (Object entry : ClientFieldGuideManager.getValidEntries()) {
                    manager.unlock(entry, false);
                }
            }
            case CATEGORY -> {
                Category cat = ClientFieldGuideManager.getCategories().get(id);
                if (cat != null) {
                    for (Object entry : manager.getEntriesForCategory(cat)) {
                        manager.unlock(entry, false);
                    }
                }
            }
            case ENTRY -> ClientFieldGuideManager.getValidEntries().stream()
                    .filter(e -> {
                        ResourceLocation entryId = ClientFieldGuideManager.getEntryId(e);
                        return entryId != null && entryId.equals(id);
                    })
                    .findFirst()
                    .ifPresent(entry -> manager.unlock(entry, true));
        }
    }

    private void handleRevoke(ClientFieldGuideManager manager) {
        switch (type) {
            case EVERYTHING -> manager.revokeAll();
            case CATEGORY -> {
                Category cat = ClientFieldGuideManager.getCategories().get(id);
                if (cat != null) {
                    for (Object entry : manager.getEntriesForCategory(cat)) {
                        manager.revoke(entry);
                    }
                }
            }
            case ENTRY -> ClientFieldGuideManager.getValidEntries().stream()
                    .filter(e -> {
                        ResourceLocation entryId = ClientFieldGuideManager.getEntryId(e);
                        return entryId != null && entryId.equals(id);
                    })
                    .findFirst()
                    .ifPresent(manager::revoke);
        }
    }

    public enum Action {GRANT, REVOKE}

    public enum TypeEnum {EVERYTHING, CATEGORY, ENTRY}
}