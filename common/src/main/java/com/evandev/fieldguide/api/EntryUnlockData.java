package com.evandev.fieldguide.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.List;

public record EntryUnlockData(
        boolean unlockedByDefault,
        List<ResourceLocation> prerequisites,
        List<UnlockTrigger> triggers
) {
    public static final EntryUnlockData DEFAULT = new EntryUnlockData(false, Collections.emptyList(), Collections.emptyList());

    public enum UnlockTrigger {
        KILL,
        SCAN,
        OBTAIN
    }
}
