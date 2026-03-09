package com.evandev.fieldguide.server.progress;

import com.evandev.fieldguide.server.ServerFieldGuideManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FieldGuideProgressManager {
    private static final FieldGuideProgressManager NOOP = new FieldGuideProgressManager();
    private static FieldGuideProgressManager INSTANCE = NOOP;

    private MinecraftServer server;
    private Path progressDir;
    private final Map<UUID, PlayerFieldGuideProgress> playerProgress = new HashMap<>();

    private FieldGuideProgressManager() {
    }

    private FieldGuideProgressManager(MinecraftServer server) {
        this.server = server;
        this.progressDir = server.getWorldPath(LevelResource.ROOT).resolve("fieldguide_progress");
    }

    public static void init(MinecraftServer server) {
        INSTANCE = new FieldGuideProgressManager(server);
    }

    public static void shutdown() {
        if (INSTANCE != NOOP) {
            INSTANCE.saveAll();
            INSTANCE.playerProgress.clear();
            INSTANCE = NOOP;
        }
    }

    public static FieldGuideProgressManager getInstance() {
        return INSTANCE;
    }

    public void onPlayerJoin(ServerPlayer player) {
        if (this == NOOP) return;
        UUID uuid = player.getUUID();
        PlayerFieldGuideProgress progress = new PlayerFieldGuideProgress(uuid, progressDir);
        progress.load();
        progress.markForFullSync();
        playerProgress.put(uuid, progress);
    }

    public void onPlayerDisconnect(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerFieldGuideProgress progress = playerProgress.remove(uuid);
        if (progress != null) {
            progress.save();
        }
    }

    public void tick() {
        for (Map.Entry<UUID, PlayerFieldGuideProgress> entry : playerProgress.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                entry.getValue().flushDirty(player);
            }
        }
    }

    public void save(ServerPlayer player) {
        PlayerFieldGuideProgress progress = playerProgress.get(player.getUUID());
        if (progress != null) {
            progress.save();
        }
    }

    public void saveAll() {
        for (PlayerFieldGuideProgress progress : playerProgress.values()) {
            progress.save();
        }
    }

    public PlayerFieldGuideProgress getProgress(ServerPlayer player) {
        return playerProgress.get(player.getUUID());
    }

    public PlayerFieldGuideProgress getProgress(UUID uuid) {
        return playerProgress.get(uuid);
    }

    public boolean isValidEntry(ResourceLocation entryId) {
        return ServerFieldGuideManager.getInstance().hasEntry(entryId);
    }

    public boolean isKillToUnlock(ResourceLocation entryId) {
        return ServerFieldGuideManager.getInstance().isKillToUnlock(entryId);
    }
}
