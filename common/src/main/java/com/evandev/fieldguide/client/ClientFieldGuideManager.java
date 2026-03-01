package com.evandev.fieldguide.client;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.data.EntryVisual;
import com.evandev.fieldguide.client.data.JournalPage;
import com.evandev.fieldguide.client.gui.util.EntryRenderHelper;
import com.evandev.fieldguide.client.progress.ProgressManager;
import com.evandev.fieldguide.client.scanning.FieldGuideScanner;
import com.evandev.fieldguide.client.search.SearchManager;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.CompositeFieldGuideEntry;
import com.evandev.fieldguide.util.EntryResolver;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.io.Reader;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class ClientFieldGuideManager implements ResourceManagerReloadListener {
    private static final ClientFieldGuideManager INSTANCE = new ClientFieldGuideManager();

    private final Map<ResourceLocation, Category> syncedCategories = new LinkedHashMap<>();
    private final Map<ResourceLocation, List<Object>> resolvedCategoryEntries = new HashMap<>();
    private final Map<ResourceLocation, EntryVisual> entryVisuals = new HashMap<>();
    private final Map<Object, List<ItemStack>> dropCache = new HashMap<>();
    private final Map<ResourceLocation, ResourceLocation> redirects = new HashMap<>();

    private final List<String> biomeAdditions = new ArrayList<>();
    private final List<String> biomeRemovals = new ArrayList<>();
    private final List<String> lootAdditions = new ArrayList<>();
    private final List<String> lootRemovals = new ArrayList<>();

    private ClientFieldGuideManager() {
    }

    public static ClientFieldGuideManager getInstance() {
        return INSTANCE;
    }

    public static void clearCache() {
        INSTANCE.dropCache.clear();
        INSTANCE.resolvedCategoryEntries.clear();
        EntryRenderHelper.clearCache();
        INSTANCE.resolveAllEntries();
    }

    public static ResourceLocation getEntryId(Object entry) {
        return EntryResolver.getEntryId(entry);
    }

    public static boolean hideFromSearch(Object entry) {
        return ModConfig.get().hideUndiscoveredFromSearch && !isUnlocked(entry);
    }

    public static boolean isUnlocked(Object entry) {
        return ProgressManager.getInstance().isUnlocked(entry);
    }

    public static boolean isNew(Object entry) {
        return ProgressManager.getInstance().isNew(entry);
    }

    public static void markAsSeen(Object entry) {
        ProgressManager.getInstance().markAsSeen(entry);
    }

    public static String getEntryDescription(Object entry) {
        ResourceLocation id = getEntryId(entry);
        if (id == null) return "";
        String custom = ProgressManager.getInstance().getCustomDescription(entry);
        if (custom != null) return custom;

        String overrideKey = "fieldguide." + id.getNamespace() + "." + id.getPath() + ".description";

        Object coreEntry = entry instanceof CompositeFieldGuideEntry composite ? composite.displayEntry() : entry;
        String fallbackKey = (coreEntry instanceof EntityType) ? "entity." + id.getNamespace() + "." + id.getPath() + ".description" : "lore." + id.getNamespace() + "." + id.getPath();
        return I18n.exists(overrideKey) ? I18n.get(overrideKey) : (I18n.exists(fallbackKey) ? I18n.get(fallbackKey) : I18n.get("fieldguide.description.missing"));
    }

    public static void setCustomDescription(Object entry, String desc) {
        ProgressManager.getInstance().setCustomDescription(entry, desc);
    }

    public static void setCustomName(Object entry, String name) {
        ProgressManager.getInstance().setCustomName(entry, name);
    }

    public static Component getEntryName(Object entry) {
        String custom = ProgressManager.getInstance().getCustomName(entry);
        if (custom != null) return Component.literal(custom);

        return getDefaultNameComponent(entry);
    }

    public static String getDefaultName(Object entry) {
        return getDefaultNameComponent(entry).getString();
    }

    private static Component getDefaultNameComponent(Object entry) {
        ResourceLocation id = getEntryId(entry);
        if (id != null) {
            String overrideKey = "fieldguide.name." + id.getNamespace() + "." + id.getPath();
            if (I18n.exists(overrideKey)) {
                return Component.translatable(overrideKey);
            }
        }

        Object coreEntry = entry instanceof CompositeFieldGuideEntry composite ? composite.displayEntry() : entry;

        if (entry instanceof CompositeFieldGuideEntry && id != null && id.getPath().endsWith("_tree")) {
            if (coreEntry instanceof Block block) {
                String saplingName = block.getName().getString();
                return Component.literal(saplingName.replace("Sapling", "Tree"));
            }
        }

        if (coreEntry instanceof EntityType<?> type) return type.getDescription();
        if (coreEntry instanceof Block block) return block.getName();

        return Component.translatable("fieldguide.unknown");
    }

    public static Map<ResourceLocation, Category> getCategories() {
        return INSTANCE.syncedCategories;
    }

    public static List<Object> getValidEntries() {
        return INSTANCE.resolvedCategoryEntries.values().stream().flatMap(List::stream).distinct().collect(Collectors.toList());
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

    public void updateModifiers(List<String> biomeAdditions, List<String> biomeRemovals, List<String> lootAdditions, List<String> lootRemovals) {
        this.biomeAdditions.clear();
        this.biomeAdditions.addAll(biomeAdditions);
        this.biomeRemovals.clear();
        this.biomeRemovals.addAll(biomeRemovals);
        this.lootAdditions.clear();
        this.lootAdditions.addAll(lootAdditions);
        this.lootRemovals.clear();
        this.lootRemovals.addAll(lootRemovals);
    }

    public Object getEntryForTarget(Object target) {
        List<Object> entries = getEntriesForTarget(target);
        if (entries.isEmpty()) return null;

        for (Object entry : entries) {
            if (entry.equals(target)) return entry;
            if (entry instanceof CompositeFieldGuideEntry composite) {
                if (composite.displayEntry() != null && composite.displayEntry().equals(target)) {
                    return entry;
                }
            }
        }

        return entries.get(0);
    }

    public List<Object> getEntriesForTarget(Object target) {
        List<Object> matches = new ArrayList<>();
        for (Object entry : getValidEntries()) {
            if (entry.equals(target)) {
                matches.add(entry);
            } else if (entry instanceof CompositeFieldGuideEntry composite) {
                if ((composite.displayEntry() != null && composite.displayEntry().equals(target)) ||
                        (composite.components() != null && composite.components().contains(target))) {
                    matches.add(entry);
                }
            }
        }
        return matches;
    }

    public String getJournalTitle() {
        return ProgressManager.getInstance().getJournalTitle();
    }

    public void setJournalTitle(String title) {
        ProgressManager.getInstance().setJournalTitle(title);
    }

    public void saveJournal() {
        ProgressManager.getInstance().saveJournal();
    }

    public List<JournalPage> getJournalPages() {
        return ProgressManager.getInstance().getJournalPages();
    }

    public void exportToLang(String type) {
        ProgressManager.getInstance().exportToLang(type);
    }

    public void updateCategoriesFromServer(List<Category> categories, Map<ResourceLocation, ResourceLocation> redirects, boolean clearCache, boolean resolveEntries) {
        if (clearCache) {
            this.redirects.clear();
            this.syncedCategories.clear();
        }

        this.redirects.putAll(redirects);

        for (Category cat : categories) {
            this.syncedCategories.put(cat.getId(), cat);
        }

        if (resolveEntries) {
            List<Category> sorted = new ArrayList<>(this.syncedCategories.values());
            sorted.sort(Comparator.comparingInt(Category::getSortIndex).thenComparing(Category::getId));
            this.syncedCategories.clear();
            for (Category cat : sorted) this.syncedCategories.put(cat.getId(), cat);

            resolveAllEntries();
        }
    }

    public void updateModifiers(List<String> biomeAdditions, List<String> biomeRemovals, List<String> lootAdditions, List<String> lootRemovals, boolean clearCache) {
        if (clearCache) {
            this.biomeAdditions.clear();
            this.biomeRemovals.clear();
            this.lootAdditions.clear();
            this.lootRemovals.clear();
        }

        if (!biomeAdditions.isEmpty()) this.biomeAdditions.addAll(biomeAdditions);
        if (!biomeRemovals.isEmpty()) this.biomeRemovals.addAll(biomeRemovals);
        if (!lootAdditions.isEmpty()) this.lootAdditions.addAll(lootAdditions);
        if (!lootRemovals.isEmpty()) this.lootRemovals.addAll(lootRemovals);
    }

    public void updateLootCache(Map<ResourceLocation, List<ItemStack>> lootCache) {
        for (Map.Entry<ResourceLocation, List<ItemStack>> entry : lootCache.entrySet()) {
            ResourceLocation id = entry.getKey();
            List<ItemStack> drops = entry.getValue();
            BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(type -> dropCache.put(type, drops));
            BuiltInRegistries.BLOCK.getOptional(id).ifPresent(block -> dropCache.put(block, drops));
        }
    }

    public EntryVisual getEntryVisual(ResourceLocation entryId) {
        return entryVisuals.getOrDefault(entryId, new EntryVisual());
    }

    public ResourceLocation getRedirect(ResourceLocation source) {
        return redirects.get(source);
    }

    @Override
    public void onResourceManagerReload(@NotNull ResourceManager resourceManager) {
        ModConfig.load();
        entryVisuals.clear();
        EntryRenderHelper.clearCache();

        loadVisuals(resourceManager, (derivedId, json) -> {
            ResourceLocation targetId = json.has("id") ? new ResourceLocation(GsonHelper.getAsString(json, "id")) : derivedId;
            EntryVisual visual = new EntryVisual();
            if (json.has("custom_sound"))
                visual.customSound = new ResourceLocation(GsonHelper.getAsString(json, "custom_sound"));
            if (json.has("alignment_icon"))
                visual.alignmentIcon = new ResourceLocation(GsonHelper.getAsString(json, "alignment_icon"));
            if (json.has("scale")) visual.scale = GsonHelper.getAsFloat(json, "scale");
            if (json.has("y_offset")) visual.yOffset = GsonHelper.getAsFloat(json, "y_offset");
            if (json.has("x_offset")) visual.xOffset = GsonHelper.getAsFloat(json, "x_offset");
            if (json.has("grid_scale")) visual.gridScale = GsonHelper.getAsFloat(json, "grid_scale");
            if (json.has("grid_y_offset")) visual.gridYOffset = GsonHelper.getAsFloat(json, "grid_y_offset");
            if (json.has("grid_x_offset")) visual.gridXOffset = GsonHelper.getAsFloat(json, "grid_x_offset");
            if (json.has("page_scale")) visual.pageScale = GsonHelper.getAsFloat(json, "page_scale");
            if (json.has("page_y_offset")) visual.pageYOffset = GsonHelper.getAsFloat(json, "page_y_offset");
            if (json.has("page_x_offset")) visual.pageXOffset = GsonHelper.getAsFloat(json, "page_x_offset");
            if (json.has("spawn_biomes")) {
                visual.spawnBiomes = new ArrayList<>();
                GsonHelper.getAsJsonArray(json, "spawn_biomes").forEach(el -> visual.spawnBiomes.add(new ResourceLocation(el.getAsString())));
            }
            entryVisuals.put(targetId, visual);
        });

        resolveAllEntries();
    }

    private void loadVisuals(ResourceManager mgr, BiConsumer<ResourceLocation, JsonObject> processor) {
        mgr.listResourceStacks("fieldguide/entries", id -> id.getPath().endsWith(".json")).forEach((fileId, resources) -> {
            String path = fileId.getPath();
            String idPath = path.substring(("fieldguide/entries" + "/").length(), path.length() - ".json".length());
            ResourceLocation targetId = new ResourceLocation(fileId.getNamespace(), idPath);
            resources.forEach(resource -> {
                try (Reader reader = resource.openAsReader()) {
                    processor.accept(targetId, GsonHelper.parse(reader));
                } catch (Exception e) {
                    Constants.LOG.error("Error loading entry visuals: {}", fileId, e);
                }
            });
        });
    }

    private void resolveAllEntries() {
        resolvedCategoryEntries.clear();
        ModConfig config = ModConfig.get();

        syncedCategories.values().forEach(category -> {
            List<Object> entries = EntryResolver.resolveCategoryEntries(category, config, Collections.emptyList(), this.redirects);

            // Group entries
            List<Object> groupedEntries = SearchManager.groupByQueries(entries, category.getGroupByQueries());
            Constants.LOG.info("queries: {}", category.getGroupByQueries());

            resolvedCategoryEntries.put(category.getId(), groupedEntries);
        });
    }

    public List<Object> getEntriesForCategory(Category category) {
        return resolvedCategoryEntries.getOrDefault(category.getId(), Collections.emptyList());
    }

    public List<Object> getRecentEntries(Category category, int limit) {
        return getEntriesForCategory(category).stream()
                .filter(ProgressManager.getInstance()::isUnlocked)
                .sorted((a, b) -> Long.compare(ProgressManager.getInstance().getDiscoveryTime(b), ProgressManager.getInstance().getDiscoveryTime(a)))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public boolean isValidEntity(EntityType<?> type, ModConfig config) {
        return EntryResolver.isValidEntity(type, config);
    }

    public Category getCategoryForEntry(Object entry) {
        return resolvedCategoryEntries.entrySet().stream().filter(e -> e.getValue().contains(entry)).map(e -> syncedCategories.get(e.getKey())).findFirst().orElse(null);
    }

    public List<Object> searchEntries(String query) {
        return SearchManager.searchEntries(query);
    }

    public List<ItemStack> getDrops(Object entry) {
        List<ItemStack> rawDrops = new ArrayList<>();
        if (entry instanceof CompositeFieldGuideEntry composite) {
            Set<Object> uniqueComponents = new HashSet<>();
            if (composite.displayEntry() != null) uniqueComponents.add(composite.displayEntry());
            if (composite.components() != null) uniqueComponents.addAll(composite.components());
            for (Object comp : uniqueComponents) {
                rawDrops.addAll(dropCache.getOrDefault(comp, Collections.emptyList()));
            }
        } else {
            rawDrops.addAll(dropCache.getOrDefault(entry, Collections.emptyList()));
        }

        List<ItemStack> distinct = new ArrayList<>();
        for (ItemStack stack : rawDrops) {
            if (distinct.stream().noneMatch(s -> isSameLootItem(s, stack))) {
                distinct.add(stack);
            }
        }
        return distinct;
    }

    private boolean isSameLootItem(ItemStack a, ItemStack b) {
        if (!ItemStack.isSameItem(a, b)) return false;
        if (a.getTag() == b.getTag()) return true;
        if (a.getTag() == null || b.getTag() == null) return false;

        CompoundTag tagA = a.getTag().copy();
        tagA.remove("FieldGuideDropChance");
        tagA.remove("FieldGuideMin");
        tagA.remove("FieldGuideMax");

        CompoundTag tagB = b.getTag().copy();
        tagB.remove("FieldGuideDropChance");
        tagB.remove("FieldGuideMin");
        tagB.remove("FieldGuideMax");

        return tagA.equals(tagB);
    }

    public void onClientTick(Minecraft minecraft) {
        FieldGuideScanner.getInstance().onClientTick(minecraft);
    }

    public long getLastUnlockTime() {
        return ProgressManager.getInstance().getLastUnlockTime();
    }

    public Object getLastUnlockedEntry() {
        return ProgressManager.getInstance().getLastUnlockedEntry();
    }

    public void onWorldLoad(String serverIdentifier) {
        ProgressManager.getInstance().onWorldLoad(serverIdentifier);
    }

    public void onWorldUnload() {
        this.dropCache.clear();
        ProgressManager.getInstance().onWorldUnload();
    }

    public void unlock(Object entry, boolean showToast) {
        ProgressManager.getInstance().unlock(entry, showToast);
    }

    public void revoke(Object entry) {
        ProgressManager.getInstance().revoke(entry);
    }

    public void revokeAll() {
        ProgressManager.getInstance().revokeAll();
    }
}