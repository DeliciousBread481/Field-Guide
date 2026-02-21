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
import com.evandev.fieldguide.data.CategoryEntry;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.*;
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
        if (entry instanceof EntityType<?> type) return BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (entry instanceof Block block) return BuiltInRegistries.BLOCK.getKey(block);
        return null;
    }

    public static boolean hideFromSearch(Object entry) {
        return ModConfig.get().hideUndiscoveredFromSearch && !isUnlocked(entry);
    }

    // Facade Methods over ProgressManager
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
        String fallbackKey = (entry instanceof EntityType) ? "entity." + id.getNamespace() + "." + id.getPath() + ".description" : "lore." + id.getNamespace() + "." + id.getPath();
        return I18n.exists(overrideKey) ? I18n.get(overrideKey) : (I18n.exists(fallbackKey) ? I18n.get(fallbackKey) : I18n.get("fieldguide.description.missing"));
    }

    public static void setCustomDescription(Object entry, String desc) {
        ProgressManager.getInstance().setCustomDescription(entry, desc);
    }

    public static Component getEntryName(Object entry) {
        String custom = ProgressManager.getInstance().getCustomName(entry);
        if (custom != null) return Component.literal(custom);
        if (entry instanceof EntityType<?> type) return type.getDescription();
        if (entry instanceof Block block) return block.getName();
        return Component.translatable("fieldguide.unknown");
    }

    public static void setCustomName(Object entry, String name) {
        ProgressManager.getInstance().setCustomName(entry, name);
    }

    public static String getDefaultName(Object entry) {
        if (entry instanceof EntityType<?> type) return type.getDescription().getString();
        if (entry instanceof Block block) return block.getName().getString();
        return I18n.get("fieldguide.unknown");
    }

    public static Map<ResourceLocation, Category> getCategories() {
        return INSTANCE.syncedCategories;
    }

    public static List<Object> getValidEntries() {
        return INSTANCE.resolvedCategoryEntries.values().stream().flatMap(List::stream).distinct().collect(Collectors.toList());
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
        syncedCategories.values().forEach(this::resolveCategory);
    }

    private void resolveCategory(Category category) {
        Set<Object> foundEntries = new LinkedHashSet<>();
        ModConfig config = ModConfig.get();

        for (CategoryEntry entry : category.getEntries()) {
            if (entry.type() == CategoryEntry.Type.ENTRY && entry.id() != null) {
                BuiltInRegistries.ENTITY_TYPE.getOptional(entry.id()).filter(t -> isValidEntity(t, config)).ifPresent(foundEntries::add);
                BuiltInRegistries.BLOCK.getOptional(entry.id()).filter(b -> isValidBlock(b, config)).ifPresent(foundEntries::add);
            } else if (entry.type() == CategoryEntry.Type.AUTO_POPULATE) {
                foundEntries.addAll(getEntriesForStrategy(entry.strategy(), config));
            }
        }
        resolvedCategoryEntries.put(category.getId(), new ArrayList<>(foundEntries));
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
        return type.canSummon() && !config.isEntityBlacklisted(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    private boolean isValidBlock(Block block, ModConfig config) {
        return !config.isEntityBlacklisted(BuiltInRegistries.BLOCK.getKey(block));
    }

    private List<Object> getEntriesForStrategy(String strategy, ModConfig config) {
        List<Object> results = new ArrayList<>();
        if ("plants".equalsIgnoreCase(strategy)) {
            results.addAll(BuiltInRegistries.BLOCK.stream().filter(b -> isPlant(b) && isValidBlock(b, config)).sorted(Comparator.comparing(b -> BuiltInRegistries.BLOCK.getKey(b).toString())).toList());
        } else if (strategy.startsWith("mod:")) {
            String modId = strategy.substring(4);
            results.addAll(BuiltInRegistries.ENTITY_TYPE.stream().filter(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).getNamespace().equals(modId) && isValidEntity(t, config)).sorted(Comparator.comparing(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).toString())).toList());
        } else if (strategy.startsWith("mod_plants:")) {
            String modId = strategy.substring(10);
            results.addAll(BuiltInRegistries.BLOCK.stream().filter(b -> BuiltInRegistries.BLOCK.getKey(b).getNamespace().equals(modId) && isPlant(b) && isValidBlock(b, config)).sorted(Comparator.comparing(b -> BuiltInRegistries.BLOCK.getKey(b).toString())).toList());
        } else if (strategy.startsWith("tag:")) {
            try {
                TagKey<EntityType<?>> tagKey = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(strategy.substring(4)));
                BuiltInRegistries.ENTITY_TYPE.forEach(type -> BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).flatMap(BuiltInRegistries.ENTITY_TYPE::getHolder).filter(h -> h.is(tagKey) && isValidEntity(type, config)).ifPresent(h -> results.add(type)));
                results.sort(Comparator.comparing(o -> BuiltInRegistries.ENTITY_TYPE.getKey((EntityType<?>) o).toString()));
            } catch (Exception e) {
                Constants.LOG.error("Invalid tag strategy: {}", strategy, e);
            }
        } else if ("monsters".equalsIgnoreCase(strategy) || "animals".equalsIgnoreCase(strategy)) {
            TagKey<EntityType<?>> bossesTag = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("fieldguide", "bosses"));
            results.addAll(BuiltInRegistries.ENTITY_TYPE.stream().filter(type -> {
                boolean isBoss = BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).flatMap(BuiltInRegistries.ENTITY_TYPE::getHolder).map(h -> h.is(bossesTag)).orElse(false);
                if ("monsters".equalsIgnoreCase(strategy)) return type.getCategory() == MobCategory.MONSTER && !isBoss;
                if ("animals".equalsIgnoreCase(strategy))
                    return type.getCategory() != MobCategory.MONSTER && (type.getCategory() != MobCategory.MISC || SpawnEggItem.byId(type) != null) && !isBoss;
                return false;
            }).filter(type -> isValidEntity(type, config)).sorted(Comparator.comparing(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString())).toList());
        }
        return results;
    }

    private boolean isPlant(Block block) {
        return (block instanceof BushBlock && !(block instanceof StemBlock) && !(block instanceof AttachedStemBlock)) || block instanceof LeavesBlock || block instanceof VineBlock || block instanceof CactusBlock || block instanceof SugarCaneBlock || block instanceof WaterlilyBlock || block instanceof StemGrownBlock || block instanceof CoralFanBlock || block instanceof CoralPlantBlock;
    }

    public Category getCategoryForEntry(Object entry) {
        return resolvedCategoryEntries.entrySet().stream().filter(e -> e.getValue().contains(entry)).map(e -> syncedCategories.get(e.getKey())).findFirst().orElse(null);
    }

    public List<Object> searchEntries(String query) {
        return SearchManager.searchEntries(query);
    }

    public List<ItemStack> getDrops(Object entry) {
        return dropCache.getOrDefault(entry, Collections.emptyList());
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

    public void unlock(Object entry) {
        ProgressManager.getInstance().unlock(entry);
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