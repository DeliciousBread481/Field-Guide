package com.evandev.fieldguide.server;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.server.loot.ParsedDrop;
import com.evandev.fieldguide.server.loot.StaticLootParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.loot.LootTable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LootTableHelper {

    private static final Map<Object, List<ItemStack>> SERVER_DROP_CACHE = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static File getCacheFile(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.GENERATED_DIR)
                .resolve("fieldguide").resolve("loot_cache.json").toFile();
    }

    public static boolean containsEntry(Object entry) {
        return SERVER_DROP_CACHE.containsKey(entry);
    }

    public static void clearCache() {
        SERVER_DROP_CACHE.clear();
    }

    public static void generateAll(ServerLevel level, List<Object> entries) {
        Constants.LOG.info("FieldGuide: Generating loot cache for {} entries...", entries.size());
        long start = System.currentTimeMillis();

        File cacheFile = getCacheFile(level);
        int processed = 0;

        for (Object entry : entries) {
            ResourceLocation tableId = null;

            if (entry instanceof EntityType<?> type) {
                tableId = type.getDefaultLootTable();
            } else if (entry instanceof Block block) {
                tableId = block.getLootTable();
            }

            if (tableId != null && !tableId.toString().equals("minecraft:empty")) {
                try {
                    LootTable table = level.getServer().getLootData().getLootTable(tableId);
                    List<ParsedDrop> parsedDrops = StaticLootParser.parseTable(table);

                    List<ItemStack> formattedDrops = new ArrayList<>();
                    for (ParsedDrop drop : parsedDrops) {
                        ItemStack stack = drop.stack.copy();
                        stack.getOrCreateTag().putFloat("FieldGuideDropChance", drop.chance * 100.0f);
                        formattedDrops.add(stack);
                    }

                    applyConfigModifications(entry, formattedDrops);
                    SERVER_DROP_CACHE.put(entry, formattedDrops);
                    processed++;
                } catch (Exception e) {
                    Constants.LOG.error("FieldGuide: Failed to parse loot table {}", tableId, e);
                }
            }
        }

        saveCacheToDisk(cacheFile);
        Constants.LOG.info("FieldGuide: Generated loot for {} entries in {}ms", processed, System.currentTimeMillis() - start);
    }

    public static Map<ResourceLocation, List<ItemStack>> getCacheAsMap() {
        Map<ResourceLocation, List<ItemStack>> map = new HashMap<>();
        for (Map.Entry<Object, List<ItemStack>> entry : SERVER_DROP_CACHE.entrySet()) {
            ResourceLocation id = getEntryId(entry.getKey());
            if (id != null && !entry.getValue().isEmpty()) {
                map.put(id, entry.getValue());
            }
        }
        return map;
    }

    private static void saveCacheToDisk(File file) {
        try {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();

            JsonObject root = new JsonObject();
            for (Map.Entry<Object, List<ItemStack>> entry : SERVER_DROP_CACHE.entrySet()) {
                ResourceLocation id = getEntryId(entry.getKey());
                if (id == null) continue;

                JsonArray dropsArray = new JsonArray();
                for (ItemStack stack : entry.getValue()) {
                    JsonObject itemObj = new JsonObject();
                    itemObj.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    itemObj.addProperty("count", stack.getCount());
                    if (stack.hasTag()) {
                        itemObj.addProperty("nbt", Objects.requireNonNull(stack.getTag()).toString());
                    }
                    dropsArray.add(itemObj);
                }
                root.add(id.toString(), dropsArray);
            }

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            Constants.LOG.error("FieldGuide: Failed to save loot cache", e);
        }
    }

    public static void tryLoadCache(ServerLevel level) {
        SERVER_DROP_CACHE.clear();
        File file = getCacheFile(level);
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            SERVER_DROP_CACHE.clear();

            for (String key : root.keySet()) {
                ResourceLocation id = new ResourceLocation(key);
                Object entry = resolveEntry(id);
                if (entry == null) continue;

                List<ItemStack> items = new ArrayList<>();
                JsonArray dropsArray = root.getAsJsonArray(key);

                for (var el : dropsArray) {
                    JsonObject obj = el.getAsJsonObject();
                    Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(obj.get("item").getAsString()));
                    if (item == Items.AIR) continue;

                    ItemStack stack = new ItemStack(item);
                    if (obj.has("count")) stack.setCount(obj.get("count").getAsInt());
                    if (obj.has("nbt")) {
                        stack.setTag(TagParser.parseTag(obj.get("nbt").getAsString()));
                    }
                    items.add(stack);
                }
                SERVER_DROP_CACHE.put(entry, items);
            }
            Constants.LOG.info("FieldGuide: Loaded {} entries from cache.", SERVER_DROP_CACHE.size());
        } catch (Exception e) {
            Constants.LOG.error("FieldGuide: Failed to load loot cache", e);
        }
    }

    private static ResourceLocation getEntryId(Object entry) {
        if (entry instanceof EntityType<?> type) return BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (entry instanceof Block block) return BuiltInRegistries.BLOCK.getKey(block);
        return null;
    }

    private static Object resolveEntry(ResourceLocation id) {
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return BuiltInRegistries.ENTITY_TYPE.get(id);
        if (BuiltInRegistries.BLOCK.containsKey(id)) return BuiltInRegistries.BLOCK.get(id);
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
                    ResourceLocation itemId = new ResourceLocation(parts[1]);
                    Item item = BuiltInRegistries.ITEM.get(itemId);
                    if (item != Items.AIR) {
                        ItemStack addition = new ItemStack(item);
                        addition.getOrCreateTag().putFloat("FieldGuideDropChance", 100.0f);
                        distinctDrops.add(addition);
                        added = true;
                    }
                }
            }

            if (added) {
                distinctDrops.sort(Comparator.comparing(s -> s.getHoverName().getString()));
            }
        }
    }
}