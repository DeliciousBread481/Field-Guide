package com.evandev.fieldguide.api;

import com.evandev.fieldguide.Constants;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class Category {
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

    public String getTranslationKey() {
        return "category." + id.getNamespace() + ".fieldguide." + id.getPath();
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