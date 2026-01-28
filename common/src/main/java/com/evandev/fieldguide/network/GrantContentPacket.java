package com.evandev.fieldguide.network;

import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.FieldGuideDataManager;
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
        FieldGuideDataManager manager = FieldGuideDataManager.getInstance();
        if (action == Action.GRANT) {
            handleGrant(manager);
        } else {
            handleRevoke(manager);
        }
    }

    private void handleGrant(FieldGuideDataManager manager) {
        switch (type) {
            case EVERYTHING -> {
                for (Object entry : FieldGuideDataManager.getValidEntries()) {
                    manager.unlock(entry, false);
                }
            }
            case CATEGORY -> {
                Category cat = FieldGuideDataManager.getCategories().get(id);
                if (cat != null) {
                    for (Object entry : manager.getEntriesForCategory(cat)) {
                        manager.unlock(entry, false);
                    }
                }
            }
            case ENTRY -> FieldGuideDataManager.getValidEntries().stream()
                    .filter(e -> {
                        ResourceLocation entryId = FieldGuideDataManager.getEntryId(e);
                        return entryId != null && entryId.equals(id);
                    })
                    .findFirst()
                    .ifPresent(entry -> manager.unlock(entry, true));
        }
    }

    private void handleRevoke(FieldGuideDataManager manager) {
        switch (type) {
            case EVERYTHING -> manager.revokeAll();
            case CATEGORY -> {
                Category cat = FieldGuideDataManager.getCategories().get(id);
                if (cat != null) {
                    for (Object entry : manager.getEntriesForCategory(cat)) {
                        manager.revoke(entry);
                    }
                }
            }
            case ENTRY -> FieldGuideDataManager.getValidEntries().stream()
                    .filter(e -> {
                        ResourceLocation entryId = FieldGuideDataManager.getEntryId(e);
                        return entryId != null && entryId.equals(id);
                    })
                    .findFirst()
                    .ifPresent(manager::revoke);
        }
    }

    public enum Action {GRANT, REVOKE}

    public enum Type {EVERYTHING, CATEGORY, ENTRY}
}