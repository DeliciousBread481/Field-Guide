package com.evandev.fieldguide.client;

import com.evandev.fieldguide.client.gui.screens.FieldGuideEntryScreen;
import com.evandev.fieldguide.client.gui.screens.FieldGuideScreen;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.FieldGuideDataManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.EntityType;
import org.lwjgl.glfw.GLFW;

public class FieldGuideClient {
    private static final long AUTO_OPEN_THRESHOLD_MS = 5000;
    public static KeyMapping OPEN_GUIDE_KEY;

    public static void init() {
        OPEN_GUIDE_KEY = new KeyMapping(
                "key.fieldguide.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "category.fieldguide.main"
        );
    }

    public static void onClientTick(Minecraft minecraft) {
        if (OPEN_GUIDE_KEY.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null) {
                FieldGuideDataManager manager = FieldGuideDataManager.getInstance();
                long lastTime = manager.getLastUnlockTime();
                EntityType<?> lastEntity = manager.getLastUnlockedEntity();

                boolean isRecent = (System.currentTimeMillis() - lastTime) < AUTO_OPEN_THRESHOLD_MS;

                if (isRecent && lastEntity != null) {
                    Category targetCategory = manager.getCategoryForEntity(lastEntity);
                    if (targetCategory != null) {
                        int page = FieldGuideScreen.getPageForEntry(targetCategory, lastEntity);
                        Screen mainScreen = new FieldGuideScreen(targetCategory, page);
                        minecraft.setScreen(new FieldGuideEntryScreen(mainScreen, lastEntity));
                    }
                } else {
                    minecraft.setScreen(new FieldGuideScreen());
                }
            }
        }
    }
}