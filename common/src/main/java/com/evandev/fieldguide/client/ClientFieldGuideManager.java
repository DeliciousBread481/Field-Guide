package com.evandev.fieldguide.client;

import com.evandev.fieldguide.api.AutoPopulateRegistry;
import com.evandev.fieldguide.api.Category;
import com.evandev.fieldguide.api.GuideEntry;
import com.evandev.fieldguide.api.variant.DatapackVariant;
import com.evandev.fieldguide.client.data.EntryVisual;
import com.evandev.fieldguide.client.data.JournalPage;
import com.evandev.fieldguide.client.gui.util.EntryRenderHelper;
import com.evandev.fieldguide.client.gui.util.IconCacheManager;
import com.evandev.fieldguide.client.manager.ClientCategoryManager;
import com.evandev.fieldguide.client.manager.ClientLootManager;
import com.evandev.fieldguide.client.manager.ClientTextManager;
import com.evandev.fieldguide.client.manager.ClientVisualManager;
import com.evandev.fieldguide.client.progress.ProgressManager;
import com.evandev.fieldguide.client.scan.FieldGuideScanner;
import com.evandev.fieldguide.client.search.SearchManager;
import com.evandev.fieldguide.compat.cobblemon.FieldGuideCobblemonCompat;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.config.ServerConfig;
import com.evandev.fieldguide.entry.EntryResolver;
import com.evandev.fieldguide.network.ProgressUpdatePacket;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.variant.FieldGuideVariantManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ClientFieldGuideManager implements ResourceManagerReloadListener {
    private static final ClientFieldGuideManager INSTANCE = new ClientFieldGuideManager();

    private ClientFieldGuideManager() {
    }

    public static ClientFieldGuideManager getInstance() {
        return INSTANCE;
    }

    public static void clearCache() {
        ClientLootManager.getInstance().clear();
        ClientCategoryManager.getInstance().getResolvedCategoryEntries().clear();
        EntryRenderHelper.clearCache();
        ClientCategoryManager.getInstance().resolveAllEntries();
    }

    public static ResourceLocation getEntryId(Object entry) {
        return EntryResolver.getEntryId(entry);
    }

    public static boolean hideFromSearch(Object entry) {
        return ServerConfig.get().hideUndiscoveredFromSearch && !isUnlocked(entry);
    }

    public static boolean isUnlocked(Object entry) {
        return ProgressManager.getInstance().isUnlocked(entry);
    }

    public static boolean isVariantUnlocked(Object entry, String variantId) {
        ResourceLocation id = getEntryId(entry);
        if (id == null) return false;
        String fullId = id + "#" + variantId;
        return ProgressManager.getInstance().getUnlockedEntries().contains(fullId);
    }

    public static boolean isNew(Object entry) {
        return ProgressManager.getInstance().isNew(entry);
    }

    public static void markAsSeen(Object entry) {
        ProgressManager.getInstance().markAsSeen(entry);
    }

    public static String getEntryDescription(Object entry) {
        return ClientTextManager.getInstance().getEntryDescription(entry);
    }

    public static void setCustomDescription(Object entry, String desc) {
        ClientTextManager.getInstance().setCustomDescription(entry, desc);
    }

    public static void setCustomName(Object entry, String name) {
        ClientTextManager.getInstance().setCustomName(entry, name);
    }

    public static Component getEntryName(Object entry) {
        return ClientTextManager.getInstance().getEntryName(entry);
    }

    public static String getDefaultName(Object entry) {
        return ClientTextManager.getInstance().getDefaultName(entry);
    }

    public static Component getDefaultNameComponent(Object entry) {
        return ClientTextManager.getInstance().getDefaultNameComponent(entry);
    }

    public static Map<ResourceLocation, Category> getCategories() {
        return ClientCategoryManager.getInstance().getCategories();
    }

    public static List<Object> getValidEntries() {
        return ClientCategoryManager.getInstance().getValidEntries();
    }

    public String getLastUnlockedVariant() {
        return ProgressManager.getInstance().getLastUnlockedVariant();
    }

    public List<String> getBiomeAdditions() {
        return ClientCategoryManager.getInstance().getBiomeAdditions();
    }

    public List<String> getBiomeRemovals() {
        return ClientCategoryManager.getInstance().getBiomeRemovals();
    }

    public List<String> getLootAdditions() {
        return ClientCategoryManager.getInstance().getLootAdditions();
    }

    public List<String> getLootRemovals() {
        return ClientCategoryManager.getInstance().getLootRemovals();
    }

    public Object getEntryForTarget(Object target) {
        return ClientCategoryManager.getInstance().getEntryForTarget(target);
    }

    public List<Object> getEntriesForTarget(Object target) {
        return ClientCategoryManager.getInstance().getEntriesForTarget(target);
    }

    public String getJournalTitle() {
        return ClientTextManager.getInstance().getJournalTitle();
    }

    public void setJournalTitle(String title) {
        ClientTextManager.getInstance().setJournalTitle(title);
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

    public boolean isKillToUnlock(ResourceLocation entryId) {
        return ProgressManager.getInstance().isKillToUnlock(entryId);
    }

    public void updateCategoriesFromServer(List<Category> categories, List<GuideEntry> entries, Map<ResourceLocation, ResourceLocation> redirects, boolean clearCache, boolean resolveEntries) {
        ClientCategoryManager.getInstance().updateCategoriesFromServer(categories, entries, redirects, clearCache, resolveEntries);
    }

    public void updateModifiers(List<String> biomeAdditions, List<String> biomeRemovals, List<String> lootAdditions, List<String> lootRemovals, boolean clearCache) {
        ClientCategoryManager.getInstance().updateModifiers(biomeAdditions, biomeRemovals, lootAdditions, lootRemovals, clearCache);
    }

    public void updateLootCache(Map<ResourceLocation, List<ItemStack>> lootCache, boolean clearCache) {
        ClientLootManager.getInstance().updateLootCache(lootCache, clearCache);
    }

    public void updateVariants(Map<ResourceLocation, List<DatapackVariant>> variants) {
        if (variants != null && !variants.isEmpty()) {
            FieldGuideVariantManager.setDatapackVariants(variants);
        }
    }

    public EntryVisual getEntryVisual(Object entry) {
        return ClientVisualManager.getInstance().getEntryVisual(AutoPopulateRegistry.getEntryKey(entry));
    }

    public ResourceLocation getRedirect(ResourceLocation source) {
        return ClientCategoryManager.getInstance().getRedirect(source);
    }

    @Override
    public void onResourceManagerReload(@NotNull ResourceManager resourceManager) {
        ModConfig.load();
        ClientVisualManager.getInstance().onResourceManagerReload(resourceManager);
        ClientCategoryManager.getInstance().setNeedsResolution(true);
    }

    private void resolveAllEntries() {
        ClientCategoryManager.getInstance().resolveAllEntries();
    }

    public List<Object> getEntriesForCategory(Category category) {
        return ClientCategoryManager.getInstance().getEntriesForCategory(category);
    }

    public List<Object> getRecentEntries(Category category, int limit) {
        return ClientCategoryManager.getInstance().getRecentEntries(category, limit);
    }

    public boolean isValidEntity(EntityType<?> type, ResourceLocation categoryId) {
        return EntryResolver.isValidEntity(type, categoryId);
    }

    public Category getCategoryForEntry(Object entry) {
        return ClientCategoryManager.getInstance().getCategoryForEntry(entry);
    }

    public List<Object> searchEntries(String query) {
        return SearchManager.searchEntries(query);
    }

    public List<ItemStack> getDrops(Object entry) {
        return ClientLootManager.getInstance().getDrops(entry);
    }

    public void onClientTick(Minecraft minecraft) {
        FieldGuideScanner.getInstance().onClientTick(minecraft);
        IconCacheManager.tick();

        if (ClientCategoryManager.getInstance().isNeedsResolution() && minecraft.level != null) {
            resolveAllEntries();
            ClientCategoryManager.getInstance().setNeedsResolution(false);
        }
    }

    public long getLastUnlockTime() {
        return ProgressManager.getInstance().getLastUnlockTime();
    }

    public Object getLastUnlockedEntry() {
        return ProgressManager.getInstance().getLastUnlockedEntry();
    }

    public void onWorldLoad() {
        ProgressManager.getInstance().onWorldLoad();
        warmUpCache();
    }

    private void warmUpCache() {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {
            }

            List<Object> entries = getValidEntries();
            for (Object entry : entries) {
                loadBiomesInBackground(entry);

                if (isUnlocked(entry)) {
                    getDrops(entry);
                }
            }
        });
    }

    private void loadBiomesInBackground(Object entry) {
        if (Services.PLATFORM.isModLoaded("cobblemon") && EntryResolver.getEntryId(entry).getNamespace().equals("cobblemon")) {
            FieldGuideCobblemonCompat.getCobblemonBiomes(entry);
        }
    }

    public void onWorldUnload() {
        ClientLootManager.getInstance().clear();
        ProgressManager.getInstance().onWorldUnload();
        ServerConfig.resetSyncedConfig();
    }

    public void applyServerUpdate(ProgressUpdatePacket packet) {
        ProgressManager.getInstance().applyServerUpdate(packet);
    }
}