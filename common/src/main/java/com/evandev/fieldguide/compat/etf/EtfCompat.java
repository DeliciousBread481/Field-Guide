package com.evandev.fieldguide.compat.etf;

import com.evandev.fieldguide.client.render.TintedVertexConsumer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.Nullable;
import traben.entity_texture_features.features.ETFRenderContext;
import traben.entity_texture_features.features.texture_handlers.ETFTexture;
import traben.entity_texture_features.utils.ETFVertexConsumer;

public class EtfCompat {
    public static void preventRenderLayerTextureModify() {
        ETFRenderContext.preventRenderLayerTextureModify();
    }

    public static void allowRenderLayerTextureModify() {
        ETFRenderContext.allowRenderLayerTextureModify();
    }

    public static VertexConsumer createTintedConsumer(VertexConsumer delegate, MultiBufferSource provider, float r, float g, float b, float a) {
        return new EtfTintedConsumer(delegate, provider, r, g, b, a);
    }

    private static class EtfTintedConsumer extends TintedVertexConsumer implements ETFVertexConsumer {
        private final VertexConsumer delegate;
        private final MultiBufferSource provider;

        public EtfTintedConsumer(VertexConsumer delegate, MultiBufferSource provider, float r, float g, float b, float a) {
            super(delegate, r, g, b, a);
            this.delegate = delegate;
            this.provider = provider;
        }

        @Override
        public @Nullable ETFTexture etf$getETFTexture() {
            if (this.delegate instanceof ETFVertexConsumer etfConsumer) {
                return etfConsumer.etf$getETFTexture();
            }
            return null;
        }

        @Override
        public @Nullable MultiBufferSource etf$getProvider() {
            return this.provider;
        }

        @Override
        public @Nullable RenderType etf$getRenderLayer() {
            if (this.delegate instanceof ETFVertexConsumer etfConsumer) {
                return etfConsumer.etf$getRenderLayer();
            }
            return null;
        }

        @Override
        public void etf$initETFVertexConsumer(MultiBufferSource provider, RenderType renderLayer) {
            if (this.delegate instanceof ETFVertexConsumer etfConsumer) {
                etfConsumer.etf$initETFVertexConsumer(provider, renderLayer);
            }
        }
    }
}