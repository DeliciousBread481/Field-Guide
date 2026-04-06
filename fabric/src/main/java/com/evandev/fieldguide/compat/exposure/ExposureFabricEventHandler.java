/*
package com.evandev.fieldguide.compat.exposure;

import io.github.mortuusars.exposure.fabric.api.event.FrameAddedCallback;
import net.minecraft.world.entity.player.Player;

public class ExposureFabricEventHandler {
    public static void register() {
        FrameAddedCallback.EVENT.register((cameraHolder, camera, frame, positionsInFrame, entitiesInFrame) -> {
            if (cameraHolder.asHolderEntity() instanceof Player player) {
                ExposureCompat.onPhotographTaken(player, frame);
            }
        });
    }
}*/
