package com.evandev.fieldguide.platform.services;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import java.util.function.Supplier;

public interface IRegistryHelper {

    /**
     * Registers an item.
     *
     * @param name The name of the item.
     * @param item The item supplier.
     * @return The registered item supplier.
     */
    <T extends Item> Supplier<T> registerItem(String name, Supplier<T> item);

    /**
     * Registers an item to a creative tab.
     *
     * @param tab  The creative tab.
     * @param item The item supplier.
     */
    void registerToTab(ResourceKey<CreativeModeTab> tab, Supplier<? extends ItemLike> item);
}
