package com.evandev.fieldguide.api;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.util.ModTags;
import com.evandev.fieldguide.util.entry.EntryValidator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public class AutoPopulateRegistry {
    private static final Map<String, BiFunction<String, ResourceLocation, List<Object>>> STRATEGIES = new HashMap<>();

    static {
        register("mod_entities", (modId, categoryId) -> BuiltInRegistries.ENTITY_TYPE.stream()
                .filter(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).getNamespace().equals(modId) && EntryValidator.isValidEntity(t, categoryId))
                .sorted(Comparator.comparing(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).toString()))
                .map(Object.class::cast)
                .toList());

        // Legacy alias to prevent breaking existing datapacks
        register("mod", (modId, categoryId) -> getEntries("mod_entities:" + modId, categoryId));

        register("mod_items", (modId, categoryId) -> BuiltInRegistries.ITEM.stream()
                .filter(i -> BuiltInRegistries.ITEM.getKey(i).getNamespace().equals(modId))
                .filter(i -> !(i instanceof BlockItem)) // Prevents trees, dirt, slabs, etc. from showing up
                .filter(i -> EntryValidator.isValidItem(i, categoryId))
                .sorted(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()))
                .map(Object.class::cast)
                .toList());

        register("mod_blocks", (modId, categoryId) -> BuiltInRegistries.BLOCK.stream()
                .filter(b -> BuiltInRegistries.BLOCK.getKey(b).getNamespace().equals(modId))
                .filter(b -> EntryValidator.isValidBlock(b, categoryId))
                .sorted(Comparator.comparing(b -> BuiltInRegistries.BLOCK.getKey(b).toString()))
                .map(Object.class::cast)
                .toList());

        register("plants", (params, categoryId) -> new ArrayList<>(getPlants(id -> true, categoryId)));
        register("mod_plants", (modId, categoryId) -> new ArrayList<>(getPlants(id -> id.getNamespace().equals(modId), categoryId)));
        register("trees", (params, categoryId) -> new ArrayList<>(getAutoTrees(id -> true, categoryId)));
        register("mod_trees", (modId, categoryId) -> new ArrayList<>(getAutoTrees(id -> id.getNamespace().equals(modId), categoryId)));

        register("tag", (tagPath, categoryId) -> {
            Map<ResourceLocation, Object> results = new LinkedHashMap<>();
            try {
                ResourceLocation tagLocation = new ResourceLocation(tagPath);

                // Blocks first (highest priority)
                TagKey<Block> blockTagKey = TagKey.create(Registries.BLOCK, tagLocation);
                BuiltInRegistries.BLOCK.forEach(block -> BuiltInRegistries.BLOCK.getResourceKey(block)
                        .flatMap(BuiltInRegistries.BLOCK::getHolder)
                        .filter(h -> h.is(blockTagKey) && EntryValidator.isValidBlock(block, categoryId))
                        .ifPresent(h -> results.put(BuiltInRegistries.BLOCK.getKey(block), block)));

                // Items second
                TagKey<Item> itemTagKey = TagKey.create(Registries.ITEM, tagLocation);
                BuiltInRegistries.ITEM.forEach(item -> BuiltInRegistries.ITEM.getResourceKey(item)
                        .flatMap(BuiltInRegistries.ITEM::getHolder)
                        .filter(h -> h.is(itemTagKey) && EntryValidator.isValidItem(item, categoryId))
                        .ifPresent(h -> results.putIfAbsent(BuiltInRegistries.ITEM.getKey(item), item)));

                // Entities last
                TagKey<EntityType<?>> entityTagKey = TagKey.create(Registries.ENTITY_TYPE, tagLocation);
                BuiltInRegistries.ENTITY_TYPE.forEach(type -> BuiltInRegistries.ENTITY_TYPE.getResourceKey(type)
                        .flatMap(BuiltInRegistries.ENTITY_TYPE::getHolder)
                        .filter(h -> h.is(entityTagKey) && EntryValidator.isValidEntity(type, categoryId))
                        .ifPresent(h -> results.putIfAbsent(BuiltInRegistries.ENTITY_TYPE.getKey(type), type)));

                List<Object> sortedResults = new ArrayList<>(results.values());
                sortedResults.sort(Comparator.comparing(o -> getEntryId(o).toString()));
                return sortedResults;
            } catch (Exception e) {
                Constants.LOG.error("Invalid tag strategy: {}", tagPath, e);
            }
            return Collections.emptyList();
        });

        register("monsters", (params, categoryId) -> getEntityStrategy("monsters", categoryId));
        register("animals", (params, categoryId) -> getEntityStrategy("animals", categoryId));
    }

    private static List<Object> getEntityStrategy(String strategy, ResourceLocation categoryId) {
        TagKey<EntityType<?>> bossesTag = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Constants.MOD_ID, "bosses"));
        return BuiltInRegistries.ENTITY_TYPE.stream().filter(type -> {
            boolean isBoss = BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).flatMap(BuiltInRegistries.ENTITY_TYPE::getHolder).map(h -> h.is(bossesTag)).orElse(false);
            if ("monsters".equalsIgnoreCase(strategy)) return type.getCategory() == MobCategory.MONSTER && !isBoss;
            if ("animals".equalsIgnoreCase(strategy))
                return type.getCategory() != MobCategory.MONSTER && (type.getCategory() != MobCategory.MISC || SpawnEggItem.byId(type) != null) && !isBoss;
            return false;
        }).filter(type -> EntryValidator.isValidEntity(type, categoryId)).sorted(Comparator.comparing(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString())).map(Object.class::cast).toList();
    }

    public static void register(String strategyName, BiFunction<String, ResourceLocation, List<Object>> strategy) {
        STRATEGIES.put(strategyName.toLowerCase(), strategy);
    }

    public static List<Object> getEntries(String strategy, ResourceLocation categoryId) {
        String name = strategy;
        String params = "";
        if (strategy.contains(":")) {
            int colonIndex = strategy.indexOf(":");
            name = strategy.substring(0, colonIndex);
            params = strategy.substring(colonIndex + 1);
        }

        BiFunction<String, ResourceLocation, List<Object>> strategyFunc = STRATEGIES.get(name.toLowerCase());
        if (strategyFunc != null) {
            return strategyFunc.apply(params, categoryId);
        }
        return Collections.emptyList();
    }

    public static ResourceLocation getEntryId(Object obj) {
        if (obj instanceof ResourceLocation loc) return loc;
        if (obj instanceof EntityType<?> type) return BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (obj instanceof Block block) return BuiltInRegistries.BLOCK.getKey(block);
        if (obj instanceof Item item) return BuiltInRegistries.ITEM.getKey(item);
        if (obj instanceof CompositeFieldGuideEntry comp) return comp.id();
        return null;
    }

    public static ResourceLocation getEntryId(Object obj, boolean prefixed) {
        if (!prefixed) return getEntryId(obj);
        ResourceLocation id = getEntryId(obj);
        if (id == null) return null;

        String prefix = "unknown";
        Object coreEntry = obj instanceof CompositeFieldGuideEntry comp ? comp.displayEntry() : obj;

        if (coreEntry instanceof Block) prefix = "block";
        else if (coreEntry instanceof Item) prefix = "item";
        else if (coreEntry instanceof EntityType<?>) prefix = "entity";

        return new ResourceLocation(prefix, id.toString().replace(":", "/"));
    }

    public static String getEntryKey(Object obj) {
        ResourceLocation id = getEntryId(obj, true);
        return id != null ? id.toString() : "";
    }

    public static List<Object> getAutoTrees(Predicate<ResourceLocation> namespaceFilter, ResourceLocation categoryId) {
        List<Object> results = new ArrayList<>();

        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!namespaceFilter.test(id) || !EntryValidator.isValidBlock(block, categoryId)) continue;

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

    public static List<Object> getPlants(Predicate<ResourceLocation> namespaceFilter, ResourceLocation categoryId) {
        return BuiltInRegistries.BLOCK.stream()
                .filter(b -> namespaceFilter.test(BuiltInRegistries.BLOCK.getKey(b)))
                .filter(b -> EntryValidator.isValidBlock(b, categoryId))
                .filter(b -> b.defaultBlockState().is(ModTags.Blocks.PLANTS) || b instanceof BushBlock)
                .sorted(Comparator.comparing(b -> BuiltInRegistries.BLOCK.getKey(b).toString()))
                .map(Object.class::cast)
                .toList();
    }
}