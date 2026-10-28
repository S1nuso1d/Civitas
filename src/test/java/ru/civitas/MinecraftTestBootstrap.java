package ru.civitas;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Minimal vanilla bootstrap for tests that touch item and built-in block registries. */
public final class MinecraftTestBootstrap {
    private static boolean initialized;

    private MinecraftTestBootstrap() { }

    public static synchronized void initialize() {
        if (initialized) return;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        initialized = true;
    }
}
