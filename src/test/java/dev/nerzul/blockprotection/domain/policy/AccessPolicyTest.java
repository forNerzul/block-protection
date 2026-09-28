package dev.nerzul.blockprotection.domain.policy;

import dev.nerzul.blockprotection.domain.model.Protection;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessPolicyTest {

    private final UUID owner = UUID.randomUUID();
    private final UUID trusted = UUID.randomUUID();
    private final UUID globallyTrusted = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    @Test
    void unprotectedBlockIsAlwaysAccessible() {
        assertTrue(AccessPolicy.canAccess(stranger, null, Set.of(), false));
    }

    @Test
    void ownerCanAccessTheirOwnProtection() {
        Protection protection = new Protection(owner);

        assertTrue(AccessPolicy.canAccess(owner, protection, Set.of(), false));
    }

    @Test
    void perBlockTrustedPlayerCanAccess() {
        Protection protection = new Protection(owner);
        protection.trust(trusted);

        assertTrue(AccessPolicy.canAccess(trusted, protection, Set.of(), false));
    }

    @Test
    void globallyTrustedByOwnerCanAccess() {
        Protection protection = new Protection(owner);

        assertTrue(AccessPolicy.canAccess(globallyTrusted, protection, Set.of(globallyTrusted), false));
    }

    @Test
    void strangerCannotAccessProtectedBlock() {
        Protection protection = new Protection(owner);

        assertFalse(AccessPolicy.canAccess(stranger, protection, Set.of(), false));
    }

    @Test
    void adminBypassOverridesEverything() {
        Protection protection = new Protection(owner);

        assertTrue(AccessPolicy.canAccess(stranger, protection, Set.of(), true));
    }

    @Test
    void unknownOwnerSentinelDeniesEveryRealPlayerButNotAdmins() {
        Protection unknown = Protection.unknownOwner();

        assertFalse(AccessPolicy.canAccess(stranger, unknown, Set.of(), false));
        assertFalse(AccessPolicy.canAccess(owner, unknown, Set.of(), false));
        assertTrue(AccessPolicy.canAccess(stranger, unknown, Set.of(), true));
    }
}
