package dev.nerzul.entityprotection.paper.util;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;

/**
 * A door occupies two vertically adjacent blocks (top/bottom halves) that this
 * plugin treats as a single protection. This resolves the other half of a
 * bisected block, if any.
 */
public final class DoorHalves {

    private DoorHalves() {
    }

    /**
     * @return the other half of the door {@code block} belongs to, or {@code null}
     * if {@code block} is not a bisected block (door).
     */
    public static Block otherHalfOrNull(Block block) {
        if (!(block.getBlockData() instanceof Bisected bisected)) {
            return null;
        }
        return bisected.getHalf() == Bisected.Half.BOTTOM
                ? block.getRelative(BlockFace.UP)
                : block.getRelative(BlockFace.DOWN);
    }
}
