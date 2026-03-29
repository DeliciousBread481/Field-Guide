package com.evandev.fieldguide.mixin.accessor;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityAccessor {

    @Accessor("level")
    void fieldguide$setLevel(Level level);

    @Accessor("wasTouchingWater")
    void fieldguide$setWasTouchingWater(boolean wasTouchingWater);

}
