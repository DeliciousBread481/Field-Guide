package com.evandev.fieldguide;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.client.ModRenderTypes;
import com.evandev.fieldguide.config.ServerConfig;
import com.evandev.fieldguide.network.ExportContentPacket;
import com.evandev.fieldguide.network.ProgressUpdatePacket;
import com.evandev.fieldguide.network.SyncCategoriesPacket;
import com.evandev.fieldguide.network.SyncConfigPacket;
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
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class FieldGuideFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FieldGuideClient.init();
        KeyBindingHelper.registerKeyBinding(FieldGuideClient.OPEN_GUIDE_KEY);
        KeyBindingHelper.registerKeyBinding(FieldGuideClient.SCAN_KEY);

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
            FieldGuideClient.onClientTick();
        });

        ClientPlayNetworking.registerGlobalReceiver(FabricNetworkHelper.SYNC_LOOT_CHANNEL, (client, handler, buf, responseSender) -> {
            SyncLootPacket packet = new SyncLootPacket(buf);
            client.execute(() -> ClientFieldGuideManager.getInstance().updateLootCache(packet.getLootCache(), packet.isClearCache()));
        });

        ClientPlayNetworking.registerGlobalReceiver(FabricNetworkHelper.SYNC_CATEGORIES_CHANNEL, (client, handler, buf, responseSender) -> {
            SyncCategoriesPacket packet = new SyncCategoriesPacket(buf);

            client.execute(() -> {
                ClientFieldGuideManager manager = ClientFieldGuideManager.getInstance();

                manager.updateCategoriesFromServer(
                        packet.getCategories(),
                        packet.getRedirects(),
                        packet.shouldClearCache(),
                        packet.shouldResolveEntries()
                );

                manager.updateModifiers(
                        packet.getBiomeAdditions(),
                        packet.getBiomeRemovals(),
                        packet.getLootAdditions(),
                        packet.getLootRemovals(),
                        packet.shouldClearCache()
                );

                manager.updateVariants(packet.getVariants());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(FabricNetworkHelper.PROGRESS_UPDATE_CHANNEL, (client, handler, buf, responseSender) -> {
            ProgressUpdatePacket packet = new ProgressUpdatePacket(buf);
            client.execute(() -> ClientFieldGuideManager.getInstance().applyServerUpdate(packet));
        });

        ClientPlayNetworking.registerGlobalReceiver(FabricNetworkHelper.SYNC_CONFIG_CHANNEL, (client, handler, buf, responseSender) -> {
            SyncConfigPacket packet = new SyncConfigPacket(buf);
            client.execute(() -> {
                ServerConfig synced = ServerConfig.fromJson(packet.configJson());
                ServerConfig.setSyncedConfig(synced);
            });
        });


        CoreShaderRegistrationCallback.EVENT.register(context -> {
            try {
                context.register(
                        new ResourceLocation(Constants.MOD_ID, "fieldguide_scan_block"),
                        DefaultVertexFormat.BLOCK,
                        program -> ModRenderTypes.SCAN_BLOCK_SHADER = program
                );

                context.register(
                        new ResourceLocation(Constants.MOD_ID, "fieldguide_scan_entity"),
                        DefaultVertexFormat.NEW_ENTITY,
                        program -> ModRenderTypes.SCAN_ENTITY_SHADER = program
                );

            } catch (IOException e) {
                throw new RuntimeException("Failed to register fieldguide shaders", e);
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(FabricNetworkHelper.EXPORT_CONTENT_CHANNEL, (client, handler, buf, responseSender) -> {
            ExportContentPacket packet = new ExportContentPacket(buf);
            client.execute(packet::handleClient);
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientFieldGuideManager.getInstance().onWorldLoad();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ClientFieldGuideManager.getInstance().onWorldUnload()
        );
    }
}
