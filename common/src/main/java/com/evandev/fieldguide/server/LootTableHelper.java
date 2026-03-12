package com.evandev.fieldguide.server;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.server.loot.ParsedDrop;
import com.evandev.fieldguide.server.loot.SimulatedLootParser;
import com.evandev.fieldguide.server.loot.StaticLootParser;
import com.evandev.fieldguide.util.EntryResolver;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.*;

public class LootTableHelper {

    public static Map<ResourceLocation, List<ItemStack>> generateLootMap(ServerLevel level) {
        Map<ResourceLocation, List<ItemStack>> lootMap = new HashMap<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceKey<LootTable> tableId = type.getDefaultLootTable();

            processEntry(level, type, tableId, lootMap);
        }
        for (Block block : BuiltInRegistries.BLOCK) {
            processEntry(level, block, block.getLootTable(), lootMap);
        }
        return lootMap;
    }

    private static void processEntry(ServerLevel level, Object entry, ResourceKey<LootTable> tableId, Map<ResourceLocation, List<ItemStack>> lootMap) {
        List<ItemStack> formattedDrops = new ArrayList<>();
        if (tableId != null && !tableId.toString().equals("minecraft:empty")) {
            try {
                LootTable table = level.getServer().reloadableRegistries().getLootTable(tableId);
                List<ParsedDrop> finalDrops = StaticLootParser.parseTable(table, level);

                List<ParsedDrop> simulatedDrops = new ArrayList<>();
                if (entry instanceof EntityType<?> entityType) {
                    simulatedDrops = SimulatedLootParser.simulateEntityDrop(level, entityType, table);
                } else if (entry instanceof Block block) {
                    simulatedDrops = SimulatedLootParser.simulateBlockDrop(level, block, table);
                }

                for (ParsedDrop simDrop : simulatedDrops) {
                    boolean alreadyExists = finalDrops.stream()
                            .anyMatch(staticDrop -> ItemStack.isSameItemSameComponents(staticDrop.stack, simDrop.stack));

                    if (!alreadyExists) {
                        finalDrops.add(simDrop);
                    }
                }

                for (ParsedDrop drop : finalDrops) {
                    ItemStack stack = drop.stack.copy();

                    CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                    CompoundTag tag = customData.copyTag();
                    tag.putFloat("FieldGuideDropChance", drop.chance * 100.0f);
                    tag.putInt("FieldGuideMin", drop.minCount);
                    tag.putInt("FieldGuideMax", drop.maxCount);
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

                    formattedDrops.add(stack);
                }
            } catch (Exception e) {
                Constants.LOG.error("FieldGuide: Failed to parse loot table {}", tableId, e);
            }
        }
        applyConfigModifications(entry, formattedDrops);
        if (!formattedDrops.isEmpty()) {
            ResourceLocation id = EntryResolver.getEntryId(entry);
            if (id != null) {
                lootMap.computeIfAbsent(id, k -> new ArrayList<>()).addAll(formattedDrops);
            }
        }
    }

    private static boolean matchesTarget(Object entry, String targetStr) {
        ResourceLocation entryId = EntryResolver.getEntryId(entry);
        if (entryId == null) return false;
        if (targetStr.startsWith("#")) {
            try {
                ResourceLocation tagId = ResourceLocation.parse(targetStr.substring(1));
                if (entry instanceof EntityType<?> type) {
                    return BuiltInRegistries.ENTITY_TYPE.getHolder(BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).get()).get().is(TagKey.create(Registries.ENTITY_TYPE, tagId));
                } else if (entry instanceof Block block) {
                    return BuiltInRegistries.BLOCK.getHolder(BuiltInRegistries.BLOCK.getResourceKey(block).get()).get().is(TagKey.create(Registries.BLOCK, tagId));
                }
            } catch (Exception ignored) {
            }
            return false;
        }
        return entryId.toString().equals(targetStr);
    }

    public static void applyConfigModifications(Object entry, List<ItemStack> distinctDrops) {
        ResourceLocation entryId = EntryResolver.getEntryId(entry);
        if (entryId == null) return;

        ServerFieldGuideManager manager = ServerFieldGuideManager.getInstance();
        Set<String> itemsToRemove = new HashSet<>();

        for (String line : manager.getLootRemovals()) {
            String[] parts = line.split("\\|");
            if (parts.length == 2 && matchesTarget(entry, parts[0])) itemsToRemove.add(parts[1]);
        }

        if (!itemsToRemove.isEmpty()) {
            distinctDrops.removeIf(stack -> {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                for (String t : itemsToRemove) {
                    if (t.startsWith("#")) {
                        try {
                            if (stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(t.substring(1)))))
                                return true;
                        } catch (Exception ignored) {
                        }
                    } else if (id.toString().equals(t)) return true;
                }
                return false;
            });
        }

        boolean added = false;
        for (String line : manager.getLootAdditions()) {
            String[] parts = line.split("\\|");
            if (parts.length == 2 && matchesTarget(entry, parts[0])) {
                String target = parts[1];
                if (target.startsWith("#")) {
                    try {
                        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, ResourceLocation.parse(target.substring(1))))) {
                            ItemStack s = new ItemStack(holder.value());
                            CustomData customData = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                            CompoundTag tag = customData.copyTag();
                            tag.putFloat("FieldGuideDropChance", 100.0f);
                            s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                            distinctDrops.add(s);
                            added = true;
                        }
                    } catch (Exception ignored) {
                    }
                } else {
                    Item i = BuiltInRegistries.ITEM.get(ResourceLocation.parse(target));
                    if (i != Items.AIR) {
                        ItemStack s = new ItemStack(i);
                        CustomData customData = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
                        CompoundTag tag = customData.copyTag();
                        tag.putFloat("FieldGuideDropChance", 100.0f);
                        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                        distinctDrops.add(s);
                        added = true;
                    }
                }
            }
        }
        if (added) distinctDrops.sort(Comparator.comparing(s -> s.getHoverName().getString()));
        distinctDrops.removeIf(ItemStack::isEmpty);
    }
}