package com.evandev.fieldguide.api;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public record GuideEntry(
        ResourceLocation id,
        @Nullable ResourceLocation displayId,
        @Nullable ResourceLocation icon,
        EntryKind kind,
        boolean virtual,
        boolean autoPopulate,
        @Nullable String strategy,
        @Nullable List<ResourceLocation> childEntries,
        @Nullable StructureData structureData,
        @Nullable VirtualData virtualData,
        EntryUnlockData unlockData
) {
    public boolean isComposite() {
        return childEntries != null && !childEntries.isEmpty();
    }

    public boolean isStructure() {
        return kind == EntryKind.STRUCTURE;
    }

    public boolean isVirtual() {
        return virtual;
    }

    public boolean isAutoPopulate() {
        return autoPopulate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GuideEntry that = (GuideEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}