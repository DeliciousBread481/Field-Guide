package com.evandev.fieldguide;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.client.ModRenderTypes;
import com.evandev.fieldguide.network.GrantContentPacket;
import com.evandev.fieldguide.network.SyncCategoriesPacket;
import com.evandev.fieldguide.network.SyncLootPacket;
import com.evandev.fieldguide.platform.FabricNetworkHelper;
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
        FieldGuideClient.init();
        KeyBindingHelper.registerKeyBinding(FieldGuideClient.OPEN_GUIDE_KEY);

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return new ResourceLocation(Constants.MOD_ID, "mob_data");
            }

            @Override
            public void onResourceManagerReload(@NotNull ResourceManager resourceManager) {
                ClientFieldGuideManager.getInstance().onResourceManagerReload(resourceManager);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientFieldGuideManager.getInstance().onClientTick(client);
            FieldGuideClient.onClientTick(client);
        });

        ClientPlayNetworking.registerGlobalReceiver(FabricNetworkHelper.SYNC_LOOT_CHANNEL, (client, handler, buf, responseSender) -> {
            SyncLootPacket packet = new SyncLootPacket(buf);
            client.execute(() -> ClientFieldGuideManager.getInstance().updateLootCache(packet.getLootCache()));
        });

        ClientPlayNetworking.registerGlobalReceiver(FabricNetworkHelper.SYNC_CATEGORIES_CHANNEL, (client, handler, buf, responseSender) -> {
            SyncCategoriesPacket packet = new SyncCategoriesPacket(buf);
            client.execute(() -> ClientFieldGuideManager.getInstance().updateCategoriesFromServer(packet.getCategories()));
        });

        ClientPlayNetworking.registerGlobalReceiver(FabricNetworkHelper.GRANT_CONTENT_CHANNEL, (client, handler, buf, responseSender) -> {
            GrantContentPacket packet = new GrantContentPacket(buf);
            client.execute(packet::handleClient);
        });

        CoreShaderRegistrationCallback.EVENT.register(context -> {
            try {
                context.register(
                        new ResourceLocation(Constants.MOD_ID, "fieldguide_scan"),
                        DefaultVertexFormat.POSITION_COLOR_TEX,
                        program -> ModRenderTypes.SCAN_SHADER_INSTANCE = program
                );

            } catch (IOException e) {
                throw new RuntimeException("Failed to register fieldguide shader", e);
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