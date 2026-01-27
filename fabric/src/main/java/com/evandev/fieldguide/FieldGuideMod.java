package com.evandev.fieldguide;

import com.evandev.fieldguide.network.RequestDropsPacket;
import com.evandev.fieldguide.network.SyncDropsPacket;
import com.evandev.fieldguide.platform.FabricNetworkHelper;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.server.LootTableHelper;
import com.evandev.fieldguide.server.ServerFieldGuideManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class FieldGuideMod implements ModInitializer {

    @Override
    public void onInitialize() {
        CommonClass.init();

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
        });

        ServerPlayNetworking.registerGlobalReceiver(FabricNetworkHelper.REQUEST_DROPS_CHANNEL, (server, player, handler, buf, responseSender) -> {
            RequestDropsPacket packet = new RequestDropsPacket(buf);
            server.execute(() -> {
                ResourceLocation id = packet.getEntryId();
                Object entry = null;
                Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
                if (type.isPresent()) entry = type.get();
                else {
                    Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
                    if (block.isPresent()) entry = block.get();
                }

                if (entry != null) {
                    List<ItemStack> drops = LootTableHelper.getDrops(server.getResourceManager(), entry);
                    Services.NETWORK.sendToPlayer(new SyncDropsPacket(id, drops), player);
                }
            });
        });
    }
}