package com.evandev.fieldguide.server.loot;

import com.evandev.fieldguide.mixin.accessor.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.*;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class StaticLootParser {

    public static List<ParsedDrop> parseTable(LootTable table) {
        List<ParsedDrop> allDrops = new ArrayList<>();
        LootPool[] pools = ((LootTableAccessor) table).fieldguide$getPools();

        for (LootPool pool : pools) {
            float poolRolls = getExpectedRolls(((LootPoolAccessor) pool).fieldguide$getRolls());
            float poolChance = getConditionChance(((LootPoolAccessor) pool).fieldguide$getConditions());

            LootPoolEntryContainer[] entries = ((LootPoolAccessor) pool).fieldguide$getEntries();

            int totalWeight = Arrays.stream(entries)
                    .mapToInt(StaticLootParser::getEntryWeight)
                    .sum();

            for (LootPoolEntryContainer entry : entries) {
                parseEntry(entry, allDrops, poolRolls * poolChance, totalWeight);
            }
        }

        return mergeDrops(allDrops);
    }

    private static void parseEntry(LootPoolEntryContainer entry, List<ParsedDrop> drops, float parentChance, int totalWeight) {
        int weight = getEntryWeight(entry);
        float entryConditionChance = getConditionChance(((LootPoolEntryContainerAccessor) entry).fieldguide$getConditions());

        float selectionChance = totalWeight > 0 ? ((float) weight / totalWeight) : 1f;
        float branchChance = parentChance * selectionChance * entryConditionChance;

        if (branchChance <= 0) return;

        if (entry instanceof LootItem lootItem) {
            Item item = ((LootItemAccessor) lootItem).fieldguide$getItem();
            drops.add(new ParsedDrop(new ItemStack(item), branchChance));
        } else if (entry instanceof TagEntry tagEntry) {
            var tag = ((TagEntryAccessor) tagEntry).fieldguide$getTag();
            var items = BuiltInRegistries.ITEM.getTagOrEmpty(tag);
            int tagSize = 0;
            for (var ignored : items) tagSize++;

            float chancePerItem = tagSize > 0 ? (branchChance / tagSize) : 0;

            for (var holder : items) {
                drops.add(new ParsedDrop(new ItemStack(holder.value()), chancePerItem));
            }
        } else if (entry instanceof AlternativesEntry altEntry) {
            LootPoolEntryContainer[] children = ((CompositeEntryBaseAccessor) altEntry).fieldguide$getChildren();
            float remainingChance = branchChance;
            for (LootPoolEntryContainer child : children) {
                float childCondition = getConditionChance(((LootPoolEntryContainerAccessor) child).fieldguide$getConditions());
                parseEntry(child, drops, remainingChance, 1);
                remainingChance *= (1f - childCondition);
            }
        } else if (entry instanceof CompositeEntryBase composite) {
            LootPoolEntryContainer[] children = ((CompositeEntryBaseAccessor) composite).fieldguide$getChildren();
            for (LootPoolEntryContainer child : children) {
                parseEntry(child, drops, branchChance, 1);
            }
        }
    }

    private static int getEntryWeight(LootPoolEntryContainer entry) {
        if (entry instanceof LootPoolSingletonContainer singleton) {
            return ((LootPoolSingletonContainerAccessor) singleton).fieldguide$getWeight();
        }
        return 1;
    }

    private static float getExpectedRolls(NumberProvider provider) {
        if (provider instanceof ConstantValue constant) {
            return ((ConstantValueAccessor) (Object) constant).fieldguide$getValue();
        } else if (provider instanceof UniformGenerator uniform) {
            float min = getExpectedRolls(((UniformGeneratorAccessor) uniform).fieldguide$getMin());
            float max = getExpectedRolls(((UniformGeneratorAccessor) uniform).fieldguide$getMax());
            return (min + max) / 2.0f;
        }
        return 1f;
    }

    private static float getConditionChance(LootItemCondition[] conditions) {
        float chance = 1.0f;
        for (LootItemCondition condition : conditions) {
            if (condition instanceof LootItemRandomChanceCondition randomCondition) {
                chance *= ((RandomChanceConditionAccessor) randomCondition).fieldguide$getProbability();
            }
        }
        return chance;
    }

    private static List<ParsedDrop> mergeDrops(List<ParsedDrop> drops) {
        List<ParsedDrop> merged = new ArrayList<>();
        for (ParsedDrop drop : drops) {
            ParsedDrop existing = merged.stream()
                    .filter(d -> d.stack.getItem() == drop.stack.getItem())
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                existing.chance += drop.chance;
            } else {
                merged.add(drop);
            }
        }
        merged.sort(Comparator.comparing(a -> a.stack.getHoverName().getString()));
        return merged;
    }
}