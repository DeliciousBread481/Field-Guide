package com.evandev.fieldguide.client;

public class ScanRenderState {
    private static final ThreadLocal<Boolean> IS_RENDERING_SCAN = ThreadLocal.withInitial(() -> false);

    public static boolean isScanning() {
        return IS_RENDERING_SCAN.get();
    }

    public static void setScanning(boolean scanning) {
        IS_RENDERING_SCAN.set(scanning);
    }
}