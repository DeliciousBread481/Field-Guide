package com.evandev.fieldguide.client;

import com.evandev.fieldguide.Constants;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.Identifier;

public class ClientConstants {
    public static final WidgetSprites PREV_PAGE_SPRITES = new WidgetSprites(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "widget/page_prev"), Identifier.fromNamespaceAndPath(Constants.MOD_ID, "widget/page_prev_highlighted"));
    public static final WidgetSprites NEXT_PAGE_SPRITES = new WidgetSprites(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "widget/page_next"), Identifier.fromNamespaceAndPath(Constants.MOD_ID, "widget/page_next_highlighted"));
    public static final WidgetSprites BACK_SPRITES = new WidgetSprites(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "widget/back_button"), Identifier.fromNamespaceAndPath(Constants.MOD_ID, "widget/back_button_highlighted"));

    public static final WidgetSprites PREV_SPRITES = new WidgetSprites(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "widget/grid_prev"), Identifier.fromNamespaceAndPath(Constants.MOD_ID, "widget/grid_prev_disabled"), Identifier.fromNamespaceAndPath(Constants.MOD_ID, "widget/grid_prev_highlighted"));
    public static final WidgetSprites NEXT_SPRITES = new WidgetSprites(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "widget/grid_next"), Identifier.fromNamespaceAndPath(Constants.MOD_ID, "widget/grid_next_disabled"), Identifier.fromNamespaceAndPath(Constants.MOD_ID, "widget/grid_next_highlighted"));

    public static final WidgetSprites TAB_UP_SPRITES = new WidgetSprites(
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "widget/tab_up"),
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "widget/tab_up_highlighted")
    );
    public static final WidgetSprites TAB_DOWN_SPRITES = new WidgetSprites(
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "widget/tab_down"),
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "widget/tab_down_highlighted")
    );

    public static final WidgetSprites COPY_SPRITES = new WidgetSprites(
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "widget/copy_button"),
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "widget/copy_button_disabled"),
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "widget/copy_button_highlighted"),
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "widget/copy_button_disabled")
    );
}