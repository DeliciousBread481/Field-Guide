package com.evandev.fieldguide.client.scanning.manager;

import com.evandev.fieldguide.api.VariantDef;
import com.evandev.fieldguide.api.VariantProvider;
import com.evandev.fieldguide.config.ServerConfig;
import com.evandev.fieldguide.network.ScanUnlockPacket;
import com.evandev.fieldguide.platform.Services;
import com.evandev.fieldguide.api.AutoPopulateRegistry;
import com.evandev.fieldguide.util.FieldGuideVariantManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class FieldGuideScanManager {
    private static final FieldGuideScanManager INSTANCE = new FieldGuideScanManager();

    private FieldGuideScanState currentState = null;

    private FieldGuideScanManager() {}

    public static FieldGuideScanManager getInstance() {
        return INSTANCE;
    }

    public void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            currentState = null;
            return;
        }

        Object target = FieldGuideRaytracer.getScanTarget(mc);
        if (target == null) {
            currentState = null;
            return;
        }

        if (currentState == null || !currentState.target().equals(target)) {
            currentState = new FieldGuideScanState(target, 0, false);
        } else if (!currentState.finished()) {
            float speed = (float) ServerConfig.get().scanSpeed;
            float newProgress = currentState.progress() + (0.05f * speed);

            if (newProgress >= 1.0f) {
                currentState = new FieldGuideScanState(target, 1.0f, true);
                onScanFinished(target);
            } else {
                currentState = new FieldGuideScanState(target, newProgress, false);
            }
        }
    }

    private void onScanFinished(Object target) {
        ResourceLocation id = null;
        ResourceLocation targetId = null;
        String variantId = "";
        BlockPos pos = null;
        int entityId = 0;

        if (target instanceof Entity entity) {
            id = AutoPopulateRegistry.getEntryId(entity.getType());
            targetId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            entityId = entity.getId();

            List<VariantDef> variants = FieldGuideVariantManager.getVariants(entity);
            if (!variants.isEmpty()) {
                VariantProvider<Mob> provider = FieldGuideVariantManager.getProvider(entity);
                if (provider != null) {
                    VariantDef current = provider.getCurrent((Mob) entity);
                    if (current != null) variantId = current.id();
                }
            }
        } else if (target instanceof BlockState state) {
            id = AutoPopulateRegistry.getEntryId(state.getBlock());
            targetId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (Minecraft.getInstance().player != null) {
                pos = Minecraft.getInstance().player.blockPosition();
            }
        }

        if (id != null) {
            Services.NETWORK.sendToServer(new ScanUnlockPacket(id, variantId, targetId, pos, entityId));
        }
    }

    public FieldGuideScanState getCurrentState() {
        return currentState;
    }
}
