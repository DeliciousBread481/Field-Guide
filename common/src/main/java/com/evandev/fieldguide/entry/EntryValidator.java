package com.evandev.fieldguide.entry;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.ModTags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class EntryValidator {

    public static boolean isValidEntity(EntityType<?> type, Identifier categoryId) {
        return BuiltInRegistries.ENTITY_TYPE.getResourceKey(type).flatMap(BuiltInRegistries.ENTITY_TYPE::get).map(h -> {
            if (h.is(ModTags.EntityTypes.BLACKLISTED)) return false;
            if (categoryId != null) {
                TagKey<EntityType<?>> catTag = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "blacklisted/" + categoryId.getNamespace() + "/" + categoryId.getPath()));
                return !h.is(catTag);
            }
            return true;
        }).orElse(true);
    }

    public static boolean isValidBlock(Block block, Identifier categoryId) {
        boolean blacklisted = BuiltInRegistries.BLOCK.getResourceKey(block).flatMap(BuiltInRegistries.BLOCK::get).map(h -> {
            if (h.is(ModTags.Blocks.BLACKLISTED)) return true;
            if (categoryId != null) {
                TagKey<Block> catTag = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "blacklisted/" + categoryId.getNamespace() + "/" + categoryId.getPath()));
                return (h.is(catTag));
            }
            return false;
        }).orElse(false);

        if (blacklisted) return false;

/*        if (Services.PLATFORM.isModLoaded("reliable_remover") && ServerConfig.get().enableReliableRemover && ReliableRemoverCompat.isHidden(block)) {
            return false;
        }*/
        return true;
    }

    public static boolean isValidItem(Item item, Identifier categoryId) {
        // TODO: add blacklisting

        /*        if (Services.PLATFORM.isModLoaded("reliable_remover") && ServerConfig.get().enableReliableRemover && ReliableRemoverCompat.isHidden(item)) {
            return false;
        }*/
        return true;
    }
}
