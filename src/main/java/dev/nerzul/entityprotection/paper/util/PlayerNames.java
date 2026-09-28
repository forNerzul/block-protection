package dev.nerzul.entityprotection.paper.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Resolves a UUID to a display name for messages, and a typed name back to a
 * UUID for commands. Server runs with {@code online-mode: false}, so names are
 * resolved from online players first, then from Bukkit's local offline-player
 * records; the Mojang API is never contacted.
 */
public final class PlayerNames {

    private PlayerNames() {
    }

    /**
     * UUID -> display name. Uses only local, UUID-keyed lookups (never a name-based
     * lookup that could fall back to a web request).
     */
    public static String resolve(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name != null ? name : uuid.toString();
    }

    /**
     * Name -> UUID, checking online players first, then offline players who have
     * played on this server before. Returns {@code null} if nobody matches.
     */
    public static UUID resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.hasPlayedBefore() && name.equalsIgnoreCase(offline.getName())) {
                return offline.getUniqueId();
            }
        }
        return null;
    }
}
