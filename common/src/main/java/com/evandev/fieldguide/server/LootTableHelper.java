package com.evandev.fieldguide.server;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.api.AutoPopulateRegistry;
import com.evandev.fieldguide.api.CompositeFieldGuideEntry;
import com.evandev.fieldguide.server.loot.ParsedDrop;
import com.evandev.fieldguide.server.loot.StaticLootParser;
import com.evandev.fieldguide.util.EntryResolver;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
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
        Map<ResourceLocation, List<ItemStack>> lootMap = new HashMap<>();
        Set<Object> uniqueEntries = new HashSet<>();

        Map<ResourceLocation, List<Object>> resolvedEntries = ServerFieldGuideManager.getInstance().getResolvedEntries();

        for (List<Object> categoryEntries : resolvedEntries.values()) {
            for (Object entry : categoryEntries) {
                uniqueEntries.add(entry);

                if (entry instanceof CompositeFieldGuideEntry composite) {
                    if (composite.displayEntry() != null) uniqueEntries.add(composite.displayEntry());
                    if (composite.components() != null) uniqueEntries.addAll(composite.components());
                }
            }
        }

        for (Object entry : uniqueEntries) {
            ResourceLocation tableId = null;

            if (entry instanceof EntityType<?> type) {
                tableId = type.getDefaultLootTable();
            } else if (entry instanceof Block block) {
                tableId = block.getLootTable();
            } else if (entry instanceof Item item && Block.byItem(item) != net.minecraft.world.level.block.Blocks.AIR) {
                tableId = Block.byItem(item).getLootTable();
            }

            processEntry(level, entry, tableId, lootMap);
        }

        return lootMap;
    }

    private static void processEntry(ServerLevel level, Object entry, ResourceLocation tableId, Map<ResourceLocation, List<ItemStack>> lootMap) {
        List<ItemStack> formattedDrops = new ArrayList<>();
        if (tableId != null && !tableId.toString().equals("minecraft:empty")) {
            try {
                LootTable table = level.getServer().getLootData().getLootTable(tableId);
                List<ParsedDrop> finalDrops = StaticLootParser.parseTable(table, level);

                for (ParsedDrop drop : finalDrops) {
                    ItemStack stack = drop.stack.copy();
                    CompoundTag tag = stack.getOrCreateTag();
                    tag.putFloat("FieldGuideDropChance", drop.chance * 100.0f);
                    tag.putInt("FieldGuideMin", drop.minCount);
                    tag.putInt("FieldGuideMax", drop.maxCount);
                    formattedDrops.add(stack);
                }
            } catch (Exception e) {
                Constants.LOG.error("Failed to parse loot table {}", tableId, e);
            }
        }

        applyConfigModifications(entry, formattedDrops);
        if (!formattedDrops.isEmpty()) {
            ResourceLocation id = AutoPopulateRegistry.getEntryId(entry, true);
            if (id != null) {
                List<ItemStack> existing = lootMap.computeIfAbsent(id, k -> new ArrayList<>());
                for (ItemStack newStack : formattedDrops) {
                    boolean found = false;
                    for (ItemStack s : existing) {
                        if (ItemStack.isSameItemSameTags(s, newStack)) {
                            CompoundTag existingTag = s.getOrCreateTag();
                            CompoundTag newTag = newStack.getOrCreateTag();

                            float existingChance = existingTag.getFloat("FieldGuideDropChance");
                            float newChance = newTag.getFloat("FieldGuideDropChance");
                            existingTag.putFloat("FieldGuideDropChance", Math.min(100.0f, existingChance + newChance));

                            int existingMin = existingTag.contains("FieldGuideMin") ? existingTag.getInt("FieldGuideMin") : 1;
                            int newMin = newTag.contains("FieldGuideMin") ? newTag.getInt("FieldGuideMin") : 1;
                            existingTag.putInt("FieldGuideMin", Math.min(existingMin, newMin));

                            int existingMax = existingTag.contains("FieldGuideMax") ? existingTag.getInt("FieldGuideMax") : 1;
                            int newMax = newTag.contains("FieldGuideMax") ? newTag.getInt("FieldGuideMax") : 1;
                            existingTag.putInt("FieldGuideMax", Math.max(existingMax, newMax));

                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        existing.add(newStack);
                    }
                }
            }
        }
    }

    private static boolean matchesTarget(Object entry, String targetStr) {
        Object coreEntry = entry instanceof CompositeFieldGuideEntry composite ? composite.displayEntry() : entry;
        ResourceLocation entryId = EntryResolver.getRawId(EntryResolver.getEntryId(coreEntry));

        if (entryId == null) return false;
        if (targetStr.startsWith("#")) {
            try {
                ResourceLocation tagId = new ResourceLocation(targetStr.substring(1));
                if (coreEntry instanceof EntityType<?> type) {
                    return BuiltInRegistries.ENTITY_TYPE.getHolder(BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).get()).get().is(TagKey.create(Registries.ENTITY_TYPE, tagId));
                } else if (coreEntry instanceof Block block) {
                    return BuiltInRegistries.BLOCK.getHolder(BuiltInRegistries.BLOCK.getResourceKey(block).get()).get().is(TagKey.create(Registries.BLOCK, tagId));
                } else if (coreEntry instanceof Item item) {
                    return BuiltInRegistries.ITEM.getHolder(BuiltInRegistries.ITEM.getResourceKey(item).get()).get().is(TagKey.create(Registries.ITEM, tagId));
                }
            } catch (Exception ignored) {
            }
            return false;
        }

        ResourceLocation targetId = EntryResolver.getRawId(new ResourceLocation(targetStr));
        return entryId.equals(targetId);
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
                            if (stack.is(TagKey.create(Registries.ITEM, new ResourceLocation(t.substring(1)))))
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
                        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, new ResourceLocation(target.substring(1))))) {
                            ItemStack s = new ItemStack(holder.value());
                            s.getOrCreateTag().putFloat("FieldGuideDropChance", 100.0f);
                            distinctDrops.add(s);
                            added = true;
                        }
                    } catch (Exception ignored) {
                    }
                } else {
                    Item i = BuiltInRegistries.ITEM.get(EntryResolver.getRawId(new ResourceLocation(target)));
                    if (i != Items.AIR) {
                        ItemStack s = new ItemStack(i);
                        s.getOrCreateTag().putFloat("FieldGuideDropChance", 100.0f);
                        distinctDrops.add(s);
                        added = true;
                    }
                }
            }
        }
        if (added) distinctDrops.sort(Comparator.comparing(s -> s.getHoverName().getString()));
    }
}
