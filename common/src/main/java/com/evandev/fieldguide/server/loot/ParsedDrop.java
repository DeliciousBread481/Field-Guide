package com.evandev.fieldguide.server.loot;

import net.minecraft.world.item.ItemStack;

public class ParsedDrop {
    public final ItemStack stack;
    public float chance;

    public ParsedDrop(ItemStack stack, float chance) {
        this.stack = stack;
        this.chance = chance;
    }
}