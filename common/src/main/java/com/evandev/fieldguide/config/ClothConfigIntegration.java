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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        general.addEntry(entryBuilder.startStrField(Component.translatable("option.fieldguide.default_screen"), config.defaultScreen)
                .setDefaultValue("last_opened_screen")
                .setTooltip(Component.translatable("option.fieldguide.default_screen.tooltip"))
                .setSaveConsumer(newValue -> config.defaultScreen = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.hide_tabs_until_unlocked"), config.hideTabsUntilUnlocked)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("option.fieldguide.hide_tabs_until_unlocked.tooltip"))
                .setSaveConsumer(newValue -> config.hideTabsUntilUnlocked = newValue)
                .build());

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

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.disable_scanning"), config.disableScanning)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("option.fieldguide.disable_scanning.tooltip"))
                .setSaveConsumer(newValue -> config.disableScanning = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.enable_spyglass_scanning"), config.enableSpyglassScanning)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("option.fieldguide.enable_spyglass_scanning.tooltip"))
                .setSaveConsumer(newValue -> config.enableSpyglassScanning = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.enable_naked_eye_scanning"), config.enableNakedEyeScanning)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("option.fieldguide.enable_naked_eye_scanning.tooltip"))
                .setSaveConsumer(newValue -> config.enableNakedEyeScanning = newValue)
                .build());

        general.addEntry(entryBuilder.startDoubleField(Component.translatable("option.fieldguide.scan_speed"), config.scanSpeed)
                .setDefaultValue(1.0D)
                .setMin(0.1D)
                .setMax(10.0D)
                .setTooltip(Component.translatable("option.fieldguide.scan_speed.tooltip"))
                .setSaveConsumer(newValue -> config.scanSpeed = newValue)
                .build());

        general.addEntry(entryBuilder.startDoubleField(Component.translatable("option.fieldguide.spyglass_scan_distance"), config.spyglassScanDistance)
                .setDefaultValue(64.0D)
                .setMin(1.0D)
                .setMax(256.0D)
                .setTooltip(Component.translatable("option.fieldguide.spyglass_scan_distance.tooltip"))
                .setSaveConsumer(newValue -> config.spyglassScanDistance = newValue)
                .build());

        general.addEntry(entryBuilder.startDoubleField(Component.translatable("option.fieldguide.naked_eye_scan_distance"), config.nakedEyeScanDistance)
                .setDefaultValue(10.0D)
                .setMin(1.0D)
                .setMax(256.0D)
                .setTooltip(Component.translatable("option.fieldguide.naked_eye_scan_distance.tooltip"))
                .setSaveConsumer(newValue -> config.nakedEyeScanDistance = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.grant_xp_on_scan"), config.grantXpOnScan)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("option.fieldguide.grant_xp_on_scan.tooltip"))
                .setSaveConsumer(newValue -> config.grantXpOnScan = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.enable_reliable_remover"), config.enableReliableRemover)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("option.fieldguide.enable_reliable_remover.tooltip"))
                .setSaveConsumer(newValue -> config.enableReliableRemover = newValue)
                .build());

        general.addEntry(entryBuilder.startIntField(Component.translatable("option.fieldguide.xp_amount_on_scan"), config.xpAmountOnScan)
                .setDefaultValue(5)
                .setMin(0)
                .setMax(1000)
                .setTooltip(Component.translatable("option.fieldguide.xp_amount_on_scan.tooltip"))
                .setSaveConsumer(newValue -> config.xpAmountOnScan = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.disable_loot_display"), config.disableLootDisplay)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("option.fieldguide.disable_loot_display.tooltip"))
                .setSaveConsumer(newValue -> config.disableLootDisplay = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.disable_biome_display"), config.disableBiomeDisplay)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("option.fieldguide.disable_biome_display.tooltip"))
                .setSaveConsumer(newValue -> config.disableBiomeDisplay = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.disable_editing_descriptions"), config.disableEditingDescriptions)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("option.fieldguide.disable_editing_descriptions.tooltip"))
                .setSaveConsumer(newValue -> config.disableEditingDescriptions = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.disable_editing_names"), config.disableEditingNames)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("option.fieldguide.disable_editing_names.tooltip"))
                .setSaveConsumer(newValue -> config.disableEditingNames = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.keep_silhouette"), config.keepSilhouetteWhenUnlocked)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("option.fieldguide.keep_silhouette.tooltip"))
                .setSaveConsumer(newValue -> config.keepSilhouetteWhenUnlocked = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.unlock_variants"), config.unlockAllVariants)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("option.fieldguide.unlock_variants.tooltip"))
                .setSaveConsumer(newValue -> config.unlockAllVariants = newValue)
                .build());

        // Interface
        ConfigCategory interfaceCat = builder.getOrCreateCategory(Component.translatable("category.fieldguide.interface"));

        interfaceCat.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.use_real_world_date"), config.useRealWorldDate)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("option.fieldguide.use_real_world_date.tooltip"))
                .setSaveConsumer(newValue -> config.useRealWorldDate = newValue)
                .build());

        interfaceCat.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.play_scanning_sound"), config.playScanningSound)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("option.fieldguide.play_scanning_sound.tooltip"))
                .setSaveConsumer(newValue -> config.playScanningSound = newValue)
                .build());

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

        interfaceCat.addEntry(entryBuilder.startStrField(Component.translatable("option.fieldguide.text_new_color"), config.textNewColor)
                .setDefaultValue("#63B40C")
                .setTooltip(Component.translatable("option.fieldguide.text_new_color.tooltip"))
                .setSaveConsumer(newValue -> config.textNewColor = newValue)
                .build());

        interfaceCat.addEntry(entryBuilder.startStrField(Component.translatable("option.fieldguide.text_cursor_color"), config.textCursorColor)
                .setDefaultValue("#0xFF704623")
                .setTooltip(Component.translatable("option.fieldguide.text_cursor_color.tooltip"))
                .setSaveConsumer(newValue -> config.textCursorColor = newValue)
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

        interfaceCat.addEntry(entryBuilder.startDoubleField(Component.translatable("option.fieldguide.list_silhouette_alpha"), config.listSilhouetteAlpha)
                .setDefaultValue(1.0D)
                .setMin(0.0D)
                .setMax(1.0D)
                .setTooltip(Component.translatable("option.fieldguide.list_silhouette_alpha.tooltip"))
                .setSaveConsumer(newValue -> config.listSilhouetteAlpha = newValue)
                .build());

        interfaceCat.addEntry(entryBuilder.startStrField(Component.translatable("option.fieldguide.list_unlocked_silhouette_color"), config.listUnlockedSilhouetteColor)
                .setDefaultValue("#DDC69B")
                .setTooltip(Component.translatable("option.fieldguide.list_unlocked_silhouette_color.tooltip"))
                .setSaveConsumer(newValue -> config.listUnlockedSilhouetteColor = newValue)
                .build());

        interfaceCat.addEntry(entryBuilder.startDoubleField(Component.translatable("option.fieldguide.list_unlocked_silhouette_alpha"), config.listUnlockedSilhouetteAlpha)
                .setDefaultValue(1.0D)
                .setMin(0.0D)
                .setMax(1.0D)
                .setTooltip(Component.translatable("option.fieldguide.list_unlocked_silhouette_alpha.tooltip"))
                .setSaveConsumer(newValue -> config.listUnlockedSilhouetteAlpha = newValue)
                .build());


        interfaceCat.addEntry(entryBuilder.startStrField(Component.translatable("option.fieldguide.details_silhouette_color"), config.detailsSilhouetteColor)
                .setDefaultValue("#DDC69B")
                .setTooltip(Component.translatable("option.fieldguide.details_silhouette_color.tooltip"))
                .setSaveConsumer(newValue -> config.detailsSilhouetteColor = newValue)
                .build());


        interfaceCat.addEntry(entryBuilder.startDoubleField(Component.translatable("option.fieldguide.details_silhouette_alpha"), config.detailsSilhouetteAlpha)
                .setDefaultValue(1.0D)
                .setMin(0.0D)
                .setMax(1.0D)
                .setTooltip(Component.translatable("option.fieldguide.details_silhouette_alpha.tooltip"))
                .setSaveConsumer(newValue -> config.detailsSilhouetteAlpha = newValue)
                .build());

        interfaceCat.addEntry(entryBuilder.startStrField(Component.translatable("option.fieldguide.details_unlocked_silhouette_color"), config.detailsUnlockedSilhouetteColor)
                .setDefaultValue("#DDC69B")
                .setTooltip(Component.translatable("option.fieldguide.details_unlocked_silhouette_color.tooltip"))
                .setSaveConsumer(newValue -> config.detailsUnlockedSilhouetteColor = newValue)
                .build());

        interfaceCat.addEntry(entryBuilder.startDoubleField(Component.translatable("option.fieldguide.details_unlocked_silhouette_alpha"), config.detailsUnlockedSilhouetteAlpha)
                .setDefaultValue(1.0D)
                .setMin(0.0D)
                .setMax(1.0D)
                .setTooltip(Component.translatable("option.fieldguide.details_unlocked_silhouette_alpha.tooltip"))
                .setSaveConsumer(newValue -> config.detailsUnlockedSilhouetteAlpha = newValue)
                .build());

        // Content
        ConfigCategory contentCat = builder.getOrCreateCategory(Component.translatable("category.fieldguide.content"));

        contentCat.addEntry(entryBuilder.startStrList(Component.translatable("option.fieldguide.global_scan_commands"), config.globalScanCommands)
                .setDefaultValue(new ArrayList<>())
                .setTooltip(Component.translatable("option.fieldguide.global_scan_commands.tooltip"))
                .setSaveConsumer(newValue -> config.globalScanCommands = newValue)
                .build());

        contentCat.addEntry(entryBuilder.startStrList(Component.translatable("option.fieldguide.category_scan_commands"), convertMapToList(config.categoryScanCommands))
                .setDefaultValue(new ArrayList<>())
                .setTooltip(Component.translatable("option.fieldguide.category_scan_commands.tooltip"))
                .setSaveConsumer(newValue -> config.categoryScanCommands = convertListToMap(newValue))
                .build());

        contentCat.addEntry(entryBuilder.startStrList(Component.translatable("option.fieldguide.entry_scan_commands"), convertMapToList(config.entryScanCommands))
                .setDefaultValue(new ArrayList<>())
                .setTooltip(Component.translatable("option.fieldguide.entry_scan_commands.tooltip"))
                .setSaveConsumer(newValue -> config.entryScanCommands = convertListToMap(newValue))
                .build());

        // Exposure
        ConfigCategory exposureCat = builder.getOrCreateCategory(Component.translatable("category.fieldguide.exposure"));

        exposureCat.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.exposure.add_photograph_button"), config.exposureAddPhotographButton)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("option.fieldguide.exposure.add_photograph_button.tooltip"))
                .setSaveConsumer(newValue -> config.exposureAddPhotographButton = newValue)
                .build());

        exposureCat.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.exposure.unlock_via_photograph"), config.exposureUnlockViaPhotograph)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("option.fieldguide.exposure.unlock_via_photograph.tooltip"))
                .setSaveConsumer(newValue -> config.exposureUnlockViaPhotograph = newValue)
                .build());

        exposureCat.addEntry(entryBuilder.startBooleanToggle(Component.translatable("option.fieldguide.exposure.show_photographs_in_grid"), config.exposureShowPhotographsInGrid)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("option.fieldguide.exposure.show_photographs_in_grid.tooltip"))
                .setSaveConsumer(newValue -> config.exposureShowPhotographsInGrid = newValue)
                .build());

        return builder.build();
    }

    private static List<String> convertMapToList(Map<String, List<String>> map) {
        List<String> list = new ArrayList<>();
        if (map != null) {
            for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                list.add(entry.getKey() + "=" + String.join(";", entry.getValue()));
            }
        }
        return list;
    }

    private static Map<String, List<String>> convertListToMap(List<String> list) {
        Map<String, List<String>> map = new HashMap<>();
        if (list != null) {
            for (String s : list) {
                String[] parts = s.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String[] commands = parts[1].split(";");
                    List<String> commandList = new ArrayList<>();
                    for (String cmd : commands) {
                        if (!cmd.trim().isEmpty()) {
                            commandList.add(cmd.trim());
                        }
                    }
                    map.put(key, commandList);
                }
            }
        }
        return map;
    }
}
