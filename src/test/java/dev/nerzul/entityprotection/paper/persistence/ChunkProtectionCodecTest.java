package dev.nerzul.entityprotection.paper.persistence;

import dev.nerzul.entityprotection.domain.model.BlockPosition;
import dev.nerzul.entityprotection.domain.model.Protection;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkProtectionCodecTest {

    @Test
    void encodingAnEmptyMapProducesAnEmptyString() {
        assertEquals("", ChunkProtectionCodec.encode(Map.of()));
    }

    @Test
    void decodingAnEmptyStringProducesAnEmptyResult() {
        assertTrue(ChunkProtectionCodec.decode("").entries().isEmpty());
        assertTrue(ChunkProtectionCodec.decode(null).entries().isEmpty());
    }

    @Test
    void singleProtectionWithoutTrustedPlayersRoundTrips() {
        UUID owner = UUID.randomUUID();
        BlockPosition position = new BlockPosition("world", 10, 64, -20);
        Map<BlockPosition, Protection> protections = Map.of(position, new Protection(owner));

        String encoded = ChunkProtectionCodec.encode(protections);
        Map<BlockPosition, Protection> decoded = ChunkProtectionCodec.decode(encoded).entries();

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
        Map<BlockPosition, Protection> decoded = ChunkProtectionCodec.decode(encoded).entries();

        assertEquals(2, decoded.size());
        assertTrue(decoded.get(posA).isOwner(ownerA));
        assertTrue(decoded.get(posA).isTrusted(trustedA1));
        assertTrue(decoded.get(posA).isTrusted(trustedA2));
        assertTrue(decoded.get(posB).isOwner(ownerB));
        assertTrue(decoded.get(posB).trustedPlayers().isEmpty());
    }

    @Test
    void worldNameContainingTheFieldSeparatorRoundTrips() {
        UUID owner = UUID.randomUUID();
        BlockPosition position = new BlockPosition("my|world", 1, 2, 3);
        Map<BlockPosition, Protection> protections = Map.of(position, new Protection(owner));

        String encoded = ChunkProtectionCodec.encode(protections);
        Map<BlockPosition, Protection> decoded = ChunkProtectionCodec.decode(encoded).entries();

        assertEquals(1, decoded.size());
        assertTrue(decoded.containsKey(position));
        assertTrue(decoded.get(position).isOwner(owner));
    }

    @Test
    void aMalformedLineIsSkippedWithoutLosingTheOtherLines() {
        UUID owner = UUID.randomUUID();
        BlockPosition good = new BlockPosition("world", 5, 6, 7);
        String goodLine = ChunkProtectionCodec.encode(Map.of(good, new Protection(owner)));
        String data = goodLine + "\n" + "this is not a valid protection line" + "\n" + goodLine.replace("5|6|7", "8|9|10");

        Map<BlockPosition, Protection> decoded = ChunkProtectionCodec.decode(data).entries();

        assertEquals(2, decoded.size());
        assertTrue(decoded.containsKey(good));
        assertTrue(decoded.containsKey(new BlockPosition("world", 8, 9, 10)));
    }

    @Test
    void aTruncatedWorldLengthPrefixIsSkippedWithoutThrowing() {
        UUID owner = UUID.randomUUID();
        BlockPosition good = new BlockPosition("world", 1, 1, 1);
        String goodLine = ChunkProtectionCodec.encode(Map.of(good, new Protection(owner)));
        // Claims a world name of 999 characters but the line is far shorter.
        String truncated = "999:short|1|2|3|0|0";
        String data = truncated + "\n" + goodLine;

        Map<BlockPosition, Protection> decoded = ChunkProtectionCodec.decode(data).entries();

        assertEquals(1, decoded.size());
        assertTrue(decoded.containsKey(good));
    }

    @Test
    void positionExtractableCorruptionIsReportedAsUnknownOwnerNotChunkWide() {
        // Valid world/x/y/z prefix, corrupt owner field.
        String badLine = "5:world|5|6|7|NOTANUMBER|0";

        ChunkProtectionCodec.ChunkDecodeResult result = ChunkProtectionCodec.decode(badLine);

        assertTrue(result.entries().isEmpty());
        assertEquals(1, result.unparseableLines().size());
        assertTrue(result.unknownOwnerPositions().contains(new BlockPosition("world", 5, 6, 7)));
        assertFalse(result.chunkWideCorruption());
    }

    @Test
    void unrecoverableLineTriggersChunkWideCorruption() {
        String badLine = "this is not a valid protection line";

        ChunkProtectionCodec.ChunkDecodeResult result = ChunkProtectionCodec.decode(badLine);

        assertTrue(result.entries().isEmpty());
        assertEquals(1, result.unparseableLines().size());
        assertTrue(result.unknownOwnerPositions().isEmpty());
        assertTrue(result.chunkWideCorruption());
    }

    @Test
    void preservedRawLinesSurviveAReEncodeThatChangesOtherEntries() {
        UUID owner = UUID.randomUUID();
        BlockPosition good = new BlockPosition("world", 1, 1, 1);
        String badLine = "this is not a valid protection line";
        String initialData = ChunkProtectionCodec.encode(Map.of(good, new Protection(owner))) + "\n" + badLine;

        ChunkProtectionCodec.ChunkDecodeResult first = ChunkProtectionCodec.decode(initialData);
        assertEquals(1, first.unparseableLines().size());

        // Simulate saving a brand-new position in the same chunk.
        Map<BlockPosition, Protection> updatedEntries = new HashMap<>(first.entries());
        BlockPosition newPosition = new BlockPosition("world", 9, 9, 9);
        updatedEntries.put(newPosition, new Protection(UUID.randomUUID()));

        String reEncoded = ChunkProtectionCodec.encode(updatedEntries, first.unparseableLines());
        ChunkProtectionCodec.ChunkDecodeResult second = ChunkProtectionCodec.decode(reEncoded);

        assertEquals(2, second.entries().size());
        assertTrue(second.entries().containsKey(good));
        assertTrue(second.entries().containsKey(newPosition));
        assertEquals(1, second.unparseableLines().size());
        assertTrue(second.unparseableLines().contains(badLine));
    }
}
