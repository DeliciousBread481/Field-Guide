package com.evandev.fieldguide.compat.exposure;

import io.github.mortuusars.exposure.fabric.api.event.FrameAddedCallback;

public class ExposureFabricEventHandler {
    public static void register() {
        FrameAddedCallback.EVENT.register((player, cameraStack, frame) -> {
            ExposureCompat.onPhotographTaken(player, frame);
        });
    }
}