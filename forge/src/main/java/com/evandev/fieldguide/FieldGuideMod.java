package com.evandev.fieldguide;

import com.evandev.fieldguide.client.MobDataManager;
import com.evandev.fieldguide.config.ClothConfigIntegration;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

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
}