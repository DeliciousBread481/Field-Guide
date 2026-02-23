package com.evandev.fieldguide.data;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record CompositeDefinition(
        ResourceLocation id,
        ResourceLocation displayId,
        List<ResourceLocation> components,
        ResourceLocation structureNbt,
        List<String> stackedBlocks
) {
}