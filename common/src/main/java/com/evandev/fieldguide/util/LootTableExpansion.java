package com.evandev.fieldguide.util;

import net.minecraft.world.level.storage.loot.LootPool;

import java.util.List;

public interface LootTableExpansion {
    List<LootPool> fieldguide$getPools();
}