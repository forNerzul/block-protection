package dev.nerzul.blockprotection.domain.port;

import java.util.Set;
import java.util.UUID;

/**
 * Port for the set of players an owner trusts on <em>all</em> of their protections
 * (as opposed to per-block trust, which lives on {@link dev.nerzul.blockprotection.domain.model.Protection}).
 */
public interface GlobalTrustRepository {

    Set<UUID> trustedBy(UUID owner);

    /**
     * @return {@code true} if the change was persisted, {@code false} if persistence
     * failed and the change was rolled back (nothing changed on disk or in memory).
     */
    boolean trust(UUID owner, UUID trustedPlayer);

    /**
     * @return {@code true} if the change was persisted, {@code false} if persistence
     * failed and the change was rolled back (nothing changed on disk or in memory).
     */
    boolean untrust(UUID owner, UUID trustedPlayer);
}
