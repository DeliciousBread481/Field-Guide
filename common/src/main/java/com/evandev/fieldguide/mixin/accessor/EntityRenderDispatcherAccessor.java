package com.evandev.fieldguide.mixin.accessor;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherAccessor {

    @Accessor("shouldRenderShadow")
    boolean fieldguide$shouldRenderShadow();

}
