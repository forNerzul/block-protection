package dev.nerzul.blockprotection.domain.policy;

import dev.nerzul.blockprotection.domain.model.Protection;

import java.util.Set;
import java.util.UUID;

/**
 * Pure decision of whether a player may access (open/use/break) a block.
 */
public final class AccessPolicy {

    private AccessPolicy() {
    }

    /**
     * @param requester            the player attempting access
     * @param protection           the block's protection, or {@code null} if the block is unprotected
     * @param globallyTrustedByOwner players the protection's owner has globally trusted (ignored when protection is null)
     * @param adminBypass          whether the requester holds the admin bypass permission
     */
    public static boolean canAccess(UUID requester, Protection protection, Set<UUID> globallyTrustedByOwner, boolean adminBypass) {
        if (adminBypass) {
            return true;
        }
        if (protection == null) {
            return true;
        }
        if (protection.isOwner(requester)) {
            return true;
        }
        if (protection.isTrusted(requester)) {
            return true;
        }
        return globallyTrustedByOwner.contains(requester);
    }
}
