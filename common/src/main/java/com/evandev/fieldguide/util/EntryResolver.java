package com.evandev.fieldguide.util;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.CategoryEntry;
import com.evandev.fieldguide.data.CompositeFieldGuideEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.*;

import java.util.*;
import java.util.function.Predicate;

public class EntryResolver {

    public static boolean isValidEntity(EntityType<?> type, ModConfig config) {
        return type.canSummon() && !config.isEntityBlacklisted(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    public static boolean isValidBlock(Block block, ModConfig config) {
        return !config.isEntityBlacklisted(BuiltInRegistries.BLOCK.getKey(block));
    }

    public static List<Object> resolveCategoryEntries(Category category, ModConfig config) {
        Set<Object> foundEntries = new LinkedHashSet<>();

        for (CategoryEntry entry : category.getEntries()) {
            if (entry.type() == CategoryEntry.Type.ENTRY && entry.id() != null) {
                BuiltInRegistries.ENTITY_TYPE.getOptional(entry.id()).filter(t -> isValidEntity(t, config)).ifPresent(foundEntries::add);
                BuiltInRegistries.BLOCK.getOptional(entry.id()).filter(b -> isValidBlock(b, config)).ifPresent(foundEntries::add);
            } else if (entry.type() == CategoryEntry.Type.AUTO_POPULATE) {
                foundEntries.addAll(getEntriesForStrategy(entry.strategy(), config));
            } else if (entry.type() == CategoryEntry.Type.COMPOSITE) {
                if (entry.id() == null) continue;
                List<Object> components = new ArrayList<>();
                Object displayEntry = null;

                Optional<EntityType<?>> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entry.id());
                if (entityType.isPresent() && isValidEntity(entityType.get(), config)) {
                    displayEntry = entityType.get();
                } else {
                    Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(entry.id());
                    if (block.isPresent() && isValidBlock(block.get(), config)) {
                        displayEntry = block.get();
                    }
                }

                if (entry.components() != null) {
                    for (ResourceLocation compId : entry.components()) {
                        Optional<EntityType<?>> compEntity = BuiltInRegistries.ENTITY_TYPE.getOptional(compId);
                        if (compEntity.isPresent() && isValidEntity(compEntity.get(), config)) {
                            components.add(compEntity.get());
                        } else {
                            Optional<Block> compBlock = BuiltInRegistries.BLOCK.getOptional(compId);
                            if (compBlock.isPresent() && isValidBlock(compBlock.get(), config)) {
                                components.add(compBlock.get());
                            }
                        }
                    }
                }

                if (displayEntry != null) {
                    foundEntries.add(new CompositeFieldGuideEntry(entry.id(), displayEntry, components, entry.structureNbt()));
                }
            }
        }
        return new ArrayList<>(foundEntries);
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
        } else if (strategy.startsWith("tag:")) {
            try {
                TagKey<EntityType<?>> tagKey = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(strategy.substring(4)));
                BuiltInRegistries.ENTITY_TYPE.forEach(type -> BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).flatMap(BuiltInRegistries.ENTITY_TYPE::getHolder).filter(h -> h.is(tagKey) && isValidEntity(type, config)).ifPresent(h -> results.add(type)));
                results.sort(Comparator.comparing(o -> BuiltInRegistries.ENTITY_TYPE.getKey((EntityType<?>) o).toString()));
            } catch (Exception e) {
                Constants.LOG.error("Invalid tag strategy: {}", strategy, e);
            }
        } else if ("monsters".equalsIgnoreCase(strategy) || "animals".equalsIgnoreCase(strategy)) {
            TagKey<EntityType<?>> bossesTag = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("fieldguide", "bosses"));
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

    private static List<Object> getPlants(Predicate<ResourceLocation> namespaceFilter, ModConfig config) {
        List<Object> results = new ArrayList<>();
        Map<String, Block> saplings = new HashMap<>();
        Map<String, List<Block>> treeComponents = new HashMap<>();
        List<Block> loosePlants = new ArrayList<>();

        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!namespaceFilter.test(id) || !isValidBlock(block, config)) continue;

            String path = id.getPath();
            if (path.endsWith("_sapling") && !path.startsWith("potted_")) {
                String prefix = path.substring(0, path.length() - "_sapling".length());
                String key = id.getNamespace() + ":" + prefix;
                saplings.put(key, block);
                treeComponents.put(key, new ArrayList<>());
            } else if (path.endsWith("_fungus") && !path.startsWith("potted_")) {
                String prefix = path.substring(0, path.length() - "_fungus".length());
                String key = id.getNamespace() + ":" + prefix;
                saplings.put(key, block);
                treeComponents.put(key, new ArrayList<>());
            } else if (path.endsWith("_propagule") && !path.startsWith("potted_")) {
                String prefix = path.substring(0, path.length() - "_propagule".length());
                String key = id.getNamespace() + ":" + prefix;
                saplings.put(key, block);
                treeComponents.put(key, new ArrayList<>());
            } else if (path.equals("brown_mushroom") || path.equals("red_mushroom")) {
                String key = id.getNamespace() + ":" + path;
                saplings.put(key, block);
                treeComponents.put(key, new ArrayList<>());
            }
        }

        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!namespaceFilter.test(id) || !isValidBlock(block, config)) continue;
            String path = id.getPath();

            boolean addedToTree = false;
            for (String key : saplings.keySet()) {
                String[] parts = key.split(":");
                String namespace = parts[0];
                String prefix = parts[1];

                if (id.getNamespace().equals(namespace)) {
                    if (path.equals(prefix + "_log") || path.equals(prefix + "_leaves") ||
                            path.equals("stripped_" + prefix + "_log") || path.equals(prefix + "_wood") ||
                            path.equals("stripped_" + prefix + "_wood") ||
                            path.equals(prefix + "_stem") || path.equals("stripped_" + prefix + "_stem") ||
                            path.equals(prefix + "_hyphae") || path.equals("stripped_" + prefix + "_hyphae") ||
                            path.equals(prefix + "_roots") || path.equals("muddy_" + prefix + "_roots")) {
                        treeComponents.get(key).add(block);
                        addedToTree = true;
                        break;
                    }
                    if (prefix.equals("brown_mushroom") && (path.equals("brown_mushroom_block") || path.equals("mushroom_stem"))) {
                        treeComponents.get(key).add(block);
                        addedToTree = true;
                    }
                    if (prefix.equals("red_mushroom") && (path.equals("red_mushroom_block") || path.equals("mushroom_stem"))) {
                        treeComponents.get(key).add(block);
                        addedToTree = true;
                    }
                }
            }

            if (!addedToTree && !path.endsWith("_sapling") && !path.endsWith("_fungus") && !path.endsWith("_propagule")) {
                if (isPlant(block)) loosePlants.add(block);
            }
        }

        for (Map.Entry<String, Block> entry : saplings.entrySet()) {
            ResourceLocation saplingId = BuiltInRegistries.BLOCK.getKey(entry.getValue());
            List<Block> components = treeComponents.get(entry.getKey());
            if (!components.isEmpty()) {
                results.add(new CompositeFieldGuideEntry(saplingId, entry.getValue(), new ArrayList<>(components), null));
            } else {
                results.add(entry.getValue());
            }
        }

        results.addAll(loosePlants);
        results.sort(Comparator.comparing(p -> {
            if (p instanceof CompositeFieldGuideEntry composite) return composite.id().toString();
            if (p instanceof Block b) return BuiltInRegistries.BLOCK.getKey(b).toString();
            return p.toString();
        }));

        return results;
    }

    private static boolean isPlant(Block block) {
        return (block instanceof BushBlock && !(block instanceof StemBlock) && !(block instanceof AttachedStemBlock)) || block instanceof LeavesBlock || block instanceof VineBlock || block instanceof CactusBlock || block instanceof SugarCaneBlock || block instanceof WaterlilyBlock || block instanceof StemGrownBlock || block instanceof CoralFanBlock || block instanceof CoralPlantBlock || block instanceof KelpBlock || block instanceof KelpPlantBlock;
    }
}