package com.evandev.fieldguide.client.scanning.manager;

import com.evandev.fieldguide.config.ServerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class FieldGuideRaytracer {

    public static Object getScanTarget(Minecraft mc) {
        if (mc.player == null) return null;

        boolean holdsSpyglass = mc.player.isHolding(Items.SPYGLASS);
        double dist = holdsSpyglass ? ServerConfig.get().spyglassScanDistance : ServerConfig.get().nakedEyeScanDistance;
        boolean canScan = (holdsSpyglass && ServerConfig.get().enableSpyglassScanning) || (!holdsSpyglass && ServerConfig.get().enableNakedEyeScanning);

        if (!canScan) return null;

        HitResult hit = getHitResult(mc, dist);
        if (hit == null) return null;

        if (hit.getType() == HitResult.Type.ENTITY) {
            return ((EntityHitResult) hit).getEntity();
        } else if (hit.getType() == HitResult.Type.BLOCK) {
            return mc.level.getBlockState(((BlockHitResult) hit).getBlockPos());
        }

        return null;
    }

    private static HitResult getHitResult(Minecraft mc, double dist) {
        Entity camera = mc.getCameraEntity();
        if (camera == null) return null;

        Vec3 start = camera.getEyePosition(1.0F);
        Vec3 view = camera.getViewVector(1.0F);
        Vec3 end = start.add(view.x * dist, view.y * dist, view.z * dist);

        HitResult blockHit = camera.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, camera));
        
        double entityDist = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation().distanceTo(start) : dist;
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(camera, start, end, camera.getBoundingBox().expandTowards(view.scale(dist)).inflate(1.0D), (e) -> !e.isSpectator() && e.isPickable(), entityDist);

        if (entityHit != null) return entityHit;
        return blockHit;
    }
}
