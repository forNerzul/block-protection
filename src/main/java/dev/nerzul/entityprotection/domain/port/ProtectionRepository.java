package dev.nerzul.entityprotection.domain.port;

import dev.nerzul.entityprotection.domain.model.BlockPosition;
import dev.nerzul.entityprotection.domain.model.Protection;

import java.util.Optional;

/**
 * Port for persisting {@link Protection}s keyed by {@link BlockPosition}.
 */
public interface ProtectionRepository {

    Optional<Protection> find(BlockPosition position);

    void save(BlockPosition position, Protection protection);

    void remove(BlockPosition position);
}
