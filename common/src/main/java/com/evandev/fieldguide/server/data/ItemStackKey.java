package com.evandev.fieldguide.server.data;

import net.minecraft.world.item.ItemStack;

public record ItemStackKey(ItemStack stack) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemStackKey)) return false;
        return ItemStack.isSameItemSameTags(stack, ((ItemStackKey) o).stack);
    }

    @Override
    public int hashCode() {
        int result = stack.getItem().hashCode();
        if (stack.hasTag() && stack.getTag() != null) {
            result = 31 * result + stack.getTag().hashCode();
        }
        return result;
    }
}
