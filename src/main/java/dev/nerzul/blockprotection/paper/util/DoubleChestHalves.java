package dev.nerzul.blockprotection.paper.util;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.InventoryHolder;

/**
 * A joined double chest is two physically adjacent blocks sharing one
 * combined inventory. This resolves the other half of {@code block}, if it is
 * currently part of a double chest.
 */
public final class DoubleChestHalves {

    private DoubleChestHalves() {
    }

    /**
     * @return the other half of the double chest {@code block} belongs to, or
     * {@code null} if {@code block} is not currently joined into one.
     */
    public static Block otherHalfOrNull(Block block) {
        if (!(block.getState() instanceof Container container)
                || !(container.getInventory().getHolder() instanceof DoubleChest doubleChest)) {
            return null;
        }
        Block left = blockOf(doubleChest.getLeftSide());
        Block right = blockOf(doubleChest.getRightSide());
        if (left == null || right == null) {
            return null;
        }
        if (left.equals(block)) {
            return right;
        }
        return right.equals(block) ? left : null;
    }

    private static Block blockOf(InventoryHolder holder) {
        return holder instanceof BlockState state ? state.getBlock() : null;
    }
}
