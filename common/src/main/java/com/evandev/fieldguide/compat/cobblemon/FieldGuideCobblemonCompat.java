package com.evandev.fieldguide.compat.cobblemon;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.gui.util.IconCacheManager;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.CategoryEntry;
import com.evandev.fieldguide.data.CompositeFieldGuideEntry;
import com.evandev.fieldguide.platform.Services;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.io.Reader;
import java.util.*;

public final class FieldGuideCobblemonCompat {
    public static final String MOD_ID = "cobblemon";
    private static final String POKEMON_PATH = "pokemon";

    private static final Map<ResourceLocation, LivingEntity> DUMMY_CACHE = new HashMap<>();
    private static final Map<ResourceLocation, String> FORM_CACHE = new HashMap<>();
    private static final Map<ResourceLocation, List<ItemStack>> COBBLEMON_DROPS_CACHE = new HashMap<>();

    private FieldGuideCobblemonCompat() {
    }

    public static void clearCache() {
        DUMMY_CACHE.clear();
    }

    private static String getDefaultForm(ResourceLocation id) {
        String path = id.getPath();
        if (!path.startsWith("cobblemon/")) return "standard";
        String speciesAndForm = path.substring("cobblemon/".length());
        int underscoreIndex = speciesAndForm.lastIndexOf('_');
        if (underscoreIndex != -1) {
            return speciesAndForm.substring(underscoreIndex + 1);
        }
        return "standard";
    }

    private static String getSpeciesName(ResourceLocation id) {
        String path = id.getPath();
        if (!path.startsWith("cobblemon/")) return path;
        String speciesAndForm = path.substring("cobblemon/".length());
        int underscoreIndex = speciesAndForm.lastIndexOf('_');
        if (underscoreIndex != -1) {
            return speciesAndForm.substring(0, underscoreIndex);
        }
        return speciesAndForm;
    }

    /**
     * Used to provide health, drops, and general entity data.
     */
    public static LivingEntity getDummyPokemon(ResourceLocation id, Level level) {
        if (DUMMY_CACHE.containsKey(id)) {
            return DUMMY_CACHE.get(id);
        }

        String speciesName = getSpeciesName(id);
        String formName = FORM_CACHE.getOrDefault(id, getDefaultForm(id));

        try {
            StringBuilder propsStr = new StringBuilder("species=" + speciesName);
            if (!formName.equals("standard")) {
                Species species = PokemonSpecies.INSTANCE.getByIdentifier(new ResourceLocation(MOD_ID, speciesName));
                if (species != null) {
                    for (FormData f : species.getForms()) {
                        if (f.getName().equals(formName)) {
                            for (String aspect : f.getAspects()) {
                                propsStr.append(" ").append(aspect);
                            }
                            break;
                        }
                    }
                }
            }

            PokemonProperties props = PokemonProperties.Companion.parse(propsStr.toString(), " ", "=");
            PokemonEntity pokemonEntity = props.createEntity(level);
            pokemonEntity.setNoAi(true);

            pokemonEntity.getEntityData().set(PokemonEntity.getASPECTS(), pokemonEntity.getPokemon().getAspects());
            pokemonEntity.getEntityData().set(PokemonEntity.getSPECIES(), pokemonEntity.getPokemon().getSpecies().getResourceIdentifier().toString());

            pokemonEntity.setTicksLived(25);
            pokemonEntity.setYRot(0.0F);
            pokemonEntity.yRotO = 0.0F;
            pokemonEntity.setXRot(0.0F);
            pokemonEntity.xRotO = 0.0F;
            pokemonEntity.setYHeadRot(0.0F);
            pokemonEntity.yHeadRot = 0.0F;
            pokemonEntity.yHeadRotO = 0.0F;
            pokemonEntity.setYBodyRot(0.0F);
            pokemonEntity.yBodyRot = 0.0F;
            pokemonEntity.yBodyRotO = 0.0F;

            DUMMY_CACHE.put(id, pokemonEntity);
            return pokemonEntity;
        } catch (Exception e) {
            Constants.LOG.error("Failed to construct Cobblemon dummy entity for Field Guide ID: {}", id, e);
        }

        return null;
    }

    public static ResourceLocation getPokemonEntryId(Entity entity) {
        if (!(entity instanceof PokemonEntity pokemonEntity)) {
            return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        }

        try {
            Pokemon pokemon = pokemonEntity.getPokemon();
            Species species = pokemon.getSpecies();

            String speciesName = species.getResourceIdentifier().getPath();
            return new ResourceLocation("fieldguide", "cobblemon/" + speciesName + "_standard");
        } catch (Exception e) {
            Constants.LOG.error("Failed to parse Cobblemon properties for Field Guide ID", e);
        }

        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
    }

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

                            if (json.has("drops")) {
                                JsonObject dropsObj = json.getAsJsonObject("drops");
                                if (dropsObj.has("entries")) {
                                    List<ItemStack> drops = new ArrayList<>();
                                    for (JsonElement dropElem : dropsObj.getAsJsonArray("entries")) {
                                        JsonObject dropJson = dropElem.getAsJsonObject();
                                        if (dropJson.has("item")) {
                                            String itemStr = dropJson.get("item").getAsString();
                                            float chance = dropJson.has("percentage") ? dropJson.get("percentage").getAsFloat() : 100f;
                                            int quantity = dropJson.has("quantity") ? dropJson.get("quantity").getAsInt() : 1;

                                            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(itemStr));
                                            if (item != Items.AIR) {
                                                ItemStack stack = new ItemStack(item, quantity);
                                                stack.getOrCreateTag().putFloat("FieldGuideDropChance", chance);
                                                drops.add(stack);
                                            }
                                        }
                                    }
                                    if (!drops.isEmpty()) {
                                        COBBLEMON_DROPS_CACHE.put(entryId, drops);
                                    }
                                }
                            }

                            cobblemonCategory.addEntry(new CategoryEntry(CategoryEntry.CategoryType.ENTRY, entryId, entryId, null, null, null, null));
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
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

    public static void cycleCobblemonForm(ResourceLocation id, Level level) {
        String speciesName = getSpeciesName(id);
        Species species = PokemonSpecies.INSTANCE.getByIdentifier(new ResourceLocation(MOD_ID, speciesName));
        if (species == null) return;

        List<FormData> forms = species.getForms();
        if (forms.isEmpty()) return;

        String currentFormName = FORM_CACHE.getOrDefault(id, getDefaultForm(id));
        FormData currentForm = forms.stream().filter(f -> f.getName().equals(currentFormName)).findFirst().orElse(species.getStandardForm());

        int currentIndex = forms.indexOf(currentForm);
        int nextIndex = (currentIndex + 1) % forms.size();
        FormData nextForm = forms.get(nextIndex);

        FORM_CACHE.put(id, nextForm.getName());

        DUMMY_CACHE.remove(id);

        IconCacheManager.clearCache();

        getDummyPokemon(id, level);
    }

    public static List<ItemStack> getCobblemonDrops(Object entry) {
        ResourceLocation id;
        if (entry instanceof CompositeFieldGuideEntry comp) {
            id = comp.id();
        } else {
            id = ClientFieldGuideManager.getEntryId(entry);
        }
        if (id == null) return List.of();

        if (COBBLEMON_DROPS_CACHE.containsKey(id)) {
            return COBBLEMON_DROPS_CACHE.get(id);
        }

        String speciesName = getSpeciesName(id);
        ResourceLocation standardId = new ResourceLocation("fieldguide", "cobblemon/" + speciesName + "_standard");
        if (COBBLEMON_DROPS_CACHE.containsKey(standardId)) {
            return COBBLEMON_DROPS_CACHE.get(standardId);
        }

        return List.of();
    }
}