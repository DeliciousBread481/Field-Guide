package com.evandev.fieldguide.client.data;

import net.minecraft.resources.ResourceLocation;

public class CategoryVisual {
    public static final CategoryVisual DEFAULT = new CategoryVisual();

    public String color = "#FFFFFF";
    public ResourceLocation icon = new ResourceLocation("minecraft:book");

    public int getColorInt() {
        try {
            String hex = color.startsWith("#") ? color.substring(1) : color;
            return Integer.parseInt(hex, 16);
        } catch (Exception e) {
            return 0xFFFFFF;
        }
    }
}