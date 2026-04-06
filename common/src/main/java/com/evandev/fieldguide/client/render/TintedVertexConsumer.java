package com.evandev.fieldguide.client.render;

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
    public @NotNull VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public @NotNull VertexConsumer setColor(int red, int green, int blue, int alpha) {
        delegate.setColor(
                (int) (255 * tintR),
                (int) (255 * tintG),
                (int) (255 * tintB),
                (int) (alpha * tintA)
        );
        return this;
    }

    @Override
    public VertexConsumer setColor(int color) {
        return this;
    }

    @Override
    public @NotNull VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public @NotNull VertexConsumer setUv1(int i, int i1) {
        delegate.setUv1(i, i1);
        return this;
    }

    @Override
    public @NotNull VertexConsumer setUv2(int i, int i1) {
        delegate.setUv2(i, i1);
        return this;
    }

    @Override
    public @NotNull VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        return this;
    }
}