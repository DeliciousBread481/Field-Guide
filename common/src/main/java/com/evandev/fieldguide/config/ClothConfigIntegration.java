package com.evandev.fieldguide.config;

import com.evandev.fieldguide.data.FieldGuideDataManager;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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

        ConfigCategory general = builder.getOrCreateCategory(Component.translatable("category.fieldguide.general"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.show_pause_button"), config.showPauseMenuButton)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("option.fieldguide.show_pause_button.tooltip"))
                .setSaveConsumer(newValue -> config.showPauseMenuButton = newValue)
                .build());

        general.addEntry(entryBuilder.startIntField(Component.translatable("option.fieldguide.pause_button_x"), config.pauseButtonXOffset)
                .setDefaultValue(0)
                .setTooltip(Component.translatable("option.fieldguide.pause_button_x.tooltip"))
                .setSaveConsumer(newValue -> config.pauseButtonXOffset = newValue)
                .build());

        general.addEntry(entryBuilder.startIntField(Component.translatable("option.fieldguide.pause_button_y"), config.pauseButtonYOffset)
                .setDefaultValue(0)
                .setTooltip(Component.translatable("option.fieldguide.pause_button_y.tooltip"))
                .setSaveConsumer(newValue -> config.pauseButtonYOffset = newValue)
                .build());

        // --- INVENTORY SETTINGS ---
        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.show_inventory_button"), config.showInventoryButton)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("option.fieldguide.show_inventory_button.tooltip"))
                .setSaveConsumer(newValue -> config.showInventoryButton = newValue)
                .build());

        general.addEntry(entryBuilder.startIntField(Component.translatable("option.fieldguide.inventory_button_x"), config.inventoryButtonXOffset)
                .setDefaultValue(126)
                .setTooltip(Component.translatable("option.fieldguide.inventory_button_x.tooltip"))
                .setSaveConsumer(newValue -> config.inventoryButtonXOffset = newValue)
                .build());

        general.addEntry(entryBuilder.startIntField(Component.translatable("option.fieldguide.inventory_button_y"), config.inventoryButtonYOffset)
                .setDefaultValue(61)
                .setTooltip(Component.translatable("option.fieldguide.inventory_button_y.tooltip"))
                .setSaveConsumer(newValue -> config.inventoryButtonYOffset = newValue)
                .build());

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

        general.addEntry(entryBuilder.startStrList(Component.translatable("option.fieldguide.blacklist"), config.entityBlacklist)
                .setDefaultValue(ModConfig.getDefaultBlacklist())
                .setTooltip(Component.translatable("option.fieldguide.blacklist.tooltip"))
                .setSaveConsumer(newValue -> config.entityBlacklist = newValue)
                .build());

        return builder.build();
    }

}