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
    public static final ResourceLocation JOURNAL_TITLE_PAGE_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/page_journal_title.png");
    public static final ResourceLocation JOURNAL_PAGE_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/page_journal.png");

    // Buttons
    public static final ResourceLocation BUTTON_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/fieldguide_button.png");
    public static final ResourceLocation INVENTORY_BUTTON_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/fieldguide_inventory_button.png");

    // Elements
    public static final ResourceLocation WIDGETS_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/widgets.png");
    public static final ResourceLocation LIST_ENTRY_BACKGROUND_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/list_entry_background.png");

    // Icons
    public static final ResourceLocation DEFAULT_ICON = new ResourceLocation(MOD_ID, "textures/gui/icons/book.png");
    public static final ResourceLocation TOAST_ICON = new ResourceLocation(MOD_ID, "textures/gui/book_icon.png");
    public static final ResourceLocation ATTRIBUTES_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/attributes.png");
    public static final ResourceLocation SCANNING_ICON_TEXTURE = new ResourceLocation(MOD_ID, "textures/gui/scanning.png");

    // Mob Type Icons
    public static final ResourceLocation HOSTILE_ICON = new ResourceLocation(MOD_ID, "textures/gui/icons/hostile.png");
    public static final ResourceLocation PASSIVE_ICON = new ResourceLocation(MOD_ID, "textures/gui/icons/passive.png");
    public static final ResourceLocation NEUTRAL_ICON = new ResourceLocation(MOD_ID, "textures/gui/icons/neutral.png");
}