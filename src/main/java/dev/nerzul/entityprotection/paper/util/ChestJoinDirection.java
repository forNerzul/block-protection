package dev.nerzul.entityprotection.paper.util;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;

/** Chest double-chest partner direction; mirrors vanilla {@code ChestBlock#getConnectedDirection}. */
public final class ChestJoinDirection {

    private static final BlockFace[] CYCLE = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private ChestJoinDirection() {
    }

    public static BlockFace partnerDirection(BlockFace facing, Chest.Type type) {
        return switch (type) {
            case LEFT -> rotate(facing, 1);
            case RIGHT -> rotate(facing, -1);
            case SINGLE -> null;
        };
    }

    private static BlockFace rotate(BlockFace facing, int steps) {
        for (int i = 0; i < CYCLE.length; i++) {
            if (CYCLE[i] == facing) {
                return CYCLE[Math.floorMod(i + steps, CYCLE.length)];
            }
        }
        return facing;
    }
}
