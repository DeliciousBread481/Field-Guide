package com.evandev.fieldguide.platform;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.platform.services.IRegistryHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ForgeRegistryHelper implements IRegistryHelper {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Constants.MOD_ID);
    private static final Map<ResourceKey<CreativeModeTab>, List<Supplier<? extends ItemLike>>> TAB_ENTRIES = new HashMap<>();

    public static void init(IEventBus bus) {
        ITEMS.register(bus);
        bus.addListener(ForgeRegistryHelper::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (TAB_ENTRIES.containsKey(event.getTabKey())) {
            for (Supplier<? extends ItemLike> item : TAB_ENTRIES.get(event.getTabKey())) {
                event.accept(item.get());
            }
        }
    }

    @Override
    public <T extends Item> Supplier<T> registerItem(String name, Supplier<T> item) {
        return ITEMS.register(name, item);
    }

    @Override
    public void registerToTab(ResourceKey<CreativeModeTab> tab, Supplier<? extends ItemLike> itemSupplier) {
        TAB_ENTRIES.computeIfAbsent(tab, k -> new ArrayList<>()).add(itemSupplier);
    }
}
