package com.evandev.mobendium;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {
    public static final String MOD_ID = "mobendium";
    public static final String MOD_NAME = "Mobendium";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    public static final ResourceLocation BOOK_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/book.png");
    public static final ResourceLocation BUTTON_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/mobendium_button.png");
    public static final ResourceLocation CELL_BACKGROUND_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/cell_background.png");
    public static final ResourceLocation CELL_BACKGROUND_HOVER_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/cell_background_hover.png");
    public static final ResourceLocation PAGE_DETAILS_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/page_details.png");
    public static final ResourceLocation NEXT_PAGE_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/next_page.png");
    public static final ResourceLocation PREV_PAGE_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/prev_page.png");
    public static final ResourceLocation BACK_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/back.png");
}