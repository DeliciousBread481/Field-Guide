package com.evandev.fieldguide.util;

import com.evandev.fieldguide.api.AutoPopulateRegistry;
import com.evandev.fieldguide.api.Category;
import com.evandev.fieldguide.api.CompositeDefinition;
import com.evandev.fieldguide.api.CompositeFieldGuideEntry;
import com.evandev.fieldguide.util.entry.EntryResolutionHelper;
import com.evandev.fieldguide.util.entry.EntryValidator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.*;
import java.util.stream.Collectors;

public class EntryResolver {

    public static boolean hasEntry(Map<ResourceLocation, List<Object>> resolvedEntries, ResourceLocation entryId) {
        for (List<Object> entries : resolvedEntries.values()) {
            for (Object entry : entries) {
                if (entryId.equals(getEntryId(entry))) return true;
            }
        }
        return false;
    }

    public static ResourceLocation getCategoryForEntryId(Map<ResourceLocation, List<Object>> resolvedEntries, ResourceLocation entryId) {
        for (Map.Entry<ResourceLocation, List<Object>> cat : resolvedEntries.entrySet()) {
            for (Object entry : cat.getValue()) {
                if (entryId.equals(getEntryId(entry))) return cat.getKey();
            }
        }
        return null;
    }

    public static Set<ResourceLocation> getAllEntryIds(Map<ResourceLocation, List<Object>> resolvedEntries) {
        return resolvedEntries.values().stream()
                .flatMap(List::stream)
                .map(EntryResolver::getEntryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public static Set<ResourceLocation> getEntryIdsForCategory(Map<ResourceLocation, List<Object>> resolvedEntries, ResourceLocation categoryId) {
        List<Object> entries = resolvedEntries.get(categoryId);
        if (entries == null) return Collections.emptySet();
        return entries.stream()
                .map(EntryResolver::getEntryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public static Object getEntryForTarget(Map<ResourceLocation, List<Object>> resolvedEntries, Object target) {
        List<Object> entries = getEntriesForTarget(resolvedEntries, target);
        if (entries.isEmpty()) return null;

        for (Object entry : entries) {
            if (entry.equals(target)) return entry;
            if (entry instanceof CompositeFieldGuideEntry composite) {
                if (composite.displayEntry() != null && composite.displayEntry().equals(target)) {
                    return entry;
                }
            }
        }

        return entries.get(0);
    }

    public static List<Object> getEntriesForTarget(Map<ResourceLocation, List<Object>> resolvedEntries, Object target) {
        List<Object> matches = new ArrayList<>();
        for (List<Object> entries : resolvedEntries.values()) {
            for (Object entry : entries) {
                if (entry.equals(target) || (target instanceof ResourceLocation loc && loc.equals(getEntryId(entry)))) {
                    matches.add(entry);
                } else if (entry instanceof CompositeFieldGuideEntry composite) {
                    if ((composite.displayEntry() != null && composite.displayEntry().equals(target)) ||
                            (composite.components() != null && composite.components().contains(target))) {
                        matches.add(entry);
                    }
                }
            }
        }
        return matches;
    }

    public static boolean isTargetInEntry(Map<ResourceLocation, List<Object>> resolvedEntries, ResourceLocation targetId, ResourceLocation entryId) {
        for (List<Object> entries : resolvedEntries.values()) {
            for (Object entry : entries) {
                ResourceLocation id = getEntryId(entry);
                if (!entryId.equals(id)) continue;

                if (targetId.equals(id)) return true;

                if (entry instanceof CompositeFieldGuideEntry composite) {
                    ResourceLocation displayId = composite.displayEntry() != null ? getEntryId(composite.displayEntry()) : null;
                    if (targetId.equals(displayId)) return true;
                    if (composite.components() != null) {
                        for (Object comp : composite.components()) {
                            if (targetId.equals(getEntryId(comp))) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static boolean isValidEntity(EntityType<?> type, ResourceLocation categoryId) {
        return EntryValidator.isValidEntity(type, categoryId);
    }

    public static boolean isValidBlock(Block block, ResourceLocation categoryId) {
        return EntryValidator.isValidBlock(block, categoryId);
    }

    public static boolean isValidItem(Item item, ResourceLocation categoryId) {
        return EntryValidator.isValidItem(item, categoryId);
    }

    public static ResourceLocation getEntryId(Object obj) {
        return getEntryId(obj, true);
    }

    public static ResourceLocation getEntryId(Object obj, boolean prefixed) {
        return AutoPopulateRegistry.getEntryId(obj, prefixed);
    }

    public static ResourceLocation getRawId(ResourceLocation id) {
        if (id == null) return null;
        String ns = id.getNamespace();
        if (ns.equals("item") || ns.equals("entity") || ns.equals("block")) {
            return ResourceLocation.parse(id.getPath().replace("/", ":"));
        }
        return id;
    }

    public static List<Object> resolveCategoryEntries(Category category, List<CompositeDefinition> globalComposites, Map<ResourceLocation, ResourceLocation> redirects) {
        return EntryResolutionHelper.resolveCategoryEntries(category, globalComposites, redirects);
    }
}
