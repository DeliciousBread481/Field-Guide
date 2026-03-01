package com.evandev.fieldguide.server;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.CategoryEntry;
import com.evandev.fieldguide.data.CompositeDefinition;
import com.evandev.fieldguide.data.CompositeFieldGuideEntry;
import com.evandev.fieldguide.network.ExportContentPacket;
import com.evandev.fieldguide.network.SyncCategoriesPacket;
import com.evandev.fieldguide.network.SyncLootPacket;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.util.EntryResolver;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.io.Reader;
import java.util.*;

public class ServerFieldGuideManager extends SimplePreparableReloadListener<ServerFieldGuideManager.ReloadData> {
    private static final ServerFieldGuideManager INSTANCE = new ServerFieldGuideManager();
    private final Map<ResourceLocation, List<Object>> resolvedCategoryEntries = new HashMap<>();
    private Map<ResourceLocation, Category> categories = new LinkedHashMap<>();
    private List<CompositeDefinition> composites = new ArrayList<>();
    private Map<ResourceLocation, List<ItemStack>> serverLootCache = new HashMap<>();
    private Map<ResourceLocation, ResourceLocation> redirects = new HashMap<>();

    private List<String> biomeAdditions = new ArrayList<>();
    private List<String> biomeRemovals = new ArrayList<>();
    private List<String> lootAdditions = new ArrayList<>();
    private List<String> lootRemovals = new ArrayList<>();

    public static ServerFieldGuideManager getInstance() {
        return INSTANCE;
    }

    public Map<ResourceLocation, Category> getCategories() {
        return categories;
    }

    public Map<ResourceLocation, ResourceLocation> getRedirects() {
        return redirects;
    }

    public List<String> getBiomeAdditions() {
        return biomeAdditions;
    }

    public List<String> getBiomeRemovals() {
        return biomeRemovals;
    }

    public List<String> getLootAdditions() {
        return lootAdditions;
    }

    public List<String> getLootRemovals() {
        return lootRemovals;
    }

    public void syncToPlayer(ServerPlayer player) {
        List<Category> flattenedCategories = new ArrayList<>();

        for (Category rawCat : categories.values()) {
            Category flatCat = new Category(rawCat.getId());
            flatCat.setSortIndex(rawCat.getSortIndex());
            flatCat.setIcon(rawCat.getIcon());
            flatCat.setGroupByQueries(rawCat.getGroupByQueries());
            List<Object> resolved = resolvedCategoryEntries.get(rawCat.getId());

            if (resolved != null) {
                for (Object obj : resolved) {
                    if (obj instanceof EntityType<?> type) {
                        flatCat.addEntry(new CategoryEntry(CategoryEntry.Type.ENTRY, BuiltInRegistries.ENTITY_TYPE.getKey(type), BuiltInRegistries.ENTITY_TYPE.getKey(type), null, null, null, null));
                    } else if (obj instanceof Block block) {
                        flatCat.addEntry(new CategoryEntry(CategoryEntry.Type.ENTRY, BuiltInRegistries.BLOCK.getKey(block), BuiltInRegistries.BLOCK.getKey(block), null, null, null, null));
                    } else if (obj instanceof CompositeFieldGuideEntry(
                            ResourceLocation id, Object displayEntry, List<Object> components,
                            ResourceLocation structureNbt, List<String> stackedBlocks
                    )) {
                        List<ResourceLocation> compIds = new ArrayList<>();
                        if (components != null) {
                            for (Object c : components) {
                                if (c instanceof EntityType<?> t) compIds.add(BuiltInRegistries.ENTITY_TYPE.getKey(t));
                                else if (c instanceof Block b) compIds.add(BuiltInRegistries.BLOCK.getKey(b));
                            }
                        }

                        ResourceLocation displayId = null;
                        if (displayEntry instanceof EntityType<?> t)
                            displayId = BuiltInRegistries.ENTITY_TYPE.getKey(t);
                        else if (displayEntry instanceof Block b) displayId = BuiltInRegistries.BLOCK.getKey(b);

                        flatCat.addEntry(new CategoryEntry(CategoryEntry.Type.COMPOSITE, id, displayId, null, compIds, structureNbt, stackedBlocks));
                    }
                }
            }
            flattenedCategories.add(flatCat);
        }

        Services.NETWORK.sendToPlayer(new SyncCategoriesPacket(flattenedCategories, biomeAdditions, biomeRemovals, lootAdditions, lootRemovals, redirects), player);

        if (!serverLootCache.isEmpty()) {
            Services.NETWORK.sendToPlayer(new SyncLootPacket(serverLootCache), player);
        }
    }

    private void resolveAllCategories() {
        resolvedCategoryEntries.clear();
        ModConfig config = ModConfig.get();
        Set<Object> allCompositeComponents = new HashSet<>();

        for (Category cat : categories.values()) {
            List<Object> entries = EntryResolver.resolveCategoryEntries(cat, config, composites, redirects);
            for (Object entry : entries) {
                if (entry instanceof CompositeFieldGuideEntry composite) {
                    if (composite.components() != null) {
                        allCompositeComponents.addAll(composite.components());
                    }
                    if (composite.displayEntry() != null) {
                        allCompositeComponents.add(composite.displayEntry());
                    }
                }
            }
            resolvedCategoryEntries.put(cat.getId(), entries);
        }

        for (List<Object> entries : resolvedCategoryEntries.values()) {
            entries.removeIf(entry -> !(entry instanceof CompositeFieldGuideEntry) && allCompositeComponents.contains(entry));
        }
    }

    public void onServerStarted(MinecraftServer server) {
        resolveAllCategories();
        this.serverLootCache = LootTableHelper.generateLootMap(server.overworld());
        generateAutoBiomeAdditions(server);
    }

    public Category getCategoryForEntry(Object entry) {
        for (Map.Entry<ResourceLocation, List<Object>> cat : resolvedCategoryEntries.entrySet()) {
            if (cat.getValue().contains(entry)) return categories.get(cat.getKey());
        }
        return null;
    }

    @Override
    protected @NotNull ReloadData prepare(ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        ReloadData data = new ReloadData();

        Map<ResourceLocation, List<Resource>> categoryResources = resourceManager.listResourceStacks(
                "fieldguide/categories",
                id -> id.getPath().endsWith(".json")
        );

        for (Map.Entry<ResourceLocation, List<Resource>> entry : categoryResources.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            String path = fileId.getPath();
            String idPath = path.substring("fieldguide/categories/".length(), path.length() - ".json".length());
            ResourceLocation defaultCategoryId = ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), idPath);

            for (Resource resource : entry.getValue()) {
                try (Reader reader = resource.openAsReader()) {
                    JsonObject json = GsonHelper.parse(reader);

                    ResourceLocation categoryId = defaultCategoryId;
                    if (json.has("target_category")) {
                        categoryId = ResourceLocation.parse(GsonHelper.getAsString(json, "target_category"));
                    }

                    Category category = data.categories.computeIfAbsent(categoryId, Category::new);

                    if (GsonHelper.getAsBoolean(json, "replace", false)) {
                        category.getEntries().clear();
                    }

                    if (json.has("sort_index")) {
                        category.setSortIndex(GsonHelper.getAsInt(json, "sort_index"));
                    }

                    if (json.has("icon")) {
                        category.setIcon(ResourceLocation.parse(GsonHelper.getAsString(json, "icon")));
                    }

                    if (json.has("group_by")) {
                        JsonArray groupBy = GsonHelper.getAsJsonArray(json, "group_by");
                        List<String> queries = new ArrayList<>(groupBy.size());
                        for (JsonElement query : groupBy) {
                            queries.add(query.getAsString());
                        }

                        category.setGroupByQueries(queries);
                    }

                    if (json.has("contents")) {
                        JsonArray contents = GsonHelper.getAsJsonArray(json, "contents");
                        for (JsonElement el : contents) {
                            JsonObject obj = el.getAsJsonObject();
                            String typeStr = GsonHelper.getAsString(obj, "type");

                            switch (typeStr) {
                                case "entry" -> {
                                    ResourceLocation id = ResourceLocation.parse(GsonHelper.getAsString(obj, "id"));
                                    category.addEntry(new CategoryEntry(CategoryEntry.Type.ENTRY, id, id, null, null, null, null));
                                }
                                case "auto_populate" -> {
                                    String strategy = GsonHelper.getAsString(obj, "strategy");
                                    category.addEntry(new CategoryEntry(CategoryEntry.Type.AUTO_POPULATE, null, null, strategy, null, null, null));
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Constants.LOG.error("Failed to load category: {}", fileId, e);
                }
            }
        }

        Map<ResourceLocation, List<Resource>> compositeResources = resourceManager.listResourceStacks(
                "fieldguide/composites",
                id -> id.getPath().endsWith(".json")
        );

        for (Map.Entry<ResourceLocation, List<Resource>> entry : compositeResources.entrySet()) {
            for (Resource resource : entry.getValue()) {
                try (Reader reader = resource.openAsReader()) {
                    JsonObject json = GsonHelper.parse(reader);
                    if (json.has("values")) {
                        for (JsonElement el : GsonHelper.getAsJsonArray(json, "values")) {
                            JsonObject obj = el.getAsJsonObject();
                            ResourceLocation id = ResourceLocation.parse(GsonHelper.getAsString(obj, "id"));
                            ResourceLocation displayId = obj.has("display") ? ResourceLocation.parse(GsonHelper.getAsString(obj, "display")) : id;

                            List<ResourceLocation> components = new ArrayList<>();
                            if (obj.has("components")) {
                                for (JsonElement comp : GsonHelper.getAsJsonArray(obj, "components")) {
                                    components.add(ResourceLocation.parse(comp.getAsString()));
                                }
                            }

                            ResourceLocation structureNbt = obj.has("structure_nbt") ? ResourceLocation.parse(GsonHelper.getAsString(obj, "structure_nbt")) : null;

                            List<String> stackedBlocks = null;
                            if (obj.has("render")) {
                                stackedBlocks = new ArrayList<>();
                                for (JsonElement el2 : GsonHelper.getAsJsonArray(obj, "render")) {
                                    stackedBlocks.add(el2.getAsString());
                                }
                            }

                            data.composites.add(new CompositeDefinition(id, displayId, components, structureNbt, stackedBlocks));
                        }
                    }
                } catch (Exception e) {
                    Constants.LOG.error("Failed to load composite: {}", entry.getKey(), e);
                }
            }
        }

        loadModifiers(resourceManager, "fieldguide/biome_modifiers", data.biomeAdditions, data.biomeRemovals);
        loadModifiers(resourceManager, "fieldguide/loot_modifiers", data.lootAdditions, data.lootRemovals);

        Map<ResourceLocation, List<Resource>> redirectResources = resourceManager.listResourceStacks(
                "fieldguide/redirects",
                id -> id.getPath().endsWith(".json")
        );
        for (Map.Entry<ResourceLocation, List<Resource>> entry : redirectResources.entrySet()) {
            for (Resource resource : entry.getValue()) {
                try (Reader reader = resource.openAsReader()) {
                    JsonObject json = GsonHelper.parse(reader);
                    if (json.has("entries")) {
                        for (JsonElement el : GsonHelper.getAsJsonArray(json, "entries")) {
                            JsonObject obj = el.getAsJsonObject();
                            ResourceLocation source = ResourceLocation.parse(GsonHelper.getAsString(obj, "source"));
                            ResourceLocation target = ResourceLocation.parse(GsonHelper.getAsString(obj, "target"));
                            data.redirects.put(source, target);
                        }
                    }
                } catch (Exception e) {
                    Constants.LOG.error("Failed to load redirect: {}", entry.getKey(), e);
                }
            }
        }

        return data;
    }

    private void generateAutoBiomeAdditions(MinecraftServer server) {
        Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
        for (var biomeEntry : biomeRegistry.entrySet()) {
            ResourceLocation biomeId = biomeEntry.getKey().location();
            Biome biome = biomeEntry.getValue();

            for (MobCategory cat : MobCategory.values()) {
                for (var spawn : biome.getMobSettings().getMobs(cat).unwrap()) {
                    ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(spawn.type);
                    String addition = entityId + "|" + biomeId;
                    if (!this.biomeAdditions.contains(addition)) {
                        this.biomeAdditions.add(addition);
                    }
                }
            }
        }
    }

    private void loadModifiers(ResourceManager resourceManager, String path, List<String> additions, List<String> removals) {
        Map<ResourceLocation, List<Resource>> resources = resourceManager.listResourceStacks(path, id -> id.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, List<Resource>> entry : resources.entrySet()) {
            for (Resource resource : entry.getValue()) {
                try (Reader reader = resource.openAsReader()) {
                    JsonObject json = GsonHelper.parse(reader);

                    if (GsonHelper.getAsBoolean(json, "replace", false)) {
                        additions.clear();
                        removals.clear();
                    }

                    if (json.has("additions")) {
                        for (JsonElement el : GsonHelper.getAsJsonArray(json, "additions")) {
                            JsonObject obj = el.getAsJsonObject();
                            List<String> entryList = getAsList(obj, "entry", "entries");
                            List<String> valueList = getAsList(obj, "value", "values");

                            for (String e : entryList) {
                                for (String v : valueList) {
                                    additions.add(e + "|" + v);
                                }
                            }
                        }
                    }
                    if (json.has("removals")) {
                        for (JsonElement el : GsonHelper.getAsJsonArray(json, "removals")) {
                            JsonObject obj = el.getAsJsonObject();
                            List<String> entryList = getAsList(obj, "entry", "entries");
                            List<String> valueList = getAsList(obj, "value", "values");

                            for (String e : entryList) {
                                for (String v : valueList) {
                                    removals.add(e + "|" + v);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Constants.LOG.error("Failed to load modifiers from {}", entry.getKey(), e);
                }
            }
        }
    }

    private List<String> getAsList(JsonObject obj, String singular, String plural) {
        List<String> list = new ArrayList<>();
        if (obj.has(singular)) {
            JsonElement el = obj.get(singular);
            if (el.isJsonArray()) {
                for (JsonElement e : el.getAsJsonArray()) list.add(e.getAsString());
            } else {
                list.add(el.getAsString());
            }
        }
        if (obj.has(plural)) {
            JsonElement el = obj.get(plural);
            if (el.isJsonArray()) {
                for (JsonElement e : el.getAsJsonArray()) list.add(e.getAsString());
            } else {
                list.add(el.getAsString());
            }
        }
        return list;
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
        generateAutoBiomeAdditions(server);

        syncToAll(server);
    }

    @Override
    protected void apply(@NotNull ReloadData data, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        ModConfig.load();
        this.categories = data.categories;
        this.composites = data.composites;
        this.biomeAdditions = data.biomeAdditions;
        this.biomeRemovals = data.biomeRemovals;
        this.lootAdditions = data.lootAdditions;
        this.lootRemovals = data.lootRemovals;
        this.redirects = data.redirects;
    }

    public static class ReloadData {
        public Map<ResourceLocation, Category> categories = new LinkedHashMap<>();
        public List<CompositeDefinition> composites = new ArrayList<>();
        public List<String> biomeAdditions = new ArrayList<>();
        public List<String> biomeRemovals = new ArrayList<>();
        public List<String> lootAdditions = new ArrayList<>();
        public List<String> lootRemovals = new ArrayList<>();
        public Map<ResourceLocation, ResourceLocation> redirects = new HashMap<>();
    }
}