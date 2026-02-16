package com.evandev.fieldguide.server;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.CategoryEntry;
import com.evandev.fieldguide.network.SyncCategoriesPacket;
import com.evandev.fieldguide.network.SyncLootPacket;
import com.evandev.fieldguide.platform.Services;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.*;
import org.jetbrains.annotations.NotNull;

import java.io.Reader;
import java.util.*;

public class ServerFieldGuideManager extends SimplePreparableReloadListener<Map<ResourceLocation, Category>> {
    private static final ServerFieldGuideManager INSTANCE = new ServerFieldGuideManager();
    private final Map<ResourceLocation, List<Object>> resolvedCategoryEntries = new HashMap<>();
    private Map<ResourceLocation, Category> categories = new LinkedHashMap<>();
    private Map<ResourceLocation, List<ItemStack>> serverLootCache = new HashMap<>();

    public static ServerFieldGuideManager getInstance() {
        return INSTANCE;
    }

    public Map<ResourceLocation, Category> getCategories() {
        return categories;
    }

    public void syncToPlayer(ServerPlayer player) {
        List<Category> categoryList = new ArrayList<>(categories.values());
        Services.NETWORK.sendToPlayer(new SyncCategoriesPacket(categoryList), player);

        if (!serverLootCache.isEmpty()) {
            Services.NETWORK.sendToPlayer(new SyncLootPacket(serverLootCache), player);
        }
    }

    private void resolveAllCategories() {
        resolvedCategoryEntries.clear();
        for (Category cat : categories.values()) {
            resolveCategory(cat);
        }
    }

    public void onServerStarted(MinecraftServer server) {
        resolveAllCategories();
        this.serverLootCache = LootTableHelper.generateLootMap(server.overworld());
    }

    private void resolveCategory(Category category) {
        Set<Object> foundEntries = new LinkedHashSet<>();

        for (CategoryEntry entry : category.getEntries()) {
            if (entry.type() == CategoryEntry.Type.ENTRY) {
                if (entry.id() == null) continue;
                Optional<EntityType<?>> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entry.id());
                if (entityType.isPresent()) {
                    foundEntries.add(entityType.get());
                } else {
                    Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(entry.id());
                    block.ifPresent(foundEntries::add);
                }
            } else if (entry.type() == CategoryEntry.Type.AUTO_POPULATE) {
                foundEntries.addAll(getEntriesForStrategy(entry.strategy()));
            }
        }
        resolvedCategoryEntries.put(category.getId(), new ArrayList<>(foundEntries));
    }

    public Category getCategoryForEntry(Object entry) {
        for (Map.Entry<ResourceLocation, List<Object>> cat : resolvedCategoryEntries.entrySet()) {
            if (cat.getValue().contains(entry)) return categories.get(cat.getKey());
        }
        return null;
    }

    private List<Object> getEntriesForStrategy(String strategy) {
        List<Object> results = new ArrayList<>();
        ModConfig config = ModConfig.get();

        if ("plants".equalsIgnoreCase(strategy)) {
            results.addAll(BuiltInRegistries.BLOCK.stream()
                    .filter(block -> block instanceof BushBlock || block instanceof LeavesBlock || block instanceof VineBlock || block instanceof CactusBlock || block instanceof SugarCaneBlock || block instanceof WaterlilyBlock || block instanceof StemBlock)
                    .filter(block -> !config.isEntityBlacklisted(BuiltInRegistries.BLOCK.getKey(block)))
                    .sorted(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString()))
                    .toList());
        } else if (strategy.startsWith("mod:")) {
            String modId = strategy.substring(4);
            results.addAll(BuiltInRegistries.ENTITY_TYPE.stream()
                    .filter(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals(modId))
                    .filter(type -> type.canSummon() && !config.isEntityBlacklisted(BuiltInRegistries.ENTITY_TYPE.getKey(type)))
                    .sorted(Comparator.comparing(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()))
                    .toList());
        } else if (strategy.startsWith("mod_plants:")) {
            String modId = strategy.substring(10);
            results.addAll(BuiltInRegistries.BLOCK.stream()
                    .filter(block -> BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(modId))
                    .filter(block -> block instanceof BushBlock || block instanceof LeavesBlock || block instanceof VineBlock || block instanceof CactusBlock || block instanceof SugarCaneBlock || block instanceof WaterlilyBlock || block instanceof StemBlock)
                    .filter(block -> !config.isEntityBlacklisted(BuiltInRegistries.BLOCK.getKey(block)))
                    .sorted(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString()))
                    .toList());
        } else if ("monsters".equalsIgnoreCase(strategy) || "animals".equalsIgnoreCase(strategy)) {
            TagKey<EntityType<?>> bossesTag = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("fieldguide", "bosses"));

            results.addAll(BuiltInRegistries.ENTITY_TYPE.stream()
                    .filter(type -> {
                        boolean isBoss = false;
                        var key = BuiltInRegistries.ENTITY_TYPE.getResourceKey(type);
                        if (key.isPresent()) {
                            var holder = BuiltInRegistries.ENTITY_TYPE.getHolder(key.get());
                            if (holder.isPresent() && holder.get().is(bossesTag)) {
                                isBoss = true;
                            }
                        }

                        if ("monsters".equalsIgnoreCase(strategy))
                            return type.getCategory() == MobCategory.MONSTER && !isBoss;
                        if ("animals".equalsIgnoreCase(strategy))
                            return type.getCategory() != MobCategory.MONSTER && (type.getCategory() != MobCategory.MISC || SpawnEggItem.byId(type) != null) && !isBoss;
                        return false;
                    })
                    .filter(type -> type.canSummon() && !config.isEntityBlacklisted(BuiltInRegistries.ENTITY_TYPE.getKey(type)))
                    .sorted(Comparator.comparing(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()))
                    .toList());
        }
        return results;
    }

    @Override
    protected @NotNull Map<ResourceLocation, Category> prepare(ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        Map<ResourceLocation, Category> map = new LinkedHashMap<>();

        Map<ResourceLocation, List<Resource>> resources = resourceManager.listResourceStacks(
                "fieldguide/categories",
                id -> id.getPath().endsWith(".json")
        );

        for (Map.Entry<ResourceLocation, List<Resource>> entry : resources.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            String path = fileId.getPath();
            String idPath = path.substring("fieldguide/categories/".length(), path.length() - ".json".length());
            ResourceLocation categoryId = new ResourceLocation(fileId.getNamespace(), idPath);
            Category category = new Category(categoryId);

            for (Resource resource : entry.getValue()) {
                try (Reader reader = resource.openAsReader()) {
                    JsonObject json = GsonHelper.parse(reader);
                    if (GsonHelper.getAsBoolean(json, "replace", false)) {
                        category.getEntries().clear();
                    }

                    if (json.has("sort_index")) {
                        category.setSortIndex(GsonHelper.getAsInt(json, "sort_index"));
                    }

                    if (json.has("scannable")) {
                        category.setScannable(GsonHelper.getAsBoolean(json, "scannable"));
                    }

                    if (json.has("contents")) {
                        JsonArray contents = GsonHelper.getAsJsonArray(json, "contents");
                        for (JsonElement el : contents) {
                            JsonObject obj = el.getAsJsonObject();
                            String typeStr = GsonHelper.getAsString(obj, "type");

                            if ("entry".equals(typeStr)) {
                                ResourceLocation id = new ResourceLocation(GsonHelper.getAsString(obj, "id"));
                                category.addEntry(new CategoryEntry(CategoryEntry.Type.ENTRY, id, null));
                            } else if ("auto_populate".equals(typeStr)) {
                                String strategy = GsonHelper.getAsString(obj, "strategy");
                                category.addEntry(new CategoryEntry(CategoryEntry.Type.AUTO_POPULATE, null, strategy));
                            }
                        }
                    }
                } catch (Exception e) {
                    Constants.LOG.error("Failed to load category: {}", fileId, e);
                }
            }
            map.put(categoryId, category);
        }
        return map;
    }

    public void syncToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncToPlayer(player);
        }
    }

    public void reload(MinecraftServer server) {
        resolveAllCategories();
        this.serverLootCache = LootTableHelper.generateLootMap(server.overworld());
        syncToAll(server);
    }

    @Override
    protected void apply(@NotNull Map<ResourceLocation, Category> object, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        ModConfig.load();
        this.categories = object;
    }
}