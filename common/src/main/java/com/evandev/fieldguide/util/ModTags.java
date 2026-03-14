package com.evandev.fieldguide.util;

import com.evandev.fieldguide.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
            return TagKey.create(Registries.BLOCK, new ResourceLocation(Constants.MOD_ID, name));
        }
    }

    public static class Items {
        public static final TagKey<Item> SPYGLASSES = tag("spyglasses");

        private static TagKey<Item> tag(String name) {
            return TagKey.create(Registries.ITEM, new ResourceLocation(Constants.MOD_ID, name));
        }
    }

    public static class EntityTypes {
        public static final TagKey<EntityType<?>> BLACKLISTED = tag("blacklisted");

        private static TagKey<EntityType<?>> tag(String name) {
            return TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Constants.MOD_ID, name));
        }
    }
}