package com.evandev.fieldguide.client.gui.util;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.platform.Services;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class IconCacheManager {
    private static final Path CACHE_DIR = Services.PLATFORM.getConfigDirectory().resolve("../fieldguide_cache/entries");
    private static final Map<String, ResourceLocation> TEXTURE_CACHE = new HashMap<>();
    private static final int RENDER_SIZE = 256;

    public static void init() {
        try {
            Files.createDirectories(CACHE_DIR);
        } catch (IOException e) {
            Constants.LOG.error("Failed to create icon cache directory", e);
        }
    }

    public static void clearCache() {
        TEXTURE_CACHE.clear();
    }

    public static Optional<ResourceLocation> getOrGenerateIcon(Object entry, boolean isPage, Runnable renderAction) {
        String idStr = ClientFieldGuideManager.getEntryId(entry).toString().replace(":", "_").replace("/", "_");
        String key = idStr + (isPage ? "_page" : "_grid");

        if (TEXTURE_CACHE.containsKey(key)) {
            return Optional.of(TEXTURE_CACHE.get(key));
        }

        if (!Files.exists(CACHE_DIR)) init();
        File cachedFile = CACHE_DIR.resolve(key + ".png").toFile();

        if (!cachedFile.exists()) {
            generateAndSaveIcon(cachedFile, renderAction);
        }

        if (cachedFile.exists()) {
            try {
                NativeImage image = NativeImage.read(Files.newInputStream(cachedFile.toPath()));
                DynamicTexture texture = new DynamicTexture(image);
                ResourceLocation texLoc = new ResourceLocation(Constants.MOD_ID, "generated_icon/" + key);
                Minecraft.getInstance().getTextureManager().register(texLoc, texture);
                TEXTURE_CACHE.put(key, texLoc);
                return Optional.of(texLoc);
            } catch (IOException e) {
                Constants.LOG.error("Failed to load cached icon: {}", key, e);
            }
        }

        return Optional.empty();
    }

    private static void generateAndSaveIcon(File outputFile, Runnable renderAction) {
        Minecraft mc = Minecraft.getInstance();

        Matrix4f oldProjection = RenderSystem.getProjectionMatrix();

        RenderTarget renderTarget = new TextureTarget(RENDER_SIZE, RENDER_SIZE, true, Minecraft.ON_OSX);

        renderTarget.setClearColor(1.0F, 0.0F, 1.0F, 0.0F);
        renderTarget.clear(Minecraft.ON_OSX);

        renderTarget.bindWrite(true);
        RenderSystem.viewport(0, 0, RENDER_SIZE, RENDER_SIZE);

        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        Matrix4f projectionMatrix = new Matrix4f().setOrtho(0.0F, RENDER_SIZE, RENDER_SIZE, 0.0F, 10000.0F, -10000.0F);
        RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.ORTHOGRAPHIC_Z);

        PoseStack poseStack = RenderSystem.getModelViewStack();
        poseStack.pushPose();
        poseStack.setIdentity();

        poseStack.translate(RENDER_SIZE / 2.0f, RENDER_SIZE / 2.0f, 1000.0f);
        RenderSystem.applyModelViewMatrix();

        Lighting.setupForEntityInInventory();

        renderAction.run();

        poseStack.popPose();
        RenderSystem.applyModelViewMatrix();
        Lighting.setupForFlatItems();

        RenderSystem.setProjectionMatrix(oldProjection, VertexSorting.ORTHOGRAPHIC_Z);

        renderTarget.unbindWrite();
        mc.getMainRenderTarget().bindWrite(true);
        RenderSystem.viewport(0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight());

        NativeImage nativeImage = new NativeImage(RENDER_SIZE, RENDER_SIZE, false);

        try (nativeImage) {
            RenderSystem.bindTexture(renderTarget.getColorTextureId());
            nativeImage.downloadTexture(0, false);
            nativeImage.flipY();

            for (int y = 0; y < RENDER_SIZE; y++) {
                for (int x = 0; x < RENDER_SIZE; x++) {
                    int color = nativeImage.getPixelRGBA(x, y);
                    int a = (color >> 24) & 0xFF;
                    int b = (color >> 16) & 0xFF;
                    int g = (color >> 8) & 0xFF;
                    int r = color & 0xFF;

                    if (a == 0) {
                        if (r == 255 && g == 0 && b == 255) {
                            nativeImage.setPixelRGBA(x, y, 0);
                        } else {
                            nativeImage.setPixelRGBA(x, y, color | 0xFF000000);
                        }
                    }
                }
            }

            nativeImage.writeToFile(outputFile);
        } catch (IOException e) {
            Constants.LOG.error("Failed to save generated icon", e);
        } finally {
            renderTarget.destroyBuffers();
        }
    }
}