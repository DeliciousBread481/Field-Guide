package com.evandev.fieldguide.compat;

import com.evandev.fieldguide.api.seasons.Season;
import com.evandev.fieldguide.api.seasons.SeasonsAPI;
import com.evandev.fieldguide.api.seasons.SeasonsProvider;
import com.evandev.fieldguide.compat.sereneseasons.SereneSeasonsProvider;
import com.evandev.fieldguide.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class SeasonsCompat {
    public static void init() {
        if (Services.PLATFORM.isModLoaded("sereneseasons")) {
            SeasonsAPI.registerProvider(new SereneSeasonsProvider());
        }
        
        if (Services.PLATFORM.isModLoaded("fabric-seasons")) {
            // Fabric Seasons integration would go here
        }

        if (Services.PLATFORM.isModLoaded("eclipticseasons")) {
            // Ecliptic Seasons integration would go here
        }
    }
}
