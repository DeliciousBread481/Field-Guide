package com.evandev.mobendium;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {
    public static final String MOD_ID = "mobendium";
    public static final String MOD_NAME = "Mobendium";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    public static final ResourceLocation BOOK_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/mobendium_book.png");
    public static final ResourceLocation BUTTON_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/mobendium_button.png");

}