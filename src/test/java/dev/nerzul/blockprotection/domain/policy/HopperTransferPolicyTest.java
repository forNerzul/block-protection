package dev.nerzul.blockprotection.domain.policy;

import dev.nerzul.blockprotection.domain.model.Protection;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HopperTransferPolicyTest {

    private final UUID owner = UUID.randomUUID();
    private final UUID trusted = UUID.randomUUID();
    private final UUID globallyTrusted = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();

    @Test
    void unprotectedSourceIsAlwaysAllowed() {
        Protection destination = new Protection(stranger);

        assertTrue(HopperTransferPolicy.isTransferAllowed(null, destination, Set.of()));
        assertTrue(HopperTransferPolicy.isTransferAllowed(null, null, Set.of()));
    }

    @Test
    void protectedSourceWithUnprotectedDestinationIsDenied() {
        Protection source = new Protection(owner);

        assertFalse(HopperTransferPolicy.isTransferAllowed(source, null, Set.of()));
    }

    @Test
    void destinationOwnedBySameOwnerIsAllowed() {
        Protection source = new Protection(owner);
        Protection destination = new Protection(owner);

        assertTrue(HopperTransferPolicy.isTransferAllowed(source, destination, Set.of()));
    }

    @Test
    void destinationOwnedByAPlayerTrustedOnTheSourceIsAllowed() {
        Protection source = new Protection(owner);
        source.trust(trusted);
        Protection destination = new Protection(trusted);

        assertTrue(HopperTransferPolicy.isTransferAllowed(source, destination, Set.of()));
    }

    @Test
    void destinationOwnedByAPlayerGloballyTrustedByTheSourceOwnerIsAllowed() {
        Protection source = new Protection(owner);
        Protection destination = new Protection(globallyTrusted);

        assertTrue(HopperTransferPolicy.isTransferAllowed(source, destination, Set.of(globallyTrusted)));
    }

    @Test
    void destinationOwnedByAStrangerIsDenied() {
        Protection source = new Protection(owner);
        Protection destination = new Protection(stranger);

        assertFalse(HopperTransferPolicy.isTransferAllowed(source, destination, Set.of()));
    }
}
