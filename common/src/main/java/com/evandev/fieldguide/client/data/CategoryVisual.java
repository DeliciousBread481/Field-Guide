package com.evandev.fieldguide.client.data;

import com.evandev.fieldguide.Constants;
import net.minecraft.resources.ResourceLocation;

public class CategoryVisual {
    public static final CategoryVisual DEFAULT = new CategoryVisual();

    public ResourceLocation icon = Constants.DEFAULT_ICON;
    public ResourceLocation hostileIcon = Constants.HOSTILE_ICON;
    public ResourceLocation passiveIcon = Constants.PASSIVE_ICON;
    public ResourceLocation neutralIcon = Constants.NEUTRAL_ICON;
}