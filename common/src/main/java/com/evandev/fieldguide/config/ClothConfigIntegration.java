package com.evandev.fieldguide.config;

import com.evandev.fieldguide.data.FieldGuideDataManager;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

public class ClothConfigIntegration {

    public static Screen createScreen(Screen parent) {
        ModConfig config = ModConfig.get();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("title.fieldguide.config"));

        builder.setSavingRunnable(() -> {
            ModConfig.save();
            FieldGuideDataManager.clearCache();
        });

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(Component.translatable("category.fieldguide.general"));

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.show_undiscovered_names"), config.showUndiscoveredNames)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("option.fieldguide.show_undiscovered_names.tooltip"))
                .setSaveConsumer(newValue -> config.showUndiscoveredNames = newValue)
                .build());

        general.addEntry(entryBuilder.startDoubleField(Component.translatable("option.fieldguide.scan_speed"), config.scanSpeed)
                .setDefaultValue(ModConfig.get().scanSpeed)
                .setMin(0.1D)
                .setMax(10.0D)
                .setTooltip(Component.translatable("option.fieldguide.scan_speed.tooltip"))
                .setSaveConsumer(newValue -> config.scanSpeed = newValue)
                .build());

        ConfigCategory interfaceCat = builder.getOrCreateCategory(Component.translatable("category.fieldguide.interface"));

        interfaceCat.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.show_pause_button"), config.showPauseMenuButton)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("option.fieldguide.show_pause_button.tooltip"))
                .setSaveConsumer(newValue -> config.showPauseMenuButton = newValue)
                .build());

        interfaceCat.addEntry(entryBuilder.startIntField(Component.translatable("option.fieldguide.pause_button_x"), config.pauseButtonXOffset)
                .setDefaultValue(0)
                .setTooltip(Component.translatable("option.fieldguide.pause_button_x.tooltip"))
                .setSaveConsumer(newValue -> config.pauseButtonXOffset = newValue)
                .build());

        interfaceCat.addEntry(entryBuilder.startIntField(Component.translatable("option.fieldguide.pause_button_y"), config.pauseButtonYOffset)
                .setDefaultValue(0)
                .setTooltip(Component.translatable("option.fieldguide.pause_button_y.tooltip"))
                .setSaveConsumer(newValue -> config.pauseButtonYOffset = newValue)
                .build());

        interfaceCat.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.show_inventory_button"), config.showInventoryButton)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("option.fieldguide.show_inventory_button.tooltip"))
                .setSaveConsumer(newValue -> config.showInventoryButton = newValue)
                .build());

        interfaceCat.addEntry(entryBuilder.startIntField(Component.translatable("option.fieldguide.inventory_button_x"), config.inventoryButtonXOffset)
                .setDefaultValue(126)
                .setTooltip(Component.translatable("option.fieldguide.inventory_button_x.tooltip"))
                .setSaveConsumer(newValue -> config.inventoryButtonXOffset = newValue)
                .build());

        interfaceCat.addEntry(entryBuilder.startIntField(Component.translatable("option.fieldguide.inventory_button_y"), config.inventoryButtonYOffset)
                .setDefaultValue(61)
                .setTooltip(Component.translatable("option.fieldguide.inventory_button_y.tooltip"))
                .setSaveConsumer(newValue -> config.inventoryButtonYOffset = newValue)
                .build());

        ConfigCategory contentCat = builder.getOrCreateCategory(Component.translatable("category.fieldguide.content"));

        contentCat.addEntry(entryBuilder.startStrList(Component.translatable("option.fieldguide.blacklist"), config.entityBlacklist)
                .setDefaultValue(ModConfig.getDefaultBlacklist())
                .setTooltip(Component.translatable("option.fieldguide.blacklist.tooltip"))
                .setSaveConsumer(newValue -> config.entityBlacklist = newValue)
                .build());

        contentCat.addEntry(entryBuilder.startStrList(Component.translatable("option.fieldguide.discovery_redirects"), config.discoveryRedirects)
                .setDefaultValue(new ArrayList<>())
                .setTooltip(Component.translatable("option.fieldguide.discovery_redirects.tooltip"))
                .setSaveConsumer(newValue -> config.discoveryRedirects = newValue)
                .build());

        contentCat.addEntry(entryBuilder.startStrList(Component.translatable("option.fieldguide.loot_removals"), config.lootRemovals)
                .setDefaultValue(new ArrayList<>())
                .setTooltip(Component.translatable("option.fieldguide.loot_removals.tooltip"))
                .setSaveConsumer(newValue -> config.lootRemovals = newValue)
                .build());

        contentCat.addEntry(entryBuilder.startStrList(Component.translatable("option.fieldguide.loot_additions"), config.lootAdditions)
                .setDefaultValue(new ArrayList<>())
                .setTooltip(Component.translatable("option.fieldguide.loot_additions.tooltip"))
                .setSaveConsumer(newValue -> config.lootAdditions = newValue)
                .build());

        return builder.build();
    }
}