package dev.nerzul.blockprotection.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionTest {

    private final UUID owner = UUID.randomUUID();
    private final UUID trusted = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    @Test
    void ownerIsRecognizedAsOwner() {
        Protection protection = new Protection(owner);

        assertTrue(protection.isOwner(owner));
        assertFalse(protection.isOwner(stranger));
    }

    @Test
    void ownerIsNotTrustedByDefault() {
        Protection protection = new Protection(owner);

        assertFalse(protection.isTrusted(owner));
        assertTrue(protection.trustedPlayers().isEmpty());
    }

    @Test
    void trustingAPlayerMakesThemTrusted() {
        Protection protection = new Protection(owner);

        protection.trust(trusted);

        assertTrue(protection.isTrusted(trusted));
        assertFalse(protection.isTrusted(stranger));
    }

    @Test
    void untrustingRemovesThePlayer() {
        Protection protection = new Protection(owner);
        protection.trust(trusted);

        protection.untrust(trusted);

        assertFalse(protection.isTrusted(trusted));
    }

    @Test
    void trustingTwiceIsIdempotent() {
        Protection protection = new Protection(owner);

        protection.trust(trusted);
        protection.trust(trusted);

        assertTrue(protection.trustedPlayers().size() == 1);
    }

    @Test
    void copyOfConstructorPreservesOwnerAndTrusted() {
        Protection original = new Protection(owner);
        original.trust(trusted);

        Protection copy = new Protection(original.owner(), original.trustedPlayers());

        assertTrue(copy.isOwner(owner));
        assertTrue(copy.isTrusted(trusted));
    }

    @Test
    void ownerMustNotBeNull() {
        assertThrows(NullPointerException.class, () -> new Protection(null));
    }

    @Test
    void sharesOwnerWithIsTrueOnlyForTheSameOwner() {
        Protection ownedByOwner = new Protection(owner);
        Protection alsoOwnedByOwner = new Protection(owner);
        Protection ownedByStranger = new Protection(stranger);

        assertTrue(ownedByOwner.sharesOwnerWith(alsoOwnedByOwner));
        assertFalse(ownedByOwner.sharesOwnerWith(ownedByStranger));
        assertFalse(ownedByOwner.sharesOwnerWith(null));
    }

    @Test
    void unknownOwnerIsTheNilUuidAndNoRealPlayerOwnsIt() {
        Protection unknown = Protection.unknownOwner();

        assertTrue(unknown.isOwner(Protection.UNKNOWN_OWNER));
        assertFalse(unknown.isOwner(owner));
        assertFalse(unknown.isOwner(UUID.randomUUID()));
    }
}
