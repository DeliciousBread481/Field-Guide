package com.evandev.fieldguide.api;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record CategoryEntry(CategoryType categoryType, ResourceLocation id, ResourceLocation displayId, String strategy,
                            String virtualType,
                            ResourceLocation icon,
                            List<ResourceLocation> components,
                            ResourceLocation structureNbt,
                            List<String> stackedBlocks) {
    public enum CategoryType {ENTRY, AUTO_POPULATE, COMPOSITE, VIRTUAL}
}
