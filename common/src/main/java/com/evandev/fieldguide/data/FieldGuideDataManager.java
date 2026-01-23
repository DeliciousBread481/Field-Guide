package com.evandev.fieldguide.data;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.gui.toasts.FieldGuideToast;
import com.evandev.fieldguide.client.gui.util.EntryRenderHelper;
import com.evandev.fieldguide.config.ModConfig;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class FieldGuideDataManager implements ResourceManagerReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final FieldGuideDataManager INSTANCE = new FieldGuideDataManager();
    private static final int FADE_DURATION = 10;
    private final Map<ResourceLocation, Category> categories = new LinkedHashMap<>();
    private final Set<String> unlockedEntries = new HashSet<>();
    private final Set<String> seenEntries = new HashSet<>();
    private Path currentSavePath = null;
    private List<Object> flattenedEntryCache = null;
    private long lastUnlockTime = 0;
    private Object lastUnlockedEntry = null;

    private Object scanningTarget = null;
    private int scanTicks = 0;
    private Object fadingTarget = null;
    private int fadeTicks = 0;
    private int prevScanTicks = 0;

    private FieldGuideDataManager() {
    }

    public static FieldGuideDataManager getInstance() {
        return INSTANCE;
    }

    public static List<Object> getValidEntries() {
        if (INSTANCE.flattenedEntryCache == null) {
            INSTANCE.flattenedEntryCache = INSTANCE.categories.values().stream()
                    .flatMap(cat -> cat.getEntries().stream())
                    .distinct()
                    .collect(Collectors.toList());
        }
        return INSTANCE.flattenedEntryCache;
    }

    public static Map<ResourceLocation, Category> getCategories() {
        return INSTANCE.categories;
    }

    /**
     * Checks if the player has unlocked this entry.
     */
    public static ResourceLocation getEntryId(Object entry) {
        if (entry instanceof EntityType<?> type) {
            return BuiltInRegistries.ENTITY_TYPE.getKey(type);
        } else if (entry instanceof Block block) {
            return BuiltInRegistries.BLOCK.getKey(block);
        }
        return null;
    }

    /**
     * Checks if the player has unlocked the entry but not yet viewed it.
     */
    public static boolean isUnlocked(Object entry) {
        ResourceLocation id = getEntryId(entry);
        return id != null && INSTANCE.unlockedEntries.contains(id.toString());
    }

    public static boolean isNew(Object entry) {
        ResourceLocation id = getEntryId(entry);
        if (id == null) return false;
        String key = id.toString();
        return INSTANCE.unlockedEntries.contains(key) && !INSTANCE.seenEntries.contains(key);
    }

    /**
     * Marks an entry as seen, removing the "New!" status.
     */
    public static void markAsSeen(Object entry) {
        ResourceLocation id = getEntryId(entry);
        if (id != null && INSTANCE.seenEntries.add(id.toString())) {
            INSTANCE.saveProgress();
        }
    }

    public static String getEntryDescription(Object entry) {
        ResourceLocation id = getEntryId(entry);
        if (id == null) return "";

        String overrideKey = "fieldguide." + id.getNamespace() + "." + id.getPath() + ".description";

        String fallbackKey;

        if (entry instanceof EntityType) {
            fallbackKey = "entity." + id.getNamespace() + "." + id.getPath() + ".description";
        } else {
            fallbackKey = "lore." + id.getNamespace() + "." + id.getPath();
        }

        if (I18n.exists(overrideKey)) {
            return I18n.get(overrideKey);
        } else if (I18n.exists(fallbackKey)) {
            return I18n.get(fallbackKey);
        }

        return I18n.get("fieldguide.description.missing");
    }

    public static void clearCache() {
        INSTANCE.flattenedEntryCache = null;
        EntryRenderHelper.clearCache();
        for (Category category : INSTANCE.categories.values()) {
            category.resolveEntries();
        }
    }

    private int getScanDuration() {
        return (int) (ModConfig.get().scanSpeed * 20);
    }

    public long getLastUnlockTime() {
        return lastUnlockTime;
    }

    public Object getLastUnlockedEntry() {
        return lastUnlockedEntry;
    }

    public Object getScanningTarget() {
        return scanningTarget;
    }

    public Entity getScanningEntity() {
        return scanningTarget instanceof Entity ? (Entity) scanningTarget : null;
    }

    public float getScanProgress(float partialTicks) {
        float lerped = (float) prevScanTicks + ((float) scanTicks - (float) prevScanTicks) * partialTicks;
        return Math.min(1.0F, lerped / (float) getScanDuration());
    }

    public Entity getFadingEntity() {
        return fadingTarget instanceof Entity ? (Entity) fadingTarget : null;
    }

    public float getFadeProgress() {
        return (float) fadeTicks / (float) FADE_DURATION;
    }

    /**
     * Helper to find which category holds a specific entity.
     */
    public Category getCategoryForEntry(Object entry) {
        for (Category category : categories.values()) {
            if (category.getEntries().contains(entry)) {
                return category;
            }
        }
        return null;
    }

    /**
     * Called when the client joins a world (Singleplayer).
     */
    public void onWorldLoad(Path worldSaveDir) {
        this.unlockedEntries.clear();
        this.seenEntries.clear();
        if (worldSaveDir != null) {
            this.currentSavePath = worldSaveDir.resolve("fieldguide.dat");
            loadProgress();
        } else {
            this.currentSavePath = null;
        }
    }

    /**
     * Called when the client leaves a world.
     */
    public void onWorldUnload() {
        if (this.currentSavePath != null) {
            saveProgress();
        }
        this.currentSavePath = null;
        this.unlockedEntries.clear();
        this.seenEntries.clear();
    }

    /**
     * Called every client tick to check for spyglass usage.
     */
    public void onClientTick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) return;

        boolean isUsingSpyglass = minecraft.player.isUsingItem() && minecraft.player.getUseItem().is(Items.SPYGLASS);

        if (isUsingSpyglass) {
            double range = 64.0D;
            Vec3 eyePos = minecraft.player.getEyePosition(1.0F);
            Vec3 viewVec = minecraft.player.getViewVector(1.0F);
            Vec3 endPos = eyePos.add(viewVec.scale(range));

            AABB searchBox = minecraft.player.getBoundingBox().expandTowards(viewVec.scale(range)).inflate(1.0D);
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                    minecraft.player, eyePos, endPos, searchBox,
                    (entity) -> !entity.isSpectator() && entity.isPickable(),
                    range * range
            );

            BlockHitResult blockHit = minecraft.level.clip(new ClipContext(
                    eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player
            ));

            Object foundTarget = null;
            double entityDist = entityHit != null ? eyePos.distanceToSqr(entityHit.getLocation()) : Double.MAX_VALUE;
            double blockDist = blockHit.getType() != HitResult.Type.MISS ? eyePos.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;

            if (entityHit != null && entityDist < blockDist) {
                EntityType<?> type = entityHit.getEntity().getType();
                if (getValidEntries().contains(type) && !isUnlocked(type)) {
                    foundTarget = entityHit.getEntity();
                }
            } else if (blockHit.getType() == HitResult.Type.BLOCK) {
                BlockState state = minecraft.level.getBlockState(blockHit.getBlockPos());
                Block block = state.getBlock();
                if (getValidEntries().contains(block) && !isUnlocked(block)) {
                    foundTarget = block;
                }
            }

            if (foundTarget != null) {
                Object targetKey = (foundTarget instanceof Entity) ? ((Entity) foundTarget).getType() : foundTarget;

                boolean sameTarget;
                if (scanningTarget instanceof Entity && foundTarget instanceof Entity) {
                    sameTarget = scanningTarget == foundTarget;
                } else {
                    sameTarget = Objects.equals(scanningTarget, foundTarget);
                }

                if (sameTarget) {
                    this.prevScanTicks = this.scanTicks;
                    scanTicks++;
                    if (scanTicks >= getScanDuration()) {
                        unlock(targetKey);
                        minecraft.player.playSound(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.0F, 1.0F);
                        minecraft.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);

                        scanningTarget = null;
                        scanTicks = 0;

                        fadingTarget = foundTarget;
                        fadeTicks = FADE_DURATION;
                    }
                } else {
                    this.prevScanTicks = 0;
                    scanningTarget = foundTarget;
                    scanTicks = 0;
                }
            } else {
                if (scanTicks > 0) {
                    this.prevScanTicks = this.scanTicks;
                    scanTicks -= 2;
                    if (scanTicks <= 0) {
                        scanningTarget = null;
                        scanTicks = 0;
                    }
                } else {
                    scanningTarget = null;
                }
            }

            if (scanningTarget instanceof Entity ent && (ent.isRemoved() || !ent.isAlive())) {
                scanningTarget = null;
                scanTicks = 0;
            }

        } else {
            scanningTarget = null;
            scanTicks = 0;
        }

        if (fadeTicks > 0) {
            fadeTicks--;
            if (fadeTicks <= 0) {
                fadingTarget = null;
            }
        }
    }

    public void unlock(Object entry) {
        unlock(entry, true);
    }

    public void unlock(Object entry, boolean showToast) {
        if (!getValidEntries().contains(entry)) {
            return;
        }

        ResourceLocation id = getEntryId(entry);
        if (id != null && unlockedEntries.add(id.toString())) {
            this.lastUnlockedEntry = entry;
            this.lastUnlockTime = System.currentTimeMillis();

            if (showToast) {
                Minecraft.getInstance().getToasts().addToast(new FieldGuideToast(entry));
            }
            saveProgress();
        }
    }

    public void revoke(Object entry) {
        ResourceLocation id = getEntryId(entry);
        if (id != null && unlockedEntries.remove(id.toString())) {
            seenEntries.remove(id.toString());
            saveProgress();
        }
    }

    /**
     * Revokes access to all entries.
     */
    public void revokeAll() {
        unlockedEntries.clear();
        seenEntries.clear();
        saveProgress();
    }

    private void loadProgress() {
        if (currentSavePath == null) return;
        File file = currentSavePath.toFile();

        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json != null) {
                    if (json.has("unlocked")) {
                        JsonArray array = json.getAsJsonArray("unlocked");
                        for (JsonElement e : array) {
                            unlockedEntries.add(e.getAsString());
                        }
                    }

                    if (json.has("seen")) {
                        JsonArray array = json.getAsJsonArray("seen");
                        for (JsonElement e : array) {
                            seenEntries.add(e.getAsString());
                        }
                    }
                }
            } catch (Exception e) {
                Constants.LOG.error("Failed to load field guide progress", e);
            }
        }
    }

    private void saveProgress() {
        if (currentSavePath == null) return;
        File file = currentSavePath.toFile();

        try {
            JsonObject json = new JsonObject();

            JsonArray unlockedArray = new JsonArray();
            for (String id : unlockedEntries) {
                unlockedArray.add(id);
            }
            json.add("unlocked", unlockedArray);

            JsonArray seenArray = new JsonArray();
            for (String id : seenEntries) {
                seenArray.add(id);
            }
            json.add("seen", seenArray);

            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                if (!parent.mkdirs()) {
                    throw new IOException("Failed to create directories: " + parent);
                }
            }

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            Constants.LOG.error("Failed to save field guide progress", e);
        }
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        categories.clear();
        flattenedEntryCache = null;
        EntryRenderHelper.clearCache();

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

            category.resolveEntries();

            if (!category.getEntries().isEmpty()) {
                categories.put(categoryId, category);
            }
        }

        Constants.LOG.info("Loaded {} field guide categories.", categories.size());
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
        if (json.has("tab_index")) {
            category.tabIndex = GsonHelper.getAsInt(json, "tab_index");
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

    public enum EntryType {ENTRY, AUTO_POPULATE}
}