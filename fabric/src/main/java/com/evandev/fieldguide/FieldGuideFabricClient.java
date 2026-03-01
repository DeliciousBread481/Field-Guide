package com.evandev.fieldguide;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.ModRenderTypes;
import com.evandev.fieldguide.network.ExportContentPacket;
import com.evandev.fieldguide.network.GrantContentPacket;
import com.evandev.fieldguide.network.SyncCategoriesPacket;
import com.evandev.fieldguide.network.SyncLootPacket;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

public class FieldGuideFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        com.evandev.fieldguide.client.FieldGuideClient.init();
        KeyBindingHelper.registerKeyBinding(com.evandev.fieldguide.client.FieldGuideClient.OPEN_GUIDE_KEY);

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "mob_data");
            }

            @Override
            public void onResourceManagerReload(@NotNull ResourceManager resourceManager) {
                ClientFieldGuideManager.getInstance().onResourceManagerReload(resourceManager);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientFieldGuideManager.getInstance().onClientTick(client);
            com.evandev.fieldguide.client.FieldGuideClient.onClientTick(client);
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncLootPacket.TYPE, (SyncLootPacket packet, ClientPlayNetworking.Context context) -> {
            context.client().execute(() -> ClientFieldGuideManager.getInstance().updateLootCache(packet.lootCache(), packet.clearCache()));
        });

        ClientPlayNetworking.registerGlobalReceiver(SyncCategoriesPacket.TYPE, (SyncCategoriesPacket packet, ClientPlayNetworking.Context context) -> {
            context.client().execute(() -> {
                ClientFieldGuideManager manager = ClientFieldGuideManager.getInstance();
                manager.updateCategoriesFromServer(packet.getCategories(), packet.getRedirects(), packet.isClearCache());
                manager.updateModifiers(
                        packet.getBiomeAdditions(), packet.getBiomeRemovals(),
                        packet.getLootAdditions(), packet.getLootRemovals(), packet.isClearCache()
                );
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(GrantContentPacket.TYPE, (GrantContentPacket packet, ClientPlayNetworking.Context context) -> {
            context.client().execute(packet::handleClient);
        });

        ClientPlayNetworking.registerGlobalReceiver(ExportContentPacket.TYPE, (ExportContentPacket packet, ClientPlayNetworking.Context context) -> {
            context.client().execute(packet::handleClient);
        });

        CoreShaderRegistrationCallback.EVENT.register(context -> {
            try {
                context.register(
                        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "fieldguide_scan_block"),
                        DefaultVertexFormat.BLOCK,
                        program -> ModRenderTypes.SCAN_BLOCK_SHADER = program
                );

                context.register(
                        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "fieldguide_scan_entity"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> ModRenderTypes.SCAN_ENTITY_SHADER = program
                );

            } catch (IOException e) {
                throw new RuntimeException("Failed to register fieldguide shaders", e);
            }
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            String serverId = "unknown_server";

            if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
                Path levelDatPath = client.getSingleplayerServer().getWorldPath(LevelResource.LEVEL_DATA_FILE);
                serverId = levelDatPath.getParent().getFileName().toString();
            } else if (client.getCurrentServer() != null) {
                serverId = client.getCurrentServer().ip;
            }

            ClientFieldGuideManager.getInstance().onWorldLoad(serverId);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ClientFieldGuideManager.getInstance().onWorldUnload()
        );
    }
}