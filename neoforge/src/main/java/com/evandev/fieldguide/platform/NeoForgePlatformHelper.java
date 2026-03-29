package com.evandev.fieldguide.platform;

import com.evandev.fieldguide.api.variant.VariantDef;
import com.evandev.fieldguide.api.variant.VariantProvider;
import com.evandev.fieldguide.compat.mixedlitter.MixedLitterCompat;
import com.evandev.fieldguide.platform.services.IPlatformHelper;
import com.evandev.fieldguide.variant.FieldGuideVariantManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.List;

public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public void applyMixedLitterCompat(Entity entity) {
        if (entity instanceof Mob mob) {
            FieldGuideVariantManager.registerProvider((Class<Mob>) mob.getClass(), new MixedLitterVariantProvider());
        }
        MixedLitterCompat.applyDummyVariant(entity);
    }

    private static class MixedLitterVariantProvider implements VariantProvider<Mob> {
        @Override
        public List<VariantDef> getVariants(Mob entity) {
            return MixedLitterCompat.getVariants(entity);
        }

        @Override
        public void apply(Mob entity, VariantDef def) {
            MixedLitterCompat.applyVariant(entity, def);
        }

        @Override
        public VariantDef getCurrent(Mob entity) {
            return MixedLitterCompat.getCurrentVariant(entity);
        }

        @Override
        public String getCacheKey(Mob entity) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            return id + "_mixed_litter";
        }
    }
}