package com.evandev.fieldguide.client.progress;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.FieldGuideLimits;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.data.JournalPage;
import com.evandev.fieldguide.client.gui.toasts.FieldGuideToast;
import com.evandev.fieldguide.client.gui.util.IconCacheManager;
import com.evandev.fieldguide.config.ClientConfig;
import com.evandev.fieldguide.entry.EntryResolver;
import com.evandev.fieldguide.network.MarkSeenPacket;
import com.evandev.fieldguide.network.ProgressUpdatePacket;
import com.evandev.fieldguide.network.UpdateEntryDataPacket;
import com.evandev.fieldguide.network.UpdateJournalPacket;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.server.progress.PlayerFieldGuideProgress;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ProgressManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ProgressManager INSTANCE = new ProgressManager();

    private final Set<String> unlockedEntries = new HashSet<>();
    private final Set<String> seenEntries = new HashSet<>();
    private final Map<String, Long> discoveryTimes = new HashMap<>();
    private final Map<String, Long> discoveryGameTimes = new HashMap<>();
    private final Map<String, String> customDescriptions = new HashMap<>();
    private final Map<String, String> customNames = new HashMap<>();
    private final Map<String, String> entryPhotographs = new HashMap<>();
    private final Map<String, String> selectedVariants = new HashMap<>();
    private final Set<String> killedOnly = new HashSet<>();
    private final Set<String> eatenOnly = new HashSet<>();
    private final List<JournalPage> journalPages = new ArrayList<>();
    private String lastUnlockedVariant = null;

    private String journalTitle = "My Field Guide";

    private long lastUnlockTime = 0;
    private Object lastUnlockedEntry = null;

    private ProgressManager() {
    }

    public static ProgressManager getInstance() {
        return INSTANCE;
    }

    private static void applyEntryMap(Map<String, String> source, Map<String, String> target) {
        source.forEach((key, value) -> {
            if (value.isEmpty()) {
                target.remove(key);
            } else {
                target.put(key, value);
            }
        });
    }

    public String getLastUnlockedVariant() {
        return lastUnlockedVariant;
    }

    public boolean isKillToUnlock(ResourceLocation entryId) {
        return killedOnly.contains(entryId.toString());
    }

    public boolean isEatToUnlock(ResourceLocation entryId) {
        return eatenOnly.contains(entryId.toString());
    }

    public void applyServerUpdate(ProgressUpdatePacket packet) {
        if (packet.isReset()) {
            unlockedEntries.clear();
            seenEntries.clear();
            discoveryTimes.clear();
            discoveryGameTimes.clear();
            customNames.clear();
            customDescriptions.clear();
            entryPhotographs.clear();
            selectedVariants.clear();
            killedOnly.clear();
            eatenOnly.clear();
        }

        for (String id : packet.getRevoked()) {
            unlockedEntries.remove(id);
            seenEntries.remove(id);
            discoveryTimes.remove(id);
            discoveryGameTimes.remove(id);
            entryPhotographs.remove(id);
            selectedVariants.remove(id);
        }

        Map<String, String> toastsToShow = new HashMap<>();

        for (String id : packet.getUnlocked()) {
            if (unlockedEntries.add(id) && !packet.isSilent()) {
                boolean isVariant = id.contains("#");
                String baseIdStr = isVariant ? id.split("#")[0] : id;
                String variantId = isVariant ? id.split("#")[1] : null;

                if (isVariant || !toastsToShow.containsKey(baseIdStr)) {
                    toastsToShow.put(baseIdStr, variantId);
                }
            }
        }

        for (Map.Entry<String, String> entryToToast : toastsToShow.entrySet()) {
            Object entry = resolveEntryFromId(entryToToast.getKey());
            if (entry != null) {
                this.lastUnlockTime = System.currentTimeMillis();
                this.lastUnlockedEntry = entry;
                this.lastUnlockedVariant = entryToToast.getValue();
                if (ClientConfig.get().showToasts) {
                    Minecraft.getInstance().getToasts().addToast(new FieldGuideToast(entry, entryToToast.getValue()));
                }
            }
        }

        seenEntries.addAll(packet.getSeen());
        discoveryTimes.putAll(packet.getDiscoveryTimes());
        discoveryGameTimes.putAll(packet.getDiscoveryGameTimes());
        applyEntryMap(packet.getCustomNames(), customNames);
        applyEntryMap(packet.getCustomDescriptions(), customDescriptions);
        applyEntryMap(packet.getEntryPhotographs(), entryPhotographs);
        applyEntryMap(packet.getSelectedVariants(), selectedVariants);

        killedOnly.clear();
        killedOnly.addAll(packet.getKilledOnly());

        eatenOnly.clear();
        eatenOnly.addAll(packet.getEatenOnly());

        packet.getJournalTitle().ifPresent(title -> journalTitle = title);
        packet.getJournalPages().ifPresent(pages -> {
            journalPages.clear();
            for (PlayerFieldGuideProgress.JournalPageData page : pages) {
                journalPages.add(new JournalPage(page.title(), page.content(), page.timestamp()));
            }
        });
    }

    private Object resolveEntryFromId(String idStr) {
        ResourceLocation id = ResourceLocation.tryParse(idStr);
        if (id == null) return null;

        for (Object entry : ClientFieldGuideManager.getValidEntries()) {
            ResourceLocation entryId = ClientFieldGuideManager.getEntryId(entry);
            if (id.equals(entryId)) return entry;
        }
        return null;
    }

    public boolean isUnlocked(Object entry) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        if (id == null) return false;
        if (unlockedEntries.contains(id.toString())) return true;
        return unlockedEntries.contains(EntryResolver.getRawId(id).toString());
    }

    public boolean isNew(Object entry) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        if (id == null) return false;
        String idStr = id.toString();
        String rawIdStr = EntryResolver.getRawId(id).toString();
        boolean unlocked = unlockedEntries.contains(idStr) || unlockedEntries.contains(rawIdStr);
        boolean seen = seenEntries.contains(idStr) || seenEntries.contains(rawIdStr);
        return unlocked && !seen;
    }

    public long getLastUnlockTime() {
        return lastUnlockTime;
    }

    public Object getLastUnlockedEntry() {
        return lastUnlockedEntry;
    }

    public long getDiscoveryTime(Object entry) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        return id != null ? discoveryTimes.getOrDefault(id.toString(), 0L) : 0L;
    }

    public long getDiscoveryGameTime(Object entry) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        return id != null ? discoveryGameTimes.getOrDefault(id.toString(), 0L) : 0L;
    }

    public void markAsSeen(Object entry) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        if (id != null && seenEntries.add(id.toString())) {
            Services.NETWORK.sendToServer(new MarkSeenPacket(id));
        }
    }

    public String getCustomName(Object entry) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        return id != null ? customNames.get(id.toString()) : null;
    }

    public void setCustomName(Object entry, String name) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        if (id != null) {
            boolean clear = name == null || name.isEmpty() || name.equals(ClientFieldGuideManager.getDefaultName(entry));
            if (clear) {
                customNames.remove(id.toString());
            } else {
                customNames.put(id.toString(), name);
            }
            Services.NETWORK.sendToServer(UpdateEntryDataPacket.setName(id, clear ? null : name));
        }
    }

    public String getCustomDescription(Object entry) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        return id != null ? customDescriptions.get(id.toString()) : null;
    }

    public void setCustomDescription(Object entry, String desc) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        if (id != null) {
            customDescriptions.put(id.toString(), desc);
            Services.NETWORK.sendToServer(UpdateEntryDataPacket.setDescription(id, desc));
        }
    }

    public String getSelectedVariant(Object entry) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        return id != null ? getSelectedVariant(id) : null;
    }

    public String getSelectedVariant(ResourceLocation id) {
        return selectedVariants.get(id.toString());
    }

    public void setSelectedVariant(Object entry, String variantId) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        if (id != null) {
            setSelectedVariant(id, variantId);
        }
    }

    public void setSelectedVariant(ResourceLocation id, String variantId) {
        if (variantId == null || variantId.isEmpty()) {
            selectedVariants.remove(id.toString());
        } else {
            selectedVariants.put(id.toString(), variantId);
        }
        IconCacheManager.clearCache();
        Services.NETWORK.sendToServer(UpdateEntryDataPacket.setSelectedVariant(id, variantId));
    }

    public ItemStack getPhotograph(Object entry) {
        return getPhotograph(entry, null);
    }

    public ItemStack getPhotograph(Object entry, String variantId) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        if (id != null) {
            String key = id.toString();
            if (variantId != null && !variantId.isEmpty()) {
                key += "#" + variantId;
            }
            if (entryPhotographs.containsKey(key)) {
                try {
                    CompoundTag tag = TagParser.parseTag(entryPhotographs.get(key));
                    return ItemStack.of(tag);
                } catch (Exception e) {
                    return ItemStack.EMPTY;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public void setPhotograph(Object entry, int slot, ItemStack stack, String variantId) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        if (id != null) {
            String key = id.toString();
            if (variantId != null && !variantId.isEmpty()) {
                key += "#" + variantId;
            }
            if (slot < 0 || stack == null || stack.isEmpty()) {
                entryPhotographs.remove(key);
                Services.NETWORK.sendToServer(UpdateEntryDataPacket.removePhotograph(id, variantId));
            } else {
                CompoundTag tag = new CompoundTag();
                stack.save(tag);
                entryPhotographs.put(key, tag.toString());
                Services.NETWORK.sendToServer(UpdateEntryDataPacket.setPhotograph(id, slot, variantId));
            }
        }
    }

    public String getJournalTitle() {
        return journalTitle;
    }

    public void setJournalTitle(String title) {
        this.journalTitle = title;
        sendJournalUpdate();
    }

    public void saveJournal() {
        sendJournalUpdate();
    }

    public List<JournalPage> getJournalPages() {
        if (journalPages.isEmpty()) {
            String defaultText = I18n.get("fieldguide.journal.default");
            journalPages.add(new JournalPage("", defaultText, System.currentTimeMillis()));
        }
        return journalPages;
    }

    private void sendJournalUpdate() {
        List<PlayerFieldGuideProgress.JournalPageData> pageData = new ArrayList<>();
        int limit = Math.min(journalPages.size(), FieldGuideLimits.MAX_JOURNAL_PAGES);
        for (int i = 0; i < limit; i++) {
            JournalPage page = journalPages.get(i);
            pageData.add(new PlayerFieldGuideProgress.JournalPageData(page.title, page.content, page.timestamp));
        }
        Services.NETWORK.sendToServer(new UpdateJournalPacket(journalTitle, pageData));
    }

    public void onWorldLoad() {
        unlockedEntries.clear();
        seenEntries.clear();
        discoveryTimes.clear();
        discoveryGameTimes.clear();
        customDescriptions.clear();
        customNames.clear();
        entryPhotographs.clear();
        selectedVariants.clear();
        journalPages.clear();
        journalTitle = "My Field Guide";
        lastUnlockTime = 0;
        lastUnlockedEntry = null;
        lastUnlockedVariant = null;
    }

    public void onWorldUnload() {
        unlockedEntries.clear();
        seenEntries.clear();
        discoveryTimes.clear();
        discoveryGameTimes.clear();
        customDescriptions.clear();
        customNames.clear();
        entryPhotographs.clear();
        selectedVariants.clear();
        journalPages.clear();
        journalTitle = "My Field Guide";
        lastUnlockTime = 0;
        lastUnlockedEntry = null;
        lastUnlockedVariant = null;
    }

    public Set<String> getUnlockedEntries() {
        return Collections.unmodifiableSet(unlockedEntries);
    }

    public void exportToLang(String type) {
        try {
            JsonObject langJson = new JsonObject();
            boolean exportNames = type.equals("names") || type.equals("all");
            boolean exportDesc = type.equals("descriptions") || type.equals("all");

            if (exportNames) {
                for (Map.Entry<String, String> entry : customNames.entrySet()) {
                    ResourceLocation id = new ResourceLocation(entry.getKey());
                    String key = BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? "entity." + id.getNamespace() + "." + id.getPath() : "block." + id.getNamespace() + "." + id.getPath();
                    langJson.addProperty(key, entry.getValue());
                }
            }
            if (exportDesc) {
                for (Map.Entry<String, String> entry : customDescriptions.entrySet()) {
                    ResourceLocation id = new ResourceLocation(entry.getKey());
                    langJson.addProperty("fieldguide." + id.getNamespace() + "." + id.getPath() + ".description", entry.getValue());
                }
            }

            Path exportDir = Minecraft.getInstance().gameDirectory.toPath().resolve("fieldguide_exports");
            Files.createDirectories(exportDir);
            File exportFile = exportDir.resolve("en_us_" + type + "_" + System.currentTimeMillis() + ".json").toFile();

            try (FileWriter writer = new FileWriter(exportFile)) {
                GSON.toJson(langJson, writer);
            }
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal("§aExported Field Guide data to " + exportFile.getAbsolutePath()), false);
            }
        } catch (Exception e) {
            Constants.LOG.error("Failed to export lang file", e);
            if (Minecraft.getInstance().player != null)
                Minecraft.getInstance().player.displayClientMessage(Component.literal("§cFailed to export: " + e.getMessage()), false);
        }
    }
}
