package com.evandev.fieldguide;

import com.evandev.fieldguide.client.MobDataManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;

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
                MobDataManager.getInstance().onResourceManagerReload(resourceManager);
            }
        });
    }
}