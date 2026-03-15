package com.evandev.fieldguide.compat.cobblemon;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.CategoryEntry;
import com.evandev.fieldguide.platform.Services;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.io.Reader;
import java.util.*;

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
        String speciesName = speciesAndForm;
        String formName = "standard";

        int underscoreIndex = speciesAndForm.lastIndexOf('_');
        if (underscoreIndex != -1) {
            speciesName = speciesAndForm.substring(0, underscoreIndex);
            formName = speciesAndForm.substring(underscoreIndex + 1);
        }

        try {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(new ResourceLocation(MOD_ID, POKEMON_PATH));
            Entity entity = type.create(level);

            if (!(entity instanceof PokemonEntity pokemonEntity)) return null;

            Pokemon pokemon = pokemonEntity.getPokemon();

            Species species = PokemonSpecies.INSTANCE.getByIdentifier(new ResourceLocation(MOD_ID, speciesName));

            if (species != null) {
                pokemon.setSpecies(species);

                final String targetFormName = formName;
                FormData targetForm = species.getForms().stream()
                        .filter(f -> f.getName().equalsIgnoreCase(targetFormName))
                        .findFirst()
                        .orElse(species.getStandardForm());

                pokemon.setForm(targetForm);
                pokemon.updateAspects();
            }

            pokemonEntity.setYRot(0.0F);
            pokemonEntity.yRotO = 0.0F;
            pokemonEntity.setXRot(0.0F);
            pokemonEntity.xRotO = 0.0F;
            pokemonEntity.setYBodyRot(0.0F);
            pokemonEntity.yBodyRot = 0.0F;
            pokemonEntity.yBodyRotO = 0.0F;
            pokemonEntity.setYHeadRot(0.0F);
            pokemonEntity.yHeadRot = 0.0F;
            pokemonEntity.yHeadRotO = 0.0F;
            pokemonEntity.setUUID(UUID.nameUUIDFromBytes(id.toString().getBytes()));

            pokemonEntity.setNoAi(true);
            pokemonEntity.refreshDimensions();

            DUMMY_CACHE.put(id, pokemonEntity);
            return pokemonEntity;

        } catch (Exception e) {
            Constants.LOG.error("Failed to construct Cobblemon dummy entity for Field Guide ID: {}", id, e);
        }

        return null;
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
        if (!(entity instanceof PokemonEntity pokemonEntity)) {
            return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        }

        try {
            Pokemon pokemon = pokemonEntity.getPokemon();
            Species species = pokemon.getSpecies();
            FormData form = pokemon.getForm();

            String speciesName = species.getResourceIdentifier().getPath();
            String formName = form.getName();
            if (formName.isBlank()) formName = "standard";

            return new ResourceLocation("fieldguide", "cobblemon/" + speciesName + "_" + formName.toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            Constants.LOG.error("Failed to parse Cobblemon properties for Field Guide ID", e);
        }

        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
    }

    public static void cycleCobblemonForm(LivingEntity dummyEntity) {
        if (!(dummyEntity instanceof PokemonEntity pokemonEntity)) return;

        Pokemon pokemon = pokemonEntity.getPokemon();
        Species species = pokemon.getSpecies();
        List<FormData> forms = species.getForms();

        if (forms.isEmpty()) return;

        int currentIndex = forms.indexOf(pokemon.getForm());
        int nextIndex = (currentIndex + 1) % forms.size();

        pokemon.setForm(forms.get(nextIndex));
        pokemon.updateAspects();
        pokemonEntity.refreshDimensions();
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
                            String speciesName = json.get("name").getAsString().toLowerCase(Locale.ROOT);

                            ResourceLocation entryId = new ResourceLocation("fieldguide", "cobblemon/" + speciesName + "_standard");
                            cobblemonCategory.addEntry(new CategoryEntry(CategoryEntry.CategoryType.ENTRY, entryId, entryId, null, null, null, null));

                            if (json.has("forms")) {
                                JsonArray forms = json.getAsJsonArray("forms");
                                for (JsonElement formEl : forms) {
                                    String formName = formEl.getAsJsonObject().get("name").getAsString().toLowerCase(Locale.ROOT);
                                    if (!formName.equals("standard") && !formName.equals("normal") && !formName.equals("base")) {
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