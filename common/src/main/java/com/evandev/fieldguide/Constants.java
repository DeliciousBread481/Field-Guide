package com.evandev.fieldguide;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {
    public static final String MOD_ID = "fieldguide";
    public static final String MOD_NAME = "FieldGuide";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    // Backgrounds
    public static final ResourceLocation BOOK_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/book.png");
    public static final ResourceLocation TITLE_PAGE_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/page_title.png");
    public static final ResourceLocation LIST_PAGE_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/page_list.png");
    public static final ResourceLocation DETAILS_PAGE_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/page_details.png");
    public static final ResourceLocation TOAST_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/toast.png");

    // Buttons
    public static final ResourceLocation BUTTON_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/fieldguide_button.png");
    public static final ResourceLocation INVENTORY_BUTTON_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/fieldguide_inventory_button.png");
    public static final ResourceLocation BIOME_PAGINATION_BUTTONS_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/arrows.png");

    // Elements
    public static final ResourceLocation NEXT_PAGE_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/next_page.png");
    public static final ResourceLocation PREV_PAGE_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/prev_page.png");
    public static final ResourceLocation BACK_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/back.png");
    public static final ResourceLocation TAB_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/tab.png");
    public static final ResourceLocation CELL_BACKGROUND_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/cell_background.png");
    public static final ResourceLocation ITEM_BACKGROUND_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/item_background.png");
    public static final ResourceLocation CELL_BACKGROUND_HOVER_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/cell_background_hover.png");

    // ICONS
    public static final ResourceLocation SEARCH_ICON = new ResourceLocation(MOD_ID, "textures/gui/icons/search.png");
    public static final ResourceLocation TOAST_ICON = new ResourceLocation(MOD_ID, "textures/gui/book_icon.png");
    public static final ResourceLocation ATTRIBUTES_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/attributes.png");
    public static final ResourceLocation SCANNING_ICON_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/scanning.png");

    // Colors
    public static final int TEXT_COLOR = 0x8A5E3B;
    public static final int TEXT_TITLE_COLOR = 0x704623;
    public static final int TEXT_MUTED_COLOR = 0xC7A875;
    public static final int PAGE_NUMBER_COLOR = 0xC7A875;
    public static final int LIST_SILHOUETTE_COLOR = 0xddc69b;
    public static final int DETAILS_SILHOUETTE_COLOR = 0xddc69b;

}