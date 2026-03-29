package com.evandev.fieldguide.compat.mixedlitter;

import com.evandev.fieldguide.api.variant.VariantDef;
import dev.tazer.mixed_litter.MLRegistries;
import dev.tazer.mixed_litter.VariantUtil;
import dev.tazer.mixed_litter.registry.MLDataAttachmentTypes;
import dev.tazer.mixed_litter.variants.Variant;
import dev.tazer.mixed_litter.variants.VariantGroup;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MixedLitterCompat {

    public static void applyDummyVariant(Entity entity) {
        if (entity instanceof AgeableMob ageable) {
            ageable.setAge(0);
        }
        try {
            Registry<VariantGroup> groupRegistry = entity.registryAccess().registryOrThrow(MLRegistries.VARIANT_GROUP_KEY);
            Registry<Variant> variantRegistry = entity.registryAccess().registryOrThrow(MLRegistries.VARIANT_KEY);
            String entityPath = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();

            List<Variant> selected = new ArrayList<>();
            for (Holder<VariantGroup> groupHolder : groupRegistry.holders().toList()) {
                VariantGroup group = groupHolder.value();
                List<Variant> matching = new ArrayList<>();

                for (ResourceLocation id : variantRegistry.keySet()) {
                    Variant variant = variantRegistry.get(id);
                    if (variant != null && variant.group().isPresent() && variant.group().get().equals(groupRegistry.getKey(group))) {
                        if (id.getPath().toLowerCase().contains(entityPath)) {
                            matching.add(variant);
                        }
                    }
                }

                if (!matching.isEmpty()) {
                    selected.add(matching.get(entity.getRandom().nextInt(matching.size())));
                }
            }

            for (ResourceLocation id : variantRegistry.keySet()) {
                Variant variant = variantRegistry.get(id);
                if (variant != null && variant.group().isEmpty()) {
                    if (id.getPath().toLowerCase().contains(entityPath)) {
                        selected.add(variant);
                    }
                }
            }

            if (!selected.isEmpty()) {
                VariantUtil.setVariants(entity, selected);
            }
        } catch (Exception ignored) {
        }
    }

    public static List<VariantDef> getVariants(Entity entity) {
        List<VariantDef> defs = new ArrayList<>();
        try {
            Registry<Variant> variantRegistry = entity.registryAccess().registryOrThrow(MLRegistries.VARIANT_KEY);
            String entityPath = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();

            for (ResourceLocation id : variantRegistry.keySet()) {
                if (id.getPath().toLowerCase().contains(entityPath)) {
                    Variant variant = variantRegistry.get(id);
                    if (variant != null) {
                        defs.add(new VariantDef(id.toString(), id));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return defs;
    }

    public static void applyVariant(Entity entity, VariantDef def) {
        if (def.value() instanceof ResourceLocation id) {
            try {
                Registry<Variant> variantRegistry = entity.registryAccess().registryOrThrow(MLRegistries.VARIANT_KEY);
                Variant variant = variantRegistry.get(id);
                if (variant != null) {
                    VariantUtil.setVariants(entity, Collections.singletonList(variant));
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static VariantDef getCurrentVariant(Entity entity) {
        try {
            List<ResourceLocation> variants = entity.getData(MLDataAttachmentTypes.VARIANTS.get());
            if (variants != null && !variants.isEmpty()) {
                ResourceLocation id = variants.get(0);
                return new VariantDef(id.toString(), id);
            }
        } catch (Exception ignored) {
        }
        return new VariantDef("default", null);
    }
}