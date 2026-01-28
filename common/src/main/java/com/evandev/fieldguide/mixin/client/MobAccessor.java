package com.evandev.fieldguide.mixin.client;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.jetbrains.annotations.Nullable;

@Mixin(Mob.class)
public interface MobAccessor {
    @Invoker("getAmbientSound")
    @Nullable SoundEvent fieldguide$callGetAmbientSound();
}