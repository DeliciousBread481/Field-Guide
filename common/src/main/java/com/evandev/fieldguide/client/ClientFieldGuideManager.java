package com.evandev.fieldguide.client;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.data.CategoryVisual;
import com.evandev.fieldguide.client.data.EntryVisual;
import com.evandev.fieldguide.client.gui.toasts.FieldGuideToast;
import com.evandev.fieldguide.client.gui.util.EntryRenderHelper;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.CategoryEntry;
import com.evandev.fieldguide.network.ClaimXpPacket;
import com.evandev.fieldguide.platform.Services;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ClientFieldGuideManager implements ResourceManagerReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static final ClientFieldGuideManager INSTANCE = new ClientFieldGuideManager();
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
    private final Map<String, Long> discoveryTimes = new HashMap<>();
    private final Map<Object, List<ItemStack>> dropCache = new HashMap<>();
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

    private Object outOfRangeTarget = null;

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

    public static boolean hideFromSearch(Object entry) {
        return ModConfig.get().hideUndiscoveredFromSearch && !isUnlocked(entry);
    }

    public static boolean isNew(Object entry) {
        ResourceLocation id = getEntryId(entry);
        return id != null && INSTANCE.unlockedEntries.contains(id.toString()) && !INSTANCE.seenEntries.contains(id.toString());
    }

    public static void markAsSeen(Object entry) {
        ResourceLocation id = getEntryId(entry);
        if (id != null && INSTANCE.seenEntries.add(id.toString())) INSTANCE.saveProgress();
    }

    public static String getEntryDescription(Object entry) {
        ResourceLocation id = getEntryId(entry);
        if (id == null) return "";
        String overrideKey = "fieldguide." + id.getNamespace() + "." + id.getPath() + ".description";
        String fallbackKey = (entry instanceof EntityType) ? "entity." + id.getNamespace() + "." + id.getPath() + ".description" : "lore." + id.getNamespace() + "." + id.getPath();
        return I18n.exists(overrideKey) ? I18n.get(overrideKey) : (I18n.exists(fallbackKey) ? I18n.get(fallbackKey) : I18n.get("fieldguide.description.missing"));
    }

    public void updateCategoriesFromServer(List<Category> categories) {
        this.syncedCategories.clear();
        categories.sort(Comparator.comparingInt(Category::getSortIndex).thenComparing(Category::getId));

        for (Category cat : categories) {
            this.syncedCategories.put(cat.getId(), cat);
        }
        resolveAllEntries();
    }

    public void updateLootCache(Map<ResourceLocation, List<ItemStack>> lootCache) {
        for (Map.Entry<ResourceLocation, List<ItemStack>> entry : lootCache.entrySet()) {
            ResourceLocation id = entry.getKey();
            List<ItemStack> drops = entry.getValue();

            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
            if (type.isPresent()) {
                dropCache.put(type.get(), drops);
            } else {
                Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
                block.ifPresent(b -> dropCache.put(b, drops));
            }
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
        categoryVisuals.clear();
        entryVisuals.clear();
        EntryRenderHelper.clearCache();

        loadVisuals(resourceManager, "visuals/categories", (id, json) -> {
            CategoryVisual visual = new CategoryVisual();
            if (json.has("color")) visual.color = GsonHelper.getAsString(json, "color");
            if (json.has("icon")) visual.icon = new ResourceLocation(GsonHelper.getAsString(json, "icon"));
            categoryVisuals.put(id, visual);
        });

        loadVisuals(resourceManager, "visuals/entries", (derivedId, json) -> {
            ResourceLocation targetId = derivedId;
            if (json.has("id")) {
                String idStr = GsonHelper.getAsString(json, "id");
                try {
                    targetId = new ResourceLocation(idStr);
                } catch (Exception e) {
                    Constants.LOG.error("Invalid 'id' in visual override: {}", idStr, e);
                }
            }

            EntryVisual visual = new EntryVisual();

            if (json.has("auto_rotate")) visual.autoRotate = GsonHelper.getAsBoolean(json, "auto_rotate");
            if (json.has("rotation_speed")) visual.rotationSpeed = GsonHelper.getAsFloat(json, "rotation_speed");

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

            if (json.has("spawn_biomes")) {
                visual.spawnBiomes = new ArrayList<>();
                for (JsonElement el : GsonHelper.getAsJsonArray(json, "spawn_biomes")) {
                    visual.spawnBiomes.add(new ResourceLocation(el.getAsString()));
                }
            }

            entryVisuals.put(targetId, visual);
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

    public List<Object> getEntriesForCategory(Category category) {
        return resolvedCategoryEntries.getOrDefault(category.getId(), Collections.emptyList());
    }

    public List<Object> getRecentEntries(Category category, int limit) {
        return getEntriesForCategory(category).stream()
                .filter(ClientFieldGuideManager::isUnlocked)
                .sorted((a, b) -> {
                    ResourceLocation idA = getEntryId(a);
                    ResourceLocation idB = getEntryId(b);
                    long timeA = idA != null ? discoveryTimes.getOrDefault(idA.toString(), 0L) : 0L;
                    long timeB = idB != null ? discoveryTimes.getOrDefault(idB.toString(), 0L) : 0L;
                    return Long.compare(timeB, timeA);
                })
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<Object> getEntriesForStrategy(String strategy, ModConfig config) {
        List<Object> results = new ArrayList<>();
        if ("plants".equalsIgnoreCase(strategy)) {
            results.addAll(BuiltInRegistries.BLOCK.stream()
                    .filter(block -> block instanceof BushBlock || block instanceof LeavesBlock || block instanceof VineBlock || block instanceof CactusBlock || block instanceof SugarCaneBlock || block instanceof WaterlilyBlock || block instanceof StemBlock)
                    .filter(block -> isValidBlock(block, config))
                    .sorted(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString()))
                    .toList());
        } else if (strategy.startsWith("mod:")) {
            String modId = strategy.substring(4);
            results.addAll(BuiltInRegistries.ENTITY_TYPE.stream()
                    .filter(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals(modId))
                    .filter(type -> isValidEntity(type, config))
                    .sorted(Comparator.comparing(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()))
                    .toList());
        } else if (strategy.startsWith("mod_plants:")) {
            String modId = strategy.substring(10);
            results.addAll(BuiltInRegistries.BLOCK.stream()
                    .filter(block -> BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(modId))
                    .filter(block -> block instanceof BushBlock || block instanceof LeavesBlock || block instanceof VineBlock || block instanceof CactusBlock || block instanceof SugarCaneBlock || block instanceof WaterlilyBlock || block instanceof StemBlock)
                    .filter(block -> isValidBlock(block, config))
                    .sorted(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString()))
                    .toList());
        } else if (strategy.startsWith("tag:")) {
            String tagId = strategy.substring(4);
            try {
                ResourceLocation tagLoc = new ResourceLocation(tagId);
                TagKey<EntityType<?>> tagKey = TagKey.create(Registries.ENTITY_TYPE, tagLoc);
                for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                    var key = BuiltInRegistries.ENTITY_TYPE.getResourceKey(type);
                    if (key.isPresent()) {
                        var holder = BuiltInRegistries.ENTITY_TYPE.getHolder(key.get());
                        if (holder.isPresent() && holder.get().is(tagKey)) {
                            if (isValidEntity(type, config)) results.add(type);
                        }
                    }
                }
                results.sort(Comparator.comparing(o -> BuiltInRegistries.ENTITY_TYPE.getKey((EntityType<?>) o).toString()));
            } catch (Exception e) {
                Constants.LOG.error("Invalid tag strategy: {}", strategy, e);
            }
        } else if ("monsters".equalsIgnoreCase(strategy) || "animals".equalsIgnoreCase(strategy)) {
            TagKey<EntityType<?>> bossesTag = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("fieldguide", "bosses"));

            results.addAll(BuiltInRegistries.ENTITY_TYPE.stream()
                    .filter(type -> {
                        boolean isBoss = false;
                        var key = BuiltInRegistries.ENTITY_TYPE.getResourceKey(type);
                        if (key.isPresent()) {
                            var holder = BuiltInRegistries.ENTITY_TYPE.getHolder(key.get());
                            if (holder.isPresent() && holder.get().is(bossesTag)) {
                                isBoss = true;
                            }
                        }

                        if ("monsters".equalsIgnoreCase(strategy))
                            return type.getCategory() == MobCategory.MONSTER && !isBoss;
                        if ("animals".equalsIgnoreCase(strategy))
                            return type.getCategory() != MobCategory.MONSTER && (type.getCategory() != MobCategory.MISC || SpawnEggItem.byId(type) != null) && !isBoss;
                        return false;
                    })
                    .filter(type -> isValidEntity(type, config))
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

    public Object getOutOfRangeTarget() {
        return outOfRangeTarget;
    }

    public Category getCategoryForEntry(Object entry) {
        for (Map.Entry<ResourceLocation, List<Object>> cat : resolvedCategoryEntries.entrySet()) {
            if (cat.getValue().contains(entry)) return syncedCategories.get(cat.getKey());
        }
        return null;
    }

    public List<Object> searchEntries(String query) {
        String processedQuery = query.toLowerCase(Locale.ROOT).trim();
        boolean exactMatch;
        List<Object> results = new ArrayList<>();
        if (processedQuery.isEmpty()) return results;

        // Exact Match
        if (processedQuery.startsWith("=")) {
            exactMatch = true;
            processedQuery = processedQuery.substring(1);
        } else {
            exactMatch = false;
        }

        // Search by Tag
        if (processedQuery.startsWith("#")) {
            String tagQuery = processedQuery.substring(1);
            if (tagQuery.isEmpty()) return results;

            for (Object entry : getValidEntries()) {
                if (hideFromSearch(entry)) continue;
                if (entry instanceof EntityType<?> type) {
                    var key = BuiltInRegistries.ENTITY_TYPE.getResourceKey(type);
                    if (key.isPresent()) {
                        Optional<Holder.Reference<EntityType<?>>> holder = BuiltInRegistries.ENTITY_TYPE.getHolder(key.get());
                        if (holder.isPresent()) {
                            if (exactMatch) {
                                if (holder.get().tags().anyMatch(tag -> tag.location().toString().toLowerCase(Locale.ROOT).equals(tagQuery) || tag.location().getPath().toLowerCase(Locale.ROOT).equals(tagQuery))) {
                                    results.add(entry);
                                }
                            } else {
                                if (holder.get().tags().anyMatch(tag -> tag.location().toString().toLowerCase(Locale.ROOT).contains(tagQuery) || tag.location().getPath().toLowerCase(Locale.ROOT).contains(tagQuery))) {
                                    results.add(entry);
                                }
                            }
                        }
                    }
                } else if (entry instanceof Block block) {
                    var key = BuiltInRegistries.BLOCK.getResourceKey(block);
                    if (key.isPresent()) {
                        Optional<Holder.Reference<Block>> holder = BuiltInRegistries.BLOCK.getHolder(key.get());
                        if (holder.isPresent()) {
                            if (holder.get().tags().anyMatch(tag -> tag.location().toString().toLowerCase(Locale.ROOT).contains(tagQuery) || tag.location().getPath().toLowerCase(Locale.ROOT).contains(tagQuery))) {
                                results.add(entry);
                            }
                        }
                    }
                }
            }
            return results;
        }

        // Search by Drop
        if (processedQuery.startsWith("^")) {
            String dropQuery = processedQuery.substring(1);
            if (dropQuery.isEmpty()) return results;

            for (Object entry : getValidEntries()) {
                if (hideFromSearch(entry)) continue;

                if (dropCache.containsKey(entry)) {
                    List<ItemStack> drops = dropCache.get(entry);
                    boolean match = drops.stream().anyMatch(stack -> {
                                if (exactMatch) {
                                    return stack.getHoverName().getString().toLowerCase(Locale.ROOT).equals(dropQuery);
                                } else {
                                    return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(dropQuery);
                                }
                            }
                    );
                    if (match) results.add(entry);
                }
            }
            return results;
        }

        // Search by Biome
        if (processedQuery.startsWith("!")) {
            String biomeQuery = processedQuery.substring(1);
            if (biomeQuery.isEmpty()) return results;

            if (Minecraft.getInstance().level != null) {
                var registryAccess = Minecraft.getInstance().level.registryAccess();
                var biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);

                for (var biomeEntry : biomeRegistry.entrySet()) {
                    ResourceLocation biomeId = biomeEntry.getKey().location();
                    boolean biomeMatch;
                    if (exactMatch) {
                        biomeMatch = biomeId.toString().equals(biomeQuery) || biomeId.getPath().equals(biomeQuery);
                    } else {
                        biomeMatch = biomeId.toString().contains(biomeQuery) || biomeId.getPath().contains(biomeQuery);
                    }
                    if (biomeMatch) {
                        Biome biome = biomeEntry.getValue();
                        for (MobCategory cat : MobCategory.values()) {
                            var spawns = biome.getMobSettings().getMobs(cat);
                            for (var spawn : spawns.unwrap()) {
                                if (isValidEntity(spawn.type, ModConfig.get())) {
                                    if (!hideFromSearch(spawn.type) && !results.contains(spawn.type)) {
                                        results.add(spawn.type);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return results;
        }

        // Search by Mod ID
        if (processedQuery.startsWith("@")) {
            String modQuery = processedQuery.substring(1);
            if (modQuery.isEmpty()) return results;

            for (Object entry : getValidEntries()) {
                if (hideFromSearch(entry)) continue;
                ResourceLocation id = getEntryId(entry);
                if (id != null) {
                    boolean match;
                    if (exactMatch) {
                        match = id.getNamespace().toLowerCase(Locale.ROOT).equals(modQuery);
                    } else {
                        match = id.getNamespace().toLowerCase(Locale.ROOT).contains(modQuery);
                    }
                    if (match) results.add(entry);
                }
            }
            return results;
        }

        // Standard Name/ID Search
        for (Object entry : getValidEntries()) {
            if (hideFromSearch(entry)) continue;
            ResourceLocation id = getEntryId(entry);
            if (id == null) continue;

            String name = (entry instanceof EntityType<?> type) ? type.getDescription().getString() : ((Block) entry).getName().getString();
            boolean match;
            if (exactMatch) {
                match = name.toLowerCase(Locale.ROOT).equals(processedQuery) || id.getPath().equals(processedQuery);
            } else {
                match = name.toLowerCase(Locale.ROOT).contains(processedQuery) || id.getPath().contains(processedQuery);
            }

            if (match) results.add(entry);
        }
        return results;
    }

    public void onClientTick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) return;

        boolean isScanningActive = (minecraft.player.isUsingItem() && minecraft.player.getUseItem().is(Items.SPYGLASS))
                || !ModConfig.get().requireSpyglass;

        if (isScanningActive) {
            double range = 256.0D;
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

                if (state.canBeReplaced()) {
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
            double hitDistSq = Math.min(entityDist, blockDist);

            if (entityHit != null && entityDist < blockDist) {
                Entity hitEntity = entityHit.getEntity();

                if (hitEntity instanceof EnderDragonPart part) {
                    hitEntity = part.parentMob;
                }

                EntityType<?> type = hitEntity.getType();
                Category cat = getCategoryForEntry(type);
                boolean isScannable = cat == null || cat.isScannable();

                if (getValidEntries().contains(type) && !isUnlocked(type) && isScannable) {
                    foundTarget = hitEntity;
                } else if (!isUnlocked(type) && isScannable) {
                    ResourceLocation originalId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                    if (ModConfig.get().getRedirect(originalId) != null) {
                        foundTarget = hitEntity;
                    }
                }
            } else if (blockHit.getType() == HitResult.Type.BLOCK) {
                BlockState state = minecraft.level.getBlockState(blockHit.getBlockPos());
                Block block = state.getBlock();
                if (getValidEntries().contains(block) && !isUnlocked(block)) {
                    foundTarget = block;
                } else if (!isUnlocked(block)) {
                    ResourceLocation originalId = BuiltInRegistries.BLOCK.getKey(block);
                    if (ModConfig.get().getRedirect(originalId) != null) {
                        foundTarget = block;
                    }
                }
            }

            if (foundTarget != null) {
                double activeScanDist = ModConfig.get().scanDistance;
                boolean outOfRange = hitDistSq > (activeScanDist * activeScanDist);

                if (outOfRange) {
                    this.outOfRangeTarget = foundTarget;
                    this.scanningTarget = null;
                    this.scanningPos = null;
                    this.scanTicks = 0;
                    this.prevScanTicks = 0;
                } else {
                    this.outOfRangeTarget = null;

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
                            ResourceLocation targetId = getEntryId(targetKey);
                            if (targetId != null) {
                                ResourceLocation redirectId = ModConfig.get().getRedirect(targetId);
                                if (redirectId != null) {
                                    Optional<EntityType<?>> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(redirectId);
                                    if (entityType.isPresent()) {
                                        targetKey = entityType.get();
                                    } else {
                                        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(redirectId);
                                        if (block.isPresent()) {
                                            targetKey = block.get();
                                        }
                                    }
                                }
                            }

                            unlock(targetKey);
                            minecraft.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);

                            if (ModConfig.get().grantXpOnScan && ModConfig.get().xpAmountOnScan > 0) {
                                Services.NETWORK.sendToServer(
                                        new ClaimXpPacket(ModConfig.get().xpAmountOnScan)
                                );
                            }

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

                        minecraft.player.playSound(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.0F, 1.0F);

                        scanTicks = 0;
                    }
                }
            } else {
                this.outOfRangeTarget = null;

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
            outOfRangeTarget = null;
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

    public Entity getOutOfRangeEntity() {
        return outOfRangeTarget instanceof Entity ? (Entity) outOfRangeTarget : null;
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

    public float getFadeProgress(float partialTicks) {
        float currentFade = Math.max(0, fadeTicks - partialTicks);
        return currentFade / (float) FADE_DURATION;
    }

    public boolean getIsTickingDown() {
        return scanTicks < prevScanTicks;
    }

    public void unlock(Object entry) {
        unlock(entry, true);
    }

    public void unlock(Object entry, boolean showToast) {
        ResourceLocation id = getEntryId(entry);
        if (id != null && unlockedEntries.add(id.toString())) {
            this.lastUnlockedEntry = entry;
            this.lastUnlockTime = System.currentTimeMillis();
            this.discoveryTimes.put(id.toString(), this.lastUnlockTime);
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

    public long getDiscoveryTime(Object entry) {
        ResourceLocation id = getEntryId(entry);
        if (id != null) {
            return discoveryTimes.getOrDefault(id.toString(), 0L);
        }
        return 0L;
    }

    public List<ItemStack> getDrops(Object entry) {
        return dropCache.getOrDefault(entry, Collections.emptyList());
    }

    public void onWorldLoad(String serverIdentifier) {
        this.unlockedEntries.clear();
        this.seenEntries.clear();
        this.discoveryTimes.clear();
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        Path dataDir = gameDir.resolve("config").resolve("fieldguide_data");

        try {
            Files.createDirectories(dataDir);
        } catch (Exception ignored) {
        }

        String safeName = serverIdentifier.replaceAll("[^a-zA-Z0-9.-]", "_");
        this.currentSavePath = dataDir.resolve(safeName + ".dat");
        loadProgress();
    }

    public void onWorldUnload() {
        if (this.currentSavePath != null) saveProgress();
        this.currentSavePath = null;
        this.unlockedEntries.clear();
        this.seenEntries.clear();
        this.discoveryTimes.clear();
    }

    private void loadProgress() {
        if (currentSavePath == null || !currentSavePath.toFile().exists()) return;
        try (FileReader reader = new FileReader(currentSavePath.toFile())) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json.has("unlocked"))
                for (JsonElement e : json.getAsJsonArray("unlocked")) unlockedEntries.add(e.getAsString());
            if (json.has("seen")) for (JsonElement e : json.getAsJsonArray("seen")) seenEntries.add(e.getAsString());
            if (json.has("times")) {
                JsonObject times = json.getAsJsonObject("times");
                for (Map.Entry<String, JsonElement> entry : times.entrySet()) {
                    discoveryTimes.put(entry.getKey(), entry.getValue().getAsLong());
                }
            }
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
            JsonObject timesObj = new JsonObject();
            discoveryTimes.forEach(timesObj::addProperty);
            json.add("times", timesObj);

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
            discoveryTimes.remove(id.toString());
            saveProgress();
        }
    }

    public void revokeAll() {
        unlockedEntries.clear();
        seenEntries.clear();
        discoveryTimes.clear();
        saveProgress();
    }
}