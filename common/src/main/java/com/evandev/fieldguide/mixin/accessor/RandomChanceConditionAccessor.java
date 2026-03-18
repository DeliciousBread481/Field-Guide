package com.evandev.fieldguide.mixin.accessor;

import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LootItemRandomChanceCondition.class)
public interface RandomChanceConditionAccessor {
    @Accessor("probability")
    float fieldguide$getProbability();
}
