package com.evandev.fieldguide.data;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.data.CategoryVisual;
import com.evandev.fieldguide.client.data.EntryVisual;
import com.evandev.fieldguide.client.gui.toasts.FieldGuideToast;
import com.evandev.fieldguide.client.gui.util.EntryRenderHelper;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.network.RequestDropsPacket;
import com.evandev.fieldguide.platform.Services;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class FieldGuideDataManager implements ResourceManagerReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final FieldGuideDataManager INSTANCE = new FieldGuideDataManager();
    private static final int FADE_DURATION = 10;

    // Data Structures
    private final Map<ResourceLocation, Category> syncedCategories = new LinkedHashMap<>();
    private final Map<ResourceLocation, List<Object>> resolvedCategoryEntries = new HashMap<>();

    // Visuals
    private final Map<ResourceLocation, CategoryVisual> categoryVisuals = new HashMap<>();
    private final Map<ResourceLocation, EntryVisual> entryVisuals = new HashMap<>();

    // Progress
    private final Set<String> unlockedEntries = new HashSet<>();
    private final Set<String> seenEntries = new HashSet<>();
    private final Map<Object, List<ItemStack>> dropCache = new HashMap<>();
    private final Set<Object> requestedDrops = new HashSet<>();
    private Path currentSavePath = null;

    // Scanning State
    private long lastUnlockTime = 0;
    private Object lastUnlockedEntry = null;
    private Object scanningTarget = null;
    private int scanTicks = 0;
    private BlockPos scanningPos = null;
    private Object fadingTarget = null;
    private int fadeTicks = 0;
    private int prevScanTicks = 0;
    private BlockPos fadingPos = null;

    private FieldGuideDataManager() {
    }

    public static FieldGuideDataManager getInstance() {
        return INSTANCE;
    }

    public static void clearCache() {
        INSTANCE.dropCache.clear();
        INSTANCE.resolvedCategoryEntries.clear();
        EntryRenderHelper.clearCache();
        INSTANCE.resolveAllEntries();
    }

    public static Map<ResourceLocation, Category> getCategories() {
        return INSTANCE.syncedCategories;
    }

    public static List<Object> getValidEntries() {
        return INSTANCE.resolvedCategoryEntries.values().stream()
                .flatMap(List::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    public static ResourceLocation getEntryId(Object entry) {
        if (entry instanceof EntityType<?> type) return BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (entry instanceof Block block) return BuiltInRegistries.BLOCK.getKey(block);
        return null;
    }

    public static boolean isUnlocked(Object entry) {
        ResourceLocation id = getEntryId(entry);
        return id != null && INSTANCE.unlockedEntries.contains(id.toString());
    }

    public static boolean isNew(Object entry) {
        ResourceLocation id = getEntryId(entry);
        return id != null && INSTANCE.unlockedEntries.contains(id.toString()) && !INSTANCE.seenEntries.contains(id.toString());
    }

    public static void markAsSeen(Object entry) {
        ResourceLocation id = getEntryId(entry);
        if (id != null && INSTANCE.seenEntries.add(id.toString())) INSTANCE.saveProgress();
    }

    // --- Logic & Resolving ---

    public static String getEntryDescription(Object entry) {
        ResourceLocation id = getEntryId(entry);
        if (id == null) return "";
        String overrideKey = "fieldguide." + id.getNamespace() + "." + id.getPath() + ".description";
        String fallbackKey = (entry instanceof EntityType) ? "entity." + id.getNamespace() + "." + id.getPath() + ".description" : "lore." + id.getNamespace() + "." + id.getPath();
        return I18n.exists(overrideKey) ? I18n.get(overrideKey) : (I18n.exists(fallbackKey) ? I18n.get(fallbackKey) : I18n.get("fieldguide.description.missing"));
    }

    /**
     * Called when the client receives the SyncCategoriesPacket from the server.
     */
    public void updateCategoriesFromServer(List<Category> categories) {
        this.syncedCategories.clear();
        categories.sort(Comparator.comparingInt(Category::getSortIndex).thenComparing(Category::getId));

        for (Category cat : categories) {
            this.syncedCategories.put(cat.getId(), cat);
        }
        resolveAllEntries();
    }

    public CategoryVisual getCategoryVisual(ResourceLocation categoryId) {
        return categoryVisuals.getOrDefault(categoryId, CategoryVisual.DEFAULT);
    }

    public EntryVisual getEntryVisual(ResourceLocation entryId) {
        return entryVisuals.getOrDefault(entryId, new EntryVisual());
    }

    @Override
    public void onResourceManagerReload(@NotNull ResourceManager resourceManager) {
        categoryVisuals.clear();
        entryVisuals.clear();
        EntryRenderHelper.clearCache();

        loadVisuals(resourceManager, "visuals/categories", (id, json) -> {
            CategoryVisual visual = new CategoryVisual();
            if (json.has("color")) visual.color = GsonHelper.getAsString(json, "color");
            if (json.has("icon")) visual.icon = new ResourceLocation(GsonHelper.getAsString(json, "icon"));
            categoryVisuals.put(id, visual);
        });

        loadVisuals(resourceManager, "visuals/entries", (id, json) -> {
            EntryVisual visual = new EntryVisual();

            // Base
            if (json.has("scale")) visual.scale = GsonHelper.getAsFloat(json, "scale");
            if (json.has("y_offset")) visual.yOffset = GsonHelper.getAsFloat(json, "y_offset");
            if (json.has("x_offset")) visual.xOffset = GsonHelper.getAsFloat(json, "x_offset");

            // Grid Overrides
            if (json.has("grid_scale")) visual.gridScale = GsonHelper.getAsFloat(json, "grid_scale");
            if (json.has("grid_y_offset")) visual.gridYOffset = GsonHelper.getAsFloat(json, "grid_y_offset");
            if (json.has("grid_x_offset")) visual.gridXOffset = GsonHelper.getAsFloat(json, "grid_x_offset");

            // Page Overrides
            if (json.has("page_scale")) visual.pageScale = GsonHelper.getAsFloat(json, "page_scale");
            if (json.has("page_y_offset")) visual.pageYOffset = GsonHelper.getAsFloat(json, "page_y_offset");
            if (json.has("page_x_offset")) visual.pageXOffset = GsonHelper.getAsFloat(json, "page_x_offset");

            entryVisuals.put(id, visual);
        });
        resolveAllEntries();

        Constants.LOG.info("Loaded {} category visuals and {} entry visuals.", categoryVisuals.size(), entryVisuals.size());
    }

    private void loadVisuals(ResourceManager mgr, String folder, java.util.function.BiConsumer<ResourceLocation, JsonObject> processor) {
        Map<ResourceLocation, List<Resource>> resources = mgr.listResourceStacks("fieldguide/" + folder,
                id -> id.getPath().endsWith(".json"));

        for (Map.Entry<ResourceLocation, List<Resource>> entry : resources.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            String path = fileId.getPath();
            String idPath = path.substring(("fieldguide/" + folder + "/").length(), path.length() - ".json".length());
            ResourceLocation targetId = new ResourceLocation(fileId.getNamespace(), idPath);

            for (Resource resource : entry.getValue()) {
                try (Reader reader = resource.openAsReader()) {
                    processor.accept(targetId, GsonHelper.parse(reader));
                } catch (Exception e) {
                    Constants.LOG.error("Error loading field guide visual: {}", fileId, e);
                }
            }
        }
    }

    // --- Helper Accessors ---

    private void resolveAllEntries() {
        resolvedCategoryEntries.clear();
        for (Category cat : syncedCategories.values()) {
            resolveCategory(cat);
        }
    }

    private void resolveCategory(Category category) {
        Set<Object> foundEntries = new LinkedHashSet<>();
        ModConfig config = ModConfig.get();

        for (CategoryEntry entry : category.getEntries()) {
            if (entry.type() == CategoryEntry.Type.ENTRY) {
                if (entry.id() == null) continue;
                Optional<EntityType<?>> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entry.id());
                if (entityType.isPresent()) {
                    if (isValidEntity(entityType.get(), config)) foundEntries.add(entityType.get());
                } else {
                    Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(entry.id());
                    if (block.isPresent() && isValidBlock(block.get(), config)) foundEntries.add(block.get());
                }
            } else if (entry.type() == CategoryEntry.Type.AUTO_POPULATE) {
                foundEntries.addAll(getEntriesForStrategy(entry.strategy(), config));
            }
        }
        resolvedCategoryEntries.put(category.getId(), new ArrayList<>(foundEntries));
    }

    // --- Progression ---

    public List<Object> getEntriesForCategory(Category category) {
        return resolvedCategoryEntries.getOrDefault(category.getId(), Collections.emptyList());
    }

    private List<Object> getEntriesForStrategy(String strategy, ModConfig config) {
        List<Object> results = new ArrayList<>();
        if ("flora".equalsIgnoreCase(strategy)) {
            results.addAll(BuiltInRegistries.BLOCK.stream()
                    .filter(block -> block instanceof BushBlock || block instanceof LeavesBlock || block instanceof VineBlock || block instanceof CactusBlock || block instanceof SugarCaneBlock || block instanceof WaterlilyBlock || block instanceof StemBlock)
                    .sorted(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString()))
                    .toList());
        } else {
            results.addAll(BuiltInRegistries.ENTITY_TYPE.stream()
                    .filter(type -> {
                        if ("hostile".equalsIgnoreCase(strategy))
                            return type.getCategory() == MobCategory.MONSTER && SpawnEggItem.byId(type) != null;
                        if ("passive".equalsIgnoreCase(strategy))
                            return type.getCategory() != MobCategory.MONSTER && (type.getCategory() != MobCategory.MISC || SpawnEggItem.byId(type) != null);
                        return false;
                    })
                    .sorted(Comparator.comparing(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()))
                    .toList());
        }
        return results;
    }

    private boolean isValidEntity(EntityType<?> type, ModConfig config) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return type.canSummon() && !config.isEntityBlacklisted(id);
    }

    private boolean isValidBlock(Block block, ModConfig config) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return !config.isEntityBlacklisted(id);
    }

    public Category getCategoryForEntry(Object entry) {
        for (Map.Entry<ResourceLocation, List<Object>> cat : resolvedCategoryEntries.entrySet()) {
            if (cat.getValue().contains(entry)) return syncedCategories.get(cat.getKey());
        }
        return null;
    }

    public List<Object> searchEntries(String query) {
        String processedQuery = query.toLowerCase(Locale.ROOT).trim();
        List<Object> results = new ArrayList<>();
        if (processedQuery.isEmpty()) return results;
        for (Object entry : getValidEntries()) {
            if (!isUnlocked(entry) && !ModConfig.get().showUndiscoveredNames) continue;
            ResourceLocation id = getEntryId(entry);
            if (id == null) continue;

            String name = (entry instanceof EntityType<?> type) ? type.getDescription().getString() : ((Block) entry).getName().getString();
            boolean match = name.toLowerCase(Locale.ROOT).contains(processedQuery) || id.getPath().contains(processedQuery);

            if (match) results.add(entry);
        }
        return results;
    }

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

            while (blockHit.getType() == HitResult.Type.BLOCK) {
                BlockState state = minecraft.level.getBlockState(blockHit.getBlockPos());
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());

                if (id.getNamespace().equals("minecraft") && (id.getPath().equals("grass") || id.getPath().equals("tall_grass"))) {
                    Vec3 hitVec = blockHit.getLocation();
                    Vec3 nextStart = hitVec.add(viewVec.scale(0.01));

                    if (eyePos.distanceToSqr(nextStart) >= range * range) {
                        break;
                    }

                    blockHit = minecraft.level.clip(new ClipContext(
                            nextStart, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player
                    ));
                } else {
                    break;
                }
            }

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

                    if (foundTarget instanceof Block) {
                        this.scanningPos = blockHit.getBlockPos();
                    }

                    if (scanTicks >= (int) (ModConfig.get().scanSpeed * 20)) {
                        unlock(targetKey);
                        minecraft.player.playSound(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.0F, 1.0F);
                        minecraft.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);

                        fadingTarget = foundTarget;
                        if (foundTarget instanceof Block) {
                            fadingPos = scanningPos;
                        } else {
                            fadingPos = null;
                        }
                        fadeTicks = FADE_DURATION;

                        scanningTarget = null;
                        scanningPos = null;
                        scanTicks = 0;
                    }
                } else {
                    this.prevScanTicks = 0;
                    scanningTarget = foundTarget;

                    if (scanningTarget instanceof Block) {
                        this.scanningPos = blockHit.getBlockPos();
                    } else {
                        this.scanningPos = null;
                    }

                    scanTicks = 0;
                }
            } else {
                if (scanTicks > 0) {
                    this.prevScanTicks = this.scanTicks;
                    scanTicks -= 2;
                    if (scanTicks <= 0) {
                        scanningTarget = null;
                        scanningPos = null;
                        scanTicks = 0;
                    }
                } else {
                    scanningTarget = null;
                    scanningPos = null;
                }
            }

            if (scanningTarget instanceof Entity ent && (ent.isRemoved() || !ent.isAlive())) {
                scanningTarget = null;
                scanTicks = 0;
            }

        } else {
            scanningTarget = null;
            scanningPos = null;
            scanTicks = 0;
        }

        if (fadeTicks > 0) {
            fadeTicks--;
            if (fadeTicks <= 0) {
                fadingTarget = null;
                fadingPos = null;
            }
        }
    }

    public Object getScanningTarget() {
        return scanningTarget;
    }

    public BlockPos getScanningPos() {
        return scanningPos;
    }

    public Entity getScanningEntity() {
        return scanningTarget instanceof Entity ? (Entity) scanningTarget : null;
    }

    public float getScanProgress(float partialTicks) {
        float lerped = (float) prevScanTicks + ((float) scanTicks - (float) prevScanTicks) * partialTicks;
        return Math.min(1.0F, lerped / (int) (ModConfig.get().scanSpeed * 20));
    }

    public Object getFadingTarget() {
        return fadingTarget;
    }

    public BlockPos getFadingPos() {
        return fadingPos;
    }

    public Entity getFadingEntity() {
        return fadingTarget instanceof Entity ? (Entity) fadingTarget : null;
    }

    public float getFadeProgress() {
        return (float) fadeTicks / (float) FADE_DURATION;
    }

    public void unlock(Object entry) {
        unlock(entry, true);
    }

    public void unlock(Object entry, boolean showToast) {
        ResourceLocation id = getEntryId(entry);
        if (id != null && unlockedEntries.add(id.toString())) {
            this.lastUnlockedEntry = entry;
            this.lastUnlockTime = System.currentTimeMillis();
            if (showToast) Minecraft.getInstance().getToasts().addToast(new FieldGuideToast(entry));
            saveProgress();
        }
    }

    public long getLastUnlockTime() {
        return lastUnlockTime;
    }

    public Object getLastUnlockedEntry() {
        return lastUnlockedEntry;
    }

    // Drops Logic
    public List<ItemStack> getDrops(Object entry) {
        if (dropCache.containsKey(entry)) return dropCache.get(entry);
        if (!requestedDrops.contains(entry)) {
            ResourceLocation id = getEntryId(entry);
            if (id != null) {
                requestedDrops.add(entry);
                Services.NETWORK.sendToServer(new RequestDropsPacket(id));
            }
        }
        return Collections.emptyList();
    }

    public void setDrops(ResourceLocation entryId, List<ItemStack> drops) {
        getValidEntries().stream().filter(e -> Objects.equals(getEntryId(e), entryId))
                .findFirst().ifPresent(o -> dropCache.put(o, drops));
    }

    // Save/Load
    public void onWorldLoad(Path worldSaveDir) {
        this.unlockedEntries.clear();
        this.seenEntries.clear();
        this.currentSavePath = worldSaveDir != null ? worldSaveDir.resolve("fieldguide.dat") : null;
        loadProgress();
    }

    public void onWorldUnload() {
        if (this.currentSavePath != null) saveProgress();
        this.currentSavePath = null;
        this.unlockedEntries.clear();
        this.seenEntries.clear();
    }

    private void loadProgress() {
        if (currentSavePath == null || !currentSavePath.toFile().exists()) return;
        try (FileReader reader = new FileReader(currentSavePath.toFile())) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json.has("unlocked"))
                for (JsonElement e : json.getAsJsonArray("unlocked")) unlockedEntries.add(e.getAsString());
            if (json.has("seen")) for (JsonElement e : json.getAsJsonArray("seen")) seenEntries.add(e.getAsString());
        } catch (Exception e) {
            Constants.LOG.error("Failed to load progress", e);
        }
    }

    private void saveProgress() {
        if (currentSavePath == null) return;
        try {
            JsonObject json = new JsonObject();
            JsonArray uArr = new JsonArray();
            unlockedEntries.forEach(uArr::add);
            json.add("unlocked", uArr);
            JsonArray sArr = new JsonArray();
            seenEntries.forEach(sArr::add);
            json.add("seen", sArr);
            File file = currentSavePath.toFile();
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(file)) {
                GSON.toJson(json, w);
            }
        } catch (Exception e) {
            Constants.LOG.error("Failed to save progress", e);
        }
    }

    public void revoke(Object entry) {
        ResourceLocation id = getEntryId(entry);
        if (id != null && unlockedEntries.remove(id.toString())) {
            seenEntries.remove(id.toString());
            saveProgress();
        }
    }

    public void revokeAll() {
        unlockedEntries.clear();
        seenEntries.clear();
        saveProgress();
    }
}