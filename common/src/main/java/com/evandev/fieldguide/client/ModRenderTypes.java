package com.evandev.fieldguide.client;

import com.evandev.fieldguide.Constants;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

public class ModRenderTypes {

    private static final Map<RenderType, RenderType> SCAN_WRAP_CACHE = new IdentityHashMap<>();
    private static final Map<RenderType, RenderType> DEPTH_WRAP_CACHE = new IdentityHashMap<>();

    private static final Identifier SCAN_SHADER_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "core/fieldguide_scan");

    public static RenderType wrapForDepth(RenderType original) {
        if (original == null) {
            return null;
        }

        return DEPTH_WRAP_CACHE.computeIfAbsent(original, type -> {
            RenderPipeline originalPipeline = type.pipeline();

            RenderPipeline.Builder builder = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "scan_depth_wrap"))
                    .withVertexShader(SCAN_SHADER_ID)
                    .withFragmentShader(SCAN_SHADER_ID)
                    .withVertexFormat(originalPipeline.getVertexFormat(), originalPipeline.getVertexFormatMode())
                    .withColorTargetState(originalPipeline.getColorTargetState())
                    .withCull(originalPipeline.isCull());

            if (originalPipeline.getDepthStencilState() != null) {
                builder.withDepthStencilState(Optional.of(originalPipeline.getDepthStencilState()));
            }

            for (String sampler : originalPipeline.getSamplers()) {
                builder.withSampler(sampler);
            }

            for (RenderPipeline.UniformDescription uniform : originalPipeline.getUniforms()) {
                if (uniform.textureFormat() != null) {
                    builder.withUniform(uniform.name(), uniform.type(), uniform.textureFormat());
                } else {
                    builder.withUniform(uniform.name(), uniform.type());
                }
            }

            RenderSetup setup = RenderSetup.builder(builder.build())
                    .setOutputTarget(type.outputTarget())
                    .createRenderSetup();

            return RenderType.create(Constants.MOD_ID + "_scan_depth_wrap", setup);
        });
    }

    public static RenderType wrapForScan(RenderType original) {
        if (original == null) {
            return null;
        }

        return SCAN_WRAP_CACHE.computeIfAbsent(original, type -> {
            RenderPipeline originalPipeline = type.pipeline();

            RenderPipeline.Builder builder = RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "scan_wrap"))
                    .withVertexShader(SCAN_SHADER_ID)
                    .withFragmentShader(SCAN_SHADER_ID)
                    .withVertexFormat(originalPipeline.getVertexFormat(), originalPipeline.getVertexFormatMode())
                    .withColorTargetState(originalPipeline.getColorTargetState())
                    .withCull(originalPipeline.isCull());

            if (originalPipeline.getDepthStencilState() != null) {
                builder.withDepthStencilState(Optional.of(originalPipeline.getDepthStencilState()));
            }

            for (String sampler : originalPipeline.getSamplers()) {
                builder.withSampler(sampler);
            }

            for (RenderPipeline.UniformDescription uniform : originalPipeline.getUniforms()) {
                if (uniform.textureFormat() != null) {
                    builder.withUniform(uniform.name(), uniform.type(), uniform.textureFormat());
                } else {
                    builder.withUniform(uniform.name(), uniform.type());
                }
            }

            RenderSetup setup = RenderSetup.builder(builder.build())
                    .setOutputTarget(type.outputTarget())
                    .createRenderSetup();

            return RenderType.create(Constants.MOD_ID + "_scan_wrap", setup);
        });
    }
}