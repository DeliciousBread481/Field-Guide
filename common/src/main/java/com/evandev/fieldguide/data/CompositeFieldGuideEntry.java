package com.evandev.fieldguide.data;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public class CompositeFieldGuideEntry {
    private final ResourceLocation id;
    private final Object displayEntry;
    private final List<Object> components;

    public CompositeFieldGuideEntry(ResourceLocation id, Object displayEntry, List<Object> components) {
        this.id = id;
        this.displayEntry = displayEntry;
        this.components = components;
    }

    public ResourceLocation getId() {
        return id;
    }

    public Object getDisplayEntry() {
        return displayEntry;
    }

    public List<Object> getComponents() {
        return components;
    }

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