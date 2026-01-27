package com.evandev.fieldguide;

import com.evandev.fieldguide.network.RequestDropsPacket;
import com.evandev.fieldguide.network.SyncDropsPacket;
import com.evandev.fieldguide.platform.FabricNetworkHelper;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.server.LootTableHelper;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Optional;

public class FieldGuideMod implements ModInitializer {

    @Override
    public void onInitialize() {
        CommonClass.init();

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