package com.evandev.fieldguide.server;

import com.evandev.fieldguide.config.ModConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LootTableHelper {

    private static final int TOTAL_ITERATIONS = 200;
    private static final Map<Object, List<ItemStack>> SERVER_DROP_CACHE = new ConcurrentHashMap<>();

    private static Mob cachedMockKiller;

    public static void clearCache() {
        SERVER_DROP_CACHE.clear();
        if (cachedMockKiller != null) {
            cachedMockKiller.discard();
            cachedMockKiller = null;
        }
    }

    public static List<ItemStack> getDrops(ServerPlayer player, Object entry) {
        if (SERVER_DROP_CACHE.containsKey(entry)) {
            return SERVER_DROP_CACHE.get(entry);
        }

        ServerLevel level = player.serverLevel();
        List<ItemStack> allDrops = new ArrayList<>();

        ItemStack godTool = new ItemStack(Items.NETHERITE_SWORD);
        godTool.enchant(Enchantments.MOB_LOOTING, 10);
        godTool.enchant(Enchantments.BLOCK_FORTUNE, 3);

        if (entry instanceof EntityType<?> type) {
            handleEntityDrops(level, player, type, godTool, allDrops);
        } else if (entry instanceof Block block) {
            handleBlockDrops(level, player, block, godTool, allDrops);
        }

        List<ItemStack> distinctDrops = processDrops(allDrops);
        applyConfigModifications(entry, distinctDrops);

        SERVER_DROP_CACHE.put(entry, distinctDrops);
        return distinctDrops;
    }

    private static void handleEntityDrops(ServerLevel level, ServerPlayer player, EntityType<?> type, ItemStack tool, List<ItemStack> allDrops) {
        ResourceLocation lootTableId = type.getDefaultLootTable();

        if (cachedMockKiller == null || !cachedMockKiller.isAlive()) {
            cachedMockKiller = new Mob(EntityType.ZOMBIE, level) {
            };
        }
        cachedMockKiller.setItemSlot(EquipmentSlot.MAINHAND, tool);

        boolean isComplex = isComplexMob(type);

        int entityResets = isComplex ? 20 : 1;
        int rollsPerEntity = TOTAL_ITERATIONS / entityResets;

        for (int i = 0; i < entityResets; i++) {
            Entity dummy = type.create(level);
            if (dummy == null) continue;

            try {
                if (dummy instanceof Mob mob) {
                    DifficultyInstance difficulty = level.getCurrentDifficultyAt(dummy.blockPosition());
                    mob.finalizeSpawn(level, difficulty, MobSpawnType.COMMAND, null, null);

                    if (mob instanceof MagmaCube magmaCube) magmaCube.setSize(2, true);
                    else if (mob instanceof Slime slime) slime.setSize(1, true);
                }

                if (dummy instanceof LivingEntity living) {
                    lootTableId = living.getLootTable();
                }

                LootParams.Builder paramsBuilder = new LootParams.Builder(level)
                        .withParameter(LootContextParams.THIS_ENTITY, dummy)
                        .withParameter(LootContextParams.ORIGIN, dummy.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player))
                        .withParameter(LootContextParams.KILLER_ENTITY, cachedMockKiller)
                        .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);

                LootTable table = level.getServer().getLootData().getLootTable(lootTableId);
                LootParams params = paramsBuilder.create(LootContextParamSets.ENTITY);

                for (int r = 0; r < rollsPerEntity; r++) {
                    table.getRandomItems(params, allDrops::add);
                }
            } catch (Exception ignored) {
                // Squelch errors from misbehaving mod entities
            } finally {
                dummy.discard();
            }
        }
    }

    private static void handleBlockDrops(ServerLevel level, ServerPlayer player, Block block, ItemStack tool, List<ItemStack> allDrops) {
        ResourceLocation lootTableId = block.getLootTable();
        if (lootTableId.toString().equals("minecraft:empty")) return;

        BlockState state = block.defaultBlockState();
        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof IntegerProperty intProp) {
                int max = intProp.getPossibleValues().stream().max(Integer::compareTo).orElse(0);
                state = state.setValue(intProp, max);
            }
        }

        LootParams.Builder paramsBuilder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(player.blockPosition()))
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.TOOL, tool)
                .withParameter(LootContextParams.THIS_ENTITY, player);

        LootTable table = level.getServer().getLootData().getLootTable(lootTableId);
        LootParams params = paramsBuilder.create(LootContextParamSets.BLOCK);

        for (int i = 0; i < TOTAL_ITERATIONS; i++) {
            table.getRandomItems(params, allDrops::add);
        }
    }

    // TODO: make this a config option
    private static boolean isComplexMob(EntityType<?> type) {
        return type == EntityType.SHEEP || type == EntityType.MOOSHROOM;
    }

    public static void applyConfigModifications(Object entry, List<ItemStack> distinctDrops) {
        ResourceLocation entryId = null;
        if (entry instanceof EntityType<?> type) entryId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        else if (entry instanceof Block block) entryId = BuiltInRegistries.BLOCK.getKey(block);

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
                        distinctDrops.add(new ItemStack(item));
                        added = true;
                    }
                }
            }

            if (added) {
                distinctDrops.sort(Comparator.comparing(s -> s.getHoverName().getString()));
            }
        }
    }

    private static List<ItemStack> processDrops(List<ItemStack> allDrops) {
        List<ItemStack> distinctDrops = new ArrayList<>();
        Set<String> seenSignatures = new HashSet<>();

        for (ItemStack stack : allDrops) {
            if (stack.isEmpty()) continue;

            ItemStack displayStack = stack.copy();
            displayStack.setCount(1);

            if (displayStack.isDamageableItem()) {
                displayStack.setDamageValue(0);
            }

            if (displayStack.hasTag()) {
                CompoundTag tag = displayStack.getTag();

                if (tag != null) {
                    tag.remove("Enchantments");

                    tag.remove("StoredEnchantments");

                    if (tag.isEmpty()) {
                        displayStack.setTag(null);
                    }
                }
            }

            String signature = BuiltInRegistries.ITEM.getKey(displayStack.getItem()).toString();
            if (displayStack.hasTag()) {
                signature += Objects.requireNonNull(displayStack.getTag()).toString();
            }

            if (seenSignatures.add(signature)) {
                distinctDrops.add(displayStack);
            }
        }

        distinctDrops.sort(Comparator.comparing(s -> s.getHoverName().getString()));
        return distinctDrops;
    }
}