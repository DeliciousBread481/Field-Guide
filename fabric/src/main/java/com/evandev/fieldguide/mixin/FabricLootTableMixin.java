package com.evandev.fieldguide.mixin;

import com.evandev.fieldguide.util.LootTableExpansion;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Arrays;
import java.util.List;

@Mixin(LootTable.class)
public abstract class FabricLootTableMixin implements LootTableExpansion {
    @Accessor("pools")
    public abstract LootPool[] getPoolsInternal();

    @Override
    public List<LootPool> fieldguide$getPools() {
        return Arrays.asList(getPoolsInternal());
    }
}