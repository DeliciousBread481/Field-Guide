package com.evandev.fieldguide.config;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.platform.Services;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = Services.PLATFORM.getConfigDirectory().resolve("fieldguide.json").toFile();

    private static ModConfig INSTANCE;

    public boolean showPauseMenuButton = true;
    public int pauseButtonXOffset = 0;
    public int pauseButtonYOffset = 0;

    public boolean showInventoryButton = true;
    public int inventoryButtonXOffset = 125;
    public int inventoryButtonYOffset = 62;

    public boolean showUndiscoveredNames = false;
    public double scanSpeed = 1.0D;
    public List<String> entityBlacklist = new ArrayList<>();

    public static ModConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, ModConfig.class);
            } catch (Exception e) {
                Constants.LOG.error("Failed to load fieldguide.json", e);
                INSTANCE = new ModConfig();
            }
        } else {
            INSTANCE = new ModConfig();
            INSTANCE.entityBlacklist.addAll(getDefaultBlacklist());
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            Constants.LOG.error("Failed to save fieldguide.json", e);
        }
    }

    public static List<String> getDefaultBlacklist() {
        List<String> defaults = new ArrayList<>();
        defaults.add("minecraft:armor_stand");
        defaults.add("minecraft:grass");
        defaults.add("minecraft:tall_grass");
        return defaults;
    }

    public boolean isEntityBlacklisted(ResourceLocation location) {
        String id = location.toString();
        String namespace = location.getNamespace();

        if (entityBlacklist.contains(id)) return true;

        return entityBlacklist.contains(namespace + ":*");
    }

}