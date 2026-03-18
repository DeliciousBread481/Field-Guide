package com.evandev.fieldguide;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.client.ModRenderTypes;
import com.evandev.fieldguide.config.ClothConfigIntegration;
import com.evandev.fieldguide.network.ProgressUpdatePacket;
import com.evandev.fieldguide.network.SyncCategoriesPacket;
import com.evandev.fieldguide.network.SyncLootPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;

import java.io.IOException;

public class FieldGuideForgeClient {

    public static void handleSyncLoot(SyncLootPacket packet) {
        ClientFieldGuideManager.getInstance().updateLootCache(packet.getLootCache(), packet.isClearCache());
    }

    public static void handleSyncCategories(SyncCategoriesPacket packet) {
        ClientFieldGuideManager.getInstance().updateCategoriesFromServer(
                packet.getCategories(),
                packet.getRedirects(),
                packet.shouldClearCache(),
                packet.shouldResolveEntries()
        );

        ClientFieldGuideManager.getInstance().updateModifiers(
                packet.getBiomeAdditions(),
                packet.getBiomeRemovals(),
                packet.getLootAdditions(),
                packet.getLootRemovals(),
                packet.shouldClearCache()
        );

        ClientFieldGuideManager.getInstance().updateVariants(packet.getVariants());
    }

    public static void handleProgressUpdate(ProgressUpdatePacket packet) {
        ClientFieldGuideManager.getInstance().applyServerUpdate(packet);
    }

    @Mod.EventBusSubscriber(modid = Constants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
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
            event.register(FieldGuideClient.SCAN_KEY);
        }

        @SubscribeEvent
        public static void onConstructMod(FMLConstructModEvent event) {
            if (ModList.get().isLoaded("cloth_config")) {
                ModLoadingContext.get().registerExtensionPoint(
                        ConfigScreenHandler.ConfigScreenFactory.class,
                        () -> new ConfigScreenHandler.ConfigScreenFactory(
                                (client, parent) -> ClothConfigIntegration.createScreen(parent)
                        )
                );
            }
        }
    }

    @Mod.EventBusSubscriber(modid = Constants.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                Minecraft client = Minecraft.getInstance();
                ClientFieldGuideManager.getInstance().onClientTick(client);
                FieldGuideClient.onClientTick(client);
            }
        }

        @SubscribeEvent
        public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
            if (event.getOverlay().id().getPath().equals("crosshair")) {
                FieldGuideClient.renderScanningIcon(event.getGuiGraphics(), event.getPartialTick());
            }
        }

        @SubscribeEvent
        public static void onClientPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
            ClientFieldGuideManager.getInstance().onWorldLoad();
        }

        @SubscribeEvent
        public static void onClientPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientFieldGuideManager.getInstance().onWorldUnload();
        }
    }
}
