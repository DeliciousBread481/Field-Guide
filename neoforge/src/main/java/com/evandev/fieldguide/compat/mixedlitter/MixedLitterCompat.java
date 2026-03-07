package com.evandev.fieldguide.compat.mixedlitter;

import dev.tazer.mixed_litter.MLRegistries;
import dev.tazer.mixed_litter.VariantUtil;
import dev.tazer.mixed_litter.variants.Variant;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.Collections;

public class MixedLitterCompat {

    public static void applyDummyVariant(Entity entity) {
        try {
            Registry<Variant> variantRegistry = entity.registryAccess().registryOrThrow(MLRegistries.VARIANT_KEY);
            String entityPath = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();

            for (ResourceLocation id : variantRegistry.keySet()) {
                if (id.getPath().toLowerCase().contains(entityPath)) {
                    Variant variant = variantRegistry.get(id);
                    if (variant != null) {
                        VariantUtil.setVariants(entity, Collections.singletonList(variant));
                        return;
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}