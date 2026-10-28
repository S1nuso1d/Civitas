package ru.civitas;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Minimal vanilla bootstrap for tests that touch item and built-in block registries. */
public final class MinecraftTestBootstrap {
    private MinecraftTestBootstrap() { }

    public static synchronized void initialize() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }
}
