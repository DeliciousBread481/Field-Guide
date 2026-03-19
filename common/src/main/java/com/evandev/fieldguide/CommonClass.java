package com.evandev.fieldguide;

import com.evandev.fieldguide.item.ModItems;
import com.evandev.fieldguide.server.progress.FieldGuideTriggers;

public class CommonClass {

    public static void init() {
        ModItems.init();
        FieldGuideTriggers.init();
    }
}
