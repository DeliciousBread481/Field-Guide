package com.evandev.fieldguide.server;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.server.loot.ParsedDrop;
import com.evandev.fieldguide.server.loot.StaticLootParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
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

    private static boolean matchesTarget(Object entry, String targetStr) {
        ResourceLocation entryId = getEntryId(entry);
        if (entryId == null) return false;

        if (targetStr.startsWith("#")) {
            try {
                ResourceLocation tagId = new ResourceLocation(targetStr.substring(1));
                if (entry instanceof EntityType<?> type) {
                    TagKey<EntityType<?>> tagKey = TagKey.create(Registries.ENTITY_TYPE, tagId);
                    var key = BuiltInRegistries.ENTITY_TYPE.getResourceKey(type);
                    if (key.isPresent()) {
                        var holder = BuiltInRegistries.ENTITY_TYPE.getHolder(key.get());
                        return holder.isPresent() && holder.get().is(tagKey);
                    }
                } else if (entry instanceof Block block) {
                    TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, tagId);
                    var key = BuiltInRegistries.BLOCK.getResourceKey(block);
                    if (key.isPresent()) {
                        var holder = BuiltInRegistries.BLOCK.getHolder(key.get());
                        return holder.isPresent() && holder.get().is(tagKey);
                    }
                }
            } catch (Exception e) {
                Constants.LOG.error("FieldGuide: Failed to parse tag {}", targetStr, e);
            }
            return false;
        } else {
            return entryId.toString().equals(targetStr);
        }
    }

    public static void applyConfigModifications(Object entry, List<ItemStack> distinctDrops) {
        ResourceLocation entryId = getEntryId(entry);

        if (entryId != null) {
            ModConfig config = ModConfig.get();

            // Removals
            Set<String> itemsToRemove = new HashSet<>();
            for (String configLine : config.lootRemovals) {
                String[] parts = configLine.split("\\|");
                if (parts.length == 2 && matchesTarget(entry, parts[0])) {
                    itemsToRemove.add(parts[1]);
                }
            }
            if (!itemsToRemove.isEmpty()) {
                distinctDrops.removeIf(stack -> {
                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    for (String target : itemsToRemove) {
                        if (target.startsWith("#")) {
                            try {
                                ResourceLocation tagId = new ResourceLocation(target.substring(1));
                                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                                if (stack.is(tagKey)) return true;
                            } catch (Exception ignored) {
                            }
                        } else {
                            if (itemId.toString().equals(target)) return true;
                        }
                    }
                    return false;
                });
            }

            // Additions
            boolean added = false;
            for (String configLine : config.lootAdditions) {
                String[] parts = configLine.split("\\|");
                if (parts.length == 2 && matchesTarget(entry, parts[0])) {
                    String itemTarget = parts[1];
                    if (itemTarget.startsWith("#")) {
                        try {
                            ResourceLocation tagId = new ResourceLocation(itemTarget.substring(1));
                            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                            var items = BuiltInRegistries.ITEM.getTagOrEmpty(tagKey);
                            for (var holder : items) {
                                Item item = holder.value();
                                if (item != Items.AIR) {
                                    ItemStack addition = new ItemStack(item);
                                    addition.getOrCreateTag().putFloat("FieldGuideDropChance", 100.0f);
                                    distinctDrops.add(addition);
                                    added = true;
                                }
                            }
                        } catch (Exception e) {
                            Constants.LOG.error("FieldGuide: Failed to add loot tag override for {}", itemTarget, e);
                        }
                    } else {
                        try {
                            ResourceLocation itemId = new ResourceLocation(itemTarget);
                            Item item = BuiltInRegistries.ITEM.get(itemId);
                            if (item != Items.AIR) {
                                ItemStack addition = new ItemStack(item);
                                addition.getOrCreateTag().putFloat("FieldGuideDropChance", 100.0f);
                                distinctDrops.add(addition);
                                added = true;
                            }
                        } catch (Exception e) {
                            Constants.LOG.error("FieldGuide: Failed to add loot override for {}", itemTarget, e);
                        }
                    }
                }
            }

            if (added) {
                distinctDrops.sort(Comparator.comparing(s -> s.getHoverName().getString()));
            }
        }
    }
}