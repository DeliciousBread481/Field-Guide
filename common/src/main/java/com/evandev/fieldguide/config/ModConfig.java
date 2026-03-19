package com.evandev.fieldguide.config;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.platform.Services;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = Services.PLATFORM.getConfigDirectory().resolve("fieldguide.json").toFile();

    private static ModConfig INSTANCE;

    public boolean showPauseMenuButton = true;
    public int pauseButtonXOffset = 0;
    public int pauseButtonYOffset = 0;

    public boolean disableScanning = false;
    public boolean disableLootDisplay = false;
    public boolean disableBiomeDisplay = false;
    public boolean disableEditingDescriptions = false;
    public boolean disableEditingNames = false;
    public boolean keepSilhouetteWhenUnlocked = false;
    public boolean unlockAllVariants = false;
    public boolean showToasts = true;
    public boolean enableFieldGuideItem = false;
    public boolean enableTearingOutPages = true;

    public boolean showInventoryButton = true;
    public int inventoryButtonXOffset = 126;
    public int inventoryButtonYOffset = 61;

    public String defaultScreen = "last_opened_screen";
    public boolean hideTabsUntilUnlocked = false;

    public boolean enableSpyglassScanning = true;
    public double spyglassScanDistance = 64.0D;

    public boolean enableNakedEyeScanning = false;
    public double nakedEyeScanDistance = 10.0D;

    public boolean showUndiscoveredNames = false;
    public boolean hideUndiscoveredFromSearch = false;

    public double scanSpeed = 1.0D;
    public int scanIconYOffset = 2;
    public int scanIconXOffset = 30;
    public boolean showScanIcon = true;
    public boolean playScanningSound = true;
    public boolean grantXpOnScan = true;
    public int xpAmountOnScan = 5;

    public boolean enableReliableRemover = true;

    public String scanOverlayColor = "#F9EED0";
    public double scanOverlayAlpha = 0.5D;

    public String textColor = "#8A5E3B";
    public String textTitleColor = "#704623";
    public String textMutedColor = "#C7A875";
    public String textCursorColor = "#0xFF704623";
    public String pageNumberColor = "#C7A875";

    public String listSilhouetteColor = "#DDC69B";
    public double listSilhouetteAlpha = 1.0D;
    public String listUnlockedSilhouetteColor = "#DDC69B";
    public double listUnlockedSilhouetteAlpha = 1.0D;

    public String detailsSilhouetteColor = "#DDC69B";
    public double detailsSilhouetteAlpha = 1.0D;
    public String detailsUnlockedSilhouetteColor = "#DDC69B";
    public double detailsUnlockedSilhouetteAlpha = 1.0D;
    public boolean useRealWorldDate = false;

    public boolean exposureAddPhotographButton = true;
    public boolean exposureUnlockViaPhotograph = true;
    public boolean exposureShowPhotographsInGrid = true;

    public List<String> globalScanCommands = new ArrayList<>();
    public Map<String, List<String>> categoryScanCommands = new HashMap<>();
    public Map<String, List<String>> entryScanCommands = new HashMap<>();

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
                if (INSTANCE.categoryScanCommands == null) INSTANCE.categoryScanCommands = new HashMap<>();
                if (INSTANCE.entryScanCommands == null) INSTANCE.entryScanCommands = new HashMap<>();
                if (INSTANCE.globalScanCommands == null) INSTANCE.globalScanCommands = new ArrayList<>();
            } catch (Exception e) {
                Constants.LOG.error("Failed to load fieldguide.json", e);
                INSTANCE = new ModConfig();
            }
        } else {
            INSTANCE = new ModConfig();
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

    public int getTextCursorColorInt() {
        return parseColor(textCursorColor, 0xFF704623);
    }

    public int getPageNumberColorInt() {
        return parseColor(pageNumberColor, 0xC7A875);
    }

    public int getListSilhouetteColorInt() {
        return parseColor(listSilhouetteColor, 0xDDC69B);
    }

    public int getListUnlockedSilhouetteColorInt() {
        return parseColor(listUnlockedSilhouetteColor, 0xDDC69B);
    }

    public int getDetailsSilhouetteColorInt() {
        return parseColor(detailsSilhouetteColor, 0xDDC69B);
    }

    public int getDetailsUnlockedSilhouetteColorInt() {
        return parseColor(detailsUnlockedSilhouetteColor, 0xDDC69B);
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
