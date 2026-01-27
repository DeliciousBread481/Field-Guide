package com.evandev.fieldguide.data;

import net.minecraft.resources.ResourceLocation;

public record CategoryEntry(Type type, ResourceLocation id, String strategy) {

    public enum Type {ENTRY, AUTO_POPULATE}
}