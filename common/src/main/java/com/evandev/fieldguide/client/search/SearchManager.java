package com.evandev.fieldguide.client.search;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.data.CompositeFieldGuideEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SearchManager {

    public static List<Object> searchEntries(String query) {
        String processedQuery = query.toLowerCase(Locale.ROOT).trim();
        List<Object> results = new ArrayList<>();
        if (processedQuery.isEmpty()) return results;

        boolean exactMatch = false;
        if (processedQuery.startsWith("=")) {
            exactMatch = true;
            processedQuery = processedQuery.substring(1);
        }

        if (processedQuery.startsWith("#")) return searchByTag(processedQuery.substring(1), exactMatch);
        if (processedQuery.startsWith("^")) return searchByDrop(processedQuery.substring(1), exactMatch);
        if (processedQuery.startsWith("!")) return searchByBiome(processedQuery.substring(1), exactMatch);
        if (processedQuery.startsWith("@")) return searchByModId(processedQuery.substring(1), exactMatch);

        return searchByNameOrId(processedQuery, exactMatch);
    }

    private static List<Object> searchByTag(String tagQuery, boolean exactMatch) {
        List<Object> results = new ArrayList<>();
        if (tagQuery.isEmpty()) return results;

        for (Object entry : ClientFieldGuideManager.getValidEntries()) {
            if (ClientFieldGuideManager.hideFromSearch(entry)) continue;

            Object coreEntry = entry instanceof CompositeFieldGuideEntry composite ? composite.getDisplayEntry() : entry;

            if (coreEntry instanceof EntityType<?> type) {
                var key = BuiltInRegistries.ENTITY_TYPE.getResourceKey(type);
                key.flatMap(BuiltInRegistries.ENTITY_TYPE::getHolder).ifPresent(holder -> {
                    if (holder.tags().anyMatch(tag -> matchLocation(tag.location(), tagQuery, exactMatch)))
                        results.add(entry);
                });
            } else if (coreEntry instanceof Block block) {
                var key = BuiltInRegistries.BLOCK.getResourceKey(block);
                key.flatMap(BuiltInRegistries.BLOCK::getHolder).ifPresent(holder -> {
                    if (holder.tags().anyMatch(tag -> matchLocation(tag.location(), tagQuery, exactMatch)))
                        results.add(entry);
                });
            }
        }
        return results;
    }

    private static List<Object> searchByDrop(String dropQuery, boolean exactMatch) {
        List<Object> results = new ArrayList<>();
        if (dropQuery.isEmpty()) return results;

        for (Object entry : ClientFieldGuideManager.getValidEntries()) {
            if (ClientFieldGuideManager.hideFromSearch(entry)) continue;

            List<ItemStack> drops = ClientFieldGuideManager.getInstance().getDrops(entry);
            boolean match = drops.stream().anyMatch(stack -> {
                String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                return exactMatch ? name.equals(dropQuery) : name.contains(dropQuery);
            });
            if (match) results.add(entry);
        }
        return results;
    }

    private static List<Object> searchByBiome(String biomeQuery, boolean exactMatch) {
        List<Object> results = new ArrayList<>();
        if (biomeQuery.isEmpty() || Minecraft.getInstance().level == null) return results;

        var biomeRegistry = Minecraft.getInstance().level.registryAccess().registryOrThrow(Registries.BIOME);

        for (var biomeEntry : biomeRegistry.entrySet()) {
            if (matchLocation(biomeEntry.getKey().location(), biomeQuery, exactMatch)) {
                Biome biome = biomeEntry.getValue();
                for (MobCategory cat : MobCategory.values()) {
                    for (var spawn : biome.getMobSettings().getMobs(cat).unwrap()) {
                        if (ClientFieldGuideManager.getInstance().isValidEntity(spawn.type, ModConfig.get())) {
                            if (!ClientFieldGuideManager.hideFromSearch(spawn.type) && !results.contains(spawn.type)) {
                                Object entry = ClientFieldGuideManager.getInstance().getEntryForTarget(spawn.type);
                                if (entry != null && !results.contains(entry)) results.add(entry);
                            }
                        }
                    }
                }
            }
        }

        for (Object entry : ClientFieldGuideManager.getValidEntries()) {
            if (ClientFieldGuideManager.hideFromSearch(entry)) continue;
            ResourceLocation entryId = ClientFieldGuideManager.getEntryId(entry);
            if (entryId != null) {
                for (String addition : ModConfig.get().biomeAdditions) {
                    String[] parts = addition.split("\\|");
                    if (parts.length == 2 && parts[0].equals(entryId.toString())) {
                        ResourceLocation biomeId = new ResourceLocation(parts[1]);
                        if (matchLocation(biomeId, biomeQuery, exactMatch)) {
                            if (!results.contains(entry)) results.add(entry);
                        }
                    }
                }
            }
        }

        for (Object entry : new ArrayList<>(results)) {
            ResourceLocation entryId = ClientFieldGuideManager.getEntryId(entry);
            if (entryId != null) {
                for (String removal : ModConfig.get().biomeRemovals) {
                    String[] parts = removal.split("\\|");
                    if (parts.length == 2 && parts[0].equals(entryId.toString())) {
                        ResourceLocation biomeId = new ResourceLocation(parts[1]);
                        if (matchLocation(biomeId, biomeQuery, exactMatch)) {
                            results.remove(entry);
                        }
                    }
                }
            }
        }

        return results;
    }

    private static List<Object> searchByModId(String modQuery, boolean exactMatch) {
        List<Object> results = new ArrayList<>();
        if (modQuery.isEmpty()) return results;

        for (Object entry : ClientFieldGuideManager.getValidEntries()) {
            if (ClientFieldGuideManager.hideFromSearch(entry)) continue;
            ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
            if (id != null) {
                String namespace = id.getNamespace().toLowerCase(Locale.ROOT);
                if (exactMatch ? namespace.equals(modQuery) : namespace.contains(modQuery)) results.add(entry);
            }
        }
        return results;
    }

    private static List<Object> searchByNameOrId(String query, boolean exactMatch) {
        List<Object> results = new ArrayList<>();
        for (Object entry : ClientFieldGuideManager.getValidEntries()) {
            if (ClientFieldGuideManager.hideFromSearch(entry)) continue;
            ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
            if (id == null) continue;

            String name = ClientFieldGuideManager.getDefaultName(entry).toLowerCase(Locale.ROOT);

            boolean match = exactMatch ?
                    (name.equals(query) || id.getPath().equals(query)) :
                    (name.contains(query) || id.getPath().contains(query));

            if (match) results.add(entry);
        }
        return results;
    }

    private static boolean matchLocation(ResourceLocation loc, String query, boolean exactMatch) {
        String full = loc.toString().toLowerCase(Locale.ROOT);
        String path = loc.getPath().toLowerCase(Locale.ROOT);
        return exactMatch ? (full.equals(query) || path.equals(query)) : (full.contains(query) || path.contains(query));
    }
}