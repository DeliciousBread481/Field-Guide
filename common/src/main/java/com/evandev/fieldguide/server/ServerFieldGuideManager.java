package com.evandev.fieldguide.server;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.api.*;
import com.evandev.fieldguide.compat.cobblemon.FieldGuideCobblemonCompat;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.network.ExportContentPacket;
import com.evandev.fieldguide.network.SyncCategoriesPacket;
import com.evandev.fieldguide.network.SyncLootPacket;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.util.EntryResolver;
import com.evandev.fieldguide.util.entry.EntryResolutionHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
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
import net.minecraft.world.item.Item;
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
    private Map<ResourceLocation, List<DatapackVariant>> variants = new HashMap<>();

    private List<String> biomeAdditions = new ArrayList<>();
    private List<String> biomeRemovals = new ArrayList<>();
    private List<String> lootAdditions = new ArrayList<>();
    private List<String> lootRemovals = new ArrayList<>();

    private List<String> prefixedBiomeAdditions = new ArrayList<>();
    private List<String> prefixedLootAdditions = new ArrayList<>();

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

    public boolean hasEntry(ResourceLocation entryId) {
        return EntryResolver.hasEntry(resolvedCategoryEntries, entryId);
    }

    public boolean isKillToUnlock(ResourceLocation entryId) {
        TagKey<EntityType<?>> killToUnlockTag = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Constants.MOD_ID, "kill_to_unlock"));
        TagKey<EntityType<?>> bossesTag = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Constants.MOD_ID, "bosses"));

        return BuiltInRegistries.ENTITY_TYPE.getOptional(entryId)
                .flatMap(BuiltInRegistries.ENTITY_TYPE::getResourceKey)
                .flatMap(BuiltInRegistries.ENTITY_TYPE::getHolder)
                .map(h -> h.is(killToUnlockTag) || h.is(bossesTag))
                .orElse(false);
    }

    public ResourceLocation getCategoryForEntryId(ResourceLocation entryId) {
        return EntryResolver.getCategoryForEntryId(resolvedCategoryEntries, entryId);
    }

    public Set<ResourceLocation> getAllEntryIds() {
        return EntryResolver.getAllEntryIds(resolvedCategoryEntries);
    }

    public Set<ResourceLocation> getEntryIdsForCategory(ResourceLocation categoryId) {
        return EntryResolver.getEntryIdsForCategory(resolvedCategoryEntries, categoryId);
    }

    public List<CompositeDefinition> getComposites() {
        return composites;
    }

    public Map<ResourceLocation, List<DatapackVariant>> getVariants() {
        return variants;
    }

    public List<Object> getEntriesForTarget(Object target) {
        return EntryResolver.getEntriesForTarget(resolvedCategoryEntries, target);
    }

    public boolean isTargetInEntry(ResourceLocation targetId, ResourceLocation entryId) {
        return EntryResolver.isTargetInEntry(resolvedCategoryEntries, targetId, entryId);
    }

    private void calculatePrefixedLists() {
        this.prefixedBiomeAdditions = biomeAdditions.stream().map(s -> {
            String[] parts = s.split("\\|", 2);
            if (parts.length == 2 && !parts[0].contains(":")) {
                Optional<Object> entry = EntryResolutionHelper.resolveSingleEntry(new ResourceLocation(parts[0]), null, null);
                if (entry.isPresent()) return AutoPopulateRegistry.getEntryId(entry.get(), true) + "|" + parts[1];
            }
            return s;
        }).toList();

        this.prefixedLootAdditions = lootAdditions.stream().map(s -> {
            String[] parts = s.split("\\|", 2);
            if (parts.length == 2 && !parts[0].contains(":")) {
                Optional<Object> entry = EntryResolutionHelper.resolveSingleEntry(new ResourceLocation(parts[0]), null, null);
                if (entry.isPresent()) return AutoPopulateRegistry.getEntryId(entry.get(), true) + "|" + parts[1];
            }
            return s;
        }).toList();
    }

    public void syncToPlayer(ServerPlayer player) {
        List<Category> flattenedCategories = new ArrayList<>();
        int maxEntriesPerChunk = 100;

        for (Category rawCat : categories.values()) {
            List<Object> resolved = resolvedCategoryEntries.get(rawCat.getId());

            if (resolved == null || resolved.isEmpty()) {
                Category flatCat = new Category(rawCat.getId());
                flatCat.setSortIndex(rawCat.getSortIndex());
                flatCat.setIcon(rawCat.getIcon());
                flatCat.setGroupByQueries(rawCat.getGroupByQueries());
                flattenedCategories.add(flatCat);
                continue;
            }

            int index = 0;
            while (index < resolved.size()) {
                Category chunkCat = new Category(rawCat.getId());
                chunkCat.setSortIndex(rawCat.getSortIndex());
                chunkCat.setIcon(rawCat.getIcon());

                if (index == 0) {
                    chunkCat.setGroupByQueries(rawCat.getGroupByQueries());
                }

                int endIndex = Math.min(index + maxEntriesPerChunk, resolved.size());
                for (int j = index; j < endIndex; j++) {
                    Object obj = resolved.get(j);
                    if (obj instanceof EntityType<?> type) {
                        ResourceLocation id = AutoPopulateRegistry.getEntryId(type, true);
                        chunkCat.addEntry(new CategoryEntry(CategoryEntry.CategoryType.ENTRY, id, id, "animals", null, null, null));
                    } else if (obj instanceof Block block) {
                        ResourceLocation id = AutoPopulateRegistry.getEntryId(block, true);
                        chunkCat.addEntry(new CategoryEntry(CategoryEntry.CategoryType.ENTRY, id, id, "plants", null, null, null));
                    } else if (obj instanceof Item item) {
                        ResourceLocation id = AutoPopulateRegistry.getEntryId(item, true);
                        chunkCat.addEntry(new CategoryEntry(CategoryEntry.CategoryType.ENTRY, id, id, "mod_items", null, null, null));
                    } else if (obj instanceof CompositeFieldGuideEntry composite) {
                        ResourceLocation id = composite.id();
                        Object displayEntry = composite.displayEntry();
                        List<Object> components = composite.components();
                        ResourceLocation structureNbt = composite.structureNbt();
                        List<String> stackedBlocks = composite.stackedBlocks();

                        List<ResourceLocation> compIds = new ArrayList<>();
                        if (components != null) {
                            for (Object c : components) {
                                compIds.add(AutoPopulateRegistry.getEntryId(c, true));
                            }
                        }

                        ResourceLocation displayId = AutoPopulateRegistry.getEntryId(displayEntry, true);
                        chunkCat.addEntry(new CategoryEntry(CategoryEntry.CategoryType.COMPOSITE, id, displayId, null, compIds, structureNbt, stackedBlocks));
                    }
                }
                flattenedCategories.add(chunkCat);
                index += maxEntriesPerChunk;
            }
        }

        Services.NETWORK.sendToPlayer(new SyncCategoriesPacket(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), variants, true, false), player);

        int maxModifiersChunkSize = 500;

        for (int i = 0; i < prefixedBiomeAdditions.size(); i += maxModifiersChunkSize) {
            List<String> chunk = prefixedBiomeAdditions.subList(i, Math.min(i + maxModifiersChunkSize, prefixedBiomeAdditions.size()));
            Services.NETWORK.sendToPlayer(new SyncCategoriesPacket(Collections.emptyList(), chunk, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false, false), player);
        }

        for (int i = 0; i < biomeRemovals.size(); i += maxModifiersChunkSize) {
            List<String> chunk = biomeRemovals.subList(i, Math.min(i + maxModifiersChunkSize, biomeRemovals.size()));
            Services.NETWORK.sendToPlayer(new SyncCategoriesPacket(Collections.emptyList(), Collections.emptyList(), chunk, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false, false), player);
        }

        for (int i = 0; i < prefixedLootAdditions.size(); i += maxModifiersChunkSize) {
            List<String> chunk = prefixedLootAdditions.subList(i, Math.min(i + maxModifiersChunkSize, prefixedLootAdditions.size()));
            Services.NETWORK.sendToPlayer(new SyncCategoriesPacket(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), chunk, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false, false), player);
        }

        for (int i = 0; i < lootRemovals.size(); i += maxModifiersChunkSize) {
            List<String> chunk = lootRemovals.subList(i, Math.min(i + maxModifiersChunkSize, lootRemovals.size()));
            Services.NETWORK.sendToPlayer(new SyncCategoriesPacket(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), chunk, Collections.emptyMap(), Collections.emptyMap(), false, false), player);
        }

        if (!redirects.isEmpty()) {
            Map<ResourceLocation, ResourceLocation> redChunk = new HashMap<>();
            int count = 0;
            for (Map.Entry<ResourceLocation, ResourceLocation> entry : redirects.entrySet()) {
                redChunk.put(entry.getKey(), entry.getValue());
                count++;
                if (count >= maxModifiersChunkSize) {
                    Services.NETWORK.sendToPlayer(new SyncCategoriesPacket(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), redChunk, Collections.emptyMap(), false, false), player);
                    redChunk = new HashMap<>();
                    count = 0;
                }
            }
            if (!redChunk.isEmpty()) {
                Services.NETWORK.sendToPlayer(new SyncCategoriesPacket(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), redChunk, Collections.emptyMap(), false, false), player);
            }
        }

        if (flattenedCategories.isEmpty()) {
            Services.NETWORK.sendToPlayer(new SyncCategoriesPacket(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false, true), player);
        } else {
            for (int i = 0; i < flattenedCategories.size(); i++) {
                List<Category> chunk = Collections.singletonList(flattenedCategories.get(i));
                boolean isLast = (i == flattenedCategories.size() - 1);
                Services.NETWORK.sendToPlayer(new SyncCategoriesPacket(chunk, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(), false, isLast), player);
            }
        }

        if (!serverLootCache.isEmpty()) {
            Map<ResourceLocation, List<ItemStack>> chunk = new HashMap<>();
            int count = 0;
            int maxChunkSize = 15;
            boolean isFirstLoot = true;

            for (var entry : serverLootCache.entrySet()) {
                chunk.put(entry.getKey(), entry.getValue());
                count++;

                if (count >= maxChunkSize) {
                    Services.NETWORK.sendToPlayer(new SyncLootPacket(new HashMap<>(chunk), isFirstLoot), player);
                    chunk.clear();
                    count = 0;
                    isFirstLoot = false;
                }
            }

            if (!chunk.isEmpty()) {
                Services.NETWORK.sendToPlayer(new SyncLootPacket(chunk, isFirstLoot), player);
            }
        }
    }

    private void resolveAllCategories() {
        resolvedCategoryEntries.clear();
        Set<String> allCompositeComponents = new HashSet<>();

        for (Category cat : categories.values()) {
            List<Object> entries = EntryResolver.resolveCategoryEntries(cat, composites, redirects);
            for (Object entry : entries) {
                if (entry instanceof CompositeFieldGuideEntry composite) {
                    if (composite.components() != null) {
                        for (Object comp : composite.components()) {
                            allCompositeComponents.add(AutoPopulateRegistry.getEntryKey(comp));
                        }
                    }
                    if (composite.displayEntry() != null) {
                        allCompositeComponents.add(AutoPopulateRegistry.getEntryKey(composite.displayEntry()));
                    }
                }
            }
            resolvedCategoryEntries.put(cat.getId(), entries);
        }

        for (List<Object> entries : resolvedCategoryEntries.values()) {
            entries.removeIf(entry -> !(entry instanceof CompositeFieldGuideEntry) && allCompositeComponents.contains(AutoPopulateRegistry.getEntryKey(entry)));
        }
    }

    public void onServerStarted(MinecraftServer server) {
        resolveAllCategories();
        this.serverLootCache = LootTableHelper.generateLootMap(server.overworld());
        expandBiomeTags(server);
        generateAutoBiomeAdditions(server);
        calculatePrefixedLists();
    }

    public void reload(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Services.NETWORK.sendToPlayer(new ExportContentPacket("reload_cache"), player);
        }

        resolveAllCategories();
        this.serverLootCache = LootTableHelper.generateLootMap(server.overworld());
        expandBiomeTags(server);
        generateAutoBiomeAdditions(server);
        calculatePrefixedLists();

        syncToAll(server);
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
            ResourceLocation defaultCategoryId = new ResourceLocation(fileId.getNamespace(), idPath);

            for (Resource resource : entry.getValue()) {
                try (Reader reader = resource.openAsReader()) {
                    JsonObject json = GsonHelper.parse(reader);

                    ResourceLocation categoryId = defaultCategoryId;
                    if (json.has("target_category")) {
                        categoryId = new ResourceLocation(GsonHelper.getAsString(json, "target_category"));
                    }

                    Category category = data.categories.computeIfAbsent(categoryId, Category::new);

                    if (GsonHelper.getAsBoolean(json, "replace", false)) {
                        category.getEntries().clear();
                    }

                    if (json.has("sort_index")) {
                        category.setSortIndex(GsonHelper.getAsInt(json, "sort_index"));
                    }

                    if (json.has("icon")) {
                        category.setIcon(new ResourceLocation(GsonHelper.getAsString(json, "icon")));
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
                                    ResourceLocation id = new ResourceLocation(GsonHelper.getAsString(obj, "id"));
                                    category.addEntry(new CategoryEntry(CategoryEntry.CategoryType.ENTRY, id, id, null, null, null, null));
                                }
                                case "auto_populate" -> {
                                    String strategy = GsonHelper.getAsString(obj, "strategy");
                                    category.addEntry(new CategoryEntry(CategoryEntry.CategoryType.AUTO_POPULATE, null, null, strategy, null, null, null));
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
                            ResourceLocation id = new ResourceLocation(GsonHelper.getAsString(obj, "id"));
                            ResourceLocation displayId = obj.has("display") ? new ResourceLocation(GsonHelper.getAsString(obj, "display")) : id;

                            List<ResourceLocation> components = new ArrayList<>();
                            if (obj.has("components")) {
                                for (JsonElement comp : GsonHelper.getAsJsonArray(obj, "components")) {
                                    components.add(new ResourceLocation(comp.getAsString()));
                                }
                            }

                            ResourceLocation structureNbt = obj.has("structure_nbt") ? new ResourceLocation(GsonHelper.getAsString(obj, "structure_nbt")) : null;

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
                            ResourceLocation source = new ResourceLocation(GsonHelper.getAsString(obj, "source"));
                            ResourceLocation target = new ResourceLocation(GsonHelper.getAsString(obj, "target"));
                            data.redirects.put(source, target);
                        }
                    }
                } catch (Exception e) {
                    Constants.LOG.error("Failed to load redirect: {}", entry.getKey(), e);
                }
            }
        }

        if (Services.PLATFORM.isModLoaded("cobblemon")) {
            FieldGuideCobblemonCompat.injectCategory(data.categories, resourceManager);
        }

        Map<ResourceLocation, List<Resource>> variantResources = resourceManager.listResourceStacks(
                "fieldguide/variants",
                id -> id.getPath().endsWith(".json")
        );

        for (Map.Entry<ResourceLocation, List<Resource>> entry : variantResources.entrySet()) {
            for (Resource resource : entry.getValue()) {
                try (Reader reader = resource.openAsReader()) {
                    JsonObject json = GsonHelper.parse(reader);

                    if (json.has("entries")) {
                        for (JsonElement el : GsonHelper.getAsJsonArray(json, "entries")) {
                            JsonObject obj = el.getAsJsonObject();
                            ResourceLocation entityId = new ResourceLocation(GsonHelper.getAsString(obj, "id"));

                            List<DatapackVariant> variantList = data.variants.computeIfAbsent(entityId, k -> new ArrayList<>());

                            if (GsonHelper.getAsBoolean(obj, "replace", false)) {
                                variantList.clear();
                            }

                            if (obj.has("variants")) {
                                for (JsonElement vEl : GsonHelper.getAsJsonArray(obj, "variants")) {
                                    JsonObject vObj = vEl.getAsJsonObject();
                                    String id = GsonHelper.getAsString(vObj, "id");
                                    CompoundTag nbt = TagParser.parseTag(GsonHelper.getAsString(vObj, "nbt"));
                                    variantList.add(new DatapackVariant(id, nbt));
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Constants.LOG.error("Failed to load variant: {}", entry.getKey(), e);
                }
            }
        }

        return data;
    }

    private void generateAutoBiomeAdditions(MinecraftServer server) {
        Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);
        Set<String> additionsSet = new LinkedHashSet<>(this.biomeAdditions);

        for (var biomeEntry : biomeRegistry.entrySet()) {
            try {
                ResourceLocation biomeId = biomeEntry.getKey().location();
                Biome biome = biomeEntry.getValue();

                for (MobCategory cat : MobCategory.values()) {
                    for (var spawn : biome.getMobSettings().getMobs(cat).unwrap()) {
                        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(spawn.type);
                        additionsSet.add(entityId + "|" + biomeId);
                    }
                }
            } catch (IllegalStateException e) {
                Constants.LOG.warn("Skipping unbound biome in registry: {}", biomeEntry.getKey().location());
            }
        }

        this.biomeAdditions = new ArrayList<>(additionsSet);
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

    private void expandBiomeTags(MinecraftServer server) {
        Registry<Biome> biomeRegistry = server.registryAccess().registryOrThrow(Registries.BIOME);

        this.biomeAdditions = expandTagsForList(this.biomeAdditions, biomeRegistry);
        this.biomeRemovals = expandTagsForList(this.biomeRemovals, biomeRegistry);
    }

    private List<String> expandTagsForList(List<String> list, Registry<Biome> biomeRegistry) {
        Set<String> expanded = new LinkedHashSet<>();

        for (String item : list) {
            String[] parts = item.split("\\|", 2);
            if (parts.length == 2 && parts[1].startsWith("#")) {
                String tagPath = parts[1].substring(1);
                try {
                    TagKey<Biome> tagKey = TagKey.create(Registries.BIOME, new ResourceLocation(tagPath));
                    biomeRegistry.getTagOrEmpty(tagKey).forEach(holder -> {
                        holder.unwrapKey().ifPresent(key -> {
                            expanded.add(parts[0] + "|" + key.location());
                        });
                    });
                } catch (Exception e) {
                    Constants.LOG.error("Failed to expand biome tag: {}", parts[1], e);
                    expanded.add(item);
                }
            } else {
                expanded.add(item);
            }
        }

        return new ArrayList<>(expanded);
    }

    public void syncToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncToPlayer(player);
        }
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
        this.variants = data.variants;
    }

    public static class ReloadData {
        public Map<ResourceLocation, Category> categories = new LinkedHashMap<>();
        public List<CompositeDefinition> composites = new ArrayList<>();
        public List<String> biomeAdditions = new ArrayList<>();
        public List<String> biomeRemovals = new ArrayList<>();
        public List<String> lootAdditions = new ArrayList<>();
        public List<String> lootRemovals = new ArrayList<>();
        public Map<ResourceLocation, ResourceLocation> redirects = new HashMap<>();
        public Map<ResourceLocation, List<DatapackVariant>> variants = new HashMap<>();
    }
}
