package dev.nerzul.blockprotection.paper.listener;

import dev.nerzul.blockprotection.domain.service.ProtectionService;
import dev.nerzul.blockprotection.paper.util.BlockPositions;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;

/**
 * Cancels a piston extend/retract that would move a protected block, so
 * protected containers/doors cannot be shoved out of their recorded position.
 */
public final class ProtectionPistonListener implements Listener {

    private final ProtectionService protectionService;

    public ProtectionPistonListener(ProtectionService protectionService) {
        this.protectionService = protectionService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent event) {
        if (touchesProtectedBlock(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRetract(BlockPistonRetractEvent event) {
        if (touchesProtectedBlock(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    private boolean touchesProtectedBlock(Iterable<Block> blocks) {
        for (Block block : blocks) {
            if (protectionService.isProtected(BlockPositions.of(block))) {
                return true;
            }
        }
        return false;
    }
}
