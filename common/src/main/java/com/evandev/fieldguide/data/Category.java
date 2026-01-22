package com.evandev.fieldguide.data;

import com.evandev.fieldguide.config.ModConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.*;

import java.util.*;

public class Category {
    final List<CategoryEntry> entries = new ArrayList<>();
    private final ResourceLocation id;
    String tabColor = "#FFFFFF";
    ResourceLocation tabIcon = new ResourceLocation("minecraft:barrier");
    int tabIndex = 0;
    private List<Object> resolvedEntries = new ArrayList<>();

    public Category(ResourceLocation id) {
        this.id = id;
    }

    public ResourceLocation getId() {
        return id;
    }

    public String getTabColor() {
        return tabColor;
    }

    public ResourceLocation getTabIcon() {
        return tabIcon;
    }

    public int getTabIndex() {
        return tabIndex;
    }

    public List<Object> getEntries() {
        return resolvedEntries;
    }

    public void resolveEntries() {
        Set<Object> foundEntries = new LinkedHashSet<>();
        ModConfig config = ModConfig.get();

        for (CategoryEntry entry : entries) {
            if (entry.type == FieldGuideDataManager.EntryType.ENTRY) {
                Optional<EntityType<?>> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entry.id);
                if (entityType.isPresent()) {
                    if (isValidEntity(entityType.get(), config)) {
                        foundEntries.add(entityType.get());
                    }
                } else {
                    Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(entry.id);
                    if (block.isPresent()) {
                        if (isValidBlock(block.get(), config)) {
                            foundEntries.add(block.get());
                        }
                    }
                }
            } else if (entry.type == FieldGuideDataManager.EntryType.AUTO_POPULATE) {
                List<Object> autoEntries = getEntriesForStrategy(entry.strategy);
                for (Object obj : autoEntries) {
                    if (obj instanceof EntityType<?> type) {
                        if (isValidEntity(type, config)) foundEntries.add(type);
                    } else if (obj instanceof Block block) {
                        if (isValidBlock(block, config)) foundEntries.add(block);
                    }
                }
            }
        }
        this.resolvedEntries = new ArrayList<>(foundEntries);
    }

    private boolean isValidEntity(EntityType<?> type, ModConfig config) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return type.canSummon() && !config.isEntityBlacklisted(id);
    }

    private boolean isValidBlock(Block block, ModConfig config) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return !config.isEntityBlacklisted(id);
    }

    private List<Object> getEntriesForStrategy(String strategy) {
        List<Object> results = new ArrayList<>();

        if ("flora".equalsIgnoreCase(strategy)) {
            results.addAll(BuiltInRegistries.BLOCK.stream()
                    .filter(block -> block instanceof BushBlock ||
                            block instanceof LeavesBlock ||
                            block instanceof VineBlock ||
                            block instanceof CactusBlock ||
                            block instanceof SugarCaneBlock ||
                            block instanceof WaterlilyBlock ||
                            block instanceof StemBlock)
                    .sorted(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString()))
                    .toList());
            return results;
        }

        results.addAll(BuiltInRegistries.ENTITY_TYPE.stream()
                .filter(type -> {
                    if ("hostile".equalsIgnoreCase(strategy)) {
                        return type.getCategory() == MobCategory.MONSTER;
                    } else if ("passive".equalsIgnoreCase(strategy)) {
                        if (type.getCategory() == MobCategory.MONSTER) {
                            return false;
                        }
                        if (type.getCategory() == MobCategory.MISC) {
                            return SpawnEggItem.byId(type) != null;
                        }
                        return true;
                    }
                    return false;
                })
                .sorted(Comparator.comparing(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()))
                .toList());

        return results;
    }
}