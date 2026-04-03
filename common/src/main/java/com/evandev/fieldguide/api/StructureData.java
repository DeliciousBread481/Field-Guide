package com.evandev.fieldguide.api;

import net.minecraft.resources.Identifier;

import java.util.List;

public record StructureData(
        Identifier structureNbt,
        List<String> stackedBlocks
) {
}