package com.evandev.fieldguide.data;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record CategoryEntry(Type type, ResourceLocation id, ResourceLocation displayId, String strategy,
                            List<ResourceLocation> components,
                            ResourceLocation structureNbt) {
    public enum Type {ENTRY, AUTO_POPULATE, COMPOSITE}
}