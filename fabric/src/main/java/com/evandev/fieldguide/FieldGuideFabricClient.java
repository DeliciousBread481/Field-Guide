package com.evandev.fieldguide;

import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.data.FieldGuideDataManager;
import com.evandev.fieldguide.network.SyncCategoriesPacket;
import com.evandev.fieldguide.network.SyncDropsPacket;
import com.evandev.fieldguide.platform.FabricNetworkHelper;
import com.evandev.fieldguide.server.command.FieldGuideCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
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
        FieldGuideClient.init();
        KeyBindingHelper.registerKeyBinding(FieldGuideClient.OPEN_GUIDE_KEY);

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

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            FieldGuideDataManager.getInstance().onClientTick(client);
            FieldGuideClient.onClientTick(client);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            FieldGuideCommand.register(dispatcher);
        });

        ClientPlayNetworking.registerGlobalReceiver(FabricNetworkHelper.SYNC_DROPS_CHANNEL, (client, handler, buf, responseSender) -> {
            SyncDropsPacket packet = new SyncDropsPacket(buf);
            client.execute(() -> FieldGuideDataManager.getInstance().setDrops(packet.getEntryId(), packet.getDrops()));
        });

        ClientPlayNetworking.registerGlobalReceiver(FabricNetworkHelper.SYNC_CATEGORIES_CHANNEL, (client, handler, buf, responseSender) -> {
            SyncCategoriesPacket packet = new SyncCategoriesPacket(buf);
            client.execute(() -> FieldGuideDataManager.getInstance().updateCategoriesFromServer(packet.getCategories()));
        });

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

        ClientPlayNetworking.registerGlobalReceiver(FabricNetworkHelper.SYNC_DROPS_CHANNEL, (client, handler, buf, responseSender) -> {
            SyncDropsPacket packet = new SyncDropsPacket(buf);
            client.execute(() -> {
                FieldGuideDataManager.getInstance().setDrops(packet.getEntryId(), packet.getDrops());
            });
        });
    }
}