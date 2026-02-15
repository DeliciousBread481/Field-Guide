package com.evandev.fieldguide.mixin;

import com.evandev.fieldguide.util.LootTableExpansion;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(LootTable.class)
public abstract class ForgeLootTableMixin implements LootTableExpansion {
    @Accessor("pools")
    public abstract List<LootPool> getPoolsInternal();

    @Override
    public List<LootPool> fieldguide$getPools() {
        return getPoolsInternal();
    }
}