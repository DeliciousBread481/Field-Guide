package com.evandev.fieldguide;

import com.evandev.fieldguide.network.*;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.server.ServerFieldGuideManager;
import com.evandev.fieldguide.server.command.FieldGuideCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
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

        PayloadTypeRegistry.playS2C().register(SyncCategoriesPacket.TYPE, SyncCategoriesPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(GrantContentPacket.TYPE, GrantContentPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncLootPacket.TYPE, SyncLootPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(ExportContentPacket.TYPE, ExportContentPacket.CODEC);

        PayloadTypeRegistry.playC2S().register(ClaimXpPacket.TYPE, ClaimXpPacket.CODEC);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> FieldGuideCommand.register(dispatcher));
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new IdentifiableResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "server_data");
            }

            @Override
            public @NotNull CompletableFuture<Void> reload(@NotNull PreparationBarrier barrier, @NotNull ResourceManager manager, @NotNull ProfilerFiller prepareProfiler, @NotNull ProfilerFiller applyProfiler, @NotNull Executor prepareExecutor, @NotNull Executor applyExecutor) {
                return ServerFieldGuideManager.getInstance().reload(barrier, manager, prepareProfiler, applyProfiler, prepareExecutor, applyExecutor);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerFieldGuideManager.getInstance().syncToPlayer(handler.player));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> ServerFieldGuideManager.getInstance().onServerStarted(server));

        ServerPlayNetworking.registerGlobalReceiver(ClaimXpPacket.TYPE, (packet, context) -> {
            context.server().execute(() -> packet.handleServer(context.player()));
        });

        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity) -> {
            if (entity instanceof ServerPlayer player) {
                TagKey<EntityType<?>> killToUnlockTag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "kill_to_unlock"));

                var key = BuiltInRegistries.ENTITY_TYPE.getResourceKey(killedEntity.getType());
                if (key.isPresent()) {
                    var holder = BuiltInRegistries.ENTITY_TYPE.getHolder(key.get());
                    if (holder.isPresent() && holder.get().is(killToUnlockTag)) {
                        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(killedEntity.getType());
                        Services.NETWORK.sendToPlayer(new GrantContentPacket(GrantContentPacket.Action.GRANT, GrantContentPacket.TypeEnum.ENTRY, entityId), player);
                    }
                }
            }
        });
    }
}