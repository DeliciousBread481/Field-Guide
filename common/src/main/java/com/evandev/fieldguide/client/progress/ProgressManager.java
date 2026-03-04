package com.evandev.fieldguide.client.progress;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.data.JournalPage;
import com.evandev.fieldguide.client.gui.toasts.FieldGuideToast;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileReader;
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
    private final List<JournalPage> journalPages = new ArrayList<>();

    private String journalTitle = "My Field Guide";
    private Path currentSavePath = null;

    private long lastUnlockTime = 0;
    private Object lastUnlockedEntry = null;

    private ProgressManager() {
    }

    public static ProgressManager getInstance() {
        return INSTANCE;
    }

    public boolean isUnlocked(Object entry) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        return id != null && unlockedEntries.contains(id.toString());
    }

    public boolean isNew(Object entry) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        return id != null && unlockedEntries.contains(id.toString()) && !seenEntries.contains(id.toString());
    }

    public void markAsSeen(Object entry) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        if (id != null && seenEntries.add(id.toString())) saveProgress();
    }

    public void unlock(Object entry) {
        unlock(entry, true);
    }

    public void unlock(Object entry, boolean showToast) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        if (id != null && unlockedEntries.add(id.toString())) {
            this.lastUnlockedEntry = entry;
            this.lastUnlockTime = System.currentTimeMillis();
            this.discoveryTimes.put(id.toString(), this.lastUnlockTime);
            if (Minecraft.getInstance().level != null) {
                this.discoveryGameTimes.put(id.toString(), Minecraft.getInstance().level.dayTime());
            }
            if (showToast) Minecraft.getInstance().getToasts().addToast(new FieldGuideToast(entry));
            saveProgress();
        }
    }

    public void revoke(Object entry) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        if (id != null && unlockedEntries.remove(id.toString())) {
            seenEntries.remove(id.toString());
            discoveryTimes.remove(id.toString());
            discoveryGameTimes.remove(id.toString());
            entryPhotographs.remove(id.toString());
            saveProgress();
        }
    }

    public void revokeAll() {
        unlockedEntries.clear();
        seenEntries.clear();
        discoveryTimes.clear();
        discoveryGameTimes.clear();
        entryPhotographs.clear();
        saveProgress();
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

    public String getCustomName(Object entry) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        return id != null ? customNames.get(id.toString()) : null;
    }

    public void setCustomName(Object entry, String name) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        if (id != null) {
            if (name == null || name.isEmpty() || name.equals(ClientFieldGuideManager.getDefaultName(entry))) {
                customNames.remove(id.toString());
            } else {
                customNames.put(id.toString(), name);
            }
            saveProgress();
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
            saveProgress();
        }
    }

    public ItemStack getPhotograph(Object entry) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        if (id != null && entryPhotographs.containsKey(id.toString())) {
            try {
                CompoundTag tag = TagParser.parseTag(entryPhotographs.get(id.toString()));
                return ItemStack.of(tag);
            } catch (Exception e) {
                return ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }

    public void setPhotograph(Object entry, ItemStack stack) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        if (id != null) {
            if (stack == null || stack.isEmpty()) {
                entryPhotographs.remove(id.toString());
            } else {
                CompoundTag tag = new CompoundTag();
                stack.save(tag);
                entryPhotographs.put(id.toString(), tag.toString());
            }
            saveProgress();
        }
    }

    public String getJournalTitle() {
        return journalTitle;
    }

    public void setJournalTitle(String title) {
        this.journalTitle = title;
        saveProgress();
    }

    public void saveJournal() {
        saveProgress();
    }

    public List<JournalPage> getJournalPages() {
        if (journalPages.isEmpty()) {
            String defaultText = I18n.get("fieldguide.journal.default");
            journalPages.add(new JournalPage("", defaultText, System.currentTimeMillis()));
        }
        return journalPages;
    }

    public void onWorldLoad(String serverIdentifier) {
        unlockedEntries.clear();
        seenEntries.clear();
        discoveryTimes.clear();
        entryPhotographs.clear();

        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null) {
                Path worldDir = minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
                Path dataDir = worldDir.resolve("fieldguide_data");
                Files.createDirectories(dataDir);
                this.currentSavePath = dataDir.resolve("progress.dat");
            } else {
                Path gameDir = minecraft.gameDirectory.toPath();
                Path dataDir = gameDir.resolve("config").resolve("fieldguide_data");
                Files.createDirectories(dataDir);
                String safeName = serverIdentifier.replaceAll("[^a-zA-Z0-9.-]", "_");
                this.currentSavePath = dataDir.resolve(safeName + ".dat");
            }
        } catch (Exception e) {
            Constants.LOG.error("Failed to setup save directory for Field Guide", e);
        }
        loadProgress();
    }

    public void onWorldUnload() {
        if (this.currentSavePath != null) saveProgress();
        this.currentSavePath = null;
        unlockedEntries.clear();
        seenEntries.clear();
        discoveryTimes.clear();
        discoveryGameTimes.clear();
        customDescriptions.clear();
        customNames.clear();
        entryPhotographs.clear();
        journalPages.clear();
        journalTitle = "My Field Guide";
    }

    private void loadProgress() {
        if (currentSavePath == null || !currentSavePath.toFile().exists()) return;
        try (FileReader reader = new FileReader(currentSavePath.toFile())) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json.has("unlocked"))
                for (JsonElement e : json.getAsJsonArray("unlocked")) unlockedEntries.add(e.getAsString());
            if (json.has("seen")) for (JsonElement e : json.getAsJsonArray("seen")) seenEntries.add(e.getAsString());
            if (json.has("times"))
                json.getAsJsonObject("times").entrySet().forEach(e -> discoveryTimes.put(e.getKey(), e.getValue().getAsLong()));
            if (json.has("gameTimes"))
                json.getAsJsonObject("gameTimes").entrySet().forEach(e -> discoveryGameTimes.put(e.getKey(), e.getValue().getAsLong()));
            if (json.has("customDescriptions"))
                json.getAsJsonObject("customDescriptions").entrySet().forEach(e -> customDescriptions.put(e.getKey(), e.getValue().getAsString()));
            if (json.has("customNames"))
                json.getAsJsonObject("customNames").entrySet().forEach(e -> customNames.put(e.getKey(), e.getValue().getAsString()));
            if (json.has("entryPhotographs"))
                json.getAsJsonObject("entryPhotographs").entrySet().forEach(e -> entryPhotographs.put(e.getKey(), e.getValue().getAsString()));
            if (json.has("journalTitle")) journalTitle = json.get("journalTitle").getAsString();
            if (json.has("journalPages")) {
                journalPages.clear();
                for (JsonElement e : json.getAsJsonArray("journalPages")) {
                    JsonObject obj = e.getAsJsonObject();
                    journalPages.add(new JournalPage(
                            obj.has("title") ? obj.get("title").getAsString() : "",
                            obj.has("content") ? obj.get("content").getAsString() : "",
                            obj.has("timestamp") ? obj.get("timestamp").getAsLong() : System.currentTimeMillis()
                    ));
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
            JsonObject gameTimesObj = new JsonObject();
            discoveryGameTimes.forEach(gameTimesObj::addProperty);
            json.add("gameTimes", gameTimesObj);
            JsonObject descsObj = new JsonObject();
            customDescriptions.forEach(descsObj::addProperty);
            json.add("customDescriptions", descsObj);
            JsonObject namesObj = new JsonObject();
            customNames.forEach(namesObj::addProperty);
            json.add("customNames", namesObj);

            JsonObject photosObj = new JsonObject();
            entryPhotographs.forEach(photosObj::addProperty);
            json.add("entryPhotographs", photosObj);

            json.addProperty("journalTitle", journalTitle);
            JsonArray jpArr = new JsonArray();
            for (JournalPage jp : getJournalPages()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("title", jp.title);
                obj.addProperty("content", jp.content);
                obj.addProperty("timestamp", jp.timestamp);
                jpArr.add(obj);
            }
            json.add("journalPages", jpArr);

            File file = currentSavePath.toFile();
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(file)) {
                GSON.toJson(json, w);
            }
        } catch (Exception e) {
            Constants.LOG.error("Failed to save progress", e);
        }
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