package com.evandev.fieldguide.client.scanning.manager;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

public record FieldGuideScanState(Object target, float progress, boolean finished) {
    public boolean isEntity() {
        return target instanceof Entity;
    }

    public boolean isBlock() {
        return target instanceof BlockState || target instanceof BlockPos;
    }
}
