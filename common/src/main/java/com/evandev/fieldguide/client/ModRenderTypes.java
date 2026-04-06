package com.evandev.fieldguide.client;

import net.minecraft.client.renderer.rendertype.RenderType;

public class ModRenderTypes {

    public static RenderType wrapForDepth(RenderType original, boolean isEntity) {
        return original; // TODO: Port custom scan shaders to 26.1 RenderPipelines
    }

    public static RenderType wrapForScan(RenderType original, boolean isEntity) {
        return original; // TODO: Port custom scan shaders to 26.1 RenderPipelines
    }
}
