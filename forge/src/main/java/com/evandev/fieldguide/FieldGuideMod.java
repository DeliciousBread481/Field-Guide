package com.evandev.fieldguide;

import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.config.ClothConfigIntegration;
import com.evandev.fieldguide.data.FieldGuideDataManager;
import com.evandev.fieldguide.network.RequestDropsPacket;
import com.evandev.fieldguide.network.SyncCategoriesPacket;
import com.evandev.fieldguide.network.SyncDropsPacket;
import com.evandev.fieldguide.platform.ForgeNetworkHelper;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.server.LootTableHelper;
import com.evandev.fieldguide.server.ServerFieldGuideManager;
import com.evandev.fieldguide.server.command.FieldGuideCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkEvent;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Mod(Constants.MOD_ID)
public class FieldGuideMod {

    public FieldGuideMod() {
        CommonClass.init();
        MinecraftForge.EVENT_BUS.register(this);

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
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

    public static void handleRequest(RequestDropsPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            ResourceLocation id = packet.getEntryId();
            Object entry = null;

            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
            if (type.isPresent()) entry = type.get();
            else {
                Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
                if (block.isPresent()) entry = block.get();
            }

            if (entry != null) {
                List<ItemStack> drops = LootTableHelper.getDrops(player.server.getResourceManager(), entry);
                Services.NETWORK.sendToPlayer(new SyncDropsPacket(id, drops), player);
            }
        });
        context.setPacketHandled(true);
    }

    public static void handleSyncDrops(SyncDropsPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            FieldGuideDataManager.getInstance().setDrops(packet.getEntryId(), packet.getDrops());
        });
        context.setPacketHandled(true);
    }

    public static void handleSyncCategories(SyncCategoriesPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            FieldGuideDataManager.getInstance().updateCategoriesFromServer(packet.getCategories());
        });
        context.setPacketHandled(true);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ForgeNetworkHelper.register();
    }

    public void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(FieldGuideDataManager.getInstance());
    }

    public void registerKeyMappings(RegisterKeyMappingsEvent event) {
        FieldGuideClient.init();
        event.register(FieldGuideClient.OPEN_GUIDE_KEY);
    }

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        // Register server data loader
        event.addListener(ServerFieldGuideManager.getInstance());
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
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerFieldGuideManager.getInstance().syncToPlayer(player);
        }
    }

    @SubscribeEvent
    public void onClientPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        FieldGuideDataManager.getInstance().onWorldUnload();
    }
}