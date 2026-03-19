package com.evandev.fieldguide.util.entry;

import com.evandev.fieldguide.api.*;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.util.EntryResolver;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class EntryResolutionHelper {

    public static List<Object> resolveCategoryEntries(Category category, List<CompositeDefinition> globalComposites, Map<ResourceLocation, ResourceLocation> redirects) {
        Set<Object> foundEntries = new LinkedHashSet<>();
        Set<String> addedKeys = new HashSet<>();
        ResourceLocation categoryId = category.getId();

        for (CategoryEntry entry : category.getEntries()) {
            if (entry.categoryType() == CategoryEntry.CategoryType.ENTRY && entry.id() != null) {
                resolveSingleEntry(entry.id(), categoryId, entry.strategy()).ifPresent(e -> {
                    String key = AutoPopulateRegistry.getEntryKey(e);
                    if (entry.stackedBlocks() != null && !entry.stackedBlocks().isEmpty()) {
                        foundEntries.add(new CompositeFieldGuideEntry(entry.id(), e, new ArrayList<>(), null, entry.stackedBlocks()));
                    } else {
                        foundEntries.add(e);
                    }
                    addedKeys.add(key);
                });
            } else if (entry.categoryType() == CategoryEntry.CategoryType.COMPOSITE && entry.id() != null) {
                ResourceLocation displayLoc = entry.displayId() != null ? entry.displayId() : entry.id();
                resolveSingleEntry(displayLoc, categoryId, entry.strategy()).ifPresent(displayEntry -> {
                    List<Object> components = new ArrayList<>();
                    if (entry.components() != null) {
                        for (ResourceLocation compId : entry.components()) {
                            resolveSingleEntry(compId, categoryId, entry.strategy()).ifPresent(components::add);
                        }
                    }
                    foundEntries.add(new CompositeFieldGuideEntry(entry.id(), displayEntry, components, entry.structureNbt(), entry.stackedBlocks()));
                    addedKeys.add(AutoPopulateRegistry.getEntryKey(displayEntry));
                });
            } else if (entry.categoryType() == CategoryEntry.CategoryType.AUTO_POPULATE) {
                for (Object obj : AutoPopulateRegistry.getEntries(entry.strategy(), categoryId)) {
                    String key = AutoPopulateRegistry.getEntryKey(obj);
                    if (!key.isEmpty() && !addedKeys.contains(key)) {
                        foundEntries.add(obj);
                        addedKeys.add(key);
                    }
                }
            }
        }

        if (globalComposites != null && !globalComposites.isEmpty()) {
            List<Object> groupedEntries = new ArrayList<>();
            Set<ResourceLocation> processedComposites = new HashSet<>();

            for (Object raw : foundEntries) {
                ResourceLocation baseId = getEntryId(raw);
                if (raw instanceof CompositeFieldGuideEntry autoComposite) {
                    baseId = getEntryId(autoComposite.displayEntry());
                }

                CompositeDefinition matchingDef = findCompositeFor(baseId, globalComposites);

                if (matchingDef != null) {
                    if (processedComposites.add(matchingDef.id())) {
                        resolveCompositeDefinition(matchingDef, categoryId, raw).ifPresent(groupedEntries::add);
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

    public static Optional<Object> resolveSingleEntryWithHint(ResourceLocation id, ResourceLocation categoryId, Object hint) {
        ResourceLocation rawId = EntryResolver.getRawId(id);
        if (hint instanceof EntityType<?>) {
            return BuiltInRegistries.ENTITY_TYPE.getOptional(rawId)
                    .filter(t -> EntryValidator.isValidEntity(t, categoryId))
                    .map(Object.class::cast)
                    .or(() -> resolveSingleEntry(id, categoryId, "animals"));
        } else if (hint instanceof Item) {
            return BuiltInRegistries.ITEM.getOptional(rawId)
                    .filter(i -> EntryValidator.isValidItem(i, categoryId))
                    .map(Object.class::cast)
                    .or(() -> resolveSingleEntry(id, categoryId, "mod_items"));
        } else if (hint instanceof Block) {
            return BuiltInRegistries.BLOCK.getOptional(rawId)
                    .filter(b -> EntryValidator.isValidBlock(b, categoryId))
                    .map(Object.class::cast)
                    .or(() -> resolveSingleEntry(id, categoryId, "plants"));
        }
        return resolveSingleEntry(id, categoryId, null);
    }

    public static Optional<CompositeFieldGuideEntry> resolveCompositeDefinition(CompositeDefinition def, ResourceLocation categoryId, Object hint) {
        ResourceLocation displayLoc = def.displayId() != null ? def.displayId() : def.id();
        return resolveSingleEntryWithHint(displayLoc, categoryId, hint).map(displayEntry -> {
            List<Object> components = new ArrayList<>();
            if (def.components() != null) {
                for (ResourceLocation compId : def.components()) {
                    resolveSingleEntryWithHint(compId, categoryId, hint).ifPresent(components::add);
                }
            }
            return new CompositeFieldGuideEntry(def.id(), displayEntry, components, def.structureNbt(), def.stackedBlocks());
        });
    }

    public static Optional<Object> resolveSingleEntry(ResourceLocation id, ResourceLocation categoryId, String strategyHint) {
        if (Services.PLATFORM.isModLoaded("cobblemon") && id.getNamespace().equals("fieldguide") && id.getPath().startsWith("cobblemon/")) {
            return Optional.of(new CompositeFieldGuideEntry(id, null, new ArrayList<>(), null, null));
        }

        ResourceLocation finalId = EntryResolver.getRawId(id);
        String namespace = id.getNamespace();

        switch (namespace) {
            case "item" -> {
                return BuiltInRegistries.ITEM.getOptional(finalId)
                        .filter(i -> EntryValidator.isValidItem(i, categoryId))
                        .map(Object.class::cast);
            }
            case "entity" -> {
                return BuiltInRegistries.ENTITY_TYPE.getOptional(finalId)
                        .filter(t -> EntryValidator.isValidEntity(t, categoryId))
                        .map(Object.class::cast);
            }
            case "block" -> {
                return BuiltInRegistries.BLOCK.getOptional(finalId)
                        .filter(b -> EntryValidator.isValidBlock(b, categoryId))
                        .map(Object.class::cast);
            }
        }

        String effectiveStrategy = getEffectiveStrategy(categoryId, strategyHint);

        boolean preferEntity = effectiveStrategy != null && (effectiveStrategy.equals("animals") || effectiveStrategy.equals("monsters") || effectiveStrategy.startsWith("mod_entities"));
        boolean preferItem = effectiveStrategy != null && (effectiveStrategy.startsWith("mod_items"));
        boolean preferBlock = effectiveStrategy != null && (effectiveStrategy.equals("plants") || effectiveStrategy.equals("trees") || effectiveStrategy.startsWith("mod_plants") || effectiveStrategy.startsWith("mod_blocks"));

        if (preferEntity) {
            Optional<Object> entity = BuiltInRegistries.ENTITY_TYPE.getOptional(finalId).filter(t -> EntryValidator.isValidEntity(t, categoryId)).map(Object.class::cast);
            if (entity.isPresent()) return entity;
        }
        if (preferBlock) {
            Optional<Object> block = BuiltInRegistries.BLOCK.getOptional(finalId).filter(b -> EntryValidator.isValidBlock(b, categoryId)).map(Object.class::cast);
            if (block.isPresent()) return block;
        }
        if (preferItem) {
            Optional<Object> item = BuiltInRegistries.ITEM.getOptional(finalId).filter(i -> EntryValidator.isValidItem(i, categoryId)).map(Object.class::cast);
            if (item.isPresent()) return item;
        }

        return BuiltInRegistries.BLOCK.getOptional(finalId)
                .filter(b -> EntryValidator.isValidBlock(b, categoryId))
                .map(Object.class::cast)
                .or(() -> BuiltInRegistries.ITEM.getOptional(finalId)
                        .filter(i -> EntryValidator.isValidItem(i, categoryId))
                        .map(Object.class::cast))
                .or(() -> BuiltInRegistries.ENTITY_TYPE.getOptional(finalId)
                        .filter(t -> EntryValidator.isValidEntity(t, categoryId))
                        .map(Object.class::cast));
    }

    private static @Nullable String getEffectiveStrategy(ResourceLocation categoryId, String strategyHint) {
        String effectiveStrategy = strategyHint;
        if (effectiveStrategy == null && categoryId != null) {
            String path = categoryId.getPath();
            if (path.contains("animal") || path.contains("monster") || path.contains("entity"))
                effectiveStrategy = "animals";
            else if (path.contains("item")) effectiveStrategy = "mod_items";
            else if (path.contains("plant") || path.contains("tree") || path.contains("block"))
                effectiveStrategy = "plants";
        }
        return effectiveStrategy;
    }

    public static ResourceLocation getEntryId(Object obj) {
        return AutoPopulateRegistry.getEntryId(obj);
    }
}
