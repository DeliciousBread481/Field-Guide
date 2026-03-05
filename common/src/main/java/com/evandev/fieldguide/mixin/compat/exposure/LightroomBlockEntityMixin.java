package com.evandev.fieldguide.mixin.compat.exposure;

import com.evandev.fieldguide.compat.exposure.ExposureCompat;
import io.github.mortuusars.exposure.block.entity.LightroomBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LightroomBlockEntity.class, remap = false)
public abstract class LightroomBlockEntityMixin {
    @Shadow
    @Nullable
    protected String lastPlayerName;

    @Inject(method = "putPrintResultInOutputSlot", at = @At("HEAD"))
    private void fieldguide$onPutPrintResult(ItemStack printResult, CallbackInfo ci) {
        Level level = ((BlockEntity) (Object) this).getLevel();

        if (level != null && !level.isClientSide && this.lastPlayerName != null) {
            Player player = level.getServer().getPlayerList().getPlayerByName(this.lastPlayerName);
            if (player != null && printResult.hasTag()) {
                ExposureCompat.onPhotographPrinted(player, printResult.getTag());
            }
        }
    }
}