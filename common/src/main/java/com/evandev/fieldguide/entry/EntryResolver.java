package com.evandev.fieldguide.entry;

import com.evandev.fieldguide.api.AutoPopulateRegistry;
import com.evandev.fieldguide.api.GuideEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.*;
import java.util.stream.Collectors;

public class EntryResolver {

    public static boolean hasEntry(Map<Identifier, List<Object>> resolvedEntries, Identifier entryId) {
        for (List<Object> entries : resolvedEntries.values()) {
            for (Object entry : entries) {
                if (entryId.equals(getEntryId(entry))) return true;
            }
        }
        return false;
    }

    public static Identifier getCategoryForEntryId(Map<Identifier, List<Object>> resolvedEntries, Identifier entryId) {
        for (Map.Entry<Identifier, List<Object>> cat : resolvedEntries.entrySet()) {
            for (Object entry : cat.getValue()) {
                if (entryId.equals(getEntryId(entry))) return cat.getKey();
            }
        }
        return null;
    }

    public static Set<Identifier> getAllEntryIds(Map<Identifier, List<Object>> resolvedEntries) {
        return resolvedEntries.values().stream()
                .flatMap(List::stream)
                .map(EntryResolver::getEntryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public static Set<Identifier> getEntryIdsForCategory(Map<Identifier, List<Object>> resolvedEntries, Identifier categoryId) {
        List<Object> entries = resolvedEntries.get(categoryId);
        if (entries == null) return Collections.emptySet();
        return entries.stream()
                .map(EntryResolver::getEntryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    public static Object getEntryForTarget(Map<Identifier, List<Object>> resolvedEntries, Object target) {
        List<Object> entries = getEntriesForTarget(resolvedEntries, target);
        if (entries.isEmpty()) return null;

        Identifier rawTargetId = target instanceof Identifier loc ? getRawId(loc) : getEntryId(target, false);

        for (Object entry : entries) {
            if (entry.equals(target)) return entry;
            if (entry instanceof GuideEntry guideEntry && guideEntry.isComposite()) {
                if (guideEntry.displayId() != null && guideEntry.displayId().equals(rawTargetId)) {
                    return entry;
                }
            }
        }

        return entries.get(0);
    }

    public static List<Object> getEntriesForTarget(Map<Identifier, List<Object>> resolvedEntries, Object target) {
        List<Object> matches = new ArrayList<>();
        Identifier prefixedTargetId = target instanceof Identifier loc ? loc : getEntryId(target, true);
        Identifier rawTargetId = target instanceof Identifier loc ? getRawId(loc) : getEntryId(target, false);

        for (List<Object> entries : resolvedEntries.values()) {
            for (Object entry : entries) {
                if (entry.equals(target) || prefixedTargetId.equals(getEntryId(entry, true))) {
                    matches.add(entry);
                } else if (entry instanceof GuideEntry guideEntry && guideEntry.isComposite()) {
                    if ((guideEntry.displayId() != null && guideEntry.displayId().equals(rawTargetId)) ||
                            (guideEntry.childEntries() != null && guideEntry.childEntries().contains(rawTargetId))) {
                        matches.add(entry);
                    }
                }
            }
        }
        return matches;
    }

    public static boolean isTargetInEntry(Map<Identifier, List<Object>> resolvedEntries, Identifier targetId, Identifier entryId) {
        Identifier rawTargetId = getRawId(targetId);

        for (List<Object> entries : resolvedEntries.values()) {
            for (Object entry : entries) {
                Identifier id = getEntryId(entry, true);
                if (!entryId.equals(id)) continue;

                if (targetId.equals(id) || rawTargetId.equals(id)) return true;

                if (entry instanceof GuideEntry guideEntry && guideEntry.isComposite()) {
                    if (rawTargetId.equals(guideEntry.displayId())) return true;
                    if (guideEntry.childEntries() != null && guideEntry.childEntries().contains(rawTargetId)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isValidEntity(EntityType<?> type, Identifier categoryId) {
        return EntryValidator.isValidEntity(type, categoryId);
    }

    public static boolean isValidBlock(Block block, Identifier categoryId) {
        return EntryValidator.isValidBlock(block, categoryId);
    }

    public static boolean isValidItem(Item item, Identifier categoryId) {
        return EntryValidator.isValidItem(item, categoryId);
    }

    public static Identifier getEntryId(Object obj) {
        return getEntryId(obj, true);
    }

    public static Identifier getEntryId(Object obj, boolean prefixed) {
        return AutoPopulateRegistry.getEntryId(obj, prefixed);
    }

    public static Identifier getRawId(Identifier id) {
        if (id == null) return null;
        String ns = id.getNamespace();
        if (ns.equals("item") || ns.equals("entity") || ns.equals("block")) {
            return Identifier.parse(id.getPath().replaceFirst("/", ":"));
        }
        return id;
    }

    public static Object resolveCoreEntry(Object entry) {
        if (entry instanceof GuideEntry ge && ge.displayId() != null) {
            return BuiltInRegistries.BLOCK.getOptional(ge.displayId())
                    .map(Object.class::cast)
                    .or(() -> BuiltInRegistries.ITEM.getOptional(ge.displayId()))
                    .or(() -> BuiltInRegistries.ENTITY_TYPE.getOptional(ge.displayId()))
                    .orElse(entry);
        }
        return entry;
    }
}