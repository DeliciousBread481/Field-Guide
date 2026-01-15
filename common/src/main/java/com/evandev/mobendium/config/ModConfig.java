package com.evandev.mobendium.config;

import com.evandev.mobendium.Constants;
import com.evandev.mobendium.platform.Services;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = Services.PLATFORM.getConfigDirectory().resolve("mobendium.json").toFile();

    private static ModConfig INSTANCE;

    // Config fields
    public List<String> entityBlacklist = new ArrayList<>();

    public static ModConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, ModConfig.class);
            } catch (Exception e) {
                Constants.LOG.error("Failed to load mobendium.json", e);
                INSTANCE = new ModConfig();
            }
        } else {
            INSTANCE = new ModConfig();
            INSTANCE.entityBlacklist.addAll(getDefaultBlacklist());
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            Constants.LOG.error("Failed to save mobendium.json", e);
        }
    }

    public static List<String> getDefaultBlacklist() {
        List<String> defaults = new ArrayList<>();
        defaults.add("minecraft:arrow");
        defaults.add("minecraft:spectral_arrow");
        defaults.add("minecraft:trident");
        defaults.add("minecraft:snowball");
        defaults.add("minecraft:egg");
        defaults.add("minecraft:ender_pearl");
        defaults.add("minecraft:experience_bottle");
        defaults.add("minecraft:potion");
        defaults.add("minecraft:experience_orb");
        defaults.add("minecraft:item");
        defaults.add("minecraft:falling_block");
        defaults.add("minecraft:tnt");
        defaults.add("minecraft:marker");
        defaults.add("minecraft:armor_stand");
        defaults.add("minecraft:area_effect_cloud");
        defaults.add("minecraft:block_display");
        defaults.add("minecraft:boat");
        defaults.add("minecraft:boat_with_chest");
        defaults.add("minecraft:minecart_with_chest");
        defaults.add("minecraft:minecart_with_command_block");
        defaults.add("minecraft:dragon_fireball");
        defaults.add("minecraft:end_crystal");
        defaults.add("minecraft:evoker_fangs");
        defaults.add("minecraft:eye_of_ender");
        defaults.add("minecraft:fireball");
        defaults.add("minecraft:firework_rocket");
        defaults.add("minecraft:minecart_with_furnace");
        defaults.add("minecraft:glow_item_frame");
        defaults.add("minecraft:minecart_with_hopper");
        defaults.add("minecraft:interaction");
        defaults.add("minecraft:item_display");
        defaults.add("minecraft:item_frame");
        defaults.add("minecraft:leash_knot");
        defaults.add("minecraft:lightning_bolt");
        defaults.add("minecraft:llama_spit");
        defaults.add("minecraft:minecart");
        defaults.add("minecraft:painting");
        defaults.add("minecraft:shulker_bullet");
        defaults.add("minecraft:small_fireball");
        defaults.add("minecraft:minecart_with_monster_spawner");
        defaults.add("minecraft:text_display");
        defaults.add("minecraft:minecart_with_tnt");
        defaults.add("minecraft:wither_skull");

        return defaults;
    }

    public boolean isEntityBlacklisted(ResourceLocation location) {
        String id = location.toString();
        String namespace = location.getNamespace();

        if (entityBlacklist.contains(id)) return true;

        return entityBlacklist.contains(namespace + ":*");
    }

}