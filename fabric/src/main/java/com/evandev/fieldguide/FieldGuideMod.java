package com.evandev.fieldguide;

import com.evandev.fieldguide.compat.exposure.ExposureFabricEventHandler;
import com.evandev.fieldguide.network.MarkSeenPacket;
import com.evandev.fieldguide.network.ScanUnlockPacket;
import com.evandev.fieldguide.network.UpdateEntryDataPacket;
import com.evandev.fieldguide.network.UpdateJournalPacket;
import com.evandev.fieldguide.platform.FabricNetworkHelper;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.server.ServerFieldGuideManager;
import com.evandev.fieldguide.server.command.FieldGuideCommand;
import com.evandev.fieldguide.server.progress.FieldGuideProgressManager;
import com.evandev.fieldguide.server.progress.PlayerFieldGuideProgress;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class FieldGuideMod implements ModInitializer {

    @Override
    public void onInitialize() {
        CommonClass.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> FieldGuideCommand.register(dispatcher));

        if (Services.PLATFORM.isModLoaded("exposure")) {
            ExposureFabricEventHandler.register();
        }

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                ServerFieldGuideManager.getInstance().reload(server);
            }
        });

        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new IdentifiableResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return new ResourceLocation(Constants.MOD_ID, "server_data");
            }

            @Override
            public @NotNull CompletableFuture<Void> reload(@NotNull PreparationBarrier barrier, @NotNull ResourceManager manager, @NotNull ProfilerFiller prepareProfiler, @NotNull ProfilerFiller applyProfiler, @NotNull Executor prepareExecutor, @NotNull Executor applyExecutor) {
                return ServerFieldGuideManager.getInstance().reload(barrier, manager, prepareProfiler, applyProfiler, prepareExecutor, applyExecutor);
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

        ServerPlayNetworking.registerGlobalReceiver(FabricNetworkHelper.SCAN_UNLOCK_CHANNEL, (server, player, handler, buf, responseSender) -> {
            ScanUnlockPacket packet = new ScanUnlockPacket(buf);
            server.execute(() -> packet.handleServer(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(FabricNetworkHelper.MARK_SEEN_CHANNEL, (server, player, handler, buf, responseSender) -> {
            MarkSeenPacket packet = new MarkSeenPacket(buf);
            server.execute(() -> packet.handleServer(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(FabricNetworkHelper.UPDATE_ENTRY_DATA_CHANNEL, (server, player, handler, buf, responseSender) -> {
            UpdateEntryDataPacket packet = new UpdateEntryDataPacket(buf);
            server.execute(() -> packet.handleServer(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(FabricNetworkHelper.UPDATE_JOURNAL_CHANNEL, (server, player, handler, buf, responseSender) -> {
            UpdateJournalPacket packet = new UpdateJournalPacket(buf);
            server.execute(() -> packet.handleServer(player));
        });

        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity) -> {
            if (entity instanceof ServerPlayer player) {
                TagKey<EntityType<?>> killToUnlockTag = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Constants.MOD_ID, "kill_to_unlock"));

                var key = BuiltInRegistries.ENTITY_TYPE.getResourceKey(killedEntity.getType());
                if (key.isPresent()) {
                    var holder = BuiltInRegistries.ENTITY_TYPE.getHolder(key.get());
                    if (holder.isPresent() && holder.get().is(killToUnlockTag)) {
                        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(killedEntity.getType());
                        PlayerFieldGuideProgress progress = FieldGuideProgressManager.getInstance().getProgress(player);
                        if (progress != null) {
                            progress.unlock(player, entityId);
                        }
                    }
                }
            }
        });
    }
}