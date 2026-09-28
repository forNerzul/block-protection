package dev.nerzul.entityprotection.domain.port;

import java.util.Set;
import java.util.UUID;

/**
 * Port for the set of players an owner trusts on <em>all</em> of their protections
 * (as opposed to per-block trust, which lives on {@link dev.nerzul.entityprotection.domain.model.Protection}).
 */
public interface GlobalTrustRepository {

    Set<UUID> trustedBy(UUID owner);

    void trust(UUID owner, UUID trustedPlayer);

    void untrust(UUID owner, UUID trustedPlayer);
}
