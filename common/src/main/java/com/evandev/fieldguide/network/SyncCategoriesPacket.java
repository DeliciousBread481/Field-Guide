package com.evandev.fieldguide.network;

import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.CategoryEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class SyncCategoriesPacket {
    private final List<Category> categories;
    private final List<String> biomeAdditions;
    private final List<String> biomeRemovals;
    private final List<String> lootAdditions;
    private final List<String> lootRemovals;

    public SyncCategoriesPacket(List<Category> categories, List<String> biomeAdditions, List<String> biomeRemovals, List<String> lootAdditions, List<String> lootRemovals) {
        this.categories = categories;
        this.biomeAdditions = biomeAdditions;
        this.biomeRemovals = biomeRemovals;
        this.lootAdditions = lootAdditions;
        this.lootRemovals = lootRemovals;
    }

    public SyncCategoriesPacket(FriendlyByteBuf buf) {
        this.categories = buf.readCollection(ArrayList::new, b -> {
            ResourceLocation id = b.readResourceLocation();
            Category cat = new Category(id);
            cat.setSortIndex(b.readInt());

            int entryCount = b.readInt();
            for (int i = 0; i < entryCount; i++) {
                CategoryEntry.Type type = b.readEnum(CategoryEntry.Type.class);
                ResourceLocation entryId = b.readBoolean() ? b.readResourceLocation() : null;
                ResourceLocation displayId = b.readBoolean() ? b.readResourceLocation() : null;
                String strategy = b.readBoolean() ? b.readUtf() : null;

                List<ResourceLocation> components = null;
                if (b.readBoolean()) {
                    int compCount = b.readInt();
                    components = new ArrayList<>();
                    for (int j = 0; j < compCount; j++) {
                        components.add(b.readResourceLocation());
                    }
                }

                ResourceLocation structureNbt = b.readBoolean() ? b.readResourceLocation() : null;

                cat.addEntry(new CategoryEntry(type, entryId, displayId, strategy, components, structureNbt));
            }
            return cat;
        });

        this.biomeAdditions = buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        this.biomeRemovals = buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        this.lootAdditions = buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
        this.lootRemovals = buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(categories, (b, cat) -> {
            b.writeResourceLocation(cat.getId());
            b.writeInt(cat.getSortIndex());

            b.writeInt(cat.getEntries().size());
            for (CategoryEntry entry : cat.getEntries()) {
                b.writeEnum(entry.type());
                b.writeBoolean(entry.id() != null);
                if (entry.id() != null) b.writeResourceLocation(entry.id());

                b.writeBoolean(entry.displayId() != null);
                if (entry.displayId() != null) b.writeResourceLocation(entry.displayId());

                b.writeBoolean(entry.strategy() != null);
                if (entry.strategy() != null) b.writeUtf(entry.strategy());

                b.writeBoolean(entry.components() != null);
                if (entry.components() != null) {
                    b.writeInt(entry.components().size());
                    for (ResourceLocation comp : entry.components()) {
                        b.writeResourceLocation(comp);
                    }
                }

                b.writeBoolean(entry.structureNbt() != null);
                if (entry.structureNbt() != null) b.writeResourceLocation(entry.structureNbt());
            }
        });

        buf.writeCollection(biomeAdditions, FriendlyByteBuf::writeUtf);
        buf.writeCollection(biomeRemovals, FriendlyByteBuf::writeUtf);
        buf.writeCollection(lootAdditions, FriendlyByteBuf::writeUtf);
        buf.writeCollection(lootRemovals, FriendlyByteBuf::writeUtf);
    }

    public List<Category> getCategories() {
        return categories;
    }

    public List<String> getBiomeAdditions() {
        return biomeAdditions;
    }

    public List<String> getBiomeRemovals() {
        return biomeRemovals;
    }

    public List<String> getLootAdditions() {
        return lootAdditions;
    }

    public List<String> getLootRemovals() {
        return lootRemovals;
    }
}