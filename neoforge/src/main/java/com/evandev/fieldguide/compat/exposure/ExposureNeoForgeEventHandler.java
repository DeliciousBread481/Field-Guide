package com.evandev.fieldguide.compat.exposure;

import io.github.mortuusars.exposure.neoforge.api.event.FrameAddedEvent;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;

public class ExposureNeoForgeEventHandler {

    @SubscribeEvent
    public static void onFrameAdded(FrameAddedEvent event) {
        if (event.getCameraHolder().asHolderEntity() instanceof Player player) {
            ExposureCompat.onPhotographTaken(player, event.getFrame());
        }
    }
}