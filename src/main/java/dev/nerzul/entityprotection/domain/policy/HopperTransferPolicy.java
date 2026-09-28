package dev.nerzul.entityprotection.domain.policy;

import dev.nerzul.entityprotection.domain.model.Protection;

import java.util.Set;
import java.util.UUID;

/**
 * Bypass-prevention rule for hoppers and hopper minecarts moving items
 * between two inventories.
 *
 * <p>Pulling items out of a protected source is only allowed when the
 * destination is itself protected and its owner already has access to the
 * source (owner, per-block trusted, or globally trusted by the source's
 * owner) — the same {@link AccessPolicy} used for a player opening the
 * source directly. An unprotected destination (which includes every hopper
 * minecart: its inventory has no block position, so it can never be
 * protected) is always denied. Pushing items into a protected destination
 * from an unprotected source is unaffected by this rule (there is nothing to
 * leak out of an unprotected source).
 */
public final class HopperTransferPolicy {

    private HopperTransferPolicy() {
    }

    /**
     * @param source                 the source inventory's protection, or {@code null} if unprotected
     * @param destination             the destination inventory's protection, or {@code null} if unprotected
     * @param globallyTrustedBySourceOwner players the source's owner globally trusts (ignored if source is null)
     */
    public static boolean isTransferAllowed(Protection source, Protection destination,
                                             Set<UUID> globallyTrustedBySourceOwner) {
        if (source == null) {
            return true;
        }
        if (destination == null) {
            return false;
        }
        return AccessPolicy.canAccess(destination.owner(), source, globallyTrustedBySourceOwner, false);
    }
}
