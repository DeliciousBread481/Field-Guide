package com.evandev.fieldguide.config;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.platform.Services;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = Services.PLATFORM.getConfigDirectory().resolve("fieldguide-client.json").toFile();

    private static ClientConfig INSTANCE;

    public boolean showPauseMenuButton = true;
    public int pauseButtonXOffset = 0;
    public int pauseButtonYOffset = 0;

    public boolean showInventoryButton = true;
    public int inventoryButtonXOffset = 126;
    public int inventoryButtonYOffset = 61;

    public String defaultScreen = "last_opened_screen";

    public int scanIconYOffset = 2;
    public int scanIconXOffset = 30;
    public boolean showScanIcon = true;
    public boolean playScanningSound = true;
    public boolean playUnlockSound = true;

    public boolean showOutOfRangeOverlay = true;
    public String scanOverlayColor = "#0xFFF9EED0";
    public double scanOverlayAlpha = 0.5D;

    public String textColor = "#0xFF8A5E3B";
    public String textTitleColor = "#0xFF704623";
    public String textMutedColor = "#0xFFC7A875";
    public String textCursorColor = "#0xFF704623";
    public String pageNumberColor = "#0xFFC7A875";

    public String listSilhouetteColor = "#0xFFDDC69B";
    public double listSilhouetteAlpha = 1.0D;
    public String listUnlockedSilhouetteColor = "#0xFFDDC69B";
    public double listUnlockedSilhouetteAlpha = 1.0D;

    public String detailsSilhouetteColor = "#0xFFDDC69B";
    public double detailsSilhouetteAlpha = 1.0D;
    public String detailsUnlockedSilhouetteColor = "#0xFFDDC69B";
    public double detailsUnlockedSilhouetteAlpha = 1.0D;

    public boolean useRealWorldDate = false;
    public boolean showUnlockDate = true;
    public boolean showToasts = true;
    public boolean showSeasonIcons = true;

    public boolean exposureAddPhotographButton = true;
    public boolean exposureShowPhotographsInGrid = true;

    public static ClientConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, ClientConfig.class);
            } catch (Exception e) {
                Constants.LOG.error("Failed to load fieldguide-client.json", e);
                INSTANCE = new ClientConfig();
            }
        } else {
            INSTANCE = new ClientConfig();
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            Constants.LOG.error("Failed to save fieldguide-client.json", e);
        }
    }

    public int getScanOverlayColorInt() {
        return parseColor(scanOverlayColor, 0xFFF9EED0);
    }

    public int getTextColorInt() {
        return parseColor(textColor, 0xFF8A5E3B);
    }

    public int getTextTitleColorInt() {
        return parseColor(textTitleColor, 0xFF704623);
    }

    public int getTextMutedColorInt() {
        return parseColor(textMutedColor, 0xFFC7A875);
    }

    public int getTextCursorColorInt() {
        return parseColor(textCursorColor, 0xFF704623);
    }

    public int getPageNumberColorInt() {
        return parseColor(pageNumberColor, 0xFFC7A875);
    }

    public int getListSilhouetteColorInt() {
        return parseColor(listSilhouetteColor, 0xFFDDC69B);
    }

    public int getListUnlockedSilhouetteColorInt() {
        return parseColor(listUnlockedSilhouetteColor, 0xFFDDC69B);
    }

    public int getDetailsSilhouetteColorInt() {
        return parseColor(detailsSilhouetteColor, 0xFFDDC69B);
    }

    public int getDetailsUnlockedSilhouetteColorInt() {
        return parseColor(detailsUnlockedSilhouetteColor, 0xFFDDC69B);
    }

    private int parseColor(String colorStr, int fallback) {
        try {
            String hex = colorStr.replace("#", "").replace("0x", "");
            long color = Long.parseLong(hex, 16);
            if (hex.length() <= 6) {
                color |= 0xFF000000L;
            }

            return (int) color;
        } catch (Exception e) {
            return fallback;
        }
    }
}
