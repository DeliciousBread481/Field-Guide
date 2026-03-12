package com.evandev.fieldguide.server.loot;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class SimulatedLootParser {

    private static final int ROLLS = 100;

    public static List<ParsedDrop> simulateBlockDrop(ServerLevel level, Block block, LootTable table) {
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .withParameter(LootContextParams.TOOL, new ItemStack(Items.DIAMOND_PICKAXE))
                .withParameter(LootContextParams.BLOCK_STATE, block.defaultBlockState())
                .create(LootContextParamSets.BLOCK);

        try {
            return runSimulation(table, params);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static List<ParsedDrop> simulateEntityDrop(ServerLevel level, EntityType<?> entityType, LootTable table) {
        Entity dummyEntity = null;
        try {
            dummyEntity = entityType.create(level);
        } catch (Exception ignored) {
        }

        Entity activeEntity = dummyEntity;
        if (activeEntity == null) {
            try {
                activeEntity = EntityType.PIG.create(level);
            } catch (Exception ignored) {
            }
        }

        if (activeEntity == null) {
            return new ArrayList<>();
        }

        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                .withParameter(LootContextParams.THIS_ENTITY, activeEntity);

        List<ParsedDrop> drops = new ArrayList<>();
        try {
            drops = runSimulation(table, builder.create(LootContextParamSets.ENTITY));
        } catch (Exception ignored) {
        }

        if (dummyEntity != null) {
            dummyEntity.discard();
        } else {
            activeEntity.discard();
        }

        return drops;
    }

    private static List<ParsedDrop> runSimulation(LootTable table, LootParams params) {
        Map<Item, DropStats> stats = new HashMap<>();

        for (int i = 0; i < ROLLS; i++) {
            ObjectArrayList<ItemStack> drops = table.getRandomItems(params);

            for (ItemStack drop : drops) {
                if (drop.isEmpty() || drop.is(Items.AIR)) continue;
                stats.computeIfAbsent(drop.getItem(), k -> new DropStats(drop)).addRoll(drop.getCount());
            }
        }

        List<ParsedDrop> results = new ArrayList<>();
        for (DropStats stat : stats.values()) {
            float chance = (float) stat.timesDropped / ROLLS;
            results.add(new ParsedDrop(stat.stack, Math.min(1.0f, chance), stat.min, stat.max));
        }

        results.sort(Comparator.comparing(a -> a.stack.getHoverName().getString()));
        return results;
    }

    private static class DropStats {
        ItemStack stack;
        int timesDropped = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        DropStats(ItemStack stack) {
            this.stack = stack.copy();
            this.stack.setCount(1);
        }

        void addRoll(int count) {
            timesDropped++;
            min = Math.min(min, count);
            max = Math.max(max, count);
        }
    }
}