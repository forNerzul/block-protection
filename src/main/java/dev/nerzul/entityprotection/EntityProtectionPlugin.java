package dev.nerzul.entityprotection;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point of the EntityProtection plugin.
 *
 * <p>Wiring of domain services, Paper adapters, listeners and commands is added
 * incrementally in later tasks (see odd/tasks/container-protection.md).
 */
public final class EntityProtectionPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("EntityProtection enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("EntityProtection disabled.");
    }
}
