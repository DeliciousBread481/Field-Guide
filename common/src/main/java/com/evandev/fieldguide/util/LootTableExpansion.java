package com.evandev.fieldguide.util;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.mixin.accessor.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.BonusLevelTableCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import java.util.ArrayList;
import java.util.List;

public class LootTableExpansion {

    public static List<ResourceLocation> getItemsFromLootTable(LootTable table) {
        List<ResourceLocation> items = new ArrayList<>();
        if (table == null) return items;

        List<LootPool> pools = ((LootTableAccessor) table).fieldguide$getPools();
        for (LootPool pool : pools) {
            List<LootPoolEntryContainer> entries = ((LootPoolAccessor) pool).fieldguide$getEntries();
            for (LootPoolEntryContainer entry : entries) {
                gatherItems(entry, items);
            }
        }
        return items;
    }

    private static void gatherItems(LootPoolEntryContainer entry, List<ResourceLocation> items) {
        if (entry instanceof LootItem lootItem) {
            items.add(BuiltInRegistries.ITEM.getKey(((LootItemAccessor) lootItem).fieldguide$getItem().value()));
        } else if (entry instanceof NestedLootTable nested) {
            ResourceKey<LootTable> key = ((NestedLootTableAccessor) nested).fieldguide$getContents().map(
                resourceKey -> resourceKey,
                lootTable -> null
            );
            if (key != null) {
                items.add(key.location());
            }
        }
    }
}
