package com.evandev.fieldguide.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.CatVariant;
import net.minecraft.world.entity.animal.FrogVariant;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.DyeColor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FieldGuideVariantManager {

    private static final Map<Class<?>, VariantProvider<?>> PROVIDERS = new HashMap<>();

    static {
        registerProvider(Frog.class, new VariantProvider<>() {
            @Override
            public List<VariantDef> getVariants() {
                return BuiltInRegistries.FROG_VARIANT.entrySet().stream()
                        .map(e -> new VariantDef(e.getKey().location().toString(), e.getValue()))
                        .toList();
            }

            @Override
            public void apply(Frog entity, VariantDef def) {
                entity.setVariant((FrogVariant) def.value());
            }

            @Override
            public VariantDef getCurrent(Frog entity) {
                ResourceLocation id = BuiltInRegistries.FROG_VARIANT.getKey(entity.getVariant());
                return new VariantDef(id != null ? id.toString() : "default", entity.getVariant());
            }
        });

        registerProvider(Cat.class, new VariantProvider<>() {
            @Override
            public List<VariantDef> getVariants() {
                return BuiltInRegistries.CAT_VARIANT.entrySet().stream()
                        .map(e -> new VariantDef(e.getKey().location().toString(), e.getValue()))
                        .toList();
            }

            @Override
            public void apply(Cat entity, VariantDef def) {
                entity.setVariant((CatVariant) def.value());
            }

            @Override
            public VariantDef getCurrent(Cat entity) {
                ResourceLocation id = BuiltInRegistries.CAT_VARIANT.getKey(entity.getVariant());
                return new VariantDef(id != null ? id.toString() : "default", entity.getVariant());
            }
        });

        registerProvider(Sheep.class, new VariantProvider<>() {
            @Override
            public List<VariantDef> getVariants() {
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

    @SuppressWarnings("unchecked")
    public static <T extends Mob> VariantProvider<T> getProvider(Entity entity) {
        if (!(entity instanceof Mob mob)) return null;

        if (mob instanceof VillagerDataHolder) {
            return (VariantProvider<T>) getVillagerProvider();
        }

        Class<?> clazz = mob.getClass();
        while (clazz != null && clazz != Mob.class && clazz != Object.class) {
            if (PROVIDERS.containsKey(clazz)) {
                return (VariantProvider<T>) PROVIDERS.get(clazz);
            }
            clazz = clazz.getSuperclass();
        }

        return (VariantProvider<T>) getReflectionProvider(mob);
    }

    public static List<VariantDef> getVariants(Entity entity) {
        VariantProvider<Mob> provider = getProvider(entity);
        return provider != null ? provider.getVariants() : List.of();
    }

    private static VariantProvider<Mob> getVillagerProvider() {
        return new VariantProvider<>() {
            @Override
            public List<VariantDef> getVariants() {
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
                    (name.contains("Variant") || name.contains("Type") || name.contains("Color"))) {

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
                        public List<VariantDef> getVariants() {
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

    public interface VariantProvider<T extends Mob> {
        List<VariantDef> getVariants();

        void apply(T entity, VariantDef def);

        VariantDef getCurrent(T entity);
    }

    public record VariantDef(String id, Object value) {
    }
}