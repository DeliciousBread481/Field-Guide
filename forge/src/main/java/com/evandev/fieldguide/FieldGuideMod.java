package com.evandev.fieldguide;

import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.config.ClothConfigIntegration;
import com.evandev.fieldguide.data.FieldGuideDataManager;
import com.evandev.fieldguide.server.command.FieldGuideCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.nio.file.Path;

@Mod(Constants.MOD_ID)
public class FieldGuideMod {

    public FieldGuideMod() {
        CommonClass.init();
        MinecraftForge.EVENT_BUS.register(this);

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::registerReloadListeners);
        modEventBus.addListener(this::registerKeyMappings);

        if (ModList.get().isLoaded("cloth_config")) {
            FMLJavaModLoadingContext.get().getModEventBus().register(new Object() {
                @SubscribeEvent
                public void onConstructMod(net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent event) {
                    net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(
                            ConfigScreenHandler.ConfigScreenFactory.class,
                            () -> new ConfigScreenHandler.ConfigScreenFactory(
                                    (client, parent) -> ClothConfigIntegration.createScreen(parent)
                            )
                    );
                }
            });
        }
    }

    public void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(FieldGuideDataManager.getInstance());
    }

    public void registerKeyMappings(RegisterKeyMappingsEvent event) {
        FieldGuideClient.init();
        event.register(FieldGuideClient.OPEN_GUIDE_KEY);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        FieldGuideCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Minecraft client = Minecraft.getInstance();
            FieldGuideDataManager.getInstance().onClientTick(client);
            FieldGuideClient.onClientTick(client);
        }
    }

    @SubscribeEvent
    public void onClientPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft client = Minecraft.getInstance();
        Path saveDir = null;
        if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
            saveDir = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
        }
        FieldGuideDataManager.getInstance().onWorldLoad(saveDir);
    }

    @SubscribeEvent
    public void onClientPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        FieldGuideDataManager.getInstance().onWorldUnload();
    }
}