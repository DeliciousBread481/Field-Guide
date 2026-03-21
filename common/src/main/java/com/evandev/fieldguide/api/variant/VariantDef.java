package com.evandev.fieldguide.api.variant;

import net.minecraft.network.chat.Component;

public record VariantDef(String id, Object value) {
    public VariantDef(String id, String translationKey) {
        this(id, Component.translatable(translationKey));
    }

    public Component getName() {
        if (value instanceof Component c) return c;
        return Component.literal(id);
    }
}
