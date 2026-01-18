package com.evandev.fieldguide.data;

import net.minecraft.resources.ResourceLocation;

public class CategoryEntry {
    FieldGuideDataManager.EntryType type;
    ResourceLocation id;
    String strategy;

    CategoryEntry(FieldGuideDataManager.EntryType type, ResourceLocation id, String strategy) {
        this.type = type;
        this.id = id;
        this.strategy = strategy;
    }
}