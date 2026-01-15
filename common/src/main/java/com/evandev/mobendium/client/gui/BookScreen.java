package com.evandev.mobendium.client.gui;

import com.evandev.mobendium.client.gui.util.Bounds;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class BookScreen extends Screen {
    private static final int BG_WIDTH = 300;
    private static final int BG_HEIGHT = 200;

    protected Bounds bounds;
    protected Bounds leftPageBounds;
    protected Bounds rightPageBounds;

    protected BookScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        this.bounds = new Bounds((this.width - BG_WIDTH) / 2,(this.height - BG_HEIGHT) / 2, BG_WIDTH, BG_HEIGHT);
        this.leftPageBounds = new Bounds(this.bounds.left() + 16,this.bounds.top() + 19,132,164);
        this.rightPageBounds = new Bounds(this.leftPageBounds.right() + 5,leftPageBounds.top(),132,164);
    }
}
