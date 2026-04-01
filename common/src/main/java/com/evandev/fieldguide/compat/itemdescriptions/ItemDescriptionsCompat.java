package com.evandev.fieldguide.compat.itemdescriptions;

import cc.cassian.item_descriptions.client.DescriptionKey;
import cc.cassian.item_descriptions.client.helpers.ModHelpers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemDescriptionsCompat {

    /**
     * Attempts to find the best translation key using Item Descriptions' internal priorities
     */
    public static String tryGetDescriptionKey(Object coreEntry) {
        DescriptionKey key = null;

        if (coreEntry instanceof ItemStack stack) {
            key = ModHelpers.findLoreKey(stack);
        } else if (coreEntry instanceof Item item) {
            key = ModHelpers.findLoreKey(item.getDefaultInstance());
        } else if (coreEntry instanceof Entity entity) {
            key = ModHelpers.findLoreKey(entity);
        } else if (coreEntry instanceof EntityType<?> entityType) {
            key = ModHelpers.findLoreKey(entityType);
        }

        if (key != null && key.hasTranslation()) {
            return key.toString();
        }

        return null;
    }
}