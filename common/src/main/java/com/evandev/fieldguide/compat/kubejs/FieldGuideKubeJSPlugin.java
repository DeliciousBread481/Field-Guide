package com.evandev.fieldguide.compat.kubejs;

import com.evandev.fieldguide.server.ServerFieldGuideManager;
import com.evandev.fieldguide.server.progress.FieldGuideProgressManager;
import com.evandev.fieldguide.server.progress.PlayerFieldGuideProgress;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public class FieldGuideKubeJSPlugin extends KubeJSPlugin {
    @Override
    public void registerBindings(BindingsEvent event) {
        event.add("FieldGuide", new FieldGuideJSWrapper());
    }

    public static class FieldGuideJSWrapper {
        public ServerFieldGuideManager getManager() {
            return ServerFieldGuideManager.getInstance();
        }

        public FieldGuideProgressManager getProgressManager() {
            return FieldGuideProgressManager.getInstance();
        }

        public PlayerFieldGuideProgress getProgress(ServerPlayer player) {
            return FieldGuideProgressManager.getInstance().getProgress(player);
        }

        public boolean isUnlocked(ServerPlayer player, String entryId) {
            PlayerFieldGuideProgress progress = getProgress(player);
            return progress != null && progress.isUnlocked(entryId);
        }

        public void unlock(ServerPlayer player, String entryId) {
            PlayerFieldGuideProgress progress = getProgress(player);
            if (progress != null) {
                progress.unlock(player, new ResourceLocation(entryId), null, true);
            }
        }

        public void unlock(ServerPlayer player, String entryId, String variantId) {
            PlayerFieldGuideProgress progress = getProgress(player);
            if (progress != null) {
                progress.unlock(player, new ResourceLocation(entryId), variantId, true);
            }
        }

        public boolean revoke(ServerPlayer player, String entryId) {
            PlayerFieldGuideProgress progress = getProgress(player);
            return progress != null && progress.revoke(entryId);
        }

        public void revokeAll(ServerPlayer player) {
            PlayerFieldGuideProgress progress = getProgress(player);
            if (progress != null) {
                progress.revokeAll();
            }
        }
    }
}
