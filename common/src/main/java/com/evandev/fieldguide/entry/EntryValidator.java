package com.evandev.fieldguide.entry;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.compat.reliableremover.ReliableRemoverCompat;
import com.evandev.fieldguide.config.ServerConfig;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.ModTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class EntryValidator {

    public static boolean isValidEntity(EntityType<?> type, ResourceLocation categoryId) {
        if (!type.canSummon()) return false;
        return BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).flatMap(BuiltInRegistries.ENTITY_TYPE::getHolder).map(h -> {
            if (h.is(ModTags.EntityTypes.BLACKLISTED)) return false;
            if (categoryId != null) {
                TagKey<EntityType<?>> catTag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "blacklisted/" + categoryId.getNamespace() + "/" + categoryId.getPath()));
                return !h.is(catTag);
            }
            return true;
        }).orElse(true);
    }

    public static boolean isValidBlock(Block block, ResourceLocation categoryId) {
        boolean blacklisted = BuiltInRegistries.BLOCK.getResourceKey(block).flatMap(BuiltInRegistries.BLOCK::getHolder).map(h -> {
            if (h.is(ModTags.Blocks.BLACKLISTED)) return true;
            if (categoryId != null) {
                TagKey<Block> catTag = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "blacklisted/" + categoryId.getNamespace() + "/" + categoryId.getPath()));
                return (h.is(catTag));
            }
            return false;
        }).orElse(false);

        if (blacklisted) return false;

        if (Services.PLATFORM.isModLoaded("reliable_remover") && ServerConfig.get().enableReliableRemover && ReliableRemoverCompat.isHidden(block)) {
            return false;
        }
        return true;
    }

    public static boolean isValidItem(Item item, ResourceLocation categoryId) {
        // Items are usually validated via their corresponding block or entity if possible, but some are just items.
        return BuiltInRegistries.ITEM.getResourceKey(item).flatMap(BuiltInRegistries.ITEM::getHolder).map(h -> {
            // Add item blacklisting if needed
            return true;
        }).orElse(true);
    }
}
