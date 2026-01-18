package com.evandev.fieldguide.data;

import com.evandev.fieldguide.config.ModConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.SpawnEggItem;

import java.util.*;
import java.util.stream.Collectors;

public class Category {
    final List<CategoryEntry> entries = new ArrayList<>();
    private final ResourceLocation id;
    String tabColor = "#FFFFFF";
    ResourceLocation tabIcon = new ResourceLocation("minecraft:barrier");
    int tabIndex = 0;
    private List<EntityType<?>> resolvedEntities = new ArrayList<>();

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

    public List<EntityType<?>> getEntities() {
        return resolvedEntities;
    }

    public void resolveEntities() {
        Set<EntityType<?>> foundEntities = new LinkedHashSet<>();
        ModConfig config = ModConfig.get();

        for (CategoryEntry entry : entries) {
            if (entry.type == FieldGuideDataManager.EntryType.ENTRY) {
                BuiltInRegistries.ENTITY_TYPE.getOptional(entry.id).ifPresent(type -> {
                    if (isValid(type, config)) {
                        foundEntities.add(type);
                    }
                });
            } else if (entry.type == FieldGuideDataManager.EntryType.AUTO_POPULATE) {
                List<EntityType<?>> autoEntities = getEntitiesForStrategy(entry.strategy);
                for (EntityType<?> type : autoEntities) {
                    if (isValid(type, config)) {
                        foundEntities.add(type);
                    }
                }
            }
        }
        this.resolvedEntities = new ArrayList<>(foundEntities);
    }

    private boolean isValid(EntityType<?> type, ModConfig config) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return type.canSummon() && !config.isEntityBlacklisted(id);
    }

    private List<EntityType<?>> getEntitiesForStrategy(String strategy) {

        return BuiltInRegistries.ENTITY_TYPE.stream()
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
                .collect(Collectors.toList());
    }
}