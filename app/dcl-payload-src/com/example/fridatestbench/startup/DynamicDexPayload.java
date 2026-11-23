package com.example.fridatestbench.startup;

/** Standalone code packaged into local_dcl_payload.jar for DexClassLoader. */
public final class DynamicDexPayload {
    private DynamicDexPayload() {}

    public static String execute() {
        return "Reflection payload executed";
    }
}
