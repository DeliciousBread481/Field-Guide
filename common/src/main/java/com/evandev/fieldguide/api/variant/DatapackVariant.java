package com.evandev.fieldguide.api.variant;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.Entity;

public record DatapackVariant(String id, CompoundTag nbt) {
    public boolean matches(Entity entity) {
        CompoundTag entityNbt = new CompoundTag();
        entity.saveWithoutId(entityNbt);
        return NbtUtils.compareNbt(nbt, entityNbt, true);
    }
}
