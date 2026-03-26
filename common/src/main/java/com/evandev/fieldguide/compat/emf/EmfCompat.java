package com.evandev.fieldguide.compat.emf;

import traben.entity_model_features.models.animation.EMFAnimationEntityContext;

public class EmfCompat {

    public static void setInGui(boolean inGui) {
        try {
            EMFAnimationEntityContext.setIsInGui = inGui;
        } catch (Throwable ignored) {
        }
    }
}