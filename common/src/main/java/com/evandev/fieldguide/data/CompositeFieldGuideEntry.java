package com.evandev.fieldguide.data;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public record CompositeFieldGuideEntry(ResourceLocation id, Object displayEntry, List<Object> components,
                                       ResourceLocation structureNbt, List<String> stackedBlocks) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompositeFieldGuideEntry that = (CompositeFieldGuideEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}