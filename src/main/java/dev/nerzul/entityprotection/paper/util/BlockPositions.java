package dev.nerzul.entityprotection.paper.util;

import dev.nerzul.entityprotection.domain.model.BlockPosition;
import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * Maps Bukkit's {@code Block}/{@code Location} to the domain's Paper-free
 * {@link BlockPosition}.
 */
public final class BlockPositions {

    private BlockPositions() {
    }

    public static BlockPosition of(Block block) {
        return new BlockPosition(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    /**
     * @return the position of the block at this location, or {@code null} if the location has no world
     * (e.g. a detached location, or an inventory held by a non-block entity such as a hopper minecart).
     */
    public static BlockPosition ofLocationOrNull(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return new BlockPosition(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
