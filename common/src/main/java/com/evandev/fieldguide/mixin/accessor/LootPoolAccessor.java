package com.evandev.fieldguide.mixin.accessor;

import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LootPool.class)
public interface LootPoolAccessor {
    @Accessor("entries")
    LootPoolEntryContainer[] fieldguide$getEntries();

    @Accessor("rolls")
    NumberProvider fieldguide$getRolls();

    @Accessor("conditions")
    LootItemCondition[] fieldguide$getConditions();
}