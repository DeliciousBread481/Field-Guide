package com.evandev.fieldguide.server;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.platform.Services;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LootTableHelper {

    private static final int TOTAL_ITERATIONS = 2000;
    private static final Map<Object, List<ItemStack>> SERVER_DROP_CACHE = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static File getCacheFile(ServerLevel level) {
        return level.getServer().getWorldPath(LevelResource.GENERATED_DIR)
                .resolve("fieldguide").resolve("loot_cache.json").toFile();
    }

    public static boolean containsEntry(Object entry) {
        return SERVER_DROP_CACHE.containsKey(entry);
    }

    public static void clearCache() {
        SERVER_DROP_CACHE.clear();
    }

    /**
     * Called on server startup to generate all loot.
     */
    public static void generateAll(ServerLevel level, List<Object> entries) {
        Constants.LOG.info("FieldGuide: Generating loot cache for {} entries...", entries.size());
        long start = System.currentTimeMillis();

        File cacheFile = getCacheFile(level);

        ServerPlayer fakePlayer = Services.PLATFORM.getFakePlayer(level);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);

        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, sword);

        int processed = 0;
        for (Object entry : entries) {
            List<List<ItemStack>> allDrops = new ArrayList<>();
            if (entry instanceof EntityType<?> type) {
                handleEntityDrops(level, fakePlayer, type, allDrops);
            } else if (entry instanceof Block block) {
                handleBlockDrops(level, fakePlayer, block, sword, allDrops);
            }

            List<ItemStack> distinctDrops = processDrops(allDrops);
            applyConfigModifications(entry, distinctDrops);
            SERVER_DROP_CACHE.put(entry, distinctDrops);
            processed++;
        }

        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        saveCacheToDisk(cacheFile);

        Constants.LOG.info("FieldGuide: Generated loot for {} entries in {}ms", processed, System.currentTimeMillis() - start);
    }

    public static Map<ResourceLocation, List<ItemStack>> getCacheAsMap() {
        Map<ResourceLocation, List<ItemStack>> map = new HashMap<>();
        for (Map.Entry<Object, List<ItemStack>> entry : SERVER_DROP_CACHE.entrySet()) {
            ResourceLocation id = getEntryId(entry.getKey());
            if (id != null && !entry.getValue().isEmpty()) {
                map.put(id, entry.getValue());
            }
        }
        return map;
    }

    private static void saveCacheToDisk(File file) {
        try {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();

            JsonObject root = new JsonObject();
            for (Map.Entry<Object, List<ItemStack>> entry : SERVER_DROP_CACHE.entrySet()) {
                ResourceLocation id = getEntryId(entry.getKey());
                if (id == null) continue;

                JsonArray dropsArray = new JsonArray();
                for (ItemStack stack : entry.getValue()) {
                    JsonObject itemObj = new JsonObject();
                    itemObj.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    itemObj.addProperty("count", stack.getCount());
                    if (stack.hasTag()) {
                        itemObj.addProperty("nbt", Objects.requireNonNull(stack.getTag()).toString());
                    }
                    dropsArray.add(itemObj);
                }
                root.add(id.toString(), dropsArray);
            }

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            Constants.LOG.error("FieldGuide: Failed to save loot cache", e);
        }
    }

    public static void tryLoadCache(ServerLevel level) {
        SERVER_DROP_CACHE.clear();
        File file = getCacheFile(level);
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            SERVER_DROP_CACHE.clear();

            for (String key : root.keySet()) {
                ResourceLocation id = new ResourceLocation(key);
                Object entry = resolveEntry(id);
                if (entry == null) continue;

                List<ItemStack> items = new ArrayList<>();
                JsonArray dropsArray = root.getAsJsonArray(key);

                for (var el : dropsArray) {
                    JsonObject obj = el.getAsJsonObject();
                    Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(obj.get("item").getAsString()));
                    if (item == Items.AIR) continue;

                    ItemStack stack = new ItemStack(item);
                    if (obj.has("count")) stack.setCount(obj.get("count").getAsInt());
                    if (obj.has("nbt")) {
                        stack.setTag(TagParser.parseTag(obj.get("nbt").getAsString()));
                    }
                    items.add(stack);
                }
                SERVER_DROP_CACHE.put(entry, items);
            }
            Constants.LOG.info("FieldGuide: Loaded {} entries from disk cache.", SERVER_DROP_CACHE.size());
        } catch (Exception e) {
            Constants.LOG.error("FieldGuide: Failed to load loot cache", e);
        }
    }

    private static ResourceLocation getEntryId(Object entry) {
        if (entry instanceof EntityType<?> type) return BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (entry instanceof Block block) return BuiltInRegistries.BLOCK.getKey(block);
        return null;
    }

    private static Object resolveEntry(ResourceLocation id) {
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return BuiltInRegistries.ENTITY_TYPE.get(id);
        if (BuiltInRegistries.BLOCK.containsKey(id)) return BuiltInRegistries.BLOCK.get(id);
        return null;
    }

    private static void handleEntityDrops(ServerLevel level, ServerPlayer player, EntityType<?> type, List<List<ItemStack>> allDrops) {
        ResourceLocation lootTableId = type.getDefaultLootTable();

        Entity dummy = type.create(level);
        if (dummy == null) return;

        try {
            if (dummy instanceof Mob mob) {
                DifficultyInstance difficulty = level.getCurrentDifficultyAt(dummy.blockPosition());
                try {
                    mob.finalizeSpawn(level, difficulty, MobSpawnType.COMMAND, null, null);
                } catch (Exception ignored) {
                }

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
                    .withParameter(LootContextParams.KILLER_ENTITY, player)
                    .withParameter(LootContextParams.DIRECT_KILLER_ENTITY, player)
                    .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);

            LootTable table = level.getServer().getLootData().getLootTable(lootTableId);
            LootParams params = paramsBuilder.create(LootContextParamSets.ENTITY);

            for (int r = 0; r < TOTAL_ITERATIONS; r++) {
                List<ItemStack> iterDrops = new ArrayList<>();
                table.getRandomItems(params, iterDrops::add);
                allDrops.add(iterDrops);
            }
        } catch (Exception e) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            Constants.LOG.error("FieldGuide: Failed to generate loot for entity {}", id, e);
        } finally {
            dummy.discard();
        }
    }

    private static void handleBlockDrops(ServerLevel level, ServerPlayer player, Block block, ItemStack tool, List<List<ItemStack>> allDrops) {
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
            List<ItemStack> iterDrops = new ArrayList<>();
            table.getRandomItems(params, iterDrops::add);
            allDrops.add(iterDrops);
        }
    }

    public static void applyConfigModifications(Object entry, List<ItemStack> distinctDrops) {
        ResourceLocation entryId = getEntryId(entry);

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

    private static List<ItemStack> processDrops(List<List<ItemStack>> allDrops) {
        List<ItemStack> distinctDrops = new ArrayList<>();
        Map<String, ItemStack> signatureToStack = new HashMap<>();
        Map<String, Integer> signatureToDropCount = new HashMap<>();

        for (List<ItemStack> iterDrops : allDrops) {
            Set<String> seenThisIter = new HashSet<>();
            for (ItemStack stack : iterDrops) {
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
                        tag.remove("Damage");
                        if (tag.isEmpty()) displayStack.setTag(null);
                    }
                }

                String signature = BuiltInRegistries.ITEM.getKey(displayStack.getItem()).toString();
                if (displayStack.hasTag()) {
                    signature += Objects.requireNonNull(displayStack.getTag()).toString();
                }

                if (!signatureToStack.containsKey(signature)) {
                    signatureToStack.put(signature, displayStack);
                }
                seenThisIter.add(signature);
            }
            for (String sig : seenThisIter) {
                signatureToDropCount.put(sig, signatureToDropCount.getOrDefault(sig, 0) + 1);
            }
        }

        for (Map.Entry<String, ItemStack> entry : signatureToStack.entrySet()) {
            ItemStack stack = entry.getValue();
            float chance = (signatureToDropCount.get(entry.getKey()) / (float) TOTAL_ITERATIONS) * 100.0f;

            CompoundTag tag = stack.getOrCreateTag();
            tag.putFloat("FieldGuideDropChance", chance);

            distinctDrops.add(stack);
        }

        distinctDrops.sort(Comparator.comparing(s -> s.getHoverName().getString()));
        return distinctDrops;
    }
}