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

    public boolean disableLootDisplay = false;
    public boolean disableBiomeDisplay = false;
    public boolean disableEditingDescriptions = false;
    public boolean disableEditingNames = false;

    public boolean showInventoryButton = true;
    public int inventoryButtonXOffset = 126;
    public int inventoryButtonYOffset = 61;

    public boolean requireSpyglass = true;
    public boolean showUndiscoveredNames = false;
    public boolean hideUndiscoveredFromSearch = false;
    public boolean autoRotateModels = false;
    public float rotationSpeed = 15.0F;
    public double scanSpeed = 1.0D;
    public double scanDistance = 64.0D;
    public int scanIconYOffset = 2;
    public int scanIconXOffset = 30;
    public boolean showScanIcon = true;
    public boolean playScanningSound = true;
    public boolean grantXpOnScan = true;
    public int xpAmountOnScan = 5;

    public String scanOverlayColor = "#F9EED0";
    public double scanOverlayAlpha = 0.5D;

    public String textColor = "#8A5E3B";
    public String textTitleColor = "#704623";
    public String textMutedColor = "#C7A875";
    public String textNewColor = "#63B40C";
    public String pageNumberColor = "#C7A875";
    public String listSilhouetteColor = "#DDC69B";
    public String detailsSilhouetteColor = "#DDC69B";
    public boolean useRealWorldDate = false;

    public List<String> entityBlacklist = new ArrayList<>();

    public List<String> lootRemovals = new ArrayList<>();
    public List<String> lootAdditions = getDefaultLootAdditions();

    public List<String> biomeRemovals = new ArrayList<>();
    public List<String> biomeAdditions = getDefaultBiomeAdditions();

    public List<String> discoveryRedirects = new ArrayList<>();

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
        defaults.add("minecraft:giant");
        defaults.add("minecraft:illusioner");
        defaults.add("minecraft:grass");
        defaults.add("minecraft:tall_grass");
        return defaults;
    }

    public static List<String> getDefaultLootAdditions() {
        List<String> defaults = new ArrayList<>();
        defaults.add("minecraft:wither|minecraft:nether_star");
        return defaults;
    }

    public static List<String> getDefaultBiomeAdditions() {
        List<String> defaults = new ArrayList<>();
        // Nether Fortress Mobs
        defaults.add("minecraft:blaze|minecraft:nether_wastes");
        defaults.add("minecraft:wither_skeleton|minecraft:nether_wastes");

        // Bastion Remnant Mobs
        defaults.add("minecraft:piglin_brute|minecraft:nether_wastes");

        // Ocean Monument Mobs
        defaults.add("minecraft:guardian|minecraft:deep_ocean");
        defaults.add("minecraft:elder_guardian|minecraft:deep_ocean");

        // Woodland Mansion Mobs
        defaults.add("minecraft:evoker|minecraft:dark_forest");
        defaults.add("minecraft:vindicator|minecraft:dark_forest");
        defaults.add("minecraft:vex|minecraft:dark_forest");

        // End City Mobs
        defaults.add("minecraft:shulker|minecraft:end_highlands");

        // Ancient City Mobs
        defaults.add("minecraft:warden|minecraft:deep_dark");

        return defaults;
    }

    public boolean isEntityBlacklisted(ResourceLocation location) {
        String id = location.toString();
        String namespace = location.getNamespace();

        if (entityBlacklist.contains(id)) return true;

        return entityBlacklist.contains(namespace + ":*");
    }

    public ResourceLocation getRedirect(ResourceLocation source) {
        String sourceStr = source.toString();
        for (String line : discoveryRedirects) {
            String[] parts = line.split("\\|");
            if (parts.length == 2 && parts[0].equals(sourceStr)) {
                try {
                    return new ResourceLocation(parts[1]);
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    public int getScanOverlayColorInt() {
        return parseColor(scanOverlayColor, 0xF9EED0);
    }

    public int getTextColorInt() {
        return parseColor(textColor, 0x8A5E3B);
    }

    public int getTextTitleColorInt() {
        return parseColor(textTitleColor, 0x704623);
    }

    public int getTextMutedColorInt() {
        return parseColor(textMutedColor, 0xC7A875);
    }

    public int getTextNewColorInt() { return parseColor(textNewColor, 0x63B40C); }

    public int getPageNumberColorInt() {
        return parseColor(pageNumberColor, 0xC7A875);
    }

    public int getListSilhouetteColorInt() {
        return parseColor(listSilhouetteColor, 0xDDC69B);
    }

    public int getDetailsSilhouetteColorInt() {
        return parseColor(detailsSilhouetteColor, 0xDDC69B);
    }

    private int parseColor(String colorStr, int fallback) {
        try {
            String hex = colorStr.startsWith("#") ? colorStr.substring(1) : colorStr;
            return Integer.parseInt(hex, 16);
        } catch (Exception e) {
            return fallback;
        }
    }
}