package com.evandev.fieldguide.data;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class Category {
    private final ResourceLocation id;
    private final List<CategoryEntry> entries = new ArrayList<>();
    private int sortIndex = 0;
    private boolean scannable = true;

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

    public boolean isScannable() {
        return scannable;
    }

    public void setScannable(boolean scannable) {
        this.scannable = scannable;
    }
}