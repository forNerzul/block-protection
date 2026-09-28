package dev.nerzul.entityprotection.paper.listener;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import dev.nerzul.entityprotection.domain.service.ProtectionService;
import dev.nerzul.entityprotection.paper.util.BlockPositions;
import dev.nerzul.entityprotection.paper.util.DoorHalves;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Cleans up orphaned door protection data when a protected door is destroyed
 * by natural physics (its supporting block disappearing through a path this
 * plugin does not otherwise intercept, water erosion, a piston reaching it
 * from a direction not already covered, ...) rather than by a player directly
 * breaking it.
 *
 * <p>This does not cancel the destruction: {@link BlockDestroyEvent} has no
 * player to check access against, so fighting vanilla physics here would be
 * both wrong (nothing to authorize against) and version-fragile. The
 * meaningful, safe action is keeping stored protection data from going stale
 * once the physical door is gone.
 */
public final class ProtectionPhysicsCleanupListener implements Listener {

    private final ProtectionService protectionService;

    public ProtectionPhysicsCleanupListener(ProtectionService protectionService) {
        this.protectionService = protectionService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onNaturalDestroy(BlockDestroyEvent event) {
        Block block = event.getBlock();
        Block otherHalf = DoorHalves.otherHalfOrNull(block);
        if (otherHalf == null) {
            return;
        }
        protectionService.forceRemoveProtection(BlockPositions.of(block));
        protectionService.forceRemoveProtection(BlockPositions.of(otherHalf));
    }
}
