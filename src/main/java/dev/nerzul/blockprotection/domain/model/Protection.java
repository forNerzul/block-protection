package dev.nerzul.blockprotection.domain.model;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A protected block: who owns it, and which players the owner has explicitly
 * trusted on this specific block (as opposed to global trust, which lives in
 * {@link dev.nerzul.blockprotection.domain.port.GlobalTrustRepository}).
 *
 * <p>Pure domain model: no Paper/Bukkit imports.
 */
public final class Protection {

    /**
     * Sentinel owner for a protection record that could not be safely decoded
     * (e.g. storage corruption): no real player is ever assigned the nil UUID,
     * so {@link dev.nerzul.blockprotection.domain.policy.AccessPolicy} denies
     * every non-admin against it while admin bypass (checked first) still works.
     */
    public static final UUID UNKNOWN_OWNER = new UUID(0L, 0L);

    private final UUID owner;
    private final Set<UUID> trustedPlayers;

    public Protection(UUID owner) {
        this(owner, Set.of());
    }

    public Protection(UUID owner, Set<UUID> trustedPlayers) {
        this.owner = Objects.requireNonNull(owner, "owner must not be null");
        this.trustedPlayers = new HashSet<>(Objects.requireNonNull(trustedPlayers, "trustedPlayers must not be null"));
    }

    /** @return a protection owned by the {@link #UNKNOWN_OWNER} sentinel. */
    public static Protection unknownOwner() {
        return new Protection(UNKNOWN_OWNER);
    }

    public UUID owner() {
        return owner;
    }

    public boolean isOwner(UUID player) {
        return owner.equals(player);
    }

    /**
     * Guard used before mirroring an operation onto a paired block (a door's
     * other half, a double chest's other half): never act on {@code other} if
     * it belongs to a different owner.
     */
    public boolean sharesOwnerWith(Protection other) {
        return other != null && owner.equals(other.owner);
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
