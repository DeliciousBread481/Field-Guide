package com.evandev.fieldguide.data;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record CategoryEntry(Type type, ResourceLocation id, String strategy, List<ResourceLocation> components) {
    public enum Type {ENTRY, AUTO_POPULATE, COMPOSITE}
}