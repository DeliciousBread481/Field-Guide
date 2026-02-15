package com.evandev.fieldguide.client;

import com.evandev.fieldguide.client.gui.screens.BookScreen;
import com.evandev.fieldguide.client.gui.screens.FieldGuideEntryScreen;
import com.evandev.fieldguide.client.gui.screens.FieldGuideScreen;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.mixin.accessor.MobAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.lwjgl.glfw.GLFW;

public class FieldGuideClient {
    private static final long AUTO_OPEN_THRESHOLD_MS = 5000;
    public static KeyMapping OPEN_GUIDE_KEY;
    private static long lastCryTime = 0;

    public static void init() {
        OPEN_GUIDE_KEY = new KeyMapping(
                "key.fieldguide.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "category.fieldguide.main"
        );
    }

    public static void playMobCry(Entity entity) {
        if (entity instanceof Mob mob) {
            long now = System.currentTimeMillis();
            if (now - lastCryTime < 1000) return;

            SoundEvent sound = ((MobAccessor) mob).fieldguide$callGetAmbientSound();
            if (sound != null) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, 1.5F));
                lastCryTime = now;
            }
        }
    }

    public static void onClientTick(Minecraft minecraft) {
        if (OPEN_GUIDE_KEY.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F));
                ClientFieldGuideManager manager = ClientFieldGuideManager.getInstance();
                long lastTime = manager.getLastUnlockTime();
                Object lastEntry = manager.getLastUnlockedEntry();

                boolean isRecent = (System.currentTimeMillis() - lastTime) < AUTO_OPEN_THRESHOLD_MS;
                if (isRecent && lastEntry != null) {
                    Category targetCategory = manager.getCategoryForEntry(lastEntry);
                    if (targetCategory != null) {
                        ClientFieldGuideManager.markAsSeen(lastEntry);

                        int page = FieldGuideScreen.getPageForEntry(targetCategory, lastEntry);
                        BookScreen mainScreen = new FieldGuideScreen(targetCategory, page);
                        minecraft.setScreen(new FieldGuideEntryScreen(mainScreen, lastEntry));
                    }
                } else {
                    minecraft.setScreen(new FieldGuideScreen());
                }
            }
        }
    }
}