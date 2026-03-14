package com.evandev.fieldguide.util;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.compat.cobblemon.FieldGuideCobblemonCompat;
import com.evandev.fieldguide.compat.reliableremover.ReliableRemoverCompat;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.CategoryEntry;
import com.evandev.fieldguide.data.CompositeDefinition;
import com.evandev.fieldguide.data.CompositeFieldGuideEntry;
import com.evandev.fieldguide.platform.Services;
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
                if (entry.equals(target)) {
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
        if (!type.canSummon()) return false;
        return BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).flatMap(BuiltInRegistries.ENTITY_TYPE::getHolder).map(h -> {
            if (h.is(ModTags.EntityTypes.BLACKLISTED)) return false;
            if (categoryId != null) {
                TagKey<EntityType<?>> catTag = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Constants.MOD_ID, "blacklisted/" + categoryId.getNamespace() + "/" + categoryId.getPath()));
                return !h.is(catTag);
            }
            return true;
        }).orElse(true);
    }

    public static boolean isValidBlock(Block block, ResourceLocation categoryId) {
        boolean blacklisted = BuiltInRegistries.BLOCK.getResourceKey(block).flatMap(BuiltInRegistries.BLOCK::getHolder).map(h -> {
            if (h.is(ModTags.Blocks.BLACKLISTED)) return true;
            if (categoryId != null) {
                TagKey<Block> catTag = TagKey.create(Registries.BLOCK, new ResourceLocation(Constants.MOD_ID, "blacklisted/" + categoryId.getNamespace() + "/" + categoryId.getPath()));
                return (h.is(catTag));
            }
            return false;
        }).orElse(false);

        if (blacklisted) return false;

        if (Services.PLATFORM.isModLoaded("reliable_remover") && ModConfig.get().enableReliableRemover && ReliableRemoverCompat.isHidden(block)) {
            return false;
        }
        return true;
    }

    public static ResourceLocation getEntryId(Object obj) {
        if (obj instanceof EntityType<?> type) return BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (obj instanceof Block block) return BuiltInRegistries.BLOCK.getKey(block);
        if (obj instanceof CompositeFieldGuideEntry comp) return comp.id();
        return null;
    }

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
                for (Object obj : getEntriesForStrategy(entry.strategy(), categoryId)) {
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

    private static Optional<CompositeFieldGuideEntry> resolveCompositeDefinition(CompositeDefinition def, ResourceLocation categoryId) {
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

    private static Optional<Object> resolveSingleEntry(ResourceLocation id, ResourceLocation categoryId) {
        if (Services.PLATFORM.isModLoaded("cobblemon") && id.getNamespace().equals("fieldguide") && id.getPath().startsWith("cobblemon/")) {
            return Optional.of(new CompositeFieldGuideEntry(id, null, new ArrayList<>(), null, null));
        }

        return BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                .filter(t -> isValidEntity(t, categoryId))
                .map(Object.class::cast)
                .or(() -> BuiltInRegistries.BLOCK.getOptional(id)
                        .filter(b -> isValidBlock(b, categoryId))
                        .map(Object.class::cast));
    }

    private static List<Object> getEntriesForStrategy(String strategy, ResourceLocation categoryId) {
        List<Object> results = new ArrayList<>();
        if ("plants".equalsIgnoreCase(strategy)) {
            results.addAll(getPlants(id -> true, categoryId));
        } else if (strategy.startsWith("mod:")) {
            String modId = strategy.substring(4);
            results.addAll(BuiltInRegistries.ENTITY_TYPE.stream().filter(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).getNamespace().equals(modId) && isValidEntity(t, categoryId)).sorted(Comparator.comparing(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).toString())).toList());
        } else if (strategy.startsWith("mod_plants:")) {
            String modId = strategy.substring(10);
            results.addAll(getPlants(id -> id.getNamespace().equals(modId), categoryId));
        } else if ("trees".equalsIgnoreCase(strategy)) {
            results.addAll(getAutoTrees(id -> true, categoryId));
        } else if (strategy.startsWith("mod_trees:")) {
            String modId = strategy.substring(10);
            results.addAll(getAutoTrees(id -> id.getNamespace().equals(modId), categoryId));
        } else if (strategy.startsWith("tag:")) {
            try {
                ResourceLocation tagLocation = new ResourceLocation(strategy.substring(4));

                TagKey<EntityType<?>> entityTagKey = TagKey.create(Registries.ENTITY_TYPE, tagLocation);
                BuiltInRegistries.ENTITY_TYPE.forEach(type -> BuiltInRegistries.ENTITY_TYPE.getResourceKey(type)
                        .flatMap(BuiltInRegistries.ENTITY_TYPE::getHolder)
                        .filter(h -> h.is(entityTagKey) && isValidEntity(type, categoryId))
                        .ifPresent(h -> results.add(type)));

                TagKey<Block> blockTagKey = TagKey.create(Registries.BLOCK, tagLocation);
                BuiltInRegistries.BLOCK.forEach(block -> BuiltInRegistries.BLOCK.getResourceKey(block)
                        .flatMap(BuiltInRegistries.BLOCK::getHolder)
                        .filter(h -> h.is(blockTagKey) && isValidBlock(block, categoryId))
                        .ifPresent(h -> results.add(block)));

                results.sort(Comparator.comparing(o -> getEntryId(o).toString()));
            } catch (Exception e) {
                Constants.LOG.error("Invalid tag strategy: {}", strategy, e);
            }
        } else if ("monsters".equalsIgnoreCase(strategy) || "animals".equalsIgnoreCase(strategy)) {
            TagKey<EntityType<?>> bossesTag = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Constants.MOD_ID, "bosses"));
            results.addAll(BuiltInRegistries.ENTITY_TYPE.stream().filter(type -> {
                boolean isBoss = BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).flatMap(BuiltInRegistries.ENTITY_TYPE::getHolder).map(h -> h.is(bossesTag)).orElse(false);
                if ("monsters".equalsIgnoreCase(strategy)) return type.getCategory() == MobCategory.MONSTER && !isBoss;
                if ("animals".equalsIgnoreCase(strategy))
                    return type.getCategory() != MobCategory.MONSTER && (type.getCategory() != MobCategory.MISC || SpawnEggItem.byId(type) != null) && !isBoss;
                return false;
            }).filter(type -> isValidEntity(type, categoryId)).sorted(Comparator.comparing(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString())).toList());
        }
        return results;
    }

    private static List<Object> getAutoTrees(Predicate<ResourceLocation> namespaceFilter, ResourceLocation categoryId) {
        List<Object> results = new ArrayList<>();

        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!namespaceFilter.test(id) || !isValidBlock(block, categoryId)) continue;

            if (id.getPath().endsWith("_sapling")) {
                String baseName = id.getPath().replace("_sapling", "");

                Block leaves = BuiltInRegistries.BLOCK.get(new ResourceLocation(id.getNamespace(), baseName + "_leaves"));
                if (leaves == Blocks.AIR) continue;

                Block log = BuiltInRegistries.BLOCK.get(new ResourceLocation(id.getNamespace(), baseName + "_log"));
                if (log == Blocks.AIR) {
                    String[] parts = baseName.split("_", 2);
                    if (parts.length > 1) {
                        log = BuiltInRegistries.BLOCK.get(new ResourceLocation(id.getNamespace(), parts[1] + "_log"));
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
                        new ResourceLocation(id.getNamespace(), baseName + "_tree"),
                        block,
                        components,
                        null,
                        treeStructure
                ));
            }
        }
        return results;
    }

    private static List<Object> getPlants(Predicate<ResourceLocation> namespaceFilter, ResourceLocation categoryId) {
        return BuiltInRegistries.BLOCK.stream()
                .filter(b -> namespaceFilter.test(BuiltInRegistries.BLOCK.getKey(b)))
                .filter(b -> isValidBlock(b, categoryId))
                .filter(b -> b.defaultBlockState().is(ModTags.Blocks.PLANTS) || b instanceof BushBlock)
                .sorted(Comparator.comparing(b -> BuiltInRegistries.BLOCK.getKey(b).toString()))
                .map(Object.class::cast)
                .toList();
    }
}