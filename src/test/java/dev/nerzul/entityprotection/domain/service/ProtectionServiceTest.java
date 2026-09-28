package dev.nerzul.entityprotection.domain.service;

import dev.nerzul.entityprotection.domain.model.BlockPosition;
import dev.nerzul.entityprotection.domain.model.Protection;
import dev.nerzul.entityprotection.domain.port.GlobalTrustRepository;
import dev.nerzul.entityprotection.domain.port.ProtectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionServiceTest {

    private final UUID owner = UUID.randomUUID();
    private final UUID trusted = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final BlockPosition position = new BlockPosition("world", 1, 2, 3);

    private InMemoryProtectionRepository protectionRepository;
    private InMemoryGlobalTrustRepository globalTrustRepository;
    private ProtectionService service;

    @BeforeEach
    void setUp() {
        protectionRepository = new InMemoryProtectionRepository();
        globalTrustRepository = new InMemoryGlobalTrustRepository();
        service = new ProtectionService(protectionRepository, globalTrustRepository);
    }

    @Test
    void protectStoresANewProtectionOwnedByThePlayer() {
        service.protect(position, owner);

        Optional<Protection> stored = protectionRepository.find(position);
        assertTrue(stored.isPresent());
        assertTrue(stored.get().isOwner(owner));
    }

    @Test
    void unprotectedPositionIsAccessibleByAnyone() {
        assertTrue(service.canAccess(position, stranger, false));
    }

    @Test
    void protectedPositionIsNotAccessibleByAStranger() {
        service.protect(position, owner);

        assertFalse(service.canAccess(position, stranger, false));
        assertTrue(service.canAccess(position, owner, false));
    }

    @Test
    void adminBypassAlwaysGrantsAccess() {
        service.protect(position, owner);

        assertTrue(service.canAccess(position, stranger, true));
    }

    @Test
    void globallyTrustedPlayerCanAccessAnyProtectionOfThatOwner() {
        service.protect(position, owner);
        globalTrustRepository.trust(owner, stranger);

        assertTrue(service.canAccess(position, stranger, false));
    }

    @Test
    void onlyOwnerOrAdminCanTrustOnABlock() {
        service.protect(position, owner);

        ModificationResult ownerResult = service.trust(position, owner, trusted, false);
        assertEquals(ModificationResult.SUCCESS, ownerResult);
        assertTrue(service.canAccess(position, trusted, false));

        ModificationResult strangerResult = service.trust(position, stranger, trusted, false);
        assertEquals(ModificationResult.FORBIDDEN, strangerResult);
    }

    @Test
    void trustOnAnUnprotectedBlockReportsNotProtected() {
        ModificationResult result = service.trust(position, owner, trusted, false);

        assertEquals(ModificationResult.NOT_PROTECTED, result);
    }

    @Test
    void adminCanTrustOnBehalfOfTheOwner() {
        service.protect(position, owner);

        ModificationResult result = service.trust(position, stranger, trusted, true);

        assertEquals(ModificationResult.SUCCESS, result);
    }

    @Test
    void untrustRemovesAPreviouslyTrustedPlayer() {
        service.protect(position, owner);
        service.trust(position, owner, trusted, false);

        ModificationResult result = service.untrust(position, owner, trusted, false);

        assertEquals(ModificationResult.SUCCESS, result);
        assertFalse(service.canAccess(position, trusted, false));
    }

    @Test
    void removeProtectionRequiresOwnerOrAdmin() {
        service.protect(position, owner);

        ModificationResult forbidden = service.removeProtection(position, stranger, false);
        assertEquals(ModificationResult.FORBIDDEN, forbidden);
        assertTrue(protectionRepository.find(position).isPresent());

        ModificationResult success = service.removeProtection(position, owner, false);
        assertEquals(ModificationResult.SUCCESS, success);
        assertTrue(protectionRepository.find(position).isEmpty());
    }

    @Test
    void trustAllAddsPlayerToGlobalTrustList() {
        ModificationResult result = service.trustAll(owner, trusted);

        assertEquals(ModificationResult.SUCCESS, result);
        assertTrue(globalTrustRepository.trustedBy(owner).contains(trusted));
    }

    @Test
    void untrustAllRemovesPlayerFromGlobalTrustList() {
        service.trustAll(owner, trusted);

        ModificationResult result = service.untrustAll(owner, trusted);

        assertEquals(ModificationResult.SUCCESS, result);
        assertFalse(globalTrustRepository.trustedBy(owner).contains(trusted));
    }

    private static final class InMemoryProtectionRepository implements ProtectionRepository {
        private final Map<BlockPosition, Protection> store = new HashMap<>();

        @Override
        public Optional<Protection> find(BlockPosition position) {
            return Optional.ofNullable(store.get(position));
        }

        @Override
        public void save(BlockPosition position, Protection protection) {
            store.put(position, protection);
        }

        @Override
        public void remove(BlockPosition position) {
            store.remove(position);
        }
    }

    private static final class InMemoryGlobalTrustRepository implements GlobalTrustRepository {
        private final Map<UUID, Set<UUID>> store = new HashMap<>();

        @Override
        public Set<UUID> trustedBy(UUID owner) {
            return store.getOrDefault(owner, Set.of());
        }

        @Override
        public void trust(UUID owner, UUID trustedPlayer) {
            store.computeIfAbsent(owner, k -> new HashSet<>()).add(trustedPlayer);
        }

        @Override
        public void untrust(UUID owner, UUID trustedPlayer) {
            Set<UUID> existing = store.get(owner);
            if (existing != null) {
                existing.remove(trustedPlayer);
            }
        }
    }
}
