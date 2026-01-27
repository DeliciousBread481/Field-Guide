package com.evandev.fieldguide.data;

import net.minecraft.resources.ResourceLocation;

public class CategoryEntry {
    private final Type type;
    private final ResourceLocation id;
    private final String strategy;
    public CategoryEntry(Type type, ResourceLocation id, String strategy) {
        this.type = type;
        this.id = id;
        this.strategy = strategy;
    }

    public Type getType() {
        return type;
    }

    public ResourceLocation getId() {
        return id;
    }

    public String getStrategy() {
        return strategy;
    }

    public enum Type {ENTRY, AUTO_POPULATE}
}