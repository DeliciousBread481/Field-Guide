package com.evandev.fieldguide.api;

import net.minecraft.network.chat.Component;

public record VariantDef(String id, Object value) {
    public VariantDef(String id, String translationKey) {
        this(id, (Object) Component.translatable(translationKey));
    }

    public Component getName() {
        if (value instanceof Component c) return c;
        return Component.literal(id);
    }
}
