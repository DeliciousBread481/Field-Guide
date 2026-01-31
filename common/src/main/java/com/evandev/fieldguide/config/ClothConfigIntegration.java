package com.evandev.fieldguide.config;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
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
            ClientFieldGuideManager.clearCache();
        });

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // General
        ConfigCategory general = builder.getOrCreateCategory(Component.translatable("category.fieldguide.general"));

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.show_undiscovered_names"), config.showUndiscoveredNames)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("option.fieldguide.show_undiscovered_names.tooltip"))
                .setSaveConsumer(newValue -> config.showUndiscoveredNames = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.auto_rotate_models"), config.autoRotateModels)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("option.fieldguide.auto_rotate_models.tooltip"))
                .setSaveConsumer(newValue -> config.autoRotateModels = newValue)
                .build());

        general.addEntry(entryBuilder.startFloatField(Component.translatable("option.fieldguide.rotation_speed"), config.rotationSpeed)
                .setDefaultValue(15.0F)
                .setTooltip(Component.translatable("option.fieldguide.rotation_speed.tooltip"))
                .setSaveConsumer(newValue -> config.rotationSpeed = newValue)
                .build());

        general.addEntry(entryBuilder.startDoubleField(Component.translatable("option.fieldguide.scan_speed"), config.scanSpeed)
                .setDefaultValue(1.0D)
                .setMin(0.1D)
                .setMax(10.0D)
                .setTooltip(Component.translatable("option.fieldguide.scan_speed.tooltip"))
                .setSaveConsumer(newValue -> config.scanSpeed = newValue)
                .build());

        general.addEntry(entryBuilder.startDoubleField(Component.translatable("option.fieldguide.scan_distance"), config.scanDistance)
                .setDefaultValue(64.0D)
                .setMin(1.0D)
                .setMax(256.0D)
                .setTooltip(Component.translatable("option.fieldguide.scan_distance.tooltip"))
                .setSaveConsumer(newValue -> config.scanDistance = newValue)
                .build());

        // Interface
        ConfigCategory interfaceCat = builder.getOrCreateCategory(Component.translatable("category.fieldguide.interface"));

        interfaceCat.addEntry(entryBuilder.startStrField(Component.translatable("option.fieldguide.scan_overlay_color"), config.scanOverlayColor)
                .setDefaultValue("#FFFFFF")
                .setTooltip(Component.translatable("option.fieldguide.scan_overlay_color.tooltip"))
                .setSaveConsumer(newValue -> config.scanOverlayColor = newValue)
                .build());

        interfaceCat.addEntry(entryBuilder.startDoubleField(Component.translatable("option.fieldguide.scan_overlay_alpha"), config.scanOverlayAlpha)
                .setDefaultValue(0.5D)
                .setMin(0.0D)
                .setMax(1.0D)
                .setTooltip(Component.translatable("option.fieldguide.scan_overlay_alpha.tooltip"))
                .setSaveConsumer(newValue -> config.scanOverlayAlpha = newValue)
                .build());

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

        // Content

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

        contentCat.addEntry(entryBuilder.startStrList(Component.translatable("option.fieldguide.biome_removals"), config.biomeRemovals)
                .setDefaultValue(new ArrayList<>())
                .setTooltip(Component.translatable("option.fieldguide.biome_removals.tooltip"))
                .setSaveConsumer(newValue -> config.biomeRemovals = newValue)
                .build());

        contentCat.addEntry(entryBuilder.startStrList(Component.translatable("option.fieldguide.biome_additions"), config.biomeAdditions)
                .setDefaultValue(new ArrayList<>())
                .setTooltip(Component.translatable("option.fieldguide.biome_additions.tooltip"))
                .setSaveConsumer(newValue -> config.biomeAdditions = newValue)
                .build());

        return builder.build();
    }
}