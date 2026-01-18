package com.evandev.fieldguide.client;

import com.evandev.fieldguide.config.ModConfig;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class MobDataManager {
    private static List<EntityType<?>> cachedEntityList = null;

    /**
     * Gets a sorted list of all valid EntityTypes that should appear in the book.
     */
    public static List<EntityType<?>> getValidEntities() {
        if (cachedEntityList != null) {
            return cachedEntityList;
        }

        cachedEntityList = BuiltInRegistries.ENTITY_TYPE.stream()
                .filter(type -> {
                    ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                    return type.canSummon() && !ModConfig.get().isEntityBlacklisted(id);
                })
                .sorted(Comparator.comparing(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()))
                .collect(Collectors.toList());

        return cachedEntityList;
    }

    /**
     * Checks if the player has unlocked this mob.
     */
    public static boolean isUnlocked(EntityType<?> type) {
        // TODO: Hook up to player data
        return true;
    }

    /**
     * Gets the description for the entity based on priority.
     * 1. fieldguide.<namespace>.<id>.description
     * 2. entity.<namespace>.<id>.description (Item Descriptions)
     */
    public static String getEntityDescription(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);

        String overrideKey = "fieldguide." + id.getNamespace() + "." + id.getPath() + ".description";
        String fallbackKey = "entity." + id.getNamespace() + "." + id.getPath() + ".description";

        if (I18n.exists(overrideKey)) {
            return I18n.get(overrideKey);
        } else if (I18n.exists(fallbackKey)) {
            return I18n.get(fallbackKey);
        }

        return I18n.get("fieldguide.description.missing");
    }

    /**
     * Clears the entity cache, forcing the list to be rebuilt
     * based on the current configuration.
     */
    public static void clearCache() {
        cachedEntityList = null;
    }
}