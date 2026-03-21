package com.evandev.fieldguide.api;

import net.minecraft.resources.ResourceLocation;

public record VirtualFieldGuideEntry(ResourceLocation id, String virtualType, ResourceLocation icon) {
}
