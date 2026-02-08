package com.evandev.fieldguide.client.gui.screens;

import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.client.gui.util.Bounds;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class BookScreen extends Screen {
    private static final int BG_WIDTH = 300;
    private static final int BG_HEIGHT = 200;
    private static final int PAGE_WIDTH = 122;
    private static final int PAGE_HEIGHT = 164;

    protected Bounds bounds;
    protected Bounds leftPageBounds;
    protected Bounds rightPageBounds;

    protected BookScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        this.bounds = new Bounds((this.width - BG_WIDTH) / 2,(this.height - BG_HEIGHT) / 2, BG_WIDTH, BG_HEIGHT);
        this.leftPageBounds = new Bounds(this.bounds.left() + 22,this.bounds.top() + 19, PAGE_WIDTH, PAGE_HEIGHT);
        this.rightPageBounds = new Bounds(this.leftPageBounds.right() + 13,leftPageBounds.top(), PAGE_WIDTH, PAGE_HEIGHT);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (FieldGuideClient.OPEN_GUIDE_KEY.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}