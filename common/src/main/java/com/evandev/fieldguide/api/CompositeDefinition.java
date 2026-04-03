package com.evandev.fieldguide.api;

import net.minecraft.resources.Identifier;

import java.util.List;

public record CompositeDefinition(
        Identifier id,
        Identifier displayId,
        List<Identifier> components,
        Identifier structureNbt,
        List<String> stackedBlocks
) {
}