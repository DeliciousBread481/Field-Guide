package com.evandev.fieldguide.config;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.server.ServerFieldGuideManager;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
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

            if (Minecraft.getInstance().hasSingleplayerServer() && Minecraft.getInstance().getSingleplayerServer() != null) {
                Minecraft.getInstance().getSingleplayerServer().execute(() -> ServerFieldGuideManager.getInstance().reload(Minecraft.getInstance().getSingleplayerServer()));
            }
        });

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // General
        ConfigCategory general = builder.getOrCreateCategory(Component.translatable("category.fieldguide.general"));

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.show_undiscovered_names"), config.showUndiscoveredNames)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("option.fieldguide.show_undiscovered_names.tooltip"))
                .setSaveConsumer(newValue -> config.showUndiscoveredNames = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.hide_undiscovered_from_search"), config.hideUndiscoveredFromSearch)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("option.fieldguide.hide_undiscovered_from_search.tooltip"))
                .setSaveConsumer(newValue -> config.hideUndiscoveredFromSearch = newValue)
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

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.require_spyglass"), config.requireSpyglass)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("option.fieldguide.require_spyglass.tooltip"))
                .setSaveConsumer(newValue -> config.requireSpyglass = newValue)
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

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.grant_xp_on_scan"), config.grantXpOnScan)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("option.fieldguide.grant_xp_on_scan.tooltip"))
                .setSaveConsumer(newValue -> config.grantXpOnScan = newValue)
                .build());

        general.addEntry(entryBuilder.startIntField(Component.translatable("option.fieldguide.xp_amount_on_scan"), config.xpAmountOnScan)
                .setDefaultValue(5)
                .setMin(0)
                .setMax(1000)
                .setTooltip(Component.translatable("option.fieldguide.xp_amount_on_scan.tooltip"))
                .setSaveConsumer(newValue -> config.xpAmountOnScan = newValue)
                .build());

        // Interface
        ConfigCategory interfaceCat = builder.getOrCreateCategory(Component.translatable("category.fieldguide.interface"));

        interfaceCat.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.show_scan_icon"), config.showScanIcon)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("option.fieldguide.show_scan_icon.tooltip"))
                .setSaveConsumer(newValue -> config.showScanIcon = newValue)
                .build());

        interfaceCat.addEntry(entryBuilder.startIntField(Component.translatable("option.fieldguide.scan_icon_y_offset"), config.scanIconYOffset)
                .setDefaultValue(2)
                .setTooltip(Component.translatable("option.fieldguide.scan_icon_y_offset.tooltip"))
                .setSaveConsumer(newValue -> config.scanIconYOffset = newValue)
                .build());

        interfaceCat.addEntry(entryBuilder.startIntField(Component.translatable("option.fieldguide.scan_icon_x_offset"), config.scanIconXOffset)
                .setDefaultValue(30)
                .setTooltip(Component.translatable("option.fieldguide.scan_icon_x_offset.tooltip"))
                .setSaveConsumer(newValue -> config.scanIconXOffset = newValue)
                .build());


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

        interfaceCat.addEntry(entryBuilder.startStrField(Component.translatable("option.fieldguide.text_color"), config.textColor)
                .setDefaultValue("#8A5E3B")
                .setTooltip(Component.translatable("option.fieldguide.text_color.tooltip"))
                .setSaveConsumer(newValue -> config.textColor = newValue)
                .build());

        interfaceCat.addEntry(entryBuilder.startStrField(Component.translatable("option.fieldguide.text_title_color"), config.textTitleColor)
                .setDefaultValue("#704623")
                .setTooltip(Component.translatable("option.fieldguide.text_title_color.tooltip"))
                .setSaveConsumer(newValue -> config.textTitleColor = newValue)
                .build());

        interfaceCat.addEntry(entryBuilder.startStrField(Component.translatable("option.fieldguide.text_muted_color"), config.textMutedColor)
                .setDefaultValue("#C7A875")
                .setTooltip(Component.translatable("option.fieldguide.text_muted_color.tooltip"))
                .setSaveConsumer(newValue -> config.textMutedColor = newValue)
                .build());

        interfaceCat.addEntry(entryBuilder.startStrField(Component.translatable("option.fieldguide.page_number_color"), config.pageNumberColor)
                .setDefaultValue("#C7A875")
                .setTooltip(Component.translatable("option.fieldguide.page_number_color.tooltip"))
                .setSaveConsumer(newValue -> config.pageNumberColor = newValue)
                .build());

        interfaceCat.addEntry(entryBuilder.startStrField(Component.translatable("option.fieldguide.list_silhouette_color"), config.listSilhouetteColor)
                .setDefaultValue("#DDC69B")
                .setTooltip(Component.translatable("option.fieldguide.list_silhouette_color.tooltip"))
                .setSaveConsumer(newValue -> config.listSilhouetteColor = newValue)
                .build());

        interfaceCat.addEntry(entryBuilder.startStrField(Component.translatable("option.fieldguide.details_silhouette_color"), config.detailsSilhouetteColor)
                .setDefaultValue("#DDC69B")
                .setTooltip(Component.translatable("option.fieldguide.details_silhouette_color.tooltip"))
                .setSaveConsumer(newValue -> config.detailsSilhouetteColor = newValue)
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
                .setDefaultValue(ModConfig.getDefaultLootAdditions())
                .setTooltip(Component.translatable("option.fieldguide.loot_additions.tooltip"))
                .setSaveConsumer(newValue -> config.lootAdditions = newValue)
                .build());

        contentCat.addEntry(entryBuilder.startStrList(Component.translatable("option.fieldguide.biome_removals"), config.biomeRemovals)
                .setDefaultValue(new ArrayList<>())
                .setTooltip(Component.translatable("option.fieldguide.biome_removals.tooltip"))
                .setSaveConsumer(newValue -> config.biomeRemovals = newValue)
                .build());

        contentCat.addEntry(entryBuilder.startStrList(Component.translatable("option.fieldguide.biome_additions"), config.biomeAdditions)
                .setDefaultValue(ModConfig.getDefaultBiomeAdditions())
                .setTooltip(Component.translatable("option.fieldguide.biome_additions.tooltip"))
                .setSaveConsumer(newValue -> config.biomeAdditions = newValue)
                .build());

        return builder.build();
    }
}