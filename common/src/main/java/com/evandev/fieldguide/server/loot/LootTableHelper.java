package com.evandev.fieldguide.server.loot;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.ModDataComponents;
import com.evandev.fieldguide.api.AutoPopulateRegistry;
import com.evandev.fieldguide.api.GuideEntry;
import com.evandev.fieldguide.server.ServerFieldGuideManager;
import com.evandev.fieldguide.entry.EntryResolver;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.*;

public class LootTableHelper {

    private static void resolveAndAdd(ResourceLocation id, Set<Object> uniqueEntries) {
        BuiltInRegistries.BLOCK.getOptional(id).ifPresent(uniqueEntries::add);
        BuiltInRegistries.ITEM.getOptional(id).ifPresent(uniqueEntries::add);
        BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(uniqueEntries::add);
    }

    public static Map<ResourceLocation, List<ItemStack>> generateLootMap(ServerLevel level) {
        Map<ResourceLocation, List<ItemStack>> lootMap = new HashMap<>();
        Set<Object> uniqueEntries = new HashSet<>();
        Map<ResourceLocation, List<Object>> resolvedEntries = ServerFieldGuideManager.getInstance().getResolvedEntries();

        for (List<Object> categoryEntries : resolvedEntries.values()) {
            for (Object entry : categoryEntries) {
                uniqueEntries.add(entry);

                if (entry instanceof GuideEntry ge) {
                    if (ge.displayId() != null) resolveAndAdd(ge.displayId(), uniqueEntries);
                    if (ge.childEntries() != null) {
                        for (ResourceLocation childId : ge.childEntries()) {
                            resolveAndAdd(childId, uniqueEntries);
                        }
                    }
                }
            }
        }

        for (Object entry : uniqueEntries) {
            ResourceKey<LootTable> tableId = null;

            if (entry instanceof EntityType<?> type) {
                tableId = type.getDefaultLootTable();
            } else if (entry instanceof Block block) {
                tableId = block.getLootTable();
            } else if (entry instanceof Item item && Block.byItem(item) != Blocks.AIR) {
                tableId = Block.byItem(item).getLootTable();
            }

            processEntry(level, entry, tableId, lootMap);
        }

        return lootMap;
    }

    private static void processEntry(ServerLevel level, Object entry, ResourceKey<LootTable> tableId, Map<ResourceLocation, List<ItemStack>> lootMap) {
        List<ItemStack> formattedDrops = new ArrayList<>();

        if (tableId != null && !tableId.location().toString().equals("minecraft:empty")) {
            try {
                LootTable table = level.getServer().reloadableRegistries().getLootTable(tableId);
                List<ParsedDrop> finalDrops = StaticLootParser.parseTable(table, level);

                for (ParsedDrop drop : finalDrops) {
                    ItemStack stack = drop.stack.copy();
                    stack.set(ModDataComponents.DROP_CHANCE.get(), drop.chance * 100.0f);
                    stack.set(ModDataComponents.MIN_DROP.get(), drop.minCount);
                    stack.set(ModDataComponents.MAX_DROP.get(), drop.maxCount);
                    formattedDrops.add(stack);
                }
            } catch (Exception e) {
                Constants.LOG.error("Failed to parse loot table {}", tableId.location(), e);
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

                        ItemStack copyExisting = s.copy();
                        copyExisting.remove(ModDataComponents.DROP_CHANCE.get());
                        copyExisting.remove(ModDataComponents.MIN_DROP.get());
                        copyExisting.remove(ModDataComponents.MAX_DROP.get());

                        ItemStack copyNew = newStack.copy();
                        copyNew.remove(ModDataComponents.DROP_CHANCE.get());
                        copyNew.remove(ModDataComponents.MIN_DROP.get());
                        copyNew.remove(ModDataComponents.MAX_DROP.get());

                        if (ItemStack.isSameItemSameComponents(copyExisting, copyNew)) {
                            float existingChance = s.getOrDefault(ModDataComponents.DROP_CHANCE.get(), 0.0f);
                            float newChance = newStack.getOrDefault(ModDataComponents.DROP_CHANCE.get(), 0.0f);
                            s.set(ModDataComponents.DROP_CHANCE.get(), Math.min(100.0f, existingChance + newChance));

                            int existingMin = s.getOrDefault(ModDataComponents.MIN_DROP.get(), 1);
                            int newMin = newStack.getOrDefault(ModDataComponents.MIN_DROP.get(), 1);
                            s.set(ModDataComponents.MIN_DROP.get(), Math.min(existingMin, newMin));

                            int existingMax = s.getOrDefault(ModDataComponents.MAX_DROP.get(), 1);
                            int newMax = newStack.getOrDefault(ModDataComponents.MAX_DROP.get(), 1);
                            s.set(ModDataComponents.MAX_DROP.get(), Math.max(existingMax, newMax));

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
        Object coreEntry = EntryResolver.resolveCoreEntry(entry);
        ResourceLocation entryId = EntryResolver.getRawId(EntryResolver.getEntryId(coreEntry));

        if (entryId == null) return false;
        if (targetStr.startsWith("#")) {
            try {
                ResourceLocation tagId = ResourceLocation.parse(targetStr.substring(1));
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

        ResourceLocation targetId = EntryResolver.getRawId(ResourceLocation.parse(targetStr));
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
                            s.set(ModDataComponents.DROP_CHANCE.get(), 100.0f);
                            distinctDrops.add(s);
                            added = true;
                        }
                    } catch (Exception e) {
                        Constants.LOG.error("Failed to parse loot addition tag {}", target, e);
                    }
                } else {
                    Item i = BuiltInRegistries.ITEM.get(EntryResolver.getRawId(ResourceLocation.parse(target)));
                    if (i != Items.AIR) {
                        ItemStack s = new ItemStack(i);
                        s.set(ModDataComponents.DROP_CHANCE.get(), 100.0f);
                        distinctDrops.add(s);
                        added = true;
                    }
                }
            }
        }
        if (added) distinctDrops.sort(Comparator.comparing(s -> s.getHoverName().getString()));
    }
}