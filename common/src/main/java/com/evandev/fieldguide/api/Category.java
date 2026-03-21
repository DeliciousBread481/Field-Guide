package com.evandev.fieldguide.api;

import com.evandev.fieldguide.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class Category {

    public static final StreamCodec<RegistryFriendlyByteBuf, Category> STREAM_CODEC = StreamCodec.of(
            (buf, cat) -> {
                buf.writeResourceLocation(cat.getId());
                buf.writeInt(cat.getSortIndex());
                buf.writeNullable(cat.getIcon(), FriendlyByteBuf::writeResourceLocation);
                buf.writeCollection(cat.getGroupByQueries() != null ? cat.getGroupByQueries() : List.of(), FriendlyByteBuf::writeUtf);
                buf.writeCollection(cat.getEntryIds() != null ? cat.getEntryIds() : List.of(), FriendlyByteBuf::writeResourceLocation);
            },
            buf -> {
                Category cat = new Category(buf.readResourceLocation());
                cat.setSortIndex(buf.readInt());

                ResourceLocation icon = buf.readNullable(FriendlyByteBuf::readResourceLocation);
                if (icon != null) {
                    cat.setIcon(icon);
                }

                cat.setGroupByQueries(buf.readList(FriendlyByteBuf::readUtf));
                buf.readList(FriendlyByteBuf::readResourceLocation).forEach(cat::addEntryId);

                return cat;
            }
    );

    private final ResourceLocation id;
    private final List<ResourceLocation> entryIds = new ArrayList<>();
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

    public List<ResourceLocation> getEntryIds() {
        return entryIds;
    }

    public void addEntryId(ResourceLocation entryId) {
        this.entryIds.add(entryId);
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