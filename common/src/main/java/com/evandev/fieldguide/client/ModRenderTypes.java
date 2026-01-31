package com.evandev.fieldguide.client;

import com.evandev.fieldguide.Constants;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.io.IOException;
import java.util.function.Consumer;

public class ModRenderTypes extends RenderType {

    public static ShaderInstance SCAN_SHADER_INSTANCE;
    private static final ShaderStateShard SCAN_SHADER_STATE = new ShaderStateShard(() -> SCAN_SHADER_INSTANCE);

    public ModRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    public static RenderType getScanRenderType(ResourceLocation texture) {
        TextureStateShard textureState = new TextureStateShard(texture, false, false);
        ShaderStateShard shaderState = SCAN_SHADER_STATE;
        TransparencyStateShard transparencyState = TRANSLUCENT_TRANSPARENCY;
        CullStateShard cullState = CULL;
        WriteMaskStateShard writeMaskState = COLOR_DEPTH_WRITE;
        DepthTestStateShard depthTestState = LEQUAL_DEPTH_TEST;

        return new ModRenderTypes(
                Constants.MOD_ID + "_scan",
                DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS,
                256,
                false,
                true,
                () -> {
                    textureState.setupRenderState();
                    shaderState.setupRenderState();
                    transparencyState.setupRenderState();
                    cullState.setupRenderState();
                    writeMaskState.setupRenderState();
                    depthTestState.setupRenderState();
                },
                () -> {
                    depthTestState.clearRenderState();
                    writeMaskState.clearRenderState();
                    cullState.clearRenderState();
                    transparencyState.clearRenderState();
                    shaderState.clearRenderState();
                    textureState.clearRenderState();
                }
        );
    }

    public static void registerShaders(Consumer<ShaderInstance> provider, ResourceProvider resourceProvider) throws IOException {
        provider.accept(new ShaderInstance(
                resourceProvider,
                Constants.MOD_ID + ":fieldguide_scan",
                DefaultVertexFormat.POSITION_COLOR_TEX
        ) {
            @Override
            public void apply() {
                ModRenderTypes.SCAN_SHADER_INSTANCE = this;
                super.apply();
            }
        });
    }
}