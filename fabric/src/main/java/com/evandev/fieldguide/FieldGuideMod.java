package com.evandev.fieldguide;

import com.evandev.fieldguide.api.EntryUnlockData;
import com.evandev.fieldguide.api.variant.VariantDef;
import com.evandev.fieldguide.api.variant.VariantProvider;
import com.evandev.fieldguide.network.*;
import com.evandev.fieldguide.server.ServerFieldGuideManager;
import com.evandev.fieldguide.server.command.FieldGuideCommand;
import com.evandev.fieldguide.server.progress.FieldGuideProgressManager;
import com.evandev.fieldguide.server.progress.FieldGuideTriggers;
import com.evandev.fieldguide.server.progress.PlayerFieldGuideProgress;
import com.evandev.fieldguide.variant.FieldGuideVariantManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class FieldGuideMod implements ModInitializer {

    @Override
    public void onInitialize() {
        CommonClass.init();

        // Register Client-bound payloads
        PayloadTypeRegistry.clientboundPlay().register(SyncCategoriesPacket.TYPE, SyncCategoriesPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncLootPacket.TYPE, SyncLootPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ExportContentPacket.TYPE, ExportContentPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ProgressUpdatePacket.TYPE, ProgressUpdatePacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SyncConfigPacket.TYPE, SyncConfigPacket.CODEC);

        // Register Server-bound payloads
        PayloadTypeRegistry.serverboundPlay().register(ScanUnlockPacket.TYPE, ScanUnlockPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MarkSeenPacket.TYPE, MarkSeenPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdateEntryDataPacket.TYPE, UpdateEntryDataPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpdateJournalPacket.TYPE, UpdateJournalPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(CopyPagePacket.TYPE, CopyPagePacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RequestLootPacket.TYPE, RequestLootPacket.CODEC);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> FieldGuideCommand.register(dispatcher));

/*        if (Services.PLATFORM.isModLoaded("exposure")) {
            ExposureFabricEventHandler.register();
        }*/

        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new IdentifiableResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.fromNamespaceAndPath(Constants.MOD_ID, "server_data");
            }

            @Override
            public @NotNull CompletableFuture<Void> reload(@NotNull PreparableReloadListener.SharedState state, @NotNull Executor prepareExecutor, @NotNull PreparableReloadListener.PreparationBarrier barrier, @NotNull Executor applyExecutor) {
                return ServerFieldGuideManager.getInstance().reload(state, prepareExecutor, barrier, applyExecutor);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerFieldGuideManager.getInstance().syncToPlayer(handler.player);
            FieldGuideProgressManager.getInstance().onPlayerJoin(handler.player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            FieldGuideProgressManager.getInstance().onPlayerDisconnect(handler.player);
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerFieldGuideManager.getInstance().onServerStarted(server);
            FieldGuideProgressManager.init(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            FieldGuideProgressManager.shutdown();
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            FieldGuideProgressManager.getInstance().tick();
        });

        // Server-side receivers
        ServerPlayNetworking.registerGlobalReceiver(ScanUnlockPacket.TYPE, (packet, context) -> {
            context.server().execute(() -> packet.handleServer(context.player()));
        });

        ServerPlayNetworking.registerGlobalReceiver(MarkSeenPacket.TYPE, (packet, context) -> {
            context.server().execute(() -> packet.handleServer(context.player()));
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateEntryDataPacket.TYPE, (packet, context) -> {
            context.server().execute(() -> packet.handleServer(context.player()));
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateJournalPacket.TYPE, (packet, context) -> {
            context.server().execute(() -> packet.handleServer(context.player()));
        });

        ServerPlayNetworking.registerGlobalReceiver(CopyPagePacket.TYPE, (packet, context) -> {
            context.server().execute(() -> packet.handleServer(context.player()));
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestLootPacket.TYPE, (packet, context) -> {
            context.server().execute(() -> packet.handleServer(context.player()));
        });

        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((ServerLevel world, Entity entity, LivingEntity killedEntity, DamageSource damageSource) -> {
            if (entity instanceof ServerPlayer player) {
                FieldGuideProgressManager manager = FieldGuideProgressManager.getInstance();
                if (manager.wasRecentlyScanned(player, killedEntity.getId())) {
                    FieldGuideTriggers.SCAN_AND_KILL.get().trigger(player, killedEntity);
                }

                Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(killedEntity.getType());
                PlayerFieldGuideProgress progress = manager.getProgress(player);

                if (progress != null) {
                    String variantId = "";
                    List<VariantDef> variants = FieldGuideVariantManager.getVariants(killedEntity);
                    if (!variants.isEmpty()) {
                        VariantProvider<Mob> provider = FieldGuideVariantManager.getProvider(killedEntity);
                        if (provider != null) {
                            VariantDef current = provider.getCurrent((Mob) killedEntity);
                            if (current != null) variantId = current.id();
                        }
                    }
                    progress.tryUnlock(player, entityId, variantId, EntryUnlockData.UnlockTrigger.KILL);
                }
            }
        });
    }
}