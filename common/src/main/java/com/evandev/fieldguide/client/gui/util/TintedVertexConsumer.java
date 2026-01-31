package com.evandev.fieldguide.client.gui.util;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.jetbrains.annotations.NotNull;

public class TintedVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final float tintR, tintG, tintB, tintA;

    public TintedVertexConsumer(VertexConsumer delegate, float r, float g, float b, float a) {
        this.delegate = delegate;
        this.tintR = r;
        this.tintG = g;
        this.tintB = b;
        this.tintA = a;
    }

    @Override
    public @NotNull VertexConsumer vertex(double x, double y, double z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public @NotNull VertexConsumer color(int red, int green, int blue, int alpha) {
        delegate.color(
                (int) (red * tintR),
                (int) (green * tintG),
                (int) (blue * tintB),
                (int) (alpha * tintA)
        );
        return this;
    }

    @Override
    public @NotNull VertexConsumer uv(float u, float v) {
        delegate.uv(u, v);
        return this;
    }

    @Override
    public @NotNull VertexConsumer overlayCoords(int u, int v) {
        delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public @NotNull VertexConsumer uv2(int u, int v) {
        delegate.uv2(u, v);
        return this;
    }

    @Override
    public @NotNull VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        delegate.endVertex();
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        delegate.defaultColor(
                (int) (red * tintR),
                (int) (green * tintG),
                (int) (blue * tintB),
                (int) (alpha * tintA)
        );
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }
}