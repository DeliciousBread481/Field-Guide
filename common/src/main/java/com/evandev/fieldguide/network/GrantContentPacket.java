package com.evandev.fieldguide.network;

import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public class GrantContentPacket {
    private final Action action;
    private final Type type;
    private final ResourceLocation id;

    public GrantContentPacket(Action action, Type type, ResourceLocation id) {
        this.action = action;
        this.type = type;
        this.id = id;
    }

    public GrantContentPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(Action.class);
        this.type = buf.readEnum(Type.class);
        this.id = buf.readBoolean() ? buf.readResourceLocation() : null;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeEnum(type);
        buf.writeBoolean(id != null);
        if (id != null) buf.writeResourceLocation(id);
    }

    public Action getAction() {
        return action;
    }

    public Type getType() {
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

    public enum Type {EVERYTHING, CATEGORY, ENTRY}
}