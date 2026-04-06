package com.evandev.fieldguide;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.client.gui.screens.FieldGuideEntryScreen;
import com.evandev.fieldguide.config.ClothConfigIntegration;
import com.evandev.fieldguide.config.ServerConfig;
import com.evandev.fieldguide.network.ProgressUpdatePacket;
import com.evandev.fieldguide.network.SyncCategoriesPacket;
import com.evandev.fieldguide.network.SyncConfigPacket;
import com.evandev.fieldguide.network.SyncLootPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

public class FieldGuideNeoForgeClient {

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(FieldGuideNeoForgeClient::onClientSetup);
        modEventBus.addListener(FieldGuideNeoForgeClient::registerKeyMappings);
        modEventBus.addListener(FieldGuideNeoForgeClient::registerReloadListeners);

        NeoForge.EVENT_BUS.addListener(FieldGuideNeoForgeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(FieldGuideNeoForgeClient::onRenderGuiOverlay);
        NeoForge.EVENT_BUS.addListener(FieldGuideNeoForgeClient::onClientPlayerLogin);
        NeoForge.EVENT_BUS.addListener(FieldGuideNeoForgeClient::onClientPlayerLogout);
    }

    public static void handleSyncLoot(SyncLootPacket packet) {
        ClientFieldGuideManager.getInstance().updateLootCache(packet.lootCache(), packet.clearCache());
        if (Minecraft.getInstance().screen instanceof FieldGuideEntryScreen screen) {
            screen.refresh();
        }
    }

    public static void handleSyncConfig(SyncConfigPacket packet) {
        ServerConfig synced = ServerConfig.fromJson(packet.configJson());
        ServerConfig.setSyncedConfig(synced);
    }

    public static void handleSyncCategories(SyncCategoriesPacket packet) {
        ClientFieldGuideManager.getInstance().updateCategoriesFromServer(
                packet.getCategories(),
                packet.getEntries(),
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

    public static void registerReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "client_data"), ClientFieldGuideManager.getInstance());
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        FieldGuideClient.init();
        event.register(FieldGuideClient.OPEN_GUIDE_KEY);
        event.register(FieldGuideClient.SCAN_KEY);
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("cloth_config")) {
            ModLoadingContext.get().registerExtensionPoint(
                    IConfigScreenFactory.class,
                    () -> (client, parent) -> ClothConfigIntegration.createScreen(parent)
            );
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        ClientFieldGuideManager.getInstance().onClientTick(client);
        FieldGuideClient.onClientTick();
    }

    public static void onRenderGuiOverlay(RenderGuiLayerEvent.Post event) {
        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
            FieldGuideClient.renderScanningIcon(event.getGuiGraphics(), partialTick);
        }
    }

    public static void onClientPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        ClientFieldGuideManager.getInstance().onWorldLoad();
    }

    public static void onClientPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientFieldGuideManager.getInstance().onWorldUnload();
    }
}