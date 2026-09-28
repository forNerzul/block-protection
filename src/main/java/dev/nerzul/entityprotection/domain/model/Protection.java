package dev.nerzul.entityprotection.domain.model;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A protected block: who owns it, and which players the owner has explicitly
 * trusted on this specific block (as opposed to global trust, which lives in
 * {@link dev.nerzul.entityprotection.domain.port.GlobalTrustRepository}).
 *
 * <p>Pure domain model: no Paper/Bukkit imports.
 */
public final class Protection {

    private final UUID owner;
    private final Set<UUID> trustedPlayers;

    public Protection(UUID owner) {
        this(owner, Set.of());
    }

    public Protection(UUID owner, Set<UUID> trustedPlayers) {
        this.owner = Objects.requireNonNull(owner, "owner must not be null");
        this.trustedPlayers = new HashSet<>(Objects.requireNonNull(trustedPlayers, "trustedPlayers must not be null"));
    }

    public UUID owner() {
        return owner;
    }

    public boolean isOwner(UUID player) {
        return owner.equals(player);
    }

    public boolean isTrusted(UUID player) {
        return trustedPlayers.contains(player);
    }

    public void trust(UUID player) {
        Objects.requireNonNull(player, "player must not be null");
        trustedPlayers.add(player);
    }

    public void untrust(UUID player) {
        trustedPlayers.remove(player);
    }

    /**
     * Returns an unmodifiable snapshot of the players trusted on this block.
     */
    public Set<UUID> trustedPlayers() {
        return Set.copyOf(trustedPlayers);
    }
}
