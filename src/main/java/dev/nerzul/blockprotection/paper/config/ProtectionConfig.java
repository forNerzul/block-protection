package dev.nerzul.blockprotection.paper.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed view over the plugin's {@code config.yml}.
 */
public final class ProtectionConfig {

    private final FileConfiguration configuration;

    public ProtectionConfig(FileConfiguration configuration) {
        this.configuration = configuration;
    }

    public boolean protectDoors() {
        return configuration.getBoolean("protect-doors", false);
    }
}
