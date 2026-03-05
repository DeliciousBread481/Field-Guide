package com.evandev.fieldguide.compat.exposure;

import io.github.mortuusars.exposure.forge.api.event.FrameAddedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ExposureForgeEventHandler {
    @SubscribeEvent
    public static void onExposureFrameAdded(FrameAddedEvent event) {
        ExposureCompat.onPhotographTaken(event.player, event.frame);
    }
}