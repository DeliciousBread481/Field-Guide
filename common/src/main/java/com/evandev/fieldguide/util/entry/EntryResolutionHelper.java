package com.evandev.fieldguide.util.entry;

import com.evandev.fieldguide.api.*;
import com.evandev.fieldguide.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class EntryResolutionHelper {

    public static List<Object> resolveCategoryEntries(Category category, List<CompositeDefinition> globalComposites, Map<ResourceLocation, ResourceLocation> redirects) {
        Set<Object> foundEntries = new LinkedHashSet<>();
        Set<ResourceLocation> addedIds = new HashSet<>();
        ResourceLocation categoryId = category.getId();

        for (CategoryEntry entry : category.getEntries()) {
            if (entry.categoryType() == CategoryEntry.CategoryType.ENTRY && entry.id() != null) {
                resolveSingleEntry(entry.id(), categoryId).ifPresent(e -> {
                    if (entry.stackedBlocks() != null && !entry.stackedBlocks().isEmpty()) {
                        foundEntries.add(new CompositeFieldGuideEntry(entry.id(), e, new ArrayList<>(), null, entry.stackedBlocks()));
                    } else {
                        foundEntries.add(e);
                    }
                    addedIds.add(entry.id());
                });
            } else if (entry.categoryType() == CategoryEntry.CategoryType.COMPOSITE && entry.id() != null) {
                ResourceLocation displayLoc = entry.displayId() != null ? entry.displayId() : entry.id();
                resolveSingleEntry(displayLoc, categoryId).ifPresent(displayEntry -> {
                    List<Object> components = new ArrayList<>();
                    if (entry.components() != null) {
                        for (ResourceLocation compId : entry.components()) {
                            resolveSingleEntry(compId, categoryId).ifPresent(components::add);
                        }
                    }
                    foundEntries.add(new CompositeFieldGuideEntry(entry.id(), displayEntry, components, entry.structureNbt(), entry.stackedBlocks()));
                    addedIds.add(entry.id());
                });
            } else if (entry.categoryType() == CategoryEntry.CategoryType.AUTO_POPULATE) {
                for (Object obj : AutoPopulateRegistry.getEntries(entry.strategy(), categoryId)) {
                    ResourceLocation id = getEntryId(obj);
                    if (id != null && !addedIds.contains(id)) {
                        foundEntries.add(obj);
                        addedIds.add(id);
                    }
                }
            }
        }

        if (globalComposites != null && !globalComposites.isEmpty()) {
            List<Object> groupedEntries = new ArrayList<>();
            Set<ResourceLocation> processedComposites = new HashSet<>();

            for (Object raw : foundEntries) {
                if (raw instanceof CompositeFieldGuideEntry autoComposite) {
                    ResourceLocation displayId = getEntryId(autoComposite.displayEntry());
                    CompositeDefinition matchingDef = findCompositeFor(displayId, globalComposites);

                    if (matchingDef != null) {
                        if (processedComposites.add(matchingDef.id())) {
                            resolveCompositeDefinition(matchingDef, categoryId).ifPresent(groupedEntries::add);
                        }
                    } else {
                        groupedEntries.add(raw);
                    }
                    continue;
                }

                ResourceLocation id = getEntryId(raw);
                CompositeDefinition matchingDef = findCompositeFor(id, globalComposites);

                if (matchingDef != null) {
                    if (processedComposites.add(matchingDef.id())) {
                        resolveCompositeDefinition(matchingDef, categoryId).ifPresent(groupedEntries::add);
                    }
                } else {
                    groupedEntries.add(raw);
                }
            }
            foundEntries.clear();
            foundEntries.addAll(groupedEntries);
        }

        List<Object> resolved = new ArrayList<>(foundEntries);
        resolved.removeIf(e -> {
            ResourceLocation id = getEntryId(e);
            return id != null && redirects.containsKey(id);
        });

        return resolved;
    }

    private static CompositeDefinition findCompositeFor(ResourceLocation id, List<CompositeDefinition> composites) {
        if (id == null) return null;
        for (CompositeDefinition def : composites) {
            if (id.equals(def.displayId()) || (def.components() != null && def.components().contains(id))) {
                return def;
            }
        }
        return null;
    }

    public static Optional<CompositeFieldGuideEntry> resolveCompositeDefinition(CompositeDefinition def, ResourceLocation categoryId) {
        ResourceLocation displayLoc = def.displayId() != null ? def.displayId() : def.id();
        return resolveSingleEntry(displayLoc, categoryId).map(displayEntry -> {
            List<Object> components = new ArrayList<>();
            if (def.components() != null) {
                for (ResourceLocation compId : def.components()) {
                    resolveSingleEntry(compId, categoryId).ifPresent(components::add);
                }
            }
            return new CompositeFieldGuideEntry(def.id(), displayEntry, components, def.structureNbt(), def.stackedBlocks());
        });
    }

    public static Optional<Object> resolveSingleEntry(ResourceLocation id, ResourceLocation categoryId) {
        if (Services.PLATFORM.isModLoaded("cobblemon") && id.getNamespace().equals("fieldguide") && id.getPath().startsWith("cobblemon/")) {
            return Optional.of(new CompositeFieldGuideEntry(id, null, new ArrayList<>(), null, null));
        }

        return BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                .filter(t -> EntryValidator.isValidEntity(t, categoryId))
                .map(Object.class::cast)
                .or(() -> BuiltInRegistries.BLOCK.getOptional(id)
                        .filter(b -> EntryValidator.isValidBlock(b, categoryId))
                        .map(Object.class::cast));
    }

    public static ResourceLocation getEntryId(Object obj) {
        return AutoPopulateRegistry.getEntryId(obj);
    }
}
