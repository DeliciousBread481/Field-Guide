package com.evandev.fieldguide.mixin.accessor;

import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LootPoolSingletonContainer.class)
public interface LootPoolSingletonContainerAccessor {
    @Accessor("weight")
    int fieldguide$getWeight();

    @Accessor("functions")
    LootItemFunction[] fieldguide$getFunctions();
}
