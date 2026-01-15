package com.evandev.mobendium.client.gui.util;

public record Bounds(int x, int y, int width, int height) {
    public int left() {
        return x;
    }
    public int right() {
        return x + width;
    }
    public int top() {
        return y;
    }
    public int bottom() {
        return y + height;
    }
}
