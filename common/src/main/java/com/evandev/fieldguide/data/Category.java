package com.evandev.fieldguide.data;

import com.evandev.fieldguide.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class Category {
    public static final StreamCodec<RegistryFriendlyByteBuf, Category> CODEC = StreamCodec.of(
            (buf, cat) -> {
                buf.writeResourceLocation(cat.getId());
                buf.writeInt(cat.getSortIndex());
                buf.writeResourceLocation(cat.getIcon());
                ByteBufCodecs.stringUtf8(32767).apply(ByteBufCodecs.list()).encode(buf, cat.getGroupByQueries());
                CategoryEntry.CODEC.apply(ByteBufCodecs.list()).encode(buf, cat.getEntries());
            },
            buf -> {
                Category cat = new Category(buf.readResourceLocation());
                cat.setSortIndex(buf.readInt());
                cat.setIcon(buf.readResourceLocation());
                cat.setGroupByQueries(ByteBufCodecs.stringUtf8(32767).apply(ByteBufCodecs.list()).decode(buf));
                List<CategoryEntry> decodedEntries = CategoryEntry.CODEC.apply(ByteBufCodecs.list()).decode(buf);
                cat.getEntries().addAll(decodedEntries);
                return cat;
            }
    );

    private final ResourceLocation id;
    private final List<CategoryEntry> entries = new ArrayList<>();
    private List<String> groupByQueries = new ArrayList<>();
    private int sortIndex = 0;
    private ResourceLocation icon = Constants.DEFAULT_ICON;

    public Category(ResourceLocation id) {
        this.id = id;
    }

    public ResourceLocation getId() {
        return id;
    }

    public int getSortIndex() {
        return sortIndex;
    }

    public void setSortIndex(int sortIndex) {
        this.sortIndex = sortIndex;
    }

    public List<CategoryEntry> getEntries() {
        return entries;
    }

    public void addEntry(CategoryEntry entry) {
        this.entries.add(entry);
    }

    public ResourceLocation getIcon() {
        return icon;
    }

    public void setIcon(ResourceLocation icon) {
        this.icon = icon;
    }

    public List<String> getGroupByQueries() {
        return groupByQueries;
    }

    public void setGroupByQueries(List<String> groupByQueries) {
        this.groupByQueries = groupByQueries;
    }
}