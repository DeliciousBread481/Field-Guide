package com.evandev.fieldguide;

import com.evandev.fieldguide.network.*;
import com.evandev.fieldguide.platform.ForgeNetworkHelper;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.server.ServerFieldGuideManager;
import com.evandev.fieldguide.server.command.FieldGuideCommand;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

@Mod(Constants.MOD_ID)
public class FieldGuideMod {

    public FieldGuideMod() {
        CommonClass.init();
        MinecraftForge.EVENT_BUS.register(this);

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
    }

    public static void handleGrantContent(GrantContentPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FieldGuideForgeClient.handleGrantContent(packet)));
        context.setPacketHandled(true);
    }

    public static void handleSyncLoot(SyncLootPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FieldGuideForgeClient.handleSyncLoot(packet)));
        context.setPacketHandled(true);
    }

    public static void handleSyncCategories(SyncCategoriesPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FieldGuideForgeClient.handleSyncCategories(packet)));
        context.setPacketHandled(true);
    }

    public static void handleExportContent(ExportContentPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> packet::handleClient));
        context.setPacketHandled(true);
    }

    public static void handleClaimXp(ClaimXpPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                packet.handleServer(player);
            }
        });
        context.setPacketHandled(true);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ForgeNetworkHelper.register();
    }

    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(ServerFieldGuideManager.getInstance());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        FieldGuideCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ServerFieldGuideManager.getInstance().onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerFieldGuideManager.getInstance().syncToPlayer(player);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            TagKey<EntityType<?>> killToUnlockTag = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Constants.MOD_ID, "kill_to_unlock"));

            var key = BuiltInRegistries.ENTITY_TYPE.getResourceKey(event.getEntity().getType());
            if (key.isPresent()) {
                var holder = BuiltInRegistries.ENTITY_TYPE.getHolder(key.get());
                if (holder.isPresent() && holder.get().is(killToUnlockTag)) {
                    ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
                    Services.NETWORK.sendToPlayer(new GrantContentPacket(GrantContentPacket.Action.GRANT, GrantContentPacket.Type.ENTRY, entityId), player);
                }
            }
        }
    }
}