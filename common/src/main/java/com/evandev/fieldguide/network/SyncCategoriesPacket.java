package com.evandev.fieldguide.network;

import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.CategoryEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class SyncCategoriesPacket {
    private final List<Category> categories;

    public SyncCategoriesPacket(List<Category> categories) {
        this.categories = categories;
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
                String strategy = b.readBoolean() ? b.readUtf() : null;
                cat.addEntry(new CategoryEntry(type, entryId, strategy));
            }
            return cat;
        });
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(categories, (b, cat) -> {
            b.writeResourceLocation(cat.getId());
            b.writeInt(cat.getSortIndex());

            b.writeInt(cat.getEntries().size());
            for (CategoryEntry entry : cat.getEntries()) {
                b.writeEnum(entry.getType());
                b.writeBoolean(entry.getId() != null);
                if (entry.getId() != null) b.writeResourceLocation(entry.getId());

                b.writeBoolean(entry.getStrategy() != null);
                if (entry.getStrategy() != null) b.writeUtf(entry.getStrategy());
            }
        });
    }

    public List<Category> getCategories() {
        return categories;
    }
}