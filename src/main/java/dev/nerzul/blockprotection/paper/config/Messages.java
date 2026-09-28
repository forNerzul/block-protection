package dev.nerzul.blockprotection.paper.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Loads the {@code messages.*} section of {@code config.yml} and formats each
 * template (color codes + simple {@code %placeholder%} substitution).
 */
public final class Messages {

    private final FileConfiguration configuration;

    public Messages(FileConfiguration configuration) {
        this.configuration = configuration;
    }

    public String protectedPlace() {
        return format("protected-place");
    }

    public String noAccess(String ownerName) {
        return format("no-access").replace("%owner%", ownerName);
    }

    public String notOwner() {
        return format("not-owner");
    }

    public String trustSingleAdded(String playerName) {
        return format("trust-single-added").replace("%player%", playerName);
    }

    public String trustSingleRemoved(String playerName) {
        return format("trust-single-removed").replace("%player%", playerName);
    }

    public String trustAllAdded(String playerName) {
        return format("trust-all-added").replace("%player%", playerName);
    }

    public String trustAllRemoved(String playerName) {
        return format("trust-all-removed").replace("%player%", playerName);
    }

    public String removed() {
        return format("removed");
    }

    public String infoHeader() {
        return format("info-header");
    }

    public String infoOwner(String ownerName) {
        return format("info-owner").replace("%owner%", ownerName);
    }

    public String infoTrusted(Set<UUID> trusted, java.util.function.Function<UUID, String> nameResolver) {
        String joined = trusted.isEmpty()
                ? "-"
                : trusted.stream().map(nameResolver).collect(Collectors.joining(", "));
        return format("info-trusted").replace("%trusted%", joined);
    }

    public String notProtected() {
        return format("not-protected");
    }

    public String noTargetBlock() {
        return format("no-target-block");
    }

    public String notProtectable() {
        return format("not-protectable");
    }

    public String playerNotFound() {
        return format("player-not-found");
    }

    public String storageError() {
        return format("storage-error");
    }

    private String format(String key) {
        String raw = configuration.getString("messages." + key, "");
        return ChatColor.translateAlternateColorCodes('&', raw);
    }
}
