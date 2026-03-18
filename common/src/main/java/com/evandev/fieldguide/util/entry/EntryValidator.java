package com.evandev.fieldguide.util.entry;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.compat.reliableremover.ReliableRemoverCompat;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.util.ModTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

public class EntryValidator {

    public static boolean isValidEntity(EntityType<?> type, ResourceLocation categoryId) {
        if (!type.canSummon()) return false;
        return BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).flatMap(BuiltInRegistries.ENTITY_TYPE::getHolder).map(h -> {
            if (h.is(ModTags.EntityTypes.BLACKLISTED)) return false;
            if (categoryId != null) {
                TagKey<EntityType<?>> catTag = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Constants.MOD_ID, "blacklisted/" + categoryId.getNamespace() + "/" + categoryId.getPath()));
                return !h.is(catTag);
            }
            return true;
        }).orElse(true);
    }

    public static boolean isValidBlock(Block block, ResourceLocation categoryId) {
        boolean blacklisted = BuiltInRegistries.BLOCK.getResourceKey(block).flatMap(BuiltInRegistries.BLOCK::getHolder).map(h -> {
            if (h.is(ModTags.Blocks.BLACKLISTED)) return true;
            if (categoryId != null) {
                TagKey<Block> catTag = TagKey.create(Registries.BLOCK, new ResourceLocation(Constants.MOD_ID, "blacklisted/" + categoryId.getNamespace() + "/" + categoryId.getPath()));
                return (h.is(catTag));
            }
            return false;
        }).orElse(false);

        if (blacklisted) return false;

        if (Services.PLATFORM.isModLoaded("reliable_remover") && ModConfig.get().enableReliableRemover && ReliableRemoverCompat.isHidden(block)) {
            return false;
        }
        return true;
    }
}
