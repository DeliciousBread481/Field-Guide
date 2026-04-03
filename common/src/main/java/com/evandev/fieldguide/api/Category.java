package com.evandev.fieldguide.api;

import com.evandev.fieldguide.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public class Category {

    public static final StreamCodec<RegistryFriendlyByteBuf, Category> STREAM_CODEC = StreamCodec.of(
            (buf, cat) -> {
                buf.writeIdentifier(cat.getId());
                buf.writeInt(cat.getSortIndex());
                buf.writeNullable(cat.getIcon(), FriendlyByteBuf::writeIdentifier);
                buf.writeCollection(cat.getGroupByQueries() != null ? cat.getGroupByQueries() : List.of(), FriendlyByteBuf::writeUtf);
                buf.writeCollection(cat.getEntryIds() != null ? cat.getEntryIds() : List.of(), FriendlyByteBuf::writeIdentifier);
            },
            buf -> {
                Category cat = new Category(buf.readIdentifier());
                cat.setSortIndex(buf.readInt());

                Identifier icon = buf.readNullable(FriendlyByteBuf::readIdentifier);
                if (icon != null) {
                    cat.setIcon(icon);
                }

                cat.setGroupByQueries(buf.readList(FriendlyByteBuf::readUtf));
                buf.readList(FriendlyByteBuf::readIdentifier).forEach(cat::addEntryId);

                return cat;
            }
    );

    private final Identifier id;
    private final List<Identifier> entryIds = new ArrayList<>();
    private List<String> groupByQueries = new ArrayList<>();
    private int sortIndex = 0;
    private Identifier icon = Constants.DEFAULT_ICON;

    public Category(Identifier id) {
        this.id = id;
    }

    public Identifier getId() {
        return id;
    }

    public String getTranslationKey() {
        return "category." + id.getNamespace() + ".fieldguide." + id.getPath();
    }

    public int getSortIndex() {
        return sortIndex;
    }

    public void setSortIndex(int sortIndex) {
        this.sortIndex = sortIndex;
    }

    public List<Identifier> getEntryIds() {
        return entryIds;
    }

    public void addEntryId(Identifier entryId) {
        this.entryIds.add(entryId);
    }

    public Identifier getIcon() {
        return icon;
    }

    public void setIcon(Identifier icon) {
        this.icon = icon;
    }

    public List<String> getGroupByQueries() {
        return groupByQueries;
    }

    public void setGroupByQueries(List<String> groupByQueries) {
        this.groupByQueries = groupByQueries;
    }
}