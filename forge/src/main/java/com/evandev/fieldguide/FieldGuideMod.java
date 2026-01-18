package com.evandev.fieldguide;

import com.evandev.fieldguide.client.MobDataManager;
import com.evandev.fieldguide.config.ClothConfigIntegration;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
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
        event.registerReloadListener(MobDataManager.getInstance());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MobDataManager.getInstance().onClientTick(Minecraft.getInstance());
        }
    }

    @SubscribeEvent
    public void onClientPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft client = Minecraft.getInstance();
        Path saveDir = null;
        if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
            saveDir = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
        }
        MobDataManager.getInstance().onWorldLoad(saveDir);
    }

    @SubscribeEvent
    public void onClientPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        MobDataManager.getInstance().onWorldUnload();
    }
}