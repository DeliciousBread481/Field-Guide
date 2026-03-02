package com.evandev.fieldguide;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.client.ModRenderTypes;
import com.evandev.fieldguide.config.ClothConfigIntegration;
import com.evandev.fieldguide.network.GrantContentPacket;
import com.evandev.fieldguide.network.SyncCategoriesPacket;
import com.evandev.fieldguide.network.SyncLootPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.io.IOException;
import java.nio.file.Path;

public class FieldGuideNeoForgeClient {

    public static void handleSyncLoot(SyncLootPacket packet) {
        ClientFieldGuideManager.getInstance().updateLootCache(packet.lootCache(), packet.clearCache());
    }

    public static void handleSyncCategories(SyncCategoriesPacket packet) {
        ClientFieldGuideManager.getInstance().updateCategoriesFromServer(
                packet.getCategories(),
                packet.getRedirects(),
                packet.isClearCache(),
                packet.isLast()
        );

        ClientFieldGuideManager.getInstance().updateModifiers(
                packet.getBiomeAdditions(),
                packet.getBiomeRemovals(),
                packet.getLootAdditions(),
                packet.getLootRemovals(),
                packet.isClearCache()
        );
    }

    public static void handleGrantContent(GrantContentPacket packet) {
        packet.handleClient();
    }

    @EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void registerShaders(RegisterShadersEvent event) {
            try {
                ModRenderTypes.registerShaders(instance -> {
                    String shaderName = instance.getName();
                    event.registerShader(instance, loadedShader -> {
                        if (shaderName.contains("fieldguide_scan_block")) {
                            ModRenderTypes.SCAN_BLOCK_SHADER = loadedShader;
                        } else if (shaderName.contains("fieldguide_scan_entity")) {
                            ModRenderTypes.SCAN_ENTITY_SHADER = loadedShader;
                        }
                    });
                }, event.getResourceProvider());
            } catch (IOException e) {
                throw new RuntimeException("Failed to register Field Guide shaders", e);
            }
        }

        @SubscribeEvent
        public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(ClientFieldGuideManager.getInstance());
        }

        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            FieldGuideClient.init();
            event.register(FieldGuideClient.OPEN_GUIDE_KEY);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            if (ModList.get().isLoaded("cloth_config")) {
                ModLoadingContext.get().registerExtensionPoint(
                        IConfigScreenFactory.class,
                        () -> (client, parent) -> ClothConfigIntegration.createScreen(parent)
                );
            }
        }
    }

    @EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static class ClientNeoForgeEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft client = Minecraft.getInstance();
            ClientFieldGuideManager.getInstance().onClientTick(client);
            FieldGuideClient.onClientTick(client);
        }

        @SubscribeEvent
        public static void onRenderGuiOverlay(RenderGuiLayerEvent.Post event) {
            if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
                float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                FieldGuideClient.renderScanningIcon(event.getGuiGraphics(), partialTick);
            }
        }

        @SubscribeEvent
        public static void onClientPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
            Minecraft client = Minecraft.getInstance();
            String serverId = "unknown_server";

            if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
                Path levelDatPath = client.getSingleplayerServer().getWorldPath(LevelResource.LEVEL_DATA_FILE);
                serverId = levelDatPath.getParent().getFileName().toString();
            } else if (client.getCurrentServer() != null) {
                serverId = client.getCurrentServer().ip;
            }

            ClientFieldGuideManager.getInstance().onWorldLoad(serverId);
        }

        @SubscribeEvent
        public static void onClientPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientFieldGuideManager.getInstance().onWorldUnload();
        }
    }
}