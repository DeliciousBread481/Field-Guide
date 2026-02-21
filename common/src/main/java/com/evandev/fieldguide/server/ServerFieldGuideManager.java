package com.evandev.fieldguide.server;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.CategoryEntry;
import com.evandev.fieldguide.network.ExportContentPacket;
import com.evandev.fieldguide.network.SyncCategoriesPacket;
import com.evandev.fieldguide.network.SyncLootPacket;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.util.EntryResolver;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
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
        ModConfig config = ModConfig.get();
        for (Category cat : categories.values()) {
            resolvedCategoryEntries.put(cat.getId(), EntryResolver.resolveCategoryEntries(cat, config));
        }
    }

    public void onServerStarted(MinecraftServer server) {
        resolveAllCategories();
        this.serverLootCache = LootTableHelper.generateLootMap(server.overworld());
    }

    public Category getCategoryForEntry(Object entry) {
        for (Map.Entry<ResourceLocation, List<Object>> cat : resolvedCategoryEntries.entrySet()) {
            if (cat.getValue().contains(entry)) return categories.get(cat.getKey());
        }
        return null;
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

                    if (json.has("contents")) {
                        JsonArray contents = GsonHelper.getAsJsonArray(json, "contents");
                        for (JsonElement el : contents) {
                            JsonObject obj = el.getAsJsonObject();
                            String typeStr = GsonHelper.getAsString(obj, "type");

                            switch (typeStr) {
                                case "entry" -> {
                                    ResourceLocation id = new ResourceLocation(GsonHelper.getAsString(obj, "id"));
                                    category.addEntry(new CategoryEntry(CategoryEntry.Type.ENTRY, id, null, null, null));
                                }
                                case "auto_populate" -> {
                                    String strategy = GsonHelper.getAsString(obj, "strategy");
                                    category.addEntry(new CategoryEntry(CategoryEntry.Type.AUTO_POPULATE, null, strategy, null, null));
                                }
                                case "composite", "structure" -> {
                                    ResourceLocation id = new ResourceLocation(GsonHelper.getAsString(obj, "id"));
                                    List<ResourceLocation> components = new ArrayList<>();
                                    if (obj.has("components")) {
                                        for (JsonElement comp : GsonHelper.getAsJsonArray(obj, "components")) {
                                            components.add(new ResourceLocation(comp.getAsString()));
                                        }
                                    }
                                    ResourceLocation structureNbt = obj.has("structure_nbt") ? new ResourceLocation(GsonHelper.getAsString(obj, "structure_nbt")) : null;
                                    category.addEntry(new CategoryEntry(CategoryEntry.Type.COMPOSITE, id, null, components, structureNbt));
                                }
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
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Services.NETWORK.sendToPlayer(new ExportContentPacket("reload_cache"), player);
        }

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