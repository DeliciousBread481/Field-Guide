package com.evandev.fieldguide.server;

import com.evandev.fieldguide.Constants;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.io.Reader;
import java.util.*;

public class LootTableHelper {
    private static final Gson GSON = new GsonBuilder().create();

    public static List<ItemStack> getDrops(ResourceManager resourceManager, Object entry) {
        List<ItemStack> drops = new ArrayList<>();
        ResourceLocation lootTableId = null;

        if (entry instanceof EntityType<?> type) {
            lootTableId = type.getDefaultLootTable();
        } else if (entry instanceof Block block) {
            lootTableId = block.getLootTable();
        }

        if (lootTableId != null && !lootTableId.toString().equals("minecraft:empty")) {
            ResourceLocation fileId = new ResourceLocation(lootTableId.getNamespace(), "loot_tables/" + lootTableId.getPath() + ".json");

            Optional<Resource> resource = resourceManager.getResource(fileId);
            if (resource.isPresent()) {
                try (Reader reader = resource.get().openAsReader()) {
                    JsonObject json = GsonHelper.parse(reader);
                    collectItemsFromLootTable(json, drops);
                } catch (Exception e) {
                    Constants.LOG.error("Failed to load loot table: {}", fileId, e);
                }
            }
        }

        List<ItemStack> distinctDrops = new ArrayList<>();
        Set<net.minecraft.world.item.Item> seenItems = new HashSet<>();
        for (ItemStack stack : drops) {
            if (seenItems.add(stack.getItem())) {
                distinctDrops.add(stack);
            }
        }
        return distinctDrops;
    }

    private static void collectItemsFromLootTable(JsonElement element, List<ItemStack> drops) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("type") && "minecraft:item".equals(GsonHelper.getAsString(obj, "type"))) {
                if (obj.has("name")) {
                    String name = GsonHelper.getAsString(obj, "name");
                    ResourceLocation itemId = new ResourceLocation(name);
                    BuiltInRegistries.ITEM.getOptional(itemId).ifPresent(item -> drops.add(new ItemStack(item)));
                }
            }
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                collectItemsFromLootTable(entry.getValue(), drops);
            }
        } else if (element.isJsonArray()) {
            for (JsonElement e : element.getAsJsonArray()) {
                collectItemsFromLootTable(e, drops);
            }
        }
    }
}