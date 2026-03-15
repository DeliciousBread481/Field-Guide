package com.evandev.fieldguide.util;

import net.minecraft.core.registries.BuiltInRegistries;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FieldGuideVariantManager {

    private static final Map<Class<?>, VariantCycler<?>> CYCLERS = new HashMap<>();

    static {
        registerCycler(Frog.class, frog -> {
            FrogVariant current = frog.getVariant();
            List<FrogVariant> variants = BuiltInRegistries.FROG_VARIANT.stream().toList();
            if (!variants.isEmpty()) {
                int nextIdx = (variants.indexOf(current) + 1) % variants.size();
                frog.setVariant(variants.get(nextIdx));
            }
        });

        registerCycler(Cat.class, cat -> {
            CatVariant current = cat.getVariant();
            List<CatVariant> variants = BuiltInRegistries.CAT_VARIANT.stream().toList();
            if (!variants.isEmpty()) {
                int nextIdx = (variants.indexOf(current) + 1) % variants.size();
                cat.setVariant(variants.get(nextIdx));
            }
        });

        registerCycler(Sheep.class, sheep -> {
            DyeColor current = sheep.getColor();
            DyeColor[] colors = DyeColor.values();
            int nextIdx = (current.ordinal() + 1) % colors.length;
            sheep.setColor(colors[nextIdx]);
        });
    }

    /**
     * Register a custom variant cycler for a specific mob class.
     */
    public static <T extends Mob> void registerCycler(Class<T> entityClass, VariantCycler<T> cycler) {
        CYCLERS.put(entityClass, cycler);
    }

    @SuppressWarnings("unchecked")
    public static void cycleToNextVariant(Entity entity) {
        if (!(entity instanceof Mob mob)) return;

        if (mob instanceof VillagerDataHolder holder) {
            cycleVillagerData(holder);
            return;
        }

        Class<?> clazz = mob.getClass();
        while (clazz != null && clazz != Mob.class && clazz != Object.class) {
            if (CYCLERS.containsKey(clazz)) {
                ((VariantCycler<Mob>) CYCLERS.get(clazz)).cycle(mob);
                return;
            }
            clazz = clazz.getSuperclass();
        }

        cycleReflectionVariant(mob);
    }

    private static void cycleVillagerData(VillagerDataHolder holder) {
        VillagerData data = holder.getVillagerData();
        VillagerType currentType = data.getType();
        List<VillagerType> types = BuiltInRegistries.VILLAGER_TYPE.stream().toList();
        if (!types.isEmpty()) {
            int nextIdx = (types.indexOf(currentType) + 1) % types.size();
            holder.setVillagerData(data.setType(types.get(nextIdx)));
        }
    }

    private static void cycleReflectionVariant(Mob mob) {
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
            try {
                Object current = getter.invoke(mob);

                if (current instanceof Integer currentInt) {
                    setter.invoke(mob, currentInt + 1);
                    if (getter.invoke(mob).equals(current)) {
                        setter.invoke(mob, 0);
                    }
                } else if (current instanceof Enum<?> currentEnum) {
                    Object[] constants = currentEnum.getDeclaringClass().getEnumConstants();
                    int nextOrdinal = (currentEnum.ordinal() + 1) % constants.length;
                    setter.invoke(mob, constants[nextOrdinal]);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Interface for defining custom variant cycling logic for specific entities.
     */
    public interface VariantCycler<T extends Mob> {
        void cycle(T entity);
    }
}