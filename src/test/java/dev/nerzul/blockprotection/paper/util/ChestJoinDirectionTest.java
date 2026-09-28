package dev.nerzul.blockprotection.paper.util;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChestJoinDirectionTest {

    @ParameterizedTest
    @CsvSource({"NORTH,LEFT,EAST", "EAST,LEFT,SOUTH", "SOUTH,LEFT,WEST", "WEST,LEFT,NORTH",
            "NORTH,RIGHT,WEST", "EAST,RIGHT,NORTH", "SOUTH,RIGHT,EAST", "WEST,RIGHT,SOUTH"})
    void matchesVanilla(BlockFace facing, Chest.Type type, BlockFace expected) {
        assertEquals(expected, ChestJoinDirection.partnerDirection(facing, type));
    }

    @Test
    void single() {
        assertNull(ChestJoinDirection.partnerDirection(BlockFace.NORTH, Chest.Type.SINGLE));
    }
}
