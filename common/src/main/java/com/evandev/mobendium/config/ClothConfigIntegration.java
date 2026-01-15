package com.evandev.mobendium.config;

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
                .setTitle(Component.translatable("title.mobendium.config"));

        builder.setSavingRunnable(ModConfig::save);

        ConfigCategory general = builder.getOrCreateCategory(Component.translatable("category.mobendium.general"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        return builder.build();
    }
}