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
    public static final ResourceLocation JOURNAL_TITLE_PAGE_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/journal_title_page.png");
    public static final ResourceLocation JOURNAL_PAGE_RIGHT_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/journal_lines_right.png");
    public static final ResourceLocation JOURNAL_PAGE_LEFT_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/journal_lines_left.png");

    // Buttons
    public static final ResourceLocation BUTTON_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/fieldguide_button.png");
    public static final ResourceLocation INVENTORY_BUTTON_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/fieldguide_inventory_button.png");
    public static final ResourceLocation BIOME_PAGINATION_BUTTONS_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/arrows.png");

    // Elements
    public static final ResourceLocation NEXT_PAGE_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/next_page.png");
    public static final ResourceLocation PREV_PAGE_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/prev_page.png");
    public static final ResourceLocation BACK_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/back.png");
    public static final ResourceLocation TAB_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/tab.png");
    public static final ResourceLocation SCROLL_UP_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/scroll_up.png");
    public static final ResourceLocation SCROLL_DOWN_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/scroll_down.png");

    public static final ResourceLocation CELL_BACKGROUND_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/cell_background.png");
    public static final ResourceLocation ITEM_BACKGROUND_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/item_background.png");
    public static final ResourceLocation BIOME_BACKGROUND_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/biome_background.png");
    public static final ResourceLocation CELL_BACKGROUND_HOVER_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/cell_background_hover.png");
    public static final ResourceLocation HEALTH_FRAME_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/health_frame.png");
    public static final ResourceLocation PROGRESS_BAR_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/progress_bar.png");

    // Icons
    public static final ResourceLocation DEFAULT_ICON = new ResourceLocation(MOD_ID, "textures/gui/icons/book.png");
    public static final ResourceLocation SEARCH_ICON = new ResourceLocation(MOD_ID, "textures/gui/icons/search.png");
    public static final ResourceLocation TOAST_ICON = new ResourceLocation(MOD_ID, "textures/gui/book_icon.png");
    public static final ResourceLocation ATTRIBUTES_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/attributes.png");
    public static final ResourceLocation SCANNING_ICON_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/scanning.png");

    // Mob Type Icons
    public static final ResourceLocation HOSTILE_ICON = new ResourceLocation(MOD_ID, "textures/gui/icons/hostile.png");
    public static final ResourceLocation PASSIVE_ICON = new ResourceLocation(MOD_ID, "textures/gui/icons/passive.png");
    public static final ResourceLocation NEUTRAL_ICON = new ResourceLocation(MOD_ID, "textures/gui/icons/neutral.png");
}