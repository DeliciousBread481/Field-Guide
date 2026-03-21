package com.evandev.fieldguide.compat.exposure;

import com.evandev.fieldguide.api.CompositeFieldGuideEntry;
import com.evandev.fieldguide.api.EntryUnlockData;
import com.evandev.fieldguide.api.VariantDef;
import com.evandev.fieldguide.api.VariantProvider;
import com.evandev.fieldguide.config.ServerConfig;
import com.evandev.fieldguide.server.ServerFieldGuideManager;
import com.evandev.fieldguide.server.progress.FieldGuideProgressManager;
import com.evandev.fieldguide.server.progress.PlayerFieldGuideProgress;
import com.evandev.fieldguide.util.EntryResolver;
import com.evandev.fieldguide.util.FieldGuideVariantManager;
import io.github.mortuusars.exposure.world.camera.frame.EntityInFrame;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.item.PhotographItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExposureCompat {

    public static void onPhotographTaken(Player player, Frame frame) {
        if (!ServerConfig.get().exposureUnlockViaPhotograph) return;
        unlockContentInFrame(player, frame);
    }

    private static void unlockContentInFrame(Player player, Frame frame) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        Map<Object, String> hitTargets = new HashMap<>();

        if (frame != null && frame.entitiesInFrame() != null) {
            for (EntityInFrame entityInFrame : frame.entitiesInFrame()) {
                ResourceLocation entityId = entityInFrame.id();
                if (entityId != null) {
                    BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).ifPresent(type -> hitTargets.put(type, null));
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

            String variantId = null;
            if (hitEntity instanceof Mob mob) {
                VariantProvider<Mob> provider = FieldGuideVariantManager.getProvider(mob);
                if (provider != null) {
                    VariantDef current = provider.getCurrent(mob);
                    if (current != null) {
                        variantId = current.id();
                    }
                }
            }

            hitTargets.put(hitEntity.getType(), variantId);
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
                        BlockState state = player.level().getBlockState(blockHit.getBlockPos());

                        hitTargets.put(state.getBlock(), null);

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

        PlayerFieldGuideProgress progress = FieldGuideProgressManager.getInstance().getProgress(serverPlayer);
        if (progress == null) return;

        for (Map.Entry<Object, String> targetEntry : hitTargets.entrySet()) {
            Object target = targetEntry.getKey();
            String variantId = targetEntry.getValue();

            List<Object> possibleEntries = ServerFieldGuideManager.getInstance().getEntriesForTarget(target);
            if (possibleEntries.isEmpty()) continue;

            Object bestMatch;

            if (possibleEntries.size() == 1) {
                bestMatch = possibleEntries.getFirst();
            } else {
                bestMatch = possibleEntries.getFirst();
                int maxScore = -1;

                for (Object entry : possibleEntries) {
                    if (entry instanceof CompositeFieldGuideEntry composite) {
                        int score = 0;
                        if (composite.components() != null) {
                            for (Object comp : composite.components()) {
                                if (hitTargets.containsKey(comp)) score++;
                            }
                        }
                        if (composite.displayEntry() != null && hitTargets.containsKey(composite.displayEntry())) {
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

            ResourceLocation id = EntryResolver.getEntryId(bestMatch);
            if (id != null) {
                progress.tryUnlock(serverPlayer, id, variantId, EntryUnlockData.UnlockTrigger.SCAN);
            }
        }
    }

    public static boolean isPhotographItem(ItemStack stack) {
        return stack.getItem() instanceof PhotographItem;
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
}