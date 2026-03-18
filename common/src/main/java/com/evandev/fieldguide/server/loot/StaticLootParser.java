package com.evandev.fieldguide.server.loot;

import com.evandev.fieldguide.compat.reliableremover.ReliableRemoverCompat;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.mixin.accessor.*;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.util.LootTableExpansion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.*;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.BonusLevelTableCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithLootingCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class StaticLootParser {

    public static List<ParsedDrop> parseTable(LootTable table, ServerLevel level) {
        List<ParsedDrop> allDrops = new ArrayList<>();
        List<LootPool> pools = ((LootTableExpansion) table).fieldguide$getPools();

        LootParams params = new LootParams.Builder(level).create(LootContextParamSets.EMPTY);
        LootContext context = new LootContext.Builder(params).create(null);

        for (LootPool pool : pools) {
            float poolRolls = getExpectedRolls(((LootPoolAccessor) pool).fieldguide$getRolls());
            float poolChance = getConditionChance(((LootPoolAccessor) pool).fieldguide$getConditions());
            LootPoolEntryContainer[] entries = ((LootPoolAccessor) pool).fieldguide$getEntries();

            int totalWeight = Arrays.stream(entries).mapToInt(StaticLootParser::getEntryWeight).sum();

            for (LootPoolEntryContainer entry : entries) {
                parseEntry(entry, allDrops, poolRolls * poolChance, totalWeight, context);
            }
        }

        return mergeDrops(allDrops);
    }

    private static void parseEntry(LootPoolEntryContainer entry, List<ParsedDrop> drops, float parentChance, int totalWeight, LootContext context) {
        int weight = getEntryWeight(entry);
        float entryConditionChance = getConditionChance(((LootPoolEntryContainerAccessor) entry).fieldguide$getConditions());
        float selectionChance = totalWeight > 0 ? ((float) weight / totalWeight) : 1f;
        float branchChance = parentChance * selectionChance * entryConditionChance;

        if (branchChance <= 0) return;

        if (entry instanceof LootPoolSingletonContainer singleton) {
            LootItemFunction[] functions = ((LootPoolSingletonContainerAccessor) singleton).fieldguide$getFunctions();

            if (entry instanceof LootItem lootItem) {
                Item item = ((LootItemAccessor) lootItem).fieldguide$getItem();
                ItemStack stack = new ItemStack(item);
                int min = 1, max = 1;

                for (LootItemFunction function : functions) {
                    if (function instanceof SetItemCountFunction countFunc) {
                        NumberProvider provider = ((SetItemCountFunctionAccessor) countFunc).fieldguide$getValue();
                        min = Math.max(0, Math.round(getMinRolls(provider)));
                        max = Math.max(min, Math.round(getMaxRolls(provider)));
                    }
                    try {
                        stack = function.apply(stack, context);
                    } catch (Exception ignored) {
                    }
                }

                if (stack.isEmpty()) stack.setCount(1);
                if (!stack.is(Items.AIR)) {
                    boolean isHidden = Services.PLATFORM.isModLoaded("reliable_remover") && ModConfig.get().enableReliableRemover && ReliableRemoverCompat.isHidden(stack);
                    if (!isHidden) {
                        drops.add(new ParsedDrop(stack, branchChance, min, max));
                    }
                }
            } else if (entry instanceof LootTableReference reference) {
                handleReference(reference, drops, branchChance, context);
            }
        } else if (entry instanceof LootTableReference reference) {
            handleReference(reference, drops, branchChance, context);
        } else if (entry instanceof CompositeEntryBase composite) {
            LootPoolEntryContainer[] children = ((CompositeEntryBaseAccessor) composite).fieldguide$getChildren();
            for (LootPoolEntryContainer child : children) {
                parseEntry(child, drops, branchChance, 1, context);
            }
        }
    }

    private static void handleReference(LootTableReference reference, List<ParsedDrop> drops, float branchChance, LootContext context) {
        ResourceLocation tableId = ((LootTableReferenceAccessor) reference).fieldguide$getName();

        LootTable nestedTable = context.getResolver().getLootTable(tableId);
        if (nestedTable != LootTable.EMPTY) {
            List<ParsedDrop> nestedDrops = parseTable(nestedTable, context.getLevel());
            for (ParsedDrop nested : nestedDrops) {
                drops.add(new ParsedDrop(nested.stack, nested.chance * branchChance, nested.minCount, nested.maxCount));
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

    private static float getMinRolls(NumberProvider provider) {
        if (provider instanceof ConstantValue constant)
            return ((ConstantValueAccessor) (Object) constant).fieldguide$getValue();
        if (provider instanceof UniformGenerator uniform)
            return getMinRolls(((UniformGeneratorAccessor) uniform).fieldguide$getMin());
        return 1f;
    }

    private static float getMaxRolls(NumberProvider provider) {
        if (provider instanceof ConstantValue constant)
            return ((ConstantValueAccessor) (Object) constant).fieldguide$getValue();
        if (provider instanceof UniformGenerator uniform)
            return getMaxRolls(((UniformGeneratorAccessor) uniform).fieldguide$getMax());
        return 1f;
    }

    private static float getConditionChance(LootItemCondition[] conditions) {
        float chance = 1.0f;
        for (LootItemCondition condition : conditions) {
            if (condition instanceof LootItemRandomChanceCondition rc)
                chance *= ((RandomChanceConditionAccessor) rc).fieldguide$getProbability();
            else if (condition instanceof LootItemRandomChanceWithLootingCondition lc)
                chance *= ((RandomChanceWithLootingConditionAccessor) lc).fieldguide$getPercent();
            else if (condition instanceof BonusLevelTableCondition tc) {
                float[] v = ((BonusLevelTableConditionAccessor) tc).fieldguide$getValues();
                if (v.length > 0) chance *= v[0];
            }
        }
        return chance;
    }

    private static List<ParsedDrop> mergeDrops(List<ParsedDrop> drops) {
        List<ParsedDrop> merged = new ArrayList<>();
        for (ParsedDrop drop : drops) {
            ParsedDrop existing = merged.stream()
                    .filter(d -> ItemStack.isSameItemSameTags(d.stack, drop.stack))
                    .findFirst().orElse(null);
            if (existing != null) {
                existing.chance += drop.chance;
                existing.minCount = Math.min(existing.minCount, drop.minCount);
                existing.maxCount = Math.max(existing.maxCount, drop.maxCount);
            } else {
                merged.add(new ParsedDrop(drop.stack, drop.chance, drop.minCount, drop.maxCount));
            }
        }

        for (ParsedDrop drop : merged) {
            if (drop.chance > 1.0f) {
                drop.minCount = Math.round(drop.minCount * drop.chance);
                drop.maxCount = Math.round(drop.maxCount * drop.chance);
                drop.chance = 1.0f;
            }
        }

        merged.sort(Comparator.comparing(a -> a.stack.getHoverName().getString()));
        return merged;
    }
}
