package dev.nerzul.entityprotection.paper.util;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;

/**
 * A door occupies two vertically adjacent blocks (top/bottom halves) that this
 * plugin treats as a single protection. This resolves the other half, if any.
 *
 * <p>Only {@code Door} block data is a real two-block door: {@code TrapDoor}
 * and {@code Stairs} also implement {@code Bisected} but are single-block, so
 * matching on {@code Bisected} would wrongly grab an unrelated neighbor.
 */
public final class DoorHalves {

    private DoorHalves() {
    }

    /**
     * @return the other half of the door {@code block} belongs to, or {@code null}
     * if {@code block} is not an actual (two-block) door.
     */
    public static Block otherHalfOrNull(Block block) {
        if (!(block.getBlockData() instanceof Door door)) {
            return null;
        }
        return door.getHalf() == Bisected.Half.BOTTOM
                ? block.getRelative(BlockFace.UP)
                : block.getRelative(BlockFace.DOWN);
    }
}
