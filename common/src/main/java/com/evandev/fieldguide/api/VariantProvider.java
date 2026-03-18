package com.evandev.fieldguide.api;

import net.minecraft.world.entity.Mob;

import java.util.List;

public interface VariantProvider<T extends Mob> {
    List<VariantDef> getVariants(T entity);

    void apply(T entity, VariantDef def);

    VariantDef getCurrent(T entity);
}
