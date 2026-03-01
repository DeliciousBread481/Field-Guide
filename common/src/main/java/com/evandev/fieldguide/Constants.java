package com.evandev.fieldguide;

import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants {
    public static final String MOD_ID = "fieldguide";
    public static final String MOD_NAME = "FieldGuide";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    // Backgrounds
    public static final ResourceLocation BOOK_TEXTURE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/book.png");
    public static final ResourceLocation TITLE_PAGE_TEXTURE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/page_title.png");
    public static final ResourceLocation LIST_PAGE_TEXTURE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/page_list.png");
    public static final ResourceLocation DETAILS_PAGE_TEXTURE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/page_details.png");
    public static final ResourceLocation TOAST_TEXTURE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/toast.png");
    public static final ResourceLocation JOURNAL_TITLE_PAGE_TEXTURE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/page_journal_title.png");
    public static final ResourceLocation JOURNAL_PAGE_TEXTURE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/page_journal.png");

    // Elements
    public static final ResourceLocation WIDGETS_TEXTURE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/widgets.png");
    public static final ResourceLocation LIST_ENTRY_BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/list_entry_background.png");
    public static final ResourceLocation LIST_ENTRY_NEW_TEXTURE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/list_entry_new.png");

    public static final WidgetSprites PREV_PAGE_SPRITES = new WidgetSprites(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "widget/page_prev"), ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "widget/page_prev_highlighted"));
    public static final WidgetSprites NEXT_PAGE_SPRITES = new WidgetSprites(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "widget/page_next"), ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "widget/page_next_highlighted"));
    public static final WidgetSprites BACK_SPRITES = new WidgetSprites(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "widget/back_button"), ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "widget/back_button_highlighted"));

    public static final WidgetSprites PREV_SPRITES = new WidgetSprites(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "widget/grid_prev"), ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "widget/grid_prev_highlighted"));
    public static final WidgetSprites NEXT_SPRITES = new WidgetSprites(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "widget/grid_next"), ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "widget/grid_next_highlighted"));

    // Icons
    public static final ResourceLocation DEFAULT_ICON = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/icons/book.png");
    public static final ResourceLocation TOAST_ICON = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/book_icon.png");
    public static final ResourceLocation ATTRIBUTES_TEXTURE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/attributes.png");
    public static final ResourceLocation SCANNING_ICON_TEXTURE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/scanning.png");

    // Mob Type Icons
    public static final ResourceLocation HOSTILE_ICON = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/icons/hostile.png");
    public static final ResourceLocation PASSIVE_ICON = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/icons/passive.png");
    public static final ResourceLocation NEUTRAL_ICON = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/icons/neutral.png");
    public static final WidgetSprites TAB_UP_SPRITES = new WidgetSprites(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "widget/tab_up"),
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "widget/tab_up_highlighted")
    );
    public static final WidgetSprites TAB_DOWN_SPRITES = new WidgetSprites(
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "widget/tab_down"),
            ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "widget/tab_down_highlighted")
    );
}