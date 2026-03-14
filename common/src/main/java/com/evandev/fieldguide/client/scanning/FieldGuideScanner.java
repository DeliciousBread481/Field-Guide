package com.evandev.fieldguide.client.scanning;

import com.evandev.fieldguide.Constants;
import com.evandev.fieldguide.client.ClientFieldGuideManager;
import com.evandev.fieldguide.client.FieldGuideClient;
import com.evandev.fieldguide.client.progress.ProgressManager;
import com.evandev.fieldguide.config.ModConfig;
import com.evandev.fieldguide.data.Category;
import com.evandev.fieldguide.data.CompositeFieldGuideEntry;
import com.evandev.fieldguide.network.ScanUnlockPacket;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.util.ModTags;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

import java.util.*;

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
    private BlockPos outOfRangePos = null;

    private BlockPos lastDisambiguatedPos = null;
    private Object lastDisambiguatedEntry = null;

    private FieldGuideScanner() {
    }

    public static FieldGuideScanner getInstance() {
        return INSTANCE;
    }

    public void onClientTick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) return;

        boolean hasSpyglass = minecraft.player.isUsingItem() && minecraft.player.getUseItem().is(ModTags.Items.SPYGLASSES);
        boolean canScan = (hasSpyglass && ModConfig.get().enableSpyglassScanning) || ModConfig.get().enableNakedEyeScanning;
        boolean isScanningActive = canScan && !ModConfig.get().disableScanning;

        if (FieldGuideClient.SCAN_KEY != null && !FieldGuideClient.SCAN_KEY.isUnbound()) {
            isScanningActive = isScanningActive && FieldGuideClient.SCAN_KEY.isDown();
        }

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
        BlockHitResult firstBlockHit = blockHit;

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
            ResourceLocation originalId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            ResourceLocation redirectId = ClientFieldGuideManager.getInstance().getRedirect(originalId);

            Object actualTargetKey = type;
            if (redirectId != null) {
                Optional<EntityType<?>> opt = BuiltInRegistries.ENTITY_TYPE.getOptional(redirectId);
                if (opt.isPresent()) actualTargetKey = opt.get();
                else {
                    Optional<Block> optBlock = BuiltInRegistries.BLOCK.getOptional(redirectId);
                    if (optBlock.isPresent()) actualTargetKey = optBlock.get();
                }
            }

            Object entryForTarget = getContextAwareEntry(actualTargetKey, minecraft, hitEntity.blockPosition());
            Category cat = ClientFieldGuideManager.getInstance().getCategoryForEntry(entryForTarget);
            boolean isScannable = cat != null;

            TagKey<EntityType<?>> killToUnlockTag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "kill_to_unlock"));
            boolean requiresKill = false;
            if (actualTargetKey instanceof EntityType<?> actualType) {
                var key = BuiltInRegistries.ENTITY_TYPE.getResourceKey(actualType);
                if (key.isPresent()) {
                    var holder = BuiltInRegistries.ENTITY_TYPE.getHolder(key.get());
                    if (holder.isPresent() && holder.get().is(killToUnlockTag)) requiresKill = true;
                }
            }

            if (entryForTarget != null && !ProgressManager.getInstance().isUnlocked(entryForTarget) && isScannable && !requiresKill) {
                foundTarget = hitEntity;
            }
        } else {
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                BlockState state = minecraft.level.getBlockState(blockHit.getBlockPos());
                Block block = state.getBlock();

                ResourceLocation originalId = BuiltInRegistries.BLOCK.getKey(block);
                ResourceLocation redirectId = ClientFieldGuideManager.getInstance().getRedirect(originalId);

                Object actualTargetKey = block;
                if (redirectId != null) {
                    Optional<Block> opt = BuiltInRegistries.BLOCK.getOptional(redirectId);
                    if (opt.isPresent()) actualTargetKey = opt.get();
                    else {
                        Optional<EntityType<?>> optEntity = BuiltInRegistries.ENTITY_TYPE.getOptional(redirectId);
                        if (optEntity.isPresent()) actualTargetKey = optEntity.get();
                    }
                }

                Object entryForTarget = getContextAwareEntry(actualTargetKey, minecraft, blockHit.getBlockPos());
                Category cat = ClientFieldGuideManager.getInstance().getCategoryForEntry(entryForTarget);
                if (entryForTarget != null && !ProgressManager.getInstance().isUnlocked(entryForTarget) && cat != null) {
                    foundTarget = block;
                }
            }

            if (foundTarget == null && firstBlockHit.getType() == HitResult.Type.BLOCK && !firstBlockHit.getBlockPos().equals(blockHit.getBlockPos())) {
                BlockState state = minecraft.level.getBlockState(firstBlockHit.getBlockPos());
                Block block = state.getBlock();

                ResourceLocation originalId = BuiltInRegistries.BLOCK.getKey(block);
                ResourceLocation redirectId = ClientFieldGuideManager.getInstance().getRedirect(originalId);

                Object actualTargetKey = block;
                if (redirectId != null) {
                    Optional<Block> opt = BuiltInRegistries.BLOCK.getOptional(redirectId);
                    if (opt.isPresent()) actualTargetKey = opt.get();
                    else {
                        Optional<EntityType<?>> optEntity = BuiltInRegistries.ENTITY_TYPE.getOptional(redirectId);
                        if (optEntity.isPresent()) actualTargetKey = optEntity.get();
                    }
                }

                Object entryForTarget = getContextAwareEntry(actualTargetKey, minecraft, firstBlockHit.getBlockPos());
                Category cat = ClientFieldGuideManager.getInstance().getCategoryForEntry(entryForTarget);
                if (entryForTarget != null && !ProgressManager.getInstance().isUnlocked(entryForTarget) && cat != null) {
                    foundTarget = block;
                    blockHit = firstBlockHit;
                    hitDistSq = eyePos.distanceToSqr(firstBlockHit.getLocation());
                }
            }
        }

        handleTargetAcquisition(minecraft, foundTarget, hitDistSq, blockHit);
    }

    private void handleTargetAcquisition(Minecraft minecraft, Object foundTarget, double hitDistSq, BlockHitResult blockHit) {
        if (foundTarget != null) {
            boolean usingSpyglass = minecraft.player != null && minecraft.player.isUsingItem() && minecraft.player.getUseItem().is(ModTags.Items.SPYGLASSES);

            double activeScanDist = 0;
            if (usingSpyglass && ModConfig.get().enableSpyglassScanning) {
                activeScanDist = ModConfig.get().spyglassScanDistance;
            } else if (ModConfig.get().enableNakedEyeScanning) {
                activeScanDist = ModConfig.get().nakedEyeScanDistance;
            }

            boolean outOfRange = hitDistSq > (activeScanDist * activeScanDist);

            if (outOfRange) {
                this.outOfRangeTarget = foundTarget;
                this.outOfRangePos = (foundTarget instanceof Block) ? blockHit.getBlockPos() : null;
                resetScanTicks();
            } else {
                this.outOfRangeTarget = null;
                this.outOfRangePos = null;

                BlockPos posContext = (foundTarget instanceof Block) ? blockHit.getBlockPos() : ((Entity) foundTarget).blockPosition();
                Object targetKey = getContextAwareEntry((foundTarget instanceof Entity) ? ((Entity) foundTarget).getType() : foundTarget, minecraft, posContext);

                if (targetKey == null)
                    targetKey = (foundTarget instanceof Entity) ? ((Entity) foundTarget).getType() : foundTarget;

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
                        Objects.requireNonNull(minecraft.player).playSound(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 0.5F, 1.0F);
                    }
                    scanTicks = 0;
                }
            }
        } else {
            this.outOfRangeTarget = null;
            this.outOfRangePos = null;
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

    private Object getContextAwareEntry(Object target, Minecraft minecraft, BlockPos pos) {
        List<Object> possibleEntries = ClientFieldGuideManager.getInstance().getEntriesForTarget(target);
        if (possibleEntries.isEmpty()) return null;

        if (pos != null && pos.equals(lastDisambiguatedPos)) {
            return lastDisambiguatedEntry;
        }

        Object result = disambiguateComposite(minecraft, pos, possibleEntries, target);
        lastDisambiguatedPos = pos;
        lastDisambiguatedEntry = result;
        return result;
    }

    private Object disambiguateComposite(Minecraft minecraft, BlockPos hitPos, List<Object> possibleEntries, Object actualTargetKey) {
        if (minecraft.level == null || hitPos == null) return possibleEntries.getFirst();

        Object bestMatch = null;
        int maxScore = 0;

        int radius = 4;
        Map<Object, Integer> scoreMap = new HashMap<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = hitPos.offset(dx, dy, dz);
                    BlockState state = minecraft.level.getBlockState(pos);
                    Block block = state.getBlock();

                    if (block.equals(actualTargetKey)) continue;

                    for (Object entry : possibleEntries) {
                        if (entry instanceof CompositeFieldGuideEntry composite) {
                            if (composite.components() != null && composite.components().contains(block)) {
                                scoreMap.put(entry, scoreMap.getOrDefault(entry, 0) + 1);
                            } else if (composite.displayEntry() != null && composite.displayEntry().equals(block)) {
                                scoreMap.put(entry, scoreMap.getOrDefault(entry, 0) + 2);
                            }
                        }
                    }
                }
            }
        }

        for (Map.Entry<Object, Integer> entryScore : scoreMap.entrySet()) {
            if (entryScore.getValue() > maxScore) {
                maxScore = entryScore.getValue();
                bestMatch = entryScore.getKey();
            }
        }

        if (maxScore == 0) {
            for (Object entry : possibleEntries) {
                if (entry.equals(actualTargetKey)) return entry;
                if (entry instanceof CompositeFieldGuideEntry composite && composite.displayEntry() != null && composite.displayEntry().equals(actualTargetKey)) {
                    return entry;
                }
            }
            return actualTargetKey;
        }

        return bestMatch != null ? bestMatch : possibleEntries.getFirst();
    }

    private void completeScan(Minecraft minecraft, Object targetKey, Object foundTarget) {
        ResourceLocation targetId = ClientFieldGuideManager.getEntryId(targetKey);
        if (targetId != null) {
            ResourceLocation redirectId = ClientFieldGuideManager.getInstance().getRedirect(targetId);
            if (redirectId != null) {
                Optional<EntityType<?>> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(redirectId);
                if (entityType.isPresent()) {
                    targetKey = entityType.get();
                } else {
                    Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(redirectId);
                    if (block.isPresent()) targetKey = block.get();
                }

                Object resolvedTarget = ClientFieldGuideManager.getInstance().getEntryForTarget(targetKey);
                if (resolvedTarget != null) {
                    targetKey = resolvedTarget;
                }
            }
        }

        Objects.requireNonNull(minecraft.player).playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);

        ResourceLocation entryId = ClientFieldGuideManager.getEntryId(targetKey);
        if (entryId != null) {
            ResourceLocation scannedTargetId;
            if (foundTarget instanceof Entity entity) {
                scannedTargetId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            } else {
                scannedTargetId = BuiltInRegistries.BLOCK.getKey((Block) foundTarget);
            }
            BlockPos targetBlockPos = (foundTarget instanceof Block) ? scanningPos : null;
            int targetEntityId = (foundTarget instanceof Entity) ? ((Entity) foundTarget).getId() : 0;
            Services.NETWORK.sendToServer(new ScanUnlockPacket(entryId, scannedTargetId, targetBlockPos, targetEntityId));
        }

        fadingTarget = foundTarget;
        fadingPos = (foundTarget instanceof Block) ? scanningPos : null;
        fadeTicks = FADE_DURATION;

        resetScanTicks();
    }

    private void resetScanState() {
        resetScanTicks();
        outOfRangeTarget = null;
        outOfRangePos = null;
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

    public BlockPos getOutOfRangePos() {
        return outOfRangePos;
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