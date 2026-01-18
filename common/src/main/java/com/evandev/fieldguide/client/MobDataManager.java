package com.evandev.fieldguide.client;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.config.ModConfig;
import com.google.gson.*;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.io.Reader;
import java.util.*;
import java.util.stream.Collectors;

public class MobDataManager implements ResourceManagerReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final MobDataManager INSTANCE = new MobDataManager();

    private final Map<ResourceLocation, Category> categories = new LinkedHashMap<>();

    private List<EntityType<?>> flattenedEntityCache = null;

    public static MobDataManager getInstance() {
        return INSTANCE;
    }

    /**
     * Gets a list of all valid EntityTypes from all categories.
     * Preserves the order defined in data packs (pinned items first).
     */
    public static List<EntityType<?>> getValidEntities() {
        if (INSTANCE.flattenedEntityCache == null) {
            INSTANCE.flattenedEntityCache = INSTANCE.categories.values().stream()
                    .flatMap(cat -> cat.getEntities().stream())
                    .distinct()
                    .collect(Collectors.toList());
        }
        return INSTANCE.flattenedEntityCache;
    }

    public static Map<ResourceLocation, Category> getCategories() {
        return INSTANCE.categories;
    }

    /**
     * Checks if the player has unlocked this mob.
     */
    public static boolean isUnlocked(EntityType<?> type) {
        // TODO: Hook up to player data
        return true;
    }

    /**
     * Gets the description for the entity based on priority.
     */
    public static String getEntityDescription(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);

        String overrideKey = "fieldguide." + id.getNamespace() + "." + id.getPath() + ".description";
        String fallbackKey = "entity." + id.getNamespace() + "." + id.getPath() + ".description";

        if (I18n.exists(overrideKey)) {
            return I18n.get(overrideKey);
        } else if (I18n.exists(fallbackKey)) {
            return I18n.get(fallbackKey);
        }

        return I18n.get("fieldguide.description.missing");
    }

    public static void clearCache() {
        INSTANCE.flattenedEntityCache = null;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        categories.clear();
        flattenedEntityCache = null;

        Map<ResourceLocation, List<Resource>> resources =
                resourceManager.listResourceStacks(
                        "categories",
                        id -> id.getNamespace().equals(Constants.MOD_ID)
                                && id.getPath().endsWith(".json")
                );

        List<ResourceLocation> sortedIds = new ArrayList<>(resources.keySet());
        sortedIds.sort(Comparator.comparing(ResourceLocation::getPath));

        for (ResourceLocation fileId : sortedIds) {
            String path = fileId.getPath();
            String idPath = path.substring(
                    "categories/".length(),
                    path.length() - ".json".length()
            );

            ResourceLocation categoryId =
                    new ResourceLocation(Constants.MOD_ID, idPath);

            Category category = new Category(categoryId);

            for (Resource resource : resources.get(fileId)) {
                try (Reader reader = resource.openAsReader()) {
                    JsonObject json = GsonHelper.parse(reader);
                    loadCategoryData(category, json);
                } catch (Exception e) {
                    Constants.LOG.error("Failed to load field guide category: {}", fileId, e);
                }
            }

            category.resolveEntities();

            if (!category.getEntities().isEmpty()) {
                categories.put(categoryId, category);
            }
        }

        Constants.LOG.info(
                "Loaded {} field guide categories.",
                categories.size()
        );
    }


    private void loadCategoryData(Category category, JsonObject json) {
        if (GsonHelper.getAsBoolean(json, "replace", false)) {
            category.entries.clear();
        }

        if (json.has("tab_color")) {
            category.tabColor = GsonHelper.getAsString(json, "tab_color");
        }
        if (json.has("tab_icon")) {
            category.tabIcon = new ResourceLocation(GsonHelper.getAsString(json, "tab_icon"));
        }

        if (json.has("contents")) {
            JsonArray contents = GsonHelper.getAsJsonArray(json, "contents");
            for (JsonElement element : contents) {
                JsonObject entryObj = element.getAsJsonObject();
                String type = GsonHelper.getAsString(entryObj, "type");

                if ("entry".equals(type)) {
                    ResourceLocation entityId = new ResourceLocation(GsonHelper.getAsString(entryObj, "id"));
                    category.entries.add(new CategoryEntry(EntryType.ENTRY, entityId, null));
                } else if ("auto_populate".equals(type)) {
                    String strategy = GsonHelper.getAsString(entryObj, "strategy");
                    category.entries.add(new CategoryEntry(EntryType.AUTO_POPULATE, null, strategy));
                }
            }
        }
    }

    private enum EntryType {ENTRY, AUTO_POPULATE}

    public static class Category {
        private final ResourceLocation id;
        private final List<CategoryEntry> entries = new ArrayList<>();
        private String tabColor = "#FFFFFF";
        private ResourceLocation tabIcon = new ResourceLocation("minecraft:barrier");
        private List<EntityType<?>> resolvedEntities = new ArrayList<>();

        public Category(ResourceLocation id) {
            this.id = id;
        }

        public ResourceLocation getId() {
            return id;
        }

        public String getTabColor() {
            return tabColor;
        }

        public ResourceLocation getTabIcon() {
            return tabIcon;
        }

        public List<EntityType<?>> getEntities() {
            return resolvedEntities;
        }

        public void resolveEntities() {
            Set<EntityType<?>> foundEntities = new LinkedHashSet<>();
            ModConfig config = ModConfig.get();

            for (CategoryEntry entry : entries) {
                if (entry.type == EntryType.ENTRY) {
                    BuiltInRegistries.ENTITY_TYPE.getOptional(entry.id).ifPresent(type -> {
                        if (isValid(type, config)) {
                            foundEntities.add(type);
                        }
                    });
                } else if (entry.type == EntryType.AUTO_POPULATE) {
                    List<EntityType<?>> autoEntities = getEntitiesForStrategy(entry.strategy);
                    for (EntityType<?> type : autoEntities) {
                        if (isValid(type, config)) {
                            foundEntities.add(type);
                        }
                    }
                }
            }
            this.resolvedEntities = new ArrayList<>(foundEntities);
        }

        private boolean isValid(EntityType<?> type, ModConfig config) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            return type.canSummon() && !config.isEntityBlacklisted(id);
        }

        private List<EntityType<?>> getEntitiesForStrategy(String strategy) {

            return BuiltInRegistries.ENTITY_TYPE.stream()
                    .filter(type -> {
                        if ("hostile".equalsIgnoreCase(strategy)) {
                            return type.getCategory() == MobCategory.MONSTER;
                        } else if ("passive".equalsIgnoreCase(strategy)) {
                            return type.getCategory() != MobCategory.MONSTER && type.getCategory() != MobCategory.MISC;
                        }
                        return false;
                    })
                    .sorted(Comparator.comparing(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()))
                    .collect(Collectors.toList());
        }
    }

    private static class CategoryEntry {
        EntryType type;
        ResourceLocation id;
        String strategy;

        CategoryEntry(EntryType type, ResourceLocation id, String strategy) {
            this.type = type;
            this.id = id;
            this.strategy = strategy;
        }
    }
}