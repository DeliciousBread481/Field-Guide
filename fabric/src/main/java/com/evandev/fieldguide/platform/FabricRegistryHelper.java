package com.evandev.fieldguide.platform;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.platform.services.IRegistryHelper;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import java.util.function.Supplier;

public class FabricRegistryHelper implements IRegistryHelper {

    @Override
    public <T extends Item> Supplier<T> registerItem(String name, Supplier<T> itemSupplier) {
        T item = Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(Constants.MOD_ID, name), itemSupplier.get());
        return () -> item;
    }

    @Override
    public void registerToTab(ResourceKey<CreativeModeTab> tab, Supplier<? extends ItemLike> itemSupplier) {
        ItemGroupEvents.modifyEntriesEvent(tab).register(content -> content.accept(itemSupplier.get()));
    }
}
