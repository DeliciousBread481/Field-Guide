package com.evandev.fieldguide.server;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.CategoryEntry;
import com.evandev.fieldguide.network.SyncCategoriesPacket;
import com.evandev.fieldguide.platform.Services;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ServerFieldGuideManager extends SimplePreparableReloadListener<Map<ResourceLocation, Category>> {
    private static final Gson GSON = new GsonBuilder().create();
    private static final ServerFieldGuideManager INSTANCE = new ServerFieldGuideManager();

    private Map<ResourceLocation, Category> categories = new LinkedHashMap<>();

    public static ServerFieldGuideManager getInstance() {
        return INSTANCE;
    }

    public void syncToPlayer(ServerPlayer player) {
        List<Category> categoryList = new ArrayList<>(categories.values());
        Services.NETWORK.sendToPlayer(new SyncCategoriesPacket(categoryList), player);
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

    @Override
    protected void apply(@NotNull Map<ResourceLocation, Category> object, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
        this.categories = object;
        Constants.LOG.info("Server loaded {} Field Guide categories.", categories.size());
    }
}