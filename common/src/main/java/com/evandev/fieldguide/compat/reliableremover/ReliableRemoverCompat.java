package com.evandev.fieldguide.compat.reliableremover;

import com.evandev.reliable_remover.api.ReliableRemoverAPI;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class ReliableRemoverCompat {
    public static boolean isHidden(ItemStack stack) {
        return ReliableRemoverAPI.isItemHidden(stack);
    }

    public static boolean isHidden(Block block) {
        return ReliableRemoverAPI.isItemHidden(new ItemStack(block));
    }
}