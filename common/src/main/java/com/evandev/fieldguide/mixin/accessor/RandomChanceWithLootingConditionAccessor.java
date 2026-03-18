package com.evandev.fieldguide.mixin.accessor;

import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceWithLootingCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LootItemRandomChanceWithLootingCondition.class)
public interface RandomChanceWithLootingConditionAccessor {
    @Accessor("percent")
    float fieldguide$getPercent();
}
