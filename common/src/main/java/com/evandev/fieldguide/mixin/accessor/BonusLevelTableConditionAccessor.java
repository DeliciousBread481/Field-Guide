package com.evandev.fieldguide.mixin.accessor;

import net.minecraft.world.level.storage.loot.predicates.BonusLevelTableCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BonusLevelTableCondition.class)
public interface BonusLevelTableConditionAccessor {
    @Accessor("values")
    float[] fieldguide$getValues();
}
