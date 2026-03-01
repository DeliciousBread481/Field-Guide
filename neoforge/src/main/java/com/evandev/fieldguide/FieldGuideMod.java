package com.evandev.fieldguide;

import com.evandev.fieldguide.network.*;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.server.ServerFieldGuideManager;
import com.evandev.fieldguide.server.command.FieldGuideCommand;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(Constants.MOD_ID)
public class FieldGuideMod {

    public FieldGuideMod(IEventBus modEventBus) {
        CommonClass.init();

        NeoForge.EVENT_BUS.register(this);
        modEventBus.addListener(this::registerPayloads);
    }

    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(Constants.MOD_ID).versioned("1.0");

        registrar.playToClient(SyncLootPacket.TYPE, SyncLootPacket.CODEC, (packet, context) ->
                context.enqueueWork(() -> FieldGuideNeoForgeClient.handleSyncLoot(packet))
        );
        registrar.playToClient(SyncCategoriesPacket.TYPE, SyncCategoriesPacket.CODEC, (packet, context) ->
                context.enqueueWork(() -> FieldGuideNeoForgeClient.handleSyncCategories(packet))
        );
        registrar.playToClient(GrantContentPacket.TYPE, GrantContentPacket.CODEC, (packet, context) ->
                context.enqueueWork(() -> FieldGuideNeoForgeClient.handleGrantContent(packet))
        );
        registrar.playToClient(ExportContentPacket.TYPE, ExportContentPacket.CODEC, (packet, context) ->
                context.enqueueWork(packet::handleClient)
        );

        registrar.playToServer(ClaimXpPacket.TYPE, ClaimXpPacket.CODEC, (packet, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        packet.handleServer(sp);
                    }
                })
        );
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
            TagKey<EntityType<?>> killToUnlockTag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "kill_to_unlock"));

            var key = BuiltInRegistries.ENTITY_TYPE.getResourceKey(event.getEntity().getType());
            if (key.isPresent()) {
                var holder = BuiltInRegistries.ENTITY_TYPE.getHolder(key.get());
                if (holder.isPresent() && holder.get().is(killToUnlockTag)) {
                    ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
                    Services.NETWORK.sendToPlayer(new GrantContentPacket(GrantContentPacket.Action.GRANT, GrantContentPacket.TypeEnum.ENTRY, entityId), player);
                }
            }
        }
    }
}