package com.evandev.fieldguide.util;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.CategoryEntry;
import com.evandev.fieldguide.data.CompositeDefinition;
import com.evandev.fieldguide.data.CompositeFieldGuideEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;

import java.util.*;
import java.util.function.Predicate;

public class EntryResolver {

    public static boolean isValidEntity(EntityType<?> type, ModConfig config) {
        return type.canSummon() && !config.isEntityBlacklisted(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    public static boolean isValidBlock(Block block, ModConfig config) {
        return !config.isEntityBlacklisted(BuiltInRegistries.BLOCK.getKey(block));
    }

    public static ResourceLocation getEntryId(Object obj) {
        if (obj instanceof EntityType<?> type) return BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (obj instanceof Block block) return BuiltInRegistries.BLOCK.getKey(block);
        if (obj instanceof CompositeFieldGuideEntry comp) return comp.id();
        return null;
    }

    public static List<Object> resolveCategoryEntries(Category category, ModConfig config, List<CompositeDefinition> globalComposites, Map<ResourceLocation, ResourceLocation> redirects) {
        Set<Object> foundEntries = new LinkedHashSet<>();
        Set<ResourceLocation> addedIds = new HashSet<>();

        for (CategoryEntry entry : category.getEntries()) {
            if (entry.type() == CategoryEntry.Type.ENTRY && entry.id() != null) {
                resolveSingleEntry(entry.id(), config).ifPresent(e -> {
                    if (entry.stackedBlocks() != null && !entry.stackedBlocks().isEmpty()) {
                        foundEntries.add(new CompositeFieldGuideEntry(entry.id(), e, new ArrayList<>(), null, entry.stackedBlocks()));
                    } else {
                        foundEntries.add(e);
                    }
                    addedIds.add(entry.id());
                });
            } else if (entry.type() == CategoryEntry.Type.COMPOSITE && entry.id() != null) {
                ResourceLocation displayLoc = entry.displayId() != null ? entry.displayId() : entry.id();
                resolveSingleEntry(displayLoc, config).ifPresent(displayEntry -> {
                    List<Object> components = new ArrayList<>();
                    if (entry.components() != null) {
                        for (ResourceLocation compId : entry.components()) {
                            resolveSingleEntry(compId, config).ifPresent(components::add);
                        }
                    }
                    foundEntries.add(new CompositeFieldGuideEntry(entry.id(), displayEntry, components, entry.structureNbt(), entry.stackedBlocks()));
                    addedIds.add(entry.id());
                });
            } else if (entry.type() == CategoryEntry.Type.AUTO_POPULATE) {
                for (Object obj : getEntriesForStrategy(entry.strategy(), config)) {
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
                            resolveCompositeDefinition(matchingDef, config).ifPresent(groupedEntries::add);
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
                        resolveCompositeDefinition(matchingDef, config).ifPresent(groupedEntries::add);
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

    private static Optional<CompositeFieldGuideEntry> resolveCompositeDefinition(CompositeDefinition def, ModConfig config) {
        ResourceLocation displayLoc = def.displayId() != null ? def.displayId() : def.id();
        return resolveSingleEntry(displayLoc, config).map(displayEntry -> {
            List<Object> components = new ArrayList<>();
            if (def.components() != null) {
                for (ResourceLocation compId : def.components()) {
                    resolveSingleEntry(compId, config).ifPresent(components::add);
                }
            }
            return new CompositeFieldGuideEntry(def.id(), displayEntry, components, def.structureNbt(), def.stackedBlocks());
        });
    }

    private static Optional<Object> resolveSingleEntry(ResourceLocation id, ModConfig config) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                .filter(t -> isValidEntity(t, config))
                .map(Object.class::cast)
                .or(() -> BuiltInRegistries.BLOCK.getOptional(id)
                        .filter(b -> isValidBlock(b, config))
                        .map(Object.class::cast));
    }

    private static List<Object> getEntriesForStrategy(String strategy, ModConfig config) {
        List<Object> results = new ArrayList<>();
        if ("plants".equalsIgnoreCase(strategy)) {
            results.addAll(getPlants(id -> true, config));
        } else if (strategy.startsWith("mod:")) {
            String modId = strategy.substring(4);
            results.addAll(BuiltInRegistries.ENTITY_TYPE.stream().filter(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).getNamespace().equals(modId) && isValidEntity(t, config)).sorted(Comparator.comparing(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).toString())).toList());
        } else if (strategy.startsWith("mod_plants:")) {
            String modId = strategy.substring(10);
            results.addAll(getPlants(id -> id.getNamespace().equals(modId), config));
        } else if ("trees".equalsIgnoreCase(strategy)) {
            results.addAll(getAutoTrees(id -> true, config));
        } else if (strategy.startsWith("mod_trees:")) {
            String modId = strategy.substring(10);
            results.addAll(getAutoTrees(id -> id.getNamespace().equals(modId), config));
        } else if (strategy.startsWith("tag:")) {
            try {
                ResourceLocation tagLocation = ResourceLocation.parse(strategy.substring(4));

                TagKey<EntityType<?>> entityTagKey = TagKey.create(Registries.ENTITY_TYPE, tagLocation);
                BuiltInRegistries.ENTITY_TYPE.forEach(type -> BuiltInRegistries.ENTITY_TYPE.getResourceKey(type)
                        .flatMap(BuiltInRegistries.ENTITY_TYPE::getHolder)
                        .filter(h -> h.is(entityTagKey) && isValidEntity(type, config))
                        .ifPresent(h -> results.add(type)));

                TagKey<Block> blockTagKey = TagKey.create(Registries.BLOCK, tagLocation);
                BuiltInRegistries.BLOCK.forEach(block -> BuiltInRegistries.BLOCK.getResourceKey(block)
                        .flatMap(BuiltInRegistries.BLOCK::getHolder)
                        .filter(h -> h.is(blockTagKey) && isValidBlock(block, config))
                        .ifPresent(h -> results.add(block)));

                results.sort(Comparator.comparing(o -> getEntryId(o).toString()));
            } catch (Exception e) {
                Constants.LOG.error("Invalid tag strategy: {}", strategy, e);
            }
        } else if ("monsters".equalsIgnoreCase(strategy) || "animals".equalsIgnoreCase(strategy)) {
            TagKey<EntityType<?>> bossesTag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "bosses"));
            results.addAll(BuiltInRegistries.ENTITY_TYPE.stream().filter(type -> {
                boolean isBoss = BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).flatMap(BuiltInRegistries.ENTITY_TYPE::getHolder).map(h -> h.is(bossesTag)).orElse(false);
                if ("monsters".equalsIgnoreCase(strategy)) return type.getCategory() == MobCategory.MONSTER && !isBoss;
                if ("animals".equalsIgnoreCase(strategy))
                    return type.getCategory() != MobCategory.MONSTER && (type.getCategory() != MobCategory.MISC || SpawnEggItem.byId(type) != null) && !isBoss;
                return false;
            }).filter(type -> isValidEntity(type, config)).sorted(Comparator.comparing(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString())).toList());
        }
        return results;
    }

    private static List<Object> getAutoTrees(Predicate<ResourceLocation> namespaceFilter, ModConfig config) {
        List<Object> results = new ArrayList<>();

        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!namespaceFilter.test(id) || !isValidBlock(block, config)) continue;

            if (id.getPath().endsWith("_sapling")) {
                String baseName = id.getPath().replace("_sapling", "");

                Block leaves = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), baseName + "_leaves"));
                if (leaves == Blocks.AIR) continue;

                Block log = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), baseName + "_log"));
                if (log == Blocks.AIR) {
                    String[] parts = baseName.split("_", 2);
                    if (parts.length > 1) {
                        log = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), parts[1] + "_log"));
                    }
                }
                if (log == Blocks.AIR) continue;

                String logId = BuiltInRegistries.BLOCK.getKey(log).toString();
                String leavesId = BuiltInRegistries.BLOCK.getKey(leaves).toString();

                List<String> treeStructure = Arrays.asList(
                        "0,0,0|" + logId,
                        "0,1,0|" + logId,
                        "-1,2,0|" + leavesId,
                        "1,2,0|" + leavesId,
                        "0,2,-1|" + leavesId,
                        "0,2,1|" + leavesId,
                        "0,2,0|" + leavesId,
                        "0,3,0|" + leavesId
                );

                List<Object> components = Arrays.asList(block, leaves, log);

                results.add(new CompositeFieldGuideEntry(
                        ResourceLocation.fromNamespaceAndPath(id.getNamespace(), baseName + "_tree"),
                        block,
                        components,
                        null,
                        treeStructure
                ));
            }
        }
        return results;
    }

    private static List<Object> getPlants(Predicate<ResourceLocation> namespaceFilter, ModConfig config) {
        return BuiltInRegistries.BLOCK.stream()
                .filter(b -> namespaceFilter.test(BuiltInRegistries.BLOCK.getKey(b)))
                .filter(b -> isValidBlock(b, config))
                .filter(b -> b.defaultBlockState().is(ModTags.Blocks.PLANTS) || b instanceof BushBlock)
                .sorted(Comparator.comparing(b -> BuiltInRegistries.BLOCK.getKey(b).toString()))
                .map(Object.class::cast)
                .toList();
    }
}