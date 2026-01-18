package com.evandev.fieldguide;

import com.evandev.fieldguide.data.FieldGuideDataManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class FieldGuideFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return new ResourceLocation(Constants.MOD_ID, "mob_data");
            }

            @Override
            public void onResourceManagerReload(@NotNull ResourceManager resourceManager) {
                FieldGuideDataManager.getInstance().onResourceManagerReload(resourceManager);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(FieldGuideDataManager.getInstance()::onClientTick);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            Path saveDir = null;
            if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
                saveDir = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
            }
            FieldGuideDataManager.getInstance().onWorldLoad(saveDir);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            FieldGuideDataManager.getInstance().onWorldUnload();
        });
    }
}