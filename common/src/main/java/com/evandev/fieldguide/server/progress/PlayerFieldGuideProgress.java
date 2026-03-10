package com.evandev.fieldguide.server.progress;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.network.ProgressUpdatePacket;
import com.evandev.fieldguide.platform.Services;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.io.BufferedReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;

public class PlayerFieldGuideProgress {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final UUID playerUUID;
    private final Path savePath;

    private final Set<String> unlockedEntries = new HashSet<>();
    private final Set<String> seenEntries = new HashSet<>();
    private final Map<String, Long> discoveryTimes = new HashMap<>();
    private final Map<String, Long> discoveryGameTimes = new HashMap<>();

    private final Map<String, String> customNames = new HashMap<>();
    private final Map<String, String> customDescriptions = new HashMap<>();
    private final Map<String, String> entryPhotographs = new HashMap<>();
    private final List<JournalPageData> journalPages = new ArrayList<>();

    private final Set<String> pendingUnlocks = new LinkedHashSet<>();
    private final Set<String> pendingRevokes = new LinkedHashSet<>();
    private final Set<String> pendingSeen = new LinkedHashSet<>();
    private final Set<String> pendingEntryResync = new LinkedHashSet<>();
    private boolean pendingFullSync = false;

    private String journalTitle = "My Field Guide";
    private boolean dirty = false;

    public PlayerFieldGuideProgress(UUID playerUUID, Path progressDir) {
        this.playerUUID = playerUUID;
        this.savePath = progressDir.resolve(playerUUID.toString() + ".json");
    }

    private static <T> List<T> chunkAt(List<T> list, int index, int length) {
        return list.subList(Math.min(index, list.size()), Math.min(index + length, list.size()));
    }

    public boolean unlock(String entryId) {
        if (unlockedEntries.add(entryId)) {
            discoveryTimes.put(entryId, System.currentTimeMillis());
            pendingUnlocks.add(entryId);
            pendingRevokes.remove(entryId);
            dirty = true;
            return true;
        }
        return false;
    }

    public boolean unlock(ResourceLocation entryId) {
        return unlock(entryId.toString());
    }

    public boolean unlock(String entryId, long gameTime) {
        if (unlockedEntries.add(entryId)) {
            discoveryTimes.put(entryId, System.currentTimeMillis());
            discoveryGameTimes.put(entryId, gameTime);
            pendingUnlocks.add(entryId);
            pendingRevokes.remove(entryId);
            dirty = true;
            return true;
        }
        return false;
    }

    public boolean revoke(String entryId) {
        if (unlockedEntries.remove(entryId)) {
            seenEntries.remove(entryId);
            discoveryTimes.remove(entryId);
            discoveryGameTimes.remove(entryId);
            entryPhotographs.remove(entryId);
            customNames.remove(entryId);
            customDescriptions.remove(entryId);
            pendingRevokes.add(entryId);
            pendingUnlocks.remove(entryId);
            dirty = true;
            return true;
        }
        return false;
    }

    public boolean revoke(ResourceLocation entryId) {
        return revoke(entryId.toString());
    }

    public void revokeAll() {
        if (!unlockedEntries.isEmpty()) {
            unlockedEntries.clear();
            seenEntries.clear();
            discoveryTimes.clear();
            discoveryGameTimes.clear();
            entryPhotographs.clear();
            customNames.clear();
            customDescriptions.clear();
            pendingUnlocks.clear();
            pendingRevokes.clear();
            pendingSeen.clear();
            pendingFullSync = true;
            dirty = true;
        }
    }

    public boolean markSeen(String entryId) {
        if (unlockedEntries.contains(entryId) && seenEntries.add(entryId)) {
            pendingSeen.add(entryId);
            dirty = true;
            return true;
        }
        return false;
    }

    public boolean isUnlocked(String entryId) {
        return unlockedEntries.contains(entryId);
    }

    public boolean isUnlocked(ResourceLocation entryId) {
        return isUnlocked(entryId.toString());
    }

    public Set<String> getUnlockedEntries() {
        return Collections.unmodifiableSet(unlockedEntries);
    }

    public void setCustomName(String entryId, String name) {
        if (name == null || name.isEmpty()) {
            customNames.remove(entryId);
        } else {
            customNames.put(entryId, name);
        }
        dirty = true;
    }

    public void setCustomDescription(String entryId, String desc) {
        if (desc == null || desc.isEmpty()) {
            customDescriptions.remove(entryId);
        } else {
            customDescriptions.put(entryId, desc);
        }
        dirty = true;
    }

    public void setPhotograph(String entryId, String nbtString) {
        if (nbtString == null || nbtString.isEmpty()) {
            entryPhotographs.remove(entryId);
        } else {
            entryPhotographs.put(entryId, nbtString);
        }
        dirty = true;
    }

    public void setJournalTitle(String title) {
        this.journalTitle = title != null ? title : "My Field Guide";
        dirty = true;
    }

    public void setJournalPages(List<JournalPageData> pages) {
        this.journalPages.clear();
        this.journalPages.addAll(pages);
        dirty = true;
    }

    public void markForFullSync() {
        this.pendingFullSync = true;
    }

    public void markEntryForResync(String entryId) {
        pendingEntryResync.add(entryId);
    }

    public void flushDirty(ServerPlayer player) {
        if (pendingFullSync) {
            sendFullSync(player);
            pendingFullSync = false;
            pendingUnlocks.clear();
            pendingRevokes.clear();
            pendingSeen.clear();
            pendingEntryResync.clear();
            return;
        }

        if (!pendingUnlocks.isEmpty() || !pendingRevokes.isEmpty() || !pendingSeen.isEmpty() || !pendingEntryResync.isEmpty()) {
            sendDelta(player);
            pendingUnlocks.clear();
            pendingRevokes.clear();
            pendingSeen.clear();
            pendingEntryResync.clear();
        }
    }

    private void sendFullSync(ServerPlayer player) {
        int chunkSize = 256;
        List<String> allUnlocked = new ArrayList<>(unlockedEntries);
        int iterations = Math.max(1, allUnlocked.size());

        boolean first = true;
        for (int i = 0; i < iterations; i += chunkSize) {
            List<String> unlockedChunk = chunkAt(allUnlocked, i, chunkSize);

            List<String> seenChunk = new ArrayList<>();
            Map<String, Long> times = new HashMap<>();
            Map<String, Long> gameTimes = new HashMap<>();
            Map<String, String> names = new HashMap<>();
            Map<String, String> descs = new HashMap<>();
            Map<String, String> photos = new HashMap<>();
            for (String id : unlockedChunk) {
                if (seenEntries.contains(id)) seenChunk.add(id);
                if (discoveryTimes.containsKey(id)) times.put(id, discoveryTimes.get(id));
                if (discoveryGameTimes.containsKey(id)) gameTimes.put(id, discoveryGameTimes.get(id));
                if (customNames.containsKey(id)) names.put(id, customNames.get(id));
                if (customDescriptions.containsKey(id)) descs.put(id, customDescriptions.get(id));
                if (entryPhotographs.containsKey(id)) photos.put(id, entryPhotographs.get(id));
            }

            Services.NETWORK.sendToPlayer(
                    new ProgressUpdatePacket.Builder()
                            .reset(first)
                            .silent(true)
                            .unlocked(unlockedChunk)
                            .seen(seenChunk)
                            .discoveryTimes(times)
                            .discoveryGameTimes(gameTimes)
                            .customNames(names)
                            .customDescriptions(descs)
                            .build(),
                    player
            );
            first = false;

            if (!photos.isEmpty()) {
                Services.NETWORK.sendToPlayer(
                        new ProgressUpdatePacket.Builder()
                                .silent(true)
                                .entryPhotographs(photos)
                                .build(),
                        player
                );
            }
        }

        Services.NETWORK.sendToPlayer(
                new ProgressUpdatePacket.Builder()
                        .silent(true)
                        .journalTitle(journalTitle)
                        .journalPages(new ArrayList<>(journalPages))
                        .build(),
                player
        );
    }

    private void sendDelta(ServerPlayer player) {
        int chunkSize = 1024;
        List<String> allUnlocks = new ArrayList<>(pendingUnlocks);
        List<String> revoked = new ArrayList<>(pendingRevokes);
        List<String> seen = new ArrayList<>(pendingSeen);
        int iterations = Math.max(1, Math.max(allUnlocks.size(), Math.max(revoked.size(), seen.size())));

        Map<String, String> entryNames = new HashMap<>();
        Map<String, String> entryDescs = new HashMap<>();
        Map<String, String> entryPhotos = new HashMap<>();
        for (String id : pendingEntryResync) {
            entryNames.put(id, customNames.getOrDefault(id, ""));
            entryDescs.put(id, customDescriptions.getOrDefault(id, ""));
            entryPhotos.put(id, entryPhotographs.getOrDefault(id, ""));
        }

        boolean first = true;
        for (int i = 0; i < iterations; i += chunkSize) {
            List<String> unlockChunk = chunkAt(allUnlocks, i, chunkSize);
            List<String> revokedChunk = chunkAt(revoked, i, chunkSize);
            List<String> seenChunk = chunkAt(seen, i, chunkSize);

            Map<String, Long> unlockTimes = new HashMap<>();
            Map<String, Long> unlockGameTimes = new HashMap<>();
            for (String id : unlockChunk) {
                if (discoveryTimes.containsKey(id)) unlockTimes.put(id, discoveryTimes.get(id));
                if (discoveryGameTimes.containsKey(id)) unlockGameTimes.put(id, discoveryGameTimes.get(id));
            }

            Services.NETWORK.sendToPlayer(
                    new ProgressUpdatePacket.Builder()
                            .unlocked(unlockChunk)
                            .revoked(revokedChunk)
                            .seen(seenChunk)
                            .discoveryTimes(unlockTimes)
                            .discoveryGameTimes(unlockGameTimes)
                            .customNames(first ? entryNames : Collections.emptyMap())
                            .customDescriptions(first ? entryDescs : Collections.emptyMap())
                            .entryPhotographs(first ? entryPhotos : Collections.emptyMap())
                            .build(),
                    player
            );
            first = false;
        }
    }

    public void load() {
        try (BufferedReader reader = Files.newBufferedReader(savePath, StandardCharsets.UTF_8)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) return;

            if (json.has("unlocked")) {
                for (JsonElement e : json.getAsJsonArray("unlocked")) {
                    unlockedEntries.add(e.getAsString());
                }
            }
            if (json.has("seen")) {
                for (JsonElement e : json.getAsJsonArray("seen")) {
                    seenEntries.add(e.getAsString());
                }
            }
            if (json.has("times")) {
                json.getAsJsonObject("times").entrySet().forEach(
                        e -> discoveryTimes.put(e.getKey(), e.getValue().getAsLong())
                );
            }
            if (json.has("gameTimes")) {
                json.getAsJsonObject("gameTimes").entrySet().forEach(
                        e -> discoveryGameTimes.put(e.getKey(), e.getValue().getAsLong())
                );
            }
            if (json.has("customNames")) {
                json.getAsJsonObject("customNames").entrySet().forEach(
                        e -> customNames.put(e.getKey(), e.getValue().getAsString())
                );
            }
            if (json.has("customDescriptions")) {
                json.getAsJsonObject("customDescriptions").entrySet().forEach(
                        e -> customDescriptions.put(e.getKey(), e.getValue().getAsString())
                );
            }
            if (json.has("entryPhotographs")) {
                json.getAsJsonObject("entryPhotographs").entrySet().forEach(
                        e -> entryPhotographs.put(e.getKey(), e.getValue().getAsString())
                );
            }
            if (json.has("journalTitle")) {
                journalTitle = json.get("journalTitle").getAsString();
            }
            if (json.has("journalPages")) {
                journalPages.clear();
                for (JsonElement e : json.getAsJsonArray("journalPages")) {
                    JsonObject obj = e.getAsJsonObject();
                    journalPages.add(new JournalPageData(
                            obj.has("title") ? obj.get("title").getAsString() : "",
                            obj.has("content") ? obj.get("content").getAsString() : "",
                            obj.has("timestamp") ? obj.get("timestamp").getAsLong() : System.currentTimeMillis()
                    ));
                }
            }
        } catch (NoSuchFileException ignored) {
        } catch (Exception e) {
            Constants.LOG.error("Failed to load field guide progress for {}", playerUUID, e);
        }
    }

    public void save() {
        if (!dirty) return;

        try {
            Files.createDirectories(savePath.getParent());

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

            JsonObject namesObj = new JsonObject();
            customNames.forEach(namesObj::addProperty);
            json.add("customNames", namesObj);

            JsonObject descsObj = new JsonObject();
            customDescriptions.forEach(descsObj::addProperty);
            json.add("customDescriptions", descsObj);

            JsonObject photosObj = new JsonObject();
            entryPhotographs.forEach(photosObj::addProperty);
            json.add("entryPhotographs", photosObj);

            json.addProperty("journalTitle", journalTitle);

            JsonArray jpArr = new JsonArray();
            for (JournalPageData jp : journalPages) {
                JsonObject obj = new JsonObject();
                obj.addProperty("title", jp.title());
                obj.addProperty("content", jp.content());
                obj.addProperty("timestamp", jp.timestamp());
                jpArr.add(obj);
            }
            json.add("journalPages", jpArr);

            try (Writer w = Files.newBufferedWriter(savePath, StandardCharsets.UTF_8)) {
                GSON.toJson(json, w);
            }

            dirty = false;
        } catch (Exception e) {
            Constants.LOG.error("Failed to save field guide progress for {}", playerUUID, e);
        }
    }

    public record JournalPageData(String title, String content, long timestamp) {
    }
}
