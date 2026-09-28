package dev.nerzul.entityprotection.paper.persistence;

import dev.nerzul.entityprotection.domain.model.BlockPosition;
import dev.nerzul.entityprotection.domain.model.Protection;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkProtectionCodecTest {

    @Test
    void encodingAnEmptyMapProducesAnEmptyString() {
        assertEquals("", ChunkProtectionCodec.encode(Map.of()));
    }

    @Test
    void decodingAnEmptyStringProducesAnEmptyMap() {
        assertTrue(ChunkProtectionCodec.decode("").isEmpty());
        assertTrue(ChunkProtectionCodec.decode(null).isEmpty());
    }

    @Test
    void singleProtectionWithoutTrustedPlayersRoundTrips() {
        UUID owner = UUID.randomUUID();
        BlockPosition position = new BlockPosition("world", 10, 64, -20);
        Map<BlockPosition, Protection> protections = Map.of(position, new Protection(owner));

        String encoded = ChunkProtectionCodec.encode(protections);
        Map<BlockPosition, Protection> decoded = ChunkProtectionCodec.decode(encoded);

        assertEquals(1, decoded.size());
        Protection decodedProtection = decoded.get(position);
        assertTrue(decodedProtection.isOwner(owner));
        assertTrue(decodedProtection.trustedPlayers().isEmpty());
    }

    @Test
    void multipleProtectionsWithTrustedPlayersRoundTrip() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        UUID trustedA1 = UUID.randomUUID();
        UUID trustedA2 = UUID.randomUUID();

        BlockPosition posA = new BlockPosition("world", 1, 2, 3);
        BlockPosition posB = new BlockPosition("world", -5, 70, 100);

        Protection protectionA = new Protection(ownerA);
        protectionA.trust(trustedA1);
        protectionA.trust(trustedA2);
        Protection protectionB = new Protection(ownerB);

        Map<BlockPosition, Protection> protections = Map.of(posA, protectionA, posB, protectionB);

        String encoded = ChunkProtectionCodec.encode(protections);
        Map<BlockPosition, Protection> decoded = ChunkProtectionCodec.decode(encoded);

        assertEquals(2, decoded.size());
        assertTrue(decoded.get(posA).isOwner(ownerA));
        assertTrue(decoded.get(posA).isTrusted(trustedA1));
        assertTrue(decoded.get(posA).isTrusted(trustedA2));
        assertTrue(decoded.get(posB).isOwner(ownerB));
        assertTrue(decoded.get(posB).trustedPlayers().isEmpty());
    }
}
