package com.evandev.fieldguide;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ModTags {

    public static class Blocks {
        public static final TagKey<Block> MULTIBLOCK_SCAN = tag("multiblock_scan");
        public static final TagKey<Block> PLANTS = tag("plants");
        public static final TagKey<Block> BLACKLISTED = tag("blacklisted");

        private static TagKey<Block> tag(String name) {
            return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Constants.MOD_ID, name));
        }
    }

    public static class Items {
        public static final TagKey<Item> SPYGLASSES = tag("spyglasses");
        public static final TagKey<Item> BLACKLISTED = tag("blacklisted");

        private static TagKey<Item> tag(String name) {
            return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Constants.MOD_ID, name));
        }
    }

    public static class EntityTypes {
        public static final TagKey<EntityType<?>> BLACKLISTED = tag("blacklisted");

        private static TagKey<EntityType<?>> tag(String name) {
            return TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Constants.MOD_ID, name));
        }
    }
}