package com.evandev.fieldguide.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LootTableHelper {

    private static final int SIMULATION_RUNS = 60;
    private static final Map<Object, List<ItemStack>> SERVER_DROP_CACHE = new ConcurrentHashMap<>();

    public static void clearCache() {
        SERVER_DROP_CACHE.clear();
    }

    public static List<ItemStack> getDrops(ServerPlayer player, Object entry) {
        if (SERVER_DROP_CACHE.containsKey(entry)) {
            return SERVER_DROP_CACHE.get(entry);
        }

        ServerLevel level = player.serverLevel();
        List<ItemStack> allDrops = new ArrayList<>();
        ResourceLocation lootTableId = null;
        LootParams.Builder paramsBuilder = new LootParams.Builder(level);

        ItemStack lootingTool = new ItemStack(Items.NETHERITE_SWORD);
        lootingTool.enchant(Enchantments.MOB_LOOTING, 10);

        LootContextParamSet paramSet = LootContextParamSets.ALL_PARAMS;

        if (entry instanceof EntityType<?> type) {
            lootTableId = type.getDefaultLootTable();
            paramSet = LootContextParamSets.ENTITY;

            Entity dummy = type.create(level);
            Mob mockKiller = new Mob(EntityType.ZOMBIE, level) {
            };
            mockKiller.setItemSlot(EquipmentSlot.MAINHAND, lootingTool);

            if (dummy != null) {
                if (dummy instanceof Mob mob) {
                    try {
                        DifficultyInstance difficulty = level.getCurrentDifficultyAt(dummy.blockPosition());
                        mob.finalizeSpawn(level, difficulty, MobSpawnType.COMMAND, null, null);
                    } catch (Exception ignored) {
                    }
                }

                if (dummy instanceof net.minecraft.world.entity.LivingEntity living) {
                    lootTableId = living.getLootTable();
                }

                paramsBuilder.withParameter(LootContextParams.THIS_ENTITY, dummy);
                paramsBuilder.withParameter(LootContextParams.ORIGIN, dummy.position());
                paramsBuilder.withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(player));

                paramsBuilder.withParameter(LootContextParams.KILLER_ENTITY, mockKiller);
                paramsBuilder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);

                dummy.discard();
                mockKiller.discard();
            } else {
                paramsBuilder.withParameter(LootContextParams.ORIGIN, Vec3.ZERO);
            }

        } else if (entry instanceof Block block) {
            lootTableId = block.getLootTable();
            paramSet = LootContextParamSets.BLOCK;

            BlockState state = block.defaultBlockState();
            for (Property<?> prop : state.getProperties()) {
                if (prop instanceof IntegerProperty intProp) {
                    int max = intProp.getPossibleValues().stream().max(Integer::compareTo).orElse(0);
                    state = state.setValue(intProp, max);
                }
            }

            paramsBuilder.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(player.blockPosition()));
            paramsBuilder.withParameter(LootContextParams.BLOCK_STATE, state);
            paramsBuilder.withParameter(LootContextParams.TOOL, lootingTool);
            paramsBuilder.withParameter(LootContextParams.THIS_ENTITY, player);
        }

        if (lootTableId == null || lootTableId.toString().equals("minecraft:empty")) {
            return Collections.emptyList();
        }

        LootTable table = level.getServer().getLootData().getLootTable(lootTableId);
        LootParams params = paramsBuilder.create(paramSet);

        try {
            for (int i = 0; i < SIMULATION_RUNS; i++) {
                table.getRandomItems(params, allDrops::add);
            }
        } catch (Exception ignored) {
        }

        List<ItemStack> distinctDrops = processDrops(allDrops);
        SERVER_DROP_CACHE.put(entry, distinctDrops);
        return distinctDrops;
    }

    private static List<ItemStack> processDrops(List<ItemStack> allDrops) {
        List<ItemStack> distinctDrops = new ArrayList<>();
        Set<String> seenSignatures = new HashSet<>();

        for (ItemStack stack : allDrops) {
            if (stack.isEmpty()) continue;

            ItemStack displayStack = stack.copy();
            displayStack.setCount(1);
            sanitizeNbt(displayStack);

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

    private static void sanitizeNbt(ItemStack stack) {
        if (!stack.hasTag()) return;
        Item item = stack.getItem();
        boolean isSensitive = item instanceof PotionItem
                || item instanceof EnchantedBookItem
                || item instanceof TippedArrowItem
                || item instanceof SuspiciousStewItem
                || item instanceof SpawnEggItem;

        if (!isSensitive) stack.setTag(null);
    }
}