package com.evandev.fieldguide.util;

import com.evandev.fieldguide.api.DatapackVariant;
import com.evandev.fieldguide.api.VariantDef;
import com.evandev.fieldguide.api.VariantProvider;
import com.evandev.fieldguide.compat.cobblemon.FieldGuideCobblemonCompat;
import com.evandev.fieldguide.platform.Services;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FieldGuideVariantManager {

    private static final Map<Class<?>, VariantProvider<?>> PROVIDERS = new HashMap<>();
    private static final Map<String, List<VariantDef>> VARIANT_CACHE = new HashMap<>();
    private static final Map<String, List<VariantDef>> ENTITY_TYPE_VARIANT_CACHE = new HashMap<>();
    private static final Map<ResourceLocation, List<DatapackVariant>> DATAPACK_VARIANTS = new HashMap<>();

    static {
        registerProvider(Sheep.class, new VariantProvider<>() {
            @Override
            public List<VariantDef> getVariants(Sheep entity) {
                return Arrays.stream(DyeColor.values())
                        .map(c -> new VariantDef(c.getName(), c))
                        .toList();
            }

            @Override
            public void apply(Sheep entity, VariantDef def) {
                entity.setColor((DyeColor) def.value());
            }

            @Override
            public VariantDef getCurrent(Sheep entity) {
                return new VariantDef(entity.getColor().getName(), entity.getColor());
            }
        });
    }

    public static <T extends Mob> void registerProvider(Class<T> entityClass, VariantProvider<T> provider) {
        PROVIDERS.put(entityClass, provider);
    }

    public static void setDatapackVariants(Map<ResourceLocation, List<DatapackVariant>> variants) {
        DATAPACK_VARIANTS.clear();
        DATAPACK_VARIANTS.putAll(variants);
        VARIANT_CACHE.clear();
        ENTITY_TYPE_VARIANT_CACHE.clear();
    }

    @SuppressWarnings("unchecked")
    public static <T extends Mob> VariantProvider<T> getProvider(Entity entity) {
        if (!(entity instanceof Mob mob)) return null;

        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (DATAPACK_VARIANTS.containsKey(entityId)) {
            return (VariantProvider<T>) getDatapackProvider(entityId);
        }

        VariantProvider<T> provider = getProvider((Class<T>) mob.getClass());
        if (provider == null) {
            provider = (VariantProvider<T>) getReflectionProvider(mob);
            if (provider != null) {
                registerProvider((Class<T>) mob.getClass(), provider);
            }
        }
        return provider;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Mob> VariantProvider<T> getProvider(Class<T> entityClass) {
        if (VillagerDataHolder.class.isAssignableFrom(entityClass)) {
            return (VariantProvider<T>) getVillagerProvider();
        }

        Class<?> clazz = entityClass;
        while (clazz != null && clazz != Mob.class && clazz != Object.class) {
            if (PROVIDERS.containsKey(clazz)) {
                return (VariantProvider<T>) PROVIDERS.get(clazz);
            }
            clazz = clazz.getSuperclass();
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public static List<VariantDef> getVariants(Entity entity) {
        if (!(entity instanceof Mob mob)) return List.of();
        Class<?> clazz = mob.getClass();
        String cacheKey = clazz.getName();

        if (Services.PLATFORM.isModLoaded("cobblemon") && FieldGuideCobblemonCompat.isPokemon(entity)) {
            ResourceLocation id = FieldGuideCobblemonCompat.getPokemonEntryId(entity);
            cacheKey += ":" + id;

            if (VARIANT_CACHE.containsKey(cacheKey)) {
                return VARIANT_CACHE.get(cacheKey);
            }

            List<VariantDef> variants = FieldGuideCobblemonCompat.getVariantIds(id).stream()
                    .map(name -> new VariantDef(name, name))
                    .toList();

            VARIANT_CACHE.put(cacheKey, variants);
            return variants;
        }

        if (VARIANT_CACHE.containsKey(cacheKey)) {
            return VARIANT_CACHE.get(cacheKey);
        }

        VariantProvider<Mob> provider = getProvider(entity);
        boolean fromReflection = false;
        if (provider == null) {
            provider = getReflectionProvider(mob);
            fromReflection = true;
        }

        if (provider != null) {
            List<VariantDef> variants = provider.getVariants(mob);
            VARIANT_CACHE.put(cacheKey, variants);
            if (fromReflection) {
                registerProvider((Class<Mob>) clazz, provider);
            }
            return variants;
        }

        return List.of();
    }

    public static List<VariantDef> getVariants(EntityType<?> type, Level level) {
        String cacheKey = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();

        if (level != null) {
            try {
                Entity entity = type.create(level);
                if (entity instanceof Mob mob) {
                    if (Services.PLATFORM.isModLoaded("cobblemon") && FieldGuideCobblemonCompat.isPokemon(entity)) {
                        ResourceLocation id = FieldGuideCobblemonCompat.getPokemonEntryId(entity);
                        cacheKey += ":" + id;
                    }

                    if (ENTITY_TYPE_VARIANT_CACHE.containsKey(cacheKey)) {
                        return ENTITY_TYPE_VARIANT_CACHE.get(cacheKey);
                    }

                    List<VariantDef> variants = getVariants(mob);
                    ENTITY_TYPE_VARIANT_CACHE.put(cacheKey, variants);
                    return variants;
                }
            } catch (Exception ignored) {
            }
        }

        return List.of();
    }

    public static List<String> getVariantIds(EntityType<?> type, Level level) {
        return getVariants(type, level).stream().map(VariantDef::id).toList();
    }

    private static VariantProvider<Mob> getDatapackProvider(ResourceLocation entityId) {
        List<DatapackVariant> variants = DATAPACK_VARIANTS.get(entityId);
        if (variants == null) return null;
        return new VariantProvider<>() {
            @Override
            public List<VariantDef> getVariants(Mob entity) {
                return variants.stream()
                        .map(v -> new VariantDef(v.id(), v.nbt()))
                        .toList();
            }

            @Override
            public void apply(Mob entity, VariantDef def) {
                if (def.value() instanceof CompoundTag nbt) {
                    CompoundTag current = new CompoundTag();
                    entity.saveWithoutId(current);
                    current.merge(nbt);
                    entity.load(current);
                }
            }

            @Override
            public VariantDef getCurrent(Mob entity) {
                CompoundTag entityNbt = new CompoundTag();
                entity.saveWithoutId(entityNbt);
                for (DatapackVariant v : variants) {
                    if (NbtUtils.compareNbt(v.nbt(), entityNbt, true)) {
                        return new VariantDef(v.id(), v.nbt());
                    }
                }
                return new VariantDef("default", null);
            }
        };
    }

    private static VariantProvider<Mob> getVillagerProvider() {
        return new VariantProvider<>() {
            @Override
            public List<VariantDef> getVariants(Mob entity) {
                return BuiltInRegistries.VILLAGER_TYPE.entrySet().stream()
                        .map(e -> new VariantDef(e.getKey().location().toString(), e.getValue()))
                        .toList();
            }

            @Override
            public void apply(Mob entity, VariantDef def) {
                if (entity instanceof VillagerDataHolder holder) {
                    VillagerData data = holder.getVillagerData();
                    holder.setVillagerData(data.setType((VillagerType) def.value()));
                }
            }

            @Override
            public VariantDef getCurrent(Mob entity) {
                if (entity instanceof VillagerDataHolder holder) {
                    VillagerType type = holder.getVillagerData().getType();
                    ResourceLocation id = BuiltInRegistries.VILLAGER_TYPE.getKey(type);
                    return new VariantDef(id.toString(), type);
                }
                return new VariantDef("default", null);
            }
        };
    }

    private static VariantProvider<Mob> getReflectionProvider(Mob mob) {
        Method[] methods = mob.getClass().getMethods();
        Method getter = null;
        Method setter = null;

        for (Method m : methods) {
            String name = m.getName();
            if (m.getParameterCount() == 0 && (name.startsWith("get") || name.startsWith("is")) &&
                    (name.contains("Variant") || name.contains("Type") || name.contains("Color")) &&
                    !name.equals("getCollarColor")) {

                String suffix = name.startsWith("get") ? name.substring(3) : name.substring(2);
                try {
                    Method potentialSetter = mob.getClass().getMethod("set" + suffix, m.getReturnType());
                    getter = m;
                    setter = potentialSetter;
                    break;
                } catch (NoSuchMethodException ignored) {
                }
            }
        }

        if (getter != null) {
            final Method finalGetter = getter;
            final Method finalSetter = setter;

            try {
                Object current = finalGetter.invoke(mob);
                if (current instanceof Enum<?> currentEnum) {
                    return new VariantProvider<>() {
                        @Override
                        public List<VariantDef> getVariants(Mob entity) {
                            return Arrays.stream(currentEnum.getDeclaringClass().getEnumConstants())
                                    .map(e -> new VariantDef(e.name(), e))
                                    .toList();
                        }

                        @Override
                        public void apply(Mob entity, VariantDef def) {
                            try {
                                finalSetter.invoke(entity, def.value());
                            } catch (Exception ignored) {
                            }
                        }

                        @Override
                        public VariantDef getCurrent(Mob entity) {
                            try {
                                Object val = finalGetter.invoke(entity);
                                if (val instanceof Enum<?> e) return new VariantDef(e.name(), e);
                            } catch (Exception ignored) {
                            }
                            return new VariantDef("default", null);
                        }
                    };
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
