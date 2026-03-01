package com.evandev.fieldguide.mixin.accessor;

import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(LootPool.class)
public interface LootPoolAccessor {
    @Accessor("rolls")
    NumberProvider fieldguide$getRolls();

    @Accessor("entries")
    List<LootPoolEntryContainer> fieldguide$getEntries();

    @Accessor("conditions")
    List<LootItemCondition> fieldguide$getConditions();
}