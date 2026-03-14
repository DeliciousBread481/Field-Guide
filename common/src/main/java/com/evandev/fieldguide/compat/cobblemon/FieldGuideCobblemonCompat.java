package com.evandev.fieldguide.compat.cobblemon;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.CategoryEntry;
import com.evandev.fieldguide.platform.Services;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class FieldGuideCobblemonCompat {
    public static final String MOD_ID = "cobblemon";
    private static final String POKEMON_PATH = "pokemon";

    private static final Map<ResourceLocation, LivingEntity> DUMMY_CACHE = new HashMap<>();

    private FieldGuideCobblemonCompat() {
    }

    public static void clearCache() {
        DUMMY_CACHE.clear();
    }

    /**
     * Creates a dummy entity and morphs it into the target species/form.
     */
    public static LivingEntity getDummyPokemon(ResourceLocation id, Level level) {
        if (DUMMY_CACHE.containsKey(id)) {
            return DUMMY_CACHE.get(id);
        }

        String path = id.getPath();
        if (!path.startsWith("cobblemon/")) return null;

        String speciesAndForm = path.substring("cobblemon/".length());
        String species = speciesAndForm;
        String form = "standard";

        int underscoreIndex = speciesAndForm.lastIndexOf('_');
        if (underscoreIndex != -1) {
            species = speciesAndForm.substring(0, underscoreIndex);
            form = speciesAndForm.substring(underscoreIndex + 1);
        }

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(MOD_ID, POKEMON_PATH));

        Entity entity = type.create(level);
        if (!(entity instanceof LivingEntity living)) return null;

        try {
            Object pokemon = entity.getClass().getMethod("getPokemon").invoke(entity);
            Class<?> speciesClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonSpecies");
            Object speciesRegistry = speciesClass.getField("INSTANCE").get(null);
            Object speciesObj = speciesRegistry.getClass().getMethod("getByIdentifier", ResourceLocation.class)
                    .invoke(speciesRegistry, new ResourceLocation(MOD_ID, species));

            if (speciesObj != null) {
                for (java.lang.reflect.Method m : pokemon.getClass().getMethods()) {
                    if (m.getName().equals("setSpecies") && m.getParameterCount() == 1) {
                        m.invoke(pokemon, speciesObj);
                        break;
                    }
                }

                Object formsList = speciesObj.getClass().getMethod("getForms").invoke(speciesObj);
                if (formsList instanceof java.util.Collection<?> forms) {
                    Object targetForm = null;
                    for (Object f : forms) {
                        String fName = (String) f.getClass().getMethod("getName").invoke(f);
                        if (fName.equalsIgnoreCase(form)) {
                            targetForm = f;
                            break;
                        }
                    }
                    if (targetForm == null) {
                        targetForm = speciesObj.getClass().getMethod("getStandardForm").invoke(speciesObj);
                    }
                    if (targetForm != null) {
                        for (java.lang.reflect.Method m : pokemon.getClass().getMethods()) {
                            if (m.getName().equals("setForm") && m.getParameterCount() == 1) {
                                m.invoke(pokemon, targetForm);
                                break;
                            }
                        }
                    }
                }

                pokemon.getClass().getMethod("updateAspects").invoke(pokemon);
                entity.refreshDimensions();
            }
        } catch (Exception e) {
            Constants.LOG.error("Failed to construct Cobblemon dummy entity for Field Guide ID: {}", id, e);
        }

        DUMMY_CACHE.put(id, living);
        return living;
    }

    public static boolean isPokemon(Entity entity) {
        if (entity == null || !Services.PLATFORM.isModLoaded(MOD_ID)) return false;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return MOD_ID.equals(id.getNamespace()) && POKEMON_PATH.equals(id.getPath());
    }

    public static boolean isPokemonType(EntityType<?> type) {
        if (!Services.PLATFORM.isModLoaded(MOD_ID)) return false;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return MOD_ID.equals(id.getNamespace());
    }

    public static ResourceLocation getPokemonEntryId(Entity entity) {
        if (!isPokemon(entity)) return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());

        try {
            CompoundTag tag = new CompoundTag();
            entity.saveWithoutId(tag);

            String species = "";
            String form = "standard";

            if (tag.contains("Pokemon", CompoundTag.TAG_COMPOUND)) {
                CompoundTag pokemonTag = tag.getCompound("Pokemon");
                species = pokemonTag.contains("Species") ? pokemonTag.getString("Species") : pokemonTag.getString("species");
                form = pokemonTag.contains("Form") ? pokemonTag.getString("Form") : pokemonTag.getString("form");
            } else if (tag.contains("species", CompoundTag.TAG_STRING)) {
                species = tag.getString("species");
                if (tag.contains("form", CompoundTag.TAG_STRING)) form = tag.getString("form");
            }

            if (!species.isEmpty()) {
                ResourceLocation speciesId = new ResourceLocation(species);
                if (form.isBlank()) form = "standard";
                return new ResourceLocation("fieldguide", "cobblemon/" + speciesId.getPath() + "_" + form.toLowerCase());
            }
        } catch (Exception e) {
            Constants.LOG.error("Failed to parse Cobblemon NBT for Field Guide ID", e);
        }

        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
    }

    /**
     * Reads all loaded Cobblemon species JSONs and injects them as virtual entries.
     */
    public static void injectCategory(Map<ResourceLocation, Category> categories, ResourceManager resourceManager) {
        ResourceLocation cobblemonCatId = new ResourceLocation(Constants.MOD_ID, "cobblemon");
        Category cobblemonCategory = categories.computeIfAbsent(cobblemonCatId, Category::new);

        if (cobblemonCategory.getEntries().isEmpty()) {
            cobblemonCategory.setIcon(new ResourceLocation(Constants.MOD_ID, "textures/gui/icons/pokeball.png"));
            cobblemonCategory.setSortIndex(100);

            Map<ResourceLocation, List<Resource>> speciesFiles = resourceManager.listResourceStacks(
                    "species", id -> id.getNamespace().equals(MOD_ID) && id.getPath().endsWith(".json")
            );

            for (Map.Entry<ResourceLocation, List<Resource>> entry : speciesFiles.entrySet()) {
                for (Resource resource : entry.getValue()) {
                    try (Reader reader = resource.openAsReader()) {
                        JsonObject json = GsonHelper.parse(reader);

                        if (json.has("name")) {
                            String speciesName = json.get("name").getAsString().toLowerCase();

                            ResourceLocation entryId = new ResourceLocation("fieldguide", "cobblemon/" + speciesName + "_standard");
                            cobblemonCategory.addEntry(new CategoryEntry(CategoryEntry.CategoryType.ENTRY, entryId, entryId, null, null, null, null));

                            if (json.has("forms")) {
                                JsonArray forms = json.getAsJsonArray("forms");
                                for (JsonElement formEl : forms) {
                                    String formName = formEl.getAsJsonObject().get("name").getAsString().toLowerCase();
                                    if (!formName.equals("standard")) {
                                        ResourceLocation formId = new ResourceLocation("fieldguide", "cobblemon/" + speciesName + "_" + formName);
                                        cobblemonCategory.addEntry(new CategoryEntry(CategoryEntry.CategoryType.ENTRY, formId, formId, null, null, null, null));
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
}