package com.evandev.fieldguide.compat.cobblemon;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.ModDataComponents;
import com.evandev.fieldguide.api.EntryKind;
import com.evandev.fieldguide.api.GuideEntry;
import com.evandev.fieldguide.api.variant.VariantDef;
import com.evandev.fieldguide.api.variant.VariantProvider;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.variant.FieldGuideVariantManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.io.Reader;
import java.util.*;

public final class FieldGuideCobblemonCompat {
    public static final String MOD_ID = "cobblemon";
    private static final String POKEMON_PATH = "pokemon";

    private static final Map<ResourceLocation, LivingEntity> DUMMY_CACHE = new HashMap<>();
    private static final Map<String, LivingEntity> VARIANT_DUMMY_CACHE = new HashMap<>();
    private static final Map<ResourceLocation, String> FORM_CACHE = new HashMap<>();
    private static final Map<ResourceLocation, List<ItemStack>> COBBLEMON_DROPS_CACHE = new HashMap<>();
    private static final List<Object> AUTO_POPULATE_CACHE = new ArrayList<>();
    private static final Map<ResourceLocation, Set<String>> COBBLEMON_BIOMES_CACHE = new HashMap<>();

    static {
        FieldGuideVariantManager.registerProvider(PokemonEntity.class, new VariantProvider<>() {
            @Override
            public List<VariantDef> getVariants(PokemonEntity entity) {
                Species species = entity.getPokemon().getSpecies();
                List<VariantDef> variants = new ArrayList<>();
                for (FormData form : species.getForms()) {
                    variants.add(new VariantDef(form.getName(), form.getName()));
                }
                return variants;
            }

            @Override
            public void apply(PokemonEntity entity, VariantDef def) {
                ResourceLocation id = getPokemonEntryId(entity);
                String formName = def.id();
                FORM_CACHE.put(id, formName);
                DUMMY_CACHE.remove(id);
            }

            @Override
            public VariantDef getCurrent(PokemonEntity entity) {
                String formName = entity.getPokemon().getForm().getName();
                return new VariantDef(formName, formName);
            }
        });
    }

    private FieldGuideCobblemonCompat() {
    }

    public static void clearCache() {
        DUMMY_CACHE.clear();
        VARIANT_DUMMY_CACHE.clear();
    }

    public static String getFormForEntry(ResourceLocation id) {
        return FORM_CACHE.getOrDefault(id, getDefaultForm(id));
    }

    private static String getDefaultForm(ResourceLocation id) {
        String path = id.getPath();
        int idx = path.lastIndexOf("cobblemon/");
        String speciesAndForm = idx != -1 ? path.substring(idx + "cobblemon/".length()) : path;

        int underscoreIndex = speciesAndForm.lastIndexOf('_');
        if (underscoreIndex != -1) {
            return speciesAndForm.substring(underscoreIndex + 1);
        }
        return "standard";
    }

    public static String getSpeciesName(ResourceLocation id) {
        String path = id.getPath();
        if (!path.startsWith("cobblemon/")) return path;
        String speciesAndForm = path.substring("cobblemon/".length());
        int underscoreIndex = speciesAndForm.lastIndexOf('_');
        if (underscoreIndex != -1) {
            return speciesAndForm.substring(0, underscoreIndex);
        }
        return speciesAndForm;
    }

    public static LivingEntity getDummyVariant(ResourceLocation id, String variantName, Level level) {
        String cacheKey = id.toString() + "#" + variantName;
        if (VARIANT_DUMMY_CACHE.containsKey(cacheKey)) {
            return VARIANT_DUMMY_CACHE.get(cacheKey);
        }

        String speciesName = getSpeciesName(id);

        try {
            StringBuilder propsStr = new StringBuilder("species=" + speciesName);
            if (!variantName.equals("standard")) {
                Species species = PokemonSpecies.getByIdentifier(ResourceLocation.fromNamespaceAndPath(MOD_ID, speciesName));
                if (species != null) {
                    for (FormData f : species.getForms()) {
                        if (f.getName().equals(variantName)) {
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

            VARIANT_DUMMY_CACHE.put(cacheKey, pokemonEntity);
            return pokemonEntity;
        } catch (Exception e) {
            Constants.LOG.error("Failed to construct Cobblemon dummy variant for Field Guide ID: {} variant: {}", id, variantName, e);
        }

        return null;
    }

    public static List<String> getVariantIds(ResourceLocation entryId) {
        String speciesName = getSpeciesName(entryId);
        Species species = PokemonSpecies.getByIdentifier(ResourceLocation.fromNamespaceAndPath(MOD_ID, speciesName));
        if (species != null) {
            return species.getForms().stream().map(FormData::getName).toList();
        }
        return List.of();
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
                Species species = PokemonSpecies.getByIdentifier(ResourceLocation.fromNamespaceAndPath(MOD_ID, speciesName));
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
            return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "cobblemon/" + speciesName + "_standard");
        } catch (Exception e) {
            Constants.LOG.error("Failed to parse Cobblemon properties for Field Guide ID", e);
        }

        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
    }

    public static void populateCache(ResourceManager resourceManager) {
        AUTO_POPULATE_CACHE.clear();
        COBBLEMON_DROPS_CACHE.clear();

        Map<ResourceLocation, List<Resource>> speciesFiles = resourceManager.listResourceStacks(
                "species", id -> id.getNamespace().equals(MOD_ID) && id.getPath().endsWith(".json")
        );

        List<Map.Entry<GuideEntry, Integer>> sortedEntries = new ArrayList<>();

        for (Map.Entry<ResourceLocation, List<Resource>> entry : speciesFiles.entrySet()) {
            for (Resource resource : entry.getValue()) {
                try (Reader reader = resource.openAsReader()) {
                    JsonObject json = GsonHelper.parse(reader);

                    if (json.has("name")) {
                        String speciesName = json.get("name").getAsString().toLowerCase(Locale.ROOT);
                        ResourceLocation entryId = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "cobblemon/" + speciesName + "_standard");
                        int pokedexNumber = json.has("nationalPokedexNumber") ? json.get("nationalPokedexNumber").getAsInt() : Integer.MAX_VALUE;

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

                                        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemStr));

                                        if (item != Items.AIR) {
                                            ItemStack stack = new ItemStack(item, quantity);
                                            stack.set(ModDataComponents.DROP_CHANCE.get(), chance);
                                            drops.add(stack);
                                        }
                                    }
                                }
                                if (!drops.isEmpty()) {
                                    COBBLEMON_DROPS_CACHE.put(entryId, drops);
                                }
                            }
                        }

                        sortedEntries.add(new AbstractMap.SimpleEntry<>(
                                new GuideEntry(entryId, null, null, EntryKind.NORMAL, true, false, null, null, null, new com.evandev.fieldguide.api.VirtualData("cobblemon"), com.evandev.fieldguide.api.EntryUnlockData.DEFAULT),
                                pokedexNumber
                        ));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        sortedEntries.sort(Map.Entry.comparingByValue());
        for (Map.Entry<GuideEntry, Integer> sortedEntry : sortedEntries) {
            AUTO_POPULATE_CACHE.add(sortedEntry.getKey());
        }

        Map<ResourceLocation, List<Resource>> spawnPoolFiles = resourceManager.listResourceStacks(
                "spawn_pool_world", id -> id.getNamespace().equals(MOD_ID) && id.getPath().endsWith(".json")
        );

        if (spawnPoolFiles.isEmpty()) {
            spawnPoolFiles = resourceManager.listResourceStacks(
                    "spawn_pool", id -> id.getNamespace().equals(MOD_ID) && id.getPath().endsWith(".json")
            );
        }

        for (Map.Entry<ResourceLocation, List<Resource>> entry : spawnPoolFiles.entrySet()) {
            for (Resource resource : entry.getValue()) {
                try (Reader reader = resource.openAsReader()) {
                    JsonObject json = GsonHelper.parse(reader);
                    if (json.has("spawns")) {
                        JsonArray spawns = json.getAsJsonArray("spawns");
                        for (JsonElement spawnEl : spawns) {
                            JsonObject spawn = spawnEl.getAsJsonObject();
                            if (spawn.has("pokemon")) {
                                String pokemonStr = spawn.get("pokemon").getAsString().toLowerCase(Locale.ROOT);
                                String speciesName = pokemonStr.split(" ")[0];
                                ResourceLocation entryId = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "cobblemon/" + speciesName + "_standard");

                                if (spawn.has("condition")) {
                                    JsonObject condition = spawn.getAsJsonObject("condition");
                                    if (condition.has("biomes")) {
                                        JsonArray biomes = condition.getAsJsonArray("biomes");
                                        for (JsonElement biomeEl : biomes) {
                                            COBBLEMON_BIOMES_CACHE.computeIfAbsent(entryId, k -> new HashSet<>()).add(biomeEl.getAsString());
                                        }
                                    }
                                }
                                if (spawn.has("anticondition")) {
                                    JsonObject anti = spawn.getAsJsonObject("anticondition");
                                    if (anti.has("biomes")) {
                                        JsonArray biomes = anti.getAsJsonArray("biomes");
                                        for (JsonElement biomeEl : biomes) {
                                            Set<String> set = COBBLEMON_BIOMES_CACHE.get(entryId);
                                            if (set != null) set.remove(biomeEl.getAsString());
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static List<Object> getAutoPopulateEntries() {
        return AUTO_POPULATE_CACHE;
    }

    public static void playPokemonCry(Entity entity) {
        if (entity instanceof PokemonEntity pokemonEntity) {
            String speciesName = pokemonEntity.getPokemon().getSpecies().getResourceIdentifier().getPath();
            ResourceLocation cryId = ResourceLocation.fromNamespaceAndPath(MOD_ID, "pokemon." + speciesName + ".cry");
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(cryId), 1.0F, 1.0F));
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


    public static List<ItemStack> getCobblemonDrops(Object entry) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        if (id == null) return List.of();

        if (COBBLEMON_DROPS_CACHE.containsKey(id)) {
            return COBBLEMON_DROPS_CACHE.get(id);
        }

        String speciesName = getSpeciesName(id);
        ResourceLocation standardId = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "cobblemon/" + speciesName + "_standard");
        if (COBBLEMON_DROPS_CACHE.containsKey(standardId)) {
            return COBBLEMON_DROPS_CACHE.get(standardId);
        }

        return List.of();
    }

    public static List<ResourceLocation> getCobblemonBiomes(Object entry) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        if (id == null) return List.of();

        Set<String> conditions = COBBLEMON_BIOMES_CACHE.get(id);
        if (conditions == null) {
            String speciesName = getSpeciesName(id);
            ResourceLocation standardId = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "cobblemon/" + speciesName + "_standard");
            conditions = COBBLEMON_BIOMES_CACHE.get(standardId);
        }

        if (conditions == null) return List.of();

        List<ResourceLocation> results = new ArrayList<>();
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            var biomeRegistry = connection.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
            for (var biomeEntry : biomeRegistry.entrySet()) {
                if (isCobblemonBiomeMatch(entry, biomeEntry.getKey().location(), biomeRegistry.getHolderOrThrow(biomeEntry.getKey()))) {
                    results.add(biomeEntry.getKey().location());
                }
            }
        }
        return results;
    }

    public static boolean isCobblemonBiomeMatch(Object entry, ResourceLocation biomeId, Holder<Biome> holder) {
        ResourceLocation id = ClientFieldGuideManager.getEntryId(entry);
        if (id == null) return false;

        Set<String> conditions = COBBLEMON_BIOMES_CACHE.get(id);
        if (conditions == null) {
            String speciesName = getSpeciesName(id);
            ResourceLocation standardId = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "cobblemon/" + speciesName + "_standard");
            conditions = COBBLEMON_BIOMES_CACHE.get(standardId);
        }

        if (conditions == null) return false;

        for (String condition : conditions) {
            if (condition.startsWith("#")) {
                String tagPath = condition.substring(1);
                TagKey<Biome> tagKey = TagKey.create(Registries.BIOME, ResourceLocation.parse(tagPath));
                if (holder.is(tagKey)) return true;
            } else {
                if (biomeId.toString().equals(condition) || biomeId.getPath().equals(condition)) return true;
            }
        }

        return false;
    }
}
