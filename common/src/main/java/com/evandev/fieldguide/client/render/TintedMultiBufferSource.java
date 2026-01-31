package com.evandev.fieldguide.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.NotNull;

public class TintedMultiBufferSource implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final float r, g, b, a;

    public TintedMultiBufferSource(MultiBufferSource delegate, float r, float g, float b, float a) {
        this.delegate = delegate;
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    @Override
    public @NotNull VertexConsumer getBuffer(@NotNull RenderType renderType) {
        return new TintedVertexConsumer(delegate.getBuffer(renderType), r, g, b, a);
    }
}
