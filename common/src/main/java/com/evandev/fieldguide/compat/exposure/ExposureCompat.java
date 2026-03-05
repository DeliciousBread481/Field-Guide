package com.evandev.fieldguide.compat.exposure;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.gui.screens.FieldGuideEntryScreen;
import com.evandev.fieldguide.client.gui.screens.FieldGuidePhotographScreen;
import com.evandev.fieldguide.client.gui.widget.FieldGuidePhotographWidget;
import com.evandev.fieldguide.client.progress.ProgressManager;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.data.CompositeFieldGuideEntry;
import com.evandev.fieldguide.network.GrantContentPacket;
import com.evandev.fieldguide.platform.Services;
import com.mojang.blaze3d.vertex.Tesselator;
import io.github.mortuusars.exposure.ExposureClient;
import io.github.mortuusars.exposure.camera.infrastructure.FrameData;
import io.github.mortuusars.exposure.gui.screen.ItemListScreen;
import io.github.mortuusars.exposure.item.PhotographItem;
import io.github.mortuusars.exposure.render.PhotographRenderProperties;
import io.github.mortuusars.exposure.render.PhotographRenderer;
import io.github.mortuusars.exposure.util.ItemAndStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class ExposureCompat {
    private static final ResourceLocation ADD_PHOTO_ICON = new ResourceLocation("fieldguide", "textures/gui/exposure/add_photo.png");
    private static final ResourceLocation MISSING_PHOTOGRAPH_BACKGROUND = new ResourceLocation("fieldguide", "textures/gui/exposure/missing_photograph.png");

    public static void setupExposureWidgets(FieldGuideEntryScreen screen, Object entry) {
        if (!ClientFieldGuideManager.isUnlocked(entry)) return;

        int leftX = screen.getLeftPageBounds().left();
        int leftY = screen.getLeftPageBounds().top();
        int leftWidth = screen.getLeftPageBounds().width();
        int leftHeight = screen.getLeftPageBounds().height();

        int iconSize = 16;
        int iconX = leftX + leftWidth - iconSize - 12;
        int iconY = leftY + 12;

        ItemStack existingPhoto = ProgressManager.getInstance().getPhotograph(entry);

        if (existingPhoto.isEmpty()) {
            if (ModConfig.get().exposureAddPhotographButton) {
                ImageButton addPhotoButton = new ImageButton(iconX, iconY, iconSize, iconSize, 0, 0, iconSize, ADD_PHOTO_ICON, 16, 32, btn -> {
                    openPhotographSelector(screen, entry);
                }, Component.translatable("gui.fieldguide.add_photograph"));

                addPhotoButton.setTooltip(Tooltip.create(Component.translatable("gui.fieldguide.add_photograph")));
                screen.addWidgetPublic(addPhotoButton);
            }
        } else {
            int photoWidth = 108;
            int photoHeight = 108;
            int photoX = leftX + (leftWidth / 2) - (photoWidth / 2);
            int photoY = leftY + (leftHeight / 2) - (photoHeight / 2) - 15;

            Rect2i exposureArea = new Rect2i(photoX + 6, photoY + 6, photoWidth - 12, photoHeight - 12);

            Component tooltipText = Component.empty()
                    .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("Left Click").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("] or [").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("Scroll Up").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("] to View\n").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("Right Click").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("] to Remove").withStyle(ChatFormatting.DARK_GRAY));

            FieldGuidePhotographWidget photoWidget = new FieldGuidePhotographWidget(
                    photoX, photoY, photoWidth, photoHeight,
                    exposureArea,
                    () -> ProgressManager.getInstance().getPhotograph(entry),

                    () -> Minecraft.getInstance().setScreen(new FieldGuidePhotographScreen(screen, List.of(new ItemAndStack<>(existingPhoto)))),

                    () -> {
                        ProgressManager.getInstance().setPhotograph(entry, ItemStack.EMPTY);
                        Minecraft.getInstance().setScreen(new FieldGuideEntryScreen(screen.getParentScreen(), entry));
                    },
                    tooltipText
            );

            screen.addWidgetPublic(photoWidget);
        }
    }

    public static void renderMissingPhotoBackground(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        guiGraphics.blit(MISSING_PHOTOGRAPH_BACKGROUND, x, y, 0, 0,width, height, width, height);
    }

    public static void renderPhotographInGrid(GuiGraphics guiGraphics, int x, int y, int width, int height, ItemStack photograph) {
        if (photograph.getItem() instanceof PhotographItem) {
            PhotographRenderProperties renderProperties = PhotographRenderProperties.get(photograph);
            Rect2i exposureArea = new Rect2i(x + 4, y + 4, width - 8, height - 8);
            int textureSize = 64;
            int sliceSize = width / 2;

            // Paper
            guiGraphics.blit(renderProperties.getPaperTexture(), x, y, 0, 0, 0, sliceSize, sliceSize, textureSize, textureSize);
            guiGraphics.blit(renderProperties.getPaperTexture(), x + sliceSize, y, 0, textureSize - sliceSize, 0, sliceSize, sliceSize, textureSize, textureSize);
            guiGraphics.blit(renderProperties.getPaperTexture(), x, y + sliceSize, 0, 0, textureSize - sliceSize, sliceSize, sliceSize, textureSize, textureSize);
            guiGraphics.blit(renderProperties.getPaperTexture(), x + sliceSize, y + sliceSize, 0, textureSize - sliceSize, textureSize - sliceSize, sliceSize, sliceSize, textureSize, textureSize);

            // Exposure
            guiGraphics.pose().pushPose();
            float scale = exposureArea.getWidth() / (float) ExposureClient.getExposureRenderer().getSize();
            guiGraphics.pose().translate(exposureArea.getX(), exposureArea.getY(), 1);
            guiGraphics.pose().scale(scale, scale, scale);

            MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
            PhotographRenderer.render(photograph, false, false, guiGraphics.pose(),
                    bufferSource, LightTexture.FULL_BRIGHT, 255, 255, 255, 255);
            bufferSource.endBatch();
            guiGraphics.pose().popPose();

            // Paper overlay
            if (renderProperties.hasPaperOverlayTexture()) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0, 0, 2);
                guiGraphics.blit(renderProperties.getPaperOverlayTexture(), x, y, 0, 0, 0, sliceSize, sliceSize, textureSize, textureSize);
                guiGraphics.blit(renderProperties.getPaperOverlayTexture(), x + sliceSize, y, 0, textureSize - sliceSize, 0, sliceSize, sliceSize, textureSize, textureSize);
                guiGraphics.blit(renderProperties.getPaperOverlayTexture(), x, y + sliceSize, 0, 0, textureSize - sliceSize, sliceSize, sliceSize, textureSize, textureSize);
                guiGraphics.blit(renderProperties.getPaperOverlayTexture(), x + sliceSize, y + sliceSize, 0, textureSize - sliceSize, textureSize - sliceSize, sliceSize, sliceSize, textureSize, textureSize);
                guiGraphics.pose().popPose();
            }
        }
    }

    private static void openPhotographSelector(FieldGuideEntryScreen parent, Object entry) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        List<ItemStack> photographs = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof PhotographItem) {
                photographs.add(stack);
            }
        }

        Minecraft.getInstance().setScreen(new PhotographSelectionScreen(parent, photographs, stack -> {
            ProgressManager.getInstance().setPhotograph(entry, stack.copy());
            Minecraft.getInstance().setScreen(new FieldGuideEntryScreen(parent.getParentScreen(), entry));
        }));
    }

    public static boolean hasPhotograph(Object entry) {
        return !ProgressManager.getInstance().getPhotograph(entry).isEmpty();
    }

    public static void onPhotographTaken(Player player, CompoundTag frame) {
        if (!ModConfig.get().exposureUnlockViaPhotograph || !ModConfig.get().exposureUnlockInstantly) return;
        unlockContentInFrame(player, frame);
    }

    public static void onPhotographPrinted(Player player, CompoundTag frame) {
        if (!ModConfig.get().exposureUnlockViaPhotograph || ModConfig.get().exposureUnlockInstantly) return;
        unlockContentInFrame(player, frame);
    }

    private static void unlockContentInFrame(Player player, CompoundTag frame) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        Set<Object> hitTargets = new HashSet<>();

        if (frame.contains(FrameData.ENTITIES_IN_FRAME, Tag.TAG_LIST)) {
            ListTag entities = frame.getList(FrameData.ENTITIES_IN_FRAME, Tag.TAG_COMPOUND);
            for (int i = 0; i < entities.size(); i++) {
                CompoundTag entityTag = entities.getCompound(i);
                String entityIdStr = entityTag.getString(FrameData.ENTITY_ID);
                ResourceLocation entityId = ResourceLocation.tryParse(entityIdStr);
                if (entityId != null) {
                    BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).ifPresent(hitTargets::add);
                }
            }
        }

        double range = 256.0D;
        Vec3 eyePos = player.getEyePosition(1.0F);
        float pitch = player.getXRot();
        float yaw = player.getYRot();

        Vec3 centerViewVec = calculateViewVector(pitch, yaw);
        Vec3 centerEndPos = eyePos.add(centerViewVec.scale(range));
        AABB searchBox = player.getBoundingBox().expandTowards(centerViewVec.scale(range)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player, eyePos, centerEndPos, searchBox,
                (entity) -> !entity.isSpectator() && entity.isPickable(),
                range * range
        );

        if (entityHit != null) {
            Entity hitEntity = entityHit.getEntity();
            if (hitEntity instanceof EnderDragonPart part) hitEntity = part.parentMob;
            hitTargets.add(hitEntity.getType());
        }

        int gridSize = 7;
        float fovSpan = 50.0f;
        float step = fovSpan / (gridSize - 1);

        for (int i = 0; i < gridSize; i++) {
            for (int j = 0; j < gridSize; j++) {
                float rayPitch = pitch - (fovSpan / 2) + (i * step);
                float rayYaw = yaw - (fovSpan / 2) + (j * step);

                Vec3 viewVec = calculateViewVector(rayPitch, rayYaw);
                Vec3 endPos = eyePos.add(viewVec.scale(range));

                Vec3 currentStart = eyePos;

                while (true) {
                    BlockHitResult blockHit = player.level().clip(new ClipContext(currentStart, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

                    if (blockHit.getType() == HitResult.Type.BLOCK) {
                        net.minecraft.world.level.block.state.BlockState state = player.level().getBlockState(blockHit.getBlockPos());

                        hitTargets.add(state.getBlock());

                        if (state.canBeReplaced()) {
                            currentStart = blockHit.getLocation().add(viewVec.scale(0.01));
                            if (eyePos.distanceToSqr(currentStart) >= range * range) break;
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }
        }

        Set<ResourceLocation> unlockedIds = new HashSet<>();

        for (Object target : hitTargets) {
            List<Object> possibleEntries = ClientFieldGuideManager.getInstance().getEntriesForTarget(target);
            if (possibleEntries.isEmpty()) continue;

            Object bestMatch;

            if (possibleEntries.size() == 1) {
                bestMatch = possibleEntries.get(0);
            } else {
                bestMatch = possibleEntries.get(0);
                int maxScore = -1;

                for (Object entry : possibleEntries) {
                    if (entry instanceof CompositeFieldGuideEntry composite) {
                        int score = 0;
                        if (composite.components() != null) {
                            for (Object comp : composite.components()) {
                                if (hitTargets.contains(comp)) score++;
                            }
                        }
                        if (composite.displayEntry() != null && hitTargets.contains(composite.displayEntry())) {
                            score += 2;
                        }

                        if (score > maxScore) {
                            maxScore = score;
                            bestMatch = entry;
                        }
                    }
                }

                if (maxScore == 0) {
                    for (Object entry : possibleEntries) {
                        if (entry instanceof CompositeFieldGuideEntry composite && composite.displayEntry() != null && composite.displayEntry().equals(target)) {
                            bestMatch = entry;
                            break;
                        }
                    }
                }
            }

            ResourceLocation id = ClientFieldGuideManager.getEntryId(bestMatch);
            if (id != null && !ClientFieldGuideManager.isUnlocked(bestMatch)) {
                unlockedIds.add(id);
            }
        }

        for (ResourceLocation id : unlockedIds) {
            Services.NETWORK.sendToPlayer(new GrantContentPacket(GrantContentPacket.Action.GRANT, GrantContentPacket.Type.ENTRY, id), serverPlayer);
        }
    }

    private static Vec3 calculateViewVector(float pitch, float yaw) {
        float f = pitch * ((float) Math.PI / 180F);
        float g = -yaw * ((float) Math.PI / 180F);
        float h = (float) Math.cos(g);
        float i = (float) Math.sin(g);
        float j = (float) Math.cos(f);
        float k = (float) Math.sin(f);
        return new Vec3((i * j), (-k), (h * j));
    }

    private static class PhotographSelectionScreen extends ItemListScreen {
        private final Consumer<ItemStack> onSelect;

        public PhotographSelectionScreen(Screen parent, List<ItemStack> items, Consumer<ItemStack> onSelect) {
            super(parent, Component.translatable("gui.fieldguide.select_photograph"), items);
            this.onSelect = onSelect;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && hoveredSlot != null && hoveredSlot.hasItem()) {
                onSelect.accept(hoveredSlot.getItem());
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }
}