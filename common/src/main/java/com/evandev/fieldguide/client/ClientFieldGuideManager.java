package com.evandev.fieldguide.client;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.data.CategoryVisual;
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
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
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
    private final Map<ResourceLocation, CategoryVisual> categoryVisuals = new HashMap<>();
    private final Map<ResourceLocation, EntryVisual> entryVisuals = new HashMap<>();
    private final Map<Object, List<ItemStack>> dropCache = new HashMap<>();

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
        if (entry instanceof CompositeFieldGuideEntry composite) return composite.getId();
        if (entry instanceof EntityType<?> type) return BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (entry instanceof Block block) return BuiltInRegistries.BLOCK.getKey(block);
        return null;
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

        Object coreEntry = entry instanceof CompositeFieldGuideEntry composite ? composite.getDisplayEntry() : entry;
        String fallbackKey = (coreEntry instanceof EntityType) ? "entity." + id.getNamespace() + "." + id.getPath() + ".description" : "lore." + id.getNamespace() + "." + id.getPath();
        return I18n.exists(overrideKey) ? I18n.get(overrideKey) : (I18n.exists(fallbackKey) ? I18n.get(fallbackKey) : I18n.get("fieldguide.description.missing"));
    }

    public static void setCustomDescription(Object entry, String desc) {
        ProgressManager.getInstance().setCustomDescription(entry, desc);
    }

    public static Component getEntryName(Object entry) {
        String custom = ProgressManager.getInstance().getCustomName(entry);
        if (custom != null) return Component.literal(custom);

        Object coreEntry = entry instanceof CompositeFieldGuideEntry composite ? composite.getDisplayEntry() : entry;
        if (coreEntry instanceof EntityType<?> type) return type.getDescription();
        if (coreEntry instanceof Block block) {
            if (entry instanceof CompositeFieldGuideEntry) {
                String name = block.getName().getString();
                if (name.endsWith(" Sapling")) {
                    return Component.literal(name.substring(0, name.length() - 8) + " Tree");
                } else if (name.endsWith(" Fungus")) {
                    return Component.literal( "Huge " + name.substring(0, name.length() - 7) + " Fungus");
                } else if (name.endsWith(" Propagule")) {
                    return Component.literal(name.substring(0, name.length() - 10) + " Tree");
                }
            }
            return block.getName();
        }
        return Component.translatable("fieldguide.unknown");
    }

    public static void setCustomName(Object entry, String name) {
        ProgressManager.getInstance().setCustomName(entry, name);
    }

    public static String getDefaultName(Object entry) {
        Object coreEntry = entry instanceof CompositeFieldGuideEntry composite ? composite.getDisplayEntry() : entry;
        if (coreEntry instanceof EntityType<?> type) return type.getDescription().getString();
        if (coreEntry instanceof Block block) {
            if (entry instanceof CompositeFieldGuideEntry) {
                String name = block.getName().getString();
                if (name.endsWith(" Sapling")) {
                    return name.substring(0, name.length() - 8) + " Tree";
                } else if (name.endsWith(" Fungus")) {
                    return name.substring(0, name.length() - 7) + " Tree";
                } else if (name.endsWith(" Propagule")) {
                    return name.substring(0, name.length() - 10) + " Tree";
                }
            }
            return block.getName().getString();
        }
        return I18n.get("fieldguide.unknown");
    }

    public static Map<ResourceLocation, Category> getCategories() {
        return INSTANCE.syncedCategories;
    }

    public static List<Object> getValidEntries() {
        return INSTANCE.resolvedCategoryEntries.values().stream().flatMap(List::stream).distinct().collect(Collectors.toList());
    }

    public Object getEntryForTarget(Object target) {
        for (Object entry : getValidEntries()) {
            if (entry.equals(target)) return entry;
            if (entry instanceof CompositeFieldGuideEntry composite) {
                if (composite.getDisplayEntry().equals(target) || composite.getComponents().contains(target)) {
                    return entry;
                }
            }
        }
        return null;
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

    public void updateCategoriesFromServer(List<Category> categories) {
        this.syncedCategories.clear();
        categories.sort(Comparator.comparingInt(Category::getSortIndex).thenComparing(Category::getId));
        for (Category cat : categories) this.syncedCategories.put(cat.getId(), cat);
        resolveAllEntries();
    }

    public void updateLootCache(Map<ResourceLocation, List<ItemStack>> lootCache) {
        for (Map.Entry<ResourceLocation, List<ItemStack>> entry : lootCache.entrySet()) {
            ResourceLocation id = entry.getKey();
            List<ItemStack> drops = entry.getValue();
            BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(type -> dropCache.put(type, drops));
            BuiltInRegistries.BLOCK.getOptional(id).ifPresent(block -> dropCache.put(block, drops));
        }
    }

    public CategoryVisual getCategoryVisual(ResourceLocation categoryId) {
        return categoryVisuals.getOrDefault(categoryId, CategoryVisual.DEFAULT);
    }

    public EntryVisual getEntryVisual(ResourceLocation entryId) {
        return entryVisuals.getOrDefault(entryId, new EntryVisual());
    }

    @Override
    public void onResourceManagerReload(@NotNull ResourceManager resourceManager) {
        ModConfig.load();
        categoryVisuals.clear();
        entryVisuals.clear();
        EntryRenderHelper.clearCache();

        loadVisuals(resourceManager, "visuals/categories", (id, json) -> {
            CategoryVisual visual = new CategoryVisual();
            if (json.has("icon")) visual.icon = new ResourceLocation(GsonHelper.getAsString(json, "icon"));
            if (json.has("hostile_icon"))
                visual.hostileIcon = new ResourceLocation(GsonHelper.getAsString(json, "hostile_icon"));
            if (json.has("passive_icon"))
                visual.passiveIcon = new ResourceLocation(GsonHelper.getAsString(json, "passive_icon"));
            if (json.has("neutral_icon"))
                visual.neutralIcon = new ResourceLocation(GsonHelper.getAsString(json, "neutral_icon"));
            categoryVisuals.put(id, visual);
        });

        loadVisuals(resourceManager, "visuals/entries", (derivedId, json) -> {
            ResourceLocation targetId = json.has("id") ? new ResourceLocation(GsonHelper.getAsString(json, "id")) : derivedId;
            EntryVisual visual = new EntryVisual();
            if (json.has("auto_rotate")) visual.autoRotate = GsonHelper.getAsBoolean(json, "auto_rotate");
            if (json.has("rotation_speed")) visual.rotationSpeed = GsonHelper.getAsFloat(json, "rotation_speed");
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

    private void loadVisuals(ResourceManager mgr, String folder, BiConsumer<ResourceLocation, com.google.gson.JsonObject> processor) {
        mgr.listResourceStacks("fieldguide/" + folder, id -> id.getPath().endsWith(".json")).forEach((fileId, resources) -> {
            String path = fileId.getPath();
            String idPath = path.substring(("fieldguide/" + folder + "/").length(), path.length() - ".json".length());
            ResourceLocation targetId = new ResourceLocation(fileId.getNamespace(), idPath);
            resources.forEach(resource -> {
                try (Reader reader = resource.openAsReader()) {
                    processor.accept(targetId, GsonHelper.parse(reader));
                } catch (Exception e) {
                    Constants.LOG.error("Error loading visual: {}", fileId, e);
                }
            });
        });
    }

    private void resolveAllEntries() {
        resolvedCategoryEntries.clear();
        ModConfig config = ModConfig.get();
        syncedCategories.values().forEach(category -> {
            resolvedCategoryEntries.put(category.getId(), EntryResolver.resolveCategoryEntries(category, config));
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
        List<ItemStack> rawDrops;
        if (entry instanceof CompositeFieldGuideEntry composite) {
            rawDrops = new ArrayList<>(dropCache.getOrDefault(composite.getDisplayEntry(), Collections.emptyList()));
            for (Object comp : composite.getComponents()) {
                rawDrops.addAll(dropCache.getOrDefault(comp, Collections.emptyList()));
            }
        } else {
            rawDrops = dropCache.getOrDefault(entry, Collections.emptyList());
        }

        List<ItemStack> distinct = new ArrayList<>();
        for (ItemStack stack : rawDrops) {
            if (distinct.stream().noneMatch(s -> ItemStack.isSameItemSameTags(s, stack))) {
                distinct.add(stack);
            }
        }
        return distinct;
    }

    public void onClientTick(net.minecraft.client.Minecraft minecraft) {
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