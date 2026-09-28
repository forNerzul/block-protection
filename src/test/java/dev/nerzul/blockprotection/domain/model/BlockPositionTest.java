package dev.nerzul.blockprotection.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BlockPositionTest {

    @Test
    void twoPositionsWithSameWorldAndCoordinatesAreEqual() {
        BlockPosition a = new BlockPosition("world", 10, 64, -5);
        BlockPosition b = new BlockPosition("world", 10, 64, -5);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void positionsInDifferentWorldsAreNotEqual() {
        BlockPosition a = new BlockPosition("world", 10, 64, -5);
        BlockPosition b = new BlockPosition("world_nether", 10, 64, -5);

        assertNotEquals(a, b);
    }

    @Test
    void positionsWithDifferentCoordinatesAreNotEqual() {
        BlockPosition a = new BlockPosition("world", 10, 64, -5);
        BlockPosition b = new BlockPosition("world", 11, 64, -5);

        assertNotEquals(a, b);
    }

    @Test
    void relativeAppliesOffsetOnEachAxis() {
        BlockPosition a = new BlockPosition("world", 10, 64, -5);

        BlockPosition above = a.relative(0, 1, 0);

        assertEquals(new BlockPosition("world", 10, 65, -5), above);
    }

    @Test
    void worldNameMustNotBeNull() {
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new BlockPosition(null, 0, 0, 0));
    }
}
