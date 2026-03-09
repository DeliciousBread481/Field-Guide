package com.evandev.fieldguide.network;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.data.Category;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class GrantContentPacket {
    private final Action action;
    private final TypeEnum typeEnum;
    private final ResourceLocation id;

    public GrantContentPacket(Action action, TypeEnum typeEnum, ResourceLocation id) {
        this.action = action;
        this.typeEnum = typeEnum;
        this.id = id;
    }

    public GrantContentPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.typeEnum = buf.readEnum(TypeEnum.class);
        this.id = buf.readBoolean() ? buf.readResourceLocation() : null;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeEnum(typeEnum);
        buf.writeBoolean(id != null);
        if (id != null) buf.writeResourceLocation(id);
    }

    public Action getAction() {
        return action;
    }

    public TypeEnum getType() {
        return typeEnum;
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
        switch (typeEnum) {
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
        switch (typeEnum) {
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