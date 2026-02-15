package com.evandev.fieldguide.server;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.server.loot.ParsedDrop;
import com.evandev.fieldguide.server.loot.StaticLootParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.*;

public class LootTableHelper {

    public static Map<ResourceLocation, List<ItemStack>> generateLootMap(ServerLevel level) {
        Constants.LOG.info("FieldGuide: Generating loot map...");
        long start = System.currentTimeMillis();

        Map<ResourceLocation, List<ItemStack>> lootMap = new HashMap<>();
        int processed = 0;

        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (processEntry(level, type, type.getDefaultLootTable(), lootMap)) processed++;
        }

        for (Block block : BuiltInRegistries.BLOCK) {
            if (processEntry(level, block, block.getLootTable(), lootMap)) processed++;
        }

        Constants.LOG.info("FieldGuide: Generated loot for {} entries in {}ms", processed, System.currentTimeMillis() - start);
        return lootMap;
    }

    private static boolean processEntry(ServerLevel level, Object entry, ResourceLocation tableId, Map<ResourceLocation, List<ItemStack>> lootMap) {
        List<ItemStack> formattedDrops = new ArrayList<>();

        if (tableId != null && !tableId.toString().equals("minecraft:empty")) {
            try {
                LootTable table = level.getServer().getLootData().getLootTable(tableId);
                List<ParsedDrop> parsedDrops = StaticLootParser.parseTable(table);

                for (ParsedDrop drop : parsedDrops) {
                    ItemStack stack = drop.stack.copy();
                    stack.getOrCreateTag().putFloat("FieldGuideDropChance", drop.chance * 100.0f);
                    formattedDrops.add(stack);
                }
            } catch (Exception e) {
                Constants.LOG.error("FieldGuide: Failed to parse loot table {}", tableId, e);
            }
        }

        applyConfigModifications(entry, formattedDrops);

        if (!formattedDrops.isEmpty()) {
            ResourceLocation id = getEntryId(entry);
            if (id != null) {
                lootMap.put(id, formattedDrops);
                return true;
            }
        }
        return false;
    }

    private static ResourceLocation getEntryId(Object entry) {
        if (entry instanceof EntityType<?> type) return BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (entry instanceof Block block) return BuiltInRegistries.BLOCK.getKey(block);
        return null;
    }

    public static void applyConfigModifications(Object entry, List<ItemStack> distinctDrops) {
        ResourceLocation entryId = getEntryId(entry);

        if (entryId != null) {
            ModConfig config = ModConfig.get();
            String idStr = entryId.toString();

            // Removals
            Set<String> itemsToRemove = new HashSet<>();
            for (String configLine : config.lootRemovals) {
                String[] parts = configLine.split("\\|");
                if (parts.length == 2 && parts[0].equals(idStr)) {
                    itemsToRemove.add(parts[1]);
                }
            }
            if (!itemsToRemove.isEmpty()) {
                distinctDrops.removeIf(stack -> {
                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    return itemsToRemove.contains(itemId.toString());
                });
            }

            // Additions
            boolean added = false;
            for (String configLine : config.lootAdditions) {
                String[] parts = configLine.split("\\|");
                if (parts.length == 2 && parts[0].equals(idStr)) {
                    try {
                        ResourceLocation itemId = new ResourceLocation(parts[1]);
                        Item item = BuiltInRegistries.ITEM.get(itemId);
                        if (item != Items.AIR) {
                            ItemStack addition = new ItemStack(item);
                            addition.getOrCreateTag().putFloat("FieldGuideDropChance", 100.0f);
                            distinctDrops.add(addition);
                            added = true;
                        }
                    } catch (Exception e) {
                        Constants.LOG.error("FieldGuide: Failed to add loot override for {}", idStr, e);
                    }
                }
            }

            if (added) {
                distinctDrops.sort(Comparator.comparing(s -> s.getHoverName().getString()));
            }
        }
    }
}