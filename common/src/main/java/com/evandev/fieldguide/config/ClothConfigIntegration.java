package com.evandev.fieldguide.config;

import com.evandev.fieldguide.client.MobDataManager;
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
            MobDataManager.clearCache();
        });

        ConfigCategory general = builder.getOrCreateCategory(Component.translatable("category.fieldguide.general"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        general.addEntry(entryBuilder.startStrList(Component.translatable("option.fieldguide.blacklist"), config.entityBlacklist)
                .setDefaultValue(ModConfig.getDefaultBlacklist())
                .setTooltip(Component.translatable("option.fieldguide.blacklist.tooltip"))
                .setSaveConsumer(newValue -> config.entityBlacklist = newValue)
                .build());

        return builder.build();
    }

}