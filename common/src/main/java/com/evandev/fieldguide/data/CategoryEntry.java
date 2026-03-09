package com.evandev.fieldguide.data;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record CategoryEntry(CategoryType categoryType, ResourceLocation id, ResourceLocation displayId, String strategy,
                            List<ResourceLocation> components,
                            ResourceLocation structureNbt,
                            List<String> stackedBlocks) {
    public enum CategoryType {ENTRY, AUTO_POPULATE, COMPOSITE}
}