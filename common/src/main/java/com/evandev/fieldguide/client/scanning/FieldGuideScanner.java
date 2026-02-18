package com.evandev.fieldguide.client.scanning;

import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.progress.ProgressManager;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.network.ClaimXpPacket;
import com.evandev.fieldguide.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

import java.util.Objects;
import java.util.Optional;

public class FieldGuideScanner {
    private static final FieldGuideScanner INSTANCE = new FieldGuideScanner();
    private static final int FADE_DURATION = 10;

    private Object scanningTarget = null;
    private int scanTicks = 0;
    private int prevScanTicks = 0;
    private BlockPos scanningPos = null;

    private Object fadingTarget = null;
    private int fadeTicks = 0;
    private BlockPos fadingPos = null;

    private Object outOfRangeTarget = null;

    private FieldGuideScanner() {
    }

    public static FieldGuideScanner getInstance() {
        return INSTANCE;
    }

    public void onClientTick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) return;

        boolean isScanningActive = (minecraft.player.isUsingItem() && minecraft.player.getUseItem().is(Items.SPYGLASS))
                || !ModConfig.get().requireSpyglass;

        if (isScanningActive) {
            processScanning(minecraft);
        } else {
            resetScanState();
        }

        if (fadeTicks > 0) {
            fadeTicks--;
            if (fadeTicks <= 0) {
                fadingTarget = null;
                fadingPos = null;
            }
        }
    }

    private void processScanning(Minecraft minecraft) {
        double range = 256.0D;
        if (minecraft.player == null) return;
        Vec3 eyePos = minecraft.player.getEyePosition(1.0F);
        Vec3 viewVec = minecraft.player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(viewVec.scale(range));

        AABB searchBox = minecraft.player.getBoundingBox().expandTowards(viewVec.scale(range)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                minecraft.player, eyePos, endPos, searchBox,
                (entity) -> !entity.isSpectator() && entity.isPickable(),
                range * range
        );

        BlockHitResult blockHit = Objects.requireNonNull(minecraft.level).clip(new ClipContext(eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));

        while (blockHit.getType() == HitResult.Type.BLOCK) {
            BlockState state = minecraft.level.getBlockState(blockHit.getBlockPos());
            if (state.canBeReplaced()) {
                Vec3 hitVec = blockHit.getLocation();
                Vec3 nextStart = hitVec.add(viewVec.scale(0.01));
                if (eyePos.distanceToSqr(nextStart) >= range * range) break;
                blockHit = minecraft.level.clip(new ClipContext(nextStart, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));
            } else {
                break;
            }
        }

        Object foundTarget = null;
        double entityDist = entityHit != null ? eyePos.distanceToSqr(entityHit.getLocation()) : Double.MAX_VALUE;
        double blockDist = blockHit.getType() != HitResult.Type.MISS ? eyePos.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;
        double hitDistSq = Math.min(entityDist, blockDist);

        if (entityHit != null && entityDist < blockDist) {
            Entity hitEntity = entityHit.getEntity();
            if (hitEntity instanceof EnderDragonPart part) hitEntity = part.parentMob;

            EntityType<?> type = hitEntity.getType();
            Category cat = ClientFieldGuideManager.getInstance().getCategoryForEntry(type);
            boolean isScannable = cat != null;

            TagKey<EntityType<?>> killToUnlockTag = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("fieldguide", "kill_to_unlock"));
            boolean requiresKill = false;
            var key = BuiltInRegistries.ENTITY_TYPE.getResourceKey(type);
            if (key.isPresent()) {
                var holder = BuiltInRegistries.ENTITY_TYPE.getHolder(key.get());
                if (holder.isPresent() && holder.get().is(killToUnlockTag)) requiresKill = true;
            }

            if (ClientFieldGuideManager.getValidEntries().contains(type) && !ProgressManager.getInstance().isUnlocked(type) && isScannable && !requiresKill) {
                foundTarget = hitEntity;
            } else if (!ProgressManager.getInstance().isUnlocked(type) && isScannable && !requiresKill) {
                ResourceLocation originalId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (ModConfig.get().getRedirect(originalId) != null) foundTarget = hitEntity;
            }
        } else if (blockHit.getType() == HitResult.Type.BLOCK) {
            BlockState state = minecraft.level.getBlockState(blockHit.getBlockPos());
            Block block = state.getBlock();
            if (ClientFieldGuideManager.getValidEntries().contains(block) && !ProgressManager.getInstance().isUnlocked(block)) {
                foundTarget = block;
            } else if (!ProgressManager.getInstance().isUnlocked(block)) {
                ResourceLocation originalId = BuiltInRegistries.BLOCK.getKey(block);
                if (ModConfig.get().getRedirect(originalId) != null) foundTarget = block;
            }
        }

        handleTargetAcquisition(minecraft, foundTarget, hitDistSq, blockHit);
    }

    private void handleTargetAcquisition(Minecraft minecraft, Object foundTarget, double hitDistSq, BlockHitResult blockHit) {
        if (foundTarget != null) {
            double activeScanDist = ModConfig.get().scanDistance;
            boolean outOfRange = hitDistSq > (activeScanDist * activeScanDist);

            if (outOfRange) {
                this.outOfRangeTarget = foundTarget;
                resetScanTicks();
            } else {
                this.outOfRangeTarget = null;
                Object targetKey = (foundTarget instanceof Entity) ? ((Entity) foundTarget).getType() : foundTarget;
                boolean sameTarget = (scanningTarget instanceof Entity && foundTarget instanceof Entity)
                        ? scanningTarget == foundTarget
                        : Objects.equals(scanningTarget, foundTarget);

                if (sameTarget) {
                    this.prevScanTicks = this.scanTicks;
                    scanTicks++;

                    if (foundTarget instanceof Block) this.scanningPos = blockHit.getBlockPos();

                    if (scanTicks >= (int) (ModConfig.get().scanSpeed * 20)) {
                        completeScan(minecraft, targetKey, foundTarget);
                    }
                } else {
                    this.prevScanTicks = 0;
                    scanningTarget = foundTarget;
                    this.scanningPos = (scanningTarget instanceof Block) ? blockHit.getBlockPos() : null;

                    if (ModConfig.get().playScanningSound) {
                        Objects.requireNonNull(minecraft.player).playSound(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.0F, 1.0F);
                    }
                    scanTicks = 0;
                }
            }
        } else {
            this.outOfRangeTarget = null;
            if (scanTicks > 0) {
                this.prevScanTicks = this.scanTicks;
                scanTicks -= 2;
                if (scanTicks <= 0) resetScanTicks();
            } else {
                resetScanTicks();
            }
        }

        if (scanningTarget instanceof Entity ent && (ent.isRemoved() || !ent.isAlive())) {
            resetScanTicks();
        }
    }

    private void completeScan(Minecraft minecraft, Object targetKey, Object foundTarget) {
        ResourceLocation targetId = ClientFieldGuideManager.getEntryId(targetKey);
        if (targetId != null) {
            ResourceLocation redirectId = ModConfig.get().getRedirect(targetId);
            if (redirectId != null) {
                Optional<EntityType<?>> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(redirectId);
                if (entityType.isPresent()) {
                    targetKey = entityType.get();
                } else {
                    Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(redirectId);
                    if (block.isPresent()) targetKey = block.get();
                }
            }
        }

        ProgressManager.getInstance().unlock(targetKey);
        Objects.requireNonNull(minecraft.player).playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);

        if (ModConfig.get().grantXpOnScan && ModConfig.get().xpAmountOnScan > 0) {
            Services.NETWORK.sendToServer(new ClaimXpPacket(ModConfig.get().xpAmountOnScan));
        }

        fadingTarget = foundTarget;
        fadingPos = (foundTarget instanceof Block) ? scanningPos : null;
        fadeTicks = FADE_DURATION;

        resetScanTicks();
    }

    private void resetScanState() {
        resetScanTicks();
        outOfRangeTarget = null;
    }

    private void resetScanTicks() {
        scanningTarget = null;
        scanningPos = null;
        scanTicks = 0;
        prevScanTicks = 0;
    }

    public Object getScanningTarget() {
        return scanningTarget;
    }

    public BlockPos getScanningPos() {
        return scanningPos;
    }

    public Entity getScanningEntity() {
        return scanningTarget instanceof Entity ? (Entity) scanningTarget : null;
    }

    public float getScanProgress(float partialTicks) {
        float lerped = (float) prevScanTicks + ((float) scanTicks - (float) prevScanTicks) * partialTicks;
        return Math.min(1.0F, lerped / (int) (ModConfig.get().scanSpeed * 20));
    }

    public Object getOutOfRangeTarget() {
        return outOfRangeTarget;
    }

    public Entity getOutOfRangeEntity() {
        return outOfRangeTarget instanceof Entity ? (Entity) outOfRangeTarget : null;
    }

    public Object getFadingTarget() {
        return fadingTarget;
    }

    public BlockPos getFadingPos() {
        return fadingPos;
    }

    public Entity getFadingEntity() {
        return fadingTarget instanceof Entity ? (Entity) fadingTarget : null;
    }

    public float getFadeProgress(float partialTicks) {
        float currentFade = Math.max(0, fadeTicks - partialTicks);
        return currentFade / (float) FADE_DURATION;
    }

    public boolean getIsTickingDown() {
        return scanTicks < prevScanTicks;
    }
}