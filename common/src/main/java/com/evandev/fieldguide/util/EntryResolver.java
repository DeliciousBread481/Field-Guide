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
import net.minecraft.world.level.block.Block;

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
                    ResourceLocation id = null;
                    if (obj instanceof Block b) id = BuiltInRegistries.BLOCK.getKey(b);
                    else if (obj instanceof EntityType<?> e) id = BuiltInRegistries.ENTITY_TYPE.getKey(e);
                    else if (obj instanceof CompositeFieldGuideEntry c) id = c.id();

                    if (id != null && !addedIds.contains(id)) {
                        foundEntries.add(obj);
                        addedIds.add(id);
                    }
                }
            }
        }
        List<Object> resolved = new ArrayList<>(foundEntries);
        resolved.removeIf(e -> {
            ResourceLocation id = null;
            if (e instanceof EntityType<?> type) id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            else if (e instanceof Block block) id = BuiltInRegistries.BLOCK.getKey(block);
            else if (e instanceof CompositeFieldGuideEntry comp) id = comp.id();
            return id != null && config.getRedirect(id) != null;
        });

        return resolved;
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
        return BuiltInRegistries.BLOCK.stream()
                .filter(b -> namespaceFilter.test(BuiltInRegistries.BLOCK.getKey(b)))
                .filter(b -> isValidBlock(b, config))
                .filter(b -> b.defaultBlockState().is(ModTags.Blocks.PLANTS))
                .sorted(Comparator.comparing(b -> BuiltInRegistries.BLOCK.getKey(b).toString()))
                .map(Object.class::cast)
                .toList();
    }

}