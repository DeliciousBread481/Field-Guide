package com.evandev.fieldguide.util;

import com.evandev.fieldguide.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ModTags {

    public static class Blocks {
        public static final TagKey<Block> MULTIBLOCK_SCAN = tag("multiblock_scan");
        public static final TagKey<Block> PLANTS = tag("plants");

        private static TagKey<Block> tag(String name) {
            return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, name));
        }
    }

    public static class Items {
        public static final TagKey<Item> SPYGLASSES = tag("spyglasses");

        private static TagKey<Item> tag(String name) {
            return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, name));
        }
    }
}