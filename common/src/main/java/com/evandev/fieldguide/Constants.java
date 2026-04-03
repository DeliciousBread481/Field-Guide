package com.evandev.fieldguide;

import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class Constants {
    public static final String MOD_ID = "fieldguide";
    public static final String MOD_NAME = "FieldGuide";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    public static final Type LIST_STRING_TYPE = new TypeToken<List<String>>() {
    }.getType();

    public static final Type MAP_STRING_LIST_STRING_TYPE = new TypeToken<Map<String, List<String>>>() {
    }.getType();

    // Backgrounds
    public static final Identifier BOOK_TEXTURE = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/book.png");
    public static final Identifier TITLE_PAGE_TEXTURE = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/page_title.png");
    public static final Identifier LIST_PAGE_TEXTURE = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/page_list.png");
    public static final Identifier DETAILS_PAGE_TEXTURE = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/page_details.png");
    public static final Identifier DETAILS_PAGE_V_TEXTURE = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/page_details_v.png");
    public static final Identifier DETAILS_PAGE_A_TEXTURE = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/page_details_a.png");
    public static final Identifier DETAILS_PAGE_VA_TEXTURE = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/page_details_va.png");
    public static final Identifier TOAST_TEXTURE = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/toast.png");
    public static final Identifier JOURNAL_TITLE_PAGE_TEXTURE = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/page_journal_title.png");
    public static final Identifier JOURNAL_PAGE_TEXTURE = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/page_journal.png");
    public static final Identifier VARIANT_WIDGET_TEXTURE = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/variant_overview_bg.png");

    // Sounds
    public static final Identifier ITEM_PICKUP_SOUND = Identifier.fromNamespaceAndPath("minecraft", "entity.item.pickup");

    // Elements
    public static final Identifier WIDGETS_TEXTURE = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/widgets.png");
    public static final Identifier LIST_ENTRY_BACKGROUND_TEXTURE = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/list_entry_background.png");
    public static final Identifier LIST_ENTRY_NEW_TEXTURE = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/list_entry_new.png");

    // Icons
    public static final Identifier DEFAULT_ICON = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/icons/book.png");
    public static final Identifier TOAST_ICON = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/book_icon.png");
    public static final Identifier QUILL_ICON = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/icons/quill.png");
    public static final Identifier ATTRIBUTES_SEPARATOR = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/attributes_separator.png");
    public static final Identifier SCANNING_ICON_TEXTURE = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/scanning.png");
    public static final Identifier SEASONS_TEXTURE = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/seasons.png");

    // Attribute Icons
    public static final Identifier HEALTH_ICON = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/attributes/health.png");
    public static final Identifier ARMOR_ICON = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/attributes/armor.png");
    public static final Identifier HOSTILE_ICON = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/attributes/hostile.png");
    public static final Identifier PASSIVE_ICON = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/attributes/passive.png");
    public static final Identifier NEUTRAL_ICON = Identifier.fromNamespaceAndPath(MOD_ID, "textures/gui/attributes/neutral.png");
}