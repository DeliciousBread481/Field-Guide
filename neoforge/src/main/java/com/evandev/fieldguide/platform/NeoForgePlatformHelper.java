package com.evandev.fieldguide.platform;

import com.evandev.fieldguide.platform.services.IPlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

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
        return !FMLLoader.getCurrent().isProduction();
    }

    @Override
    public Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

/*    @Override
    public void applyMixedLitterCompat(Entity entity) {
        if (entity instanceof Mob mob) {
            FieldGuideVariantManager.registerProvider((Class<Mob>) mob.getClass(), new MixedLitterCompat.MixedLitterVariantProvider());
        }
        MixedLitterCompat.applyDummyVariant(entity);
    }*/
}