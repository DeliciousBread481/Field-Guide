package com.evandev.fieldguide.api;

import net.minecraft.resources.ResourceLocation;

/**
 * Used for entries that do not have a direct, single EntityType, Block, or Item equivalent.
 * e.g., Cobblemon (which share a single EntityType but have distinct species).
 */
public record VirtualFieldGuideEntry(ResourceLocation id, String virtualType, ResourceLocation icon) {
}