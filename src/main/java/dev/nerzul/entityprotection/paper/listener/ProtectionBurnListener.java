package dev.nerzul.entityprotection.paper.listener;

import dev.nerzul.entityprotection.domain.service.ProtectionService;
import dev.nerzul.entityprotection.paper.util.BlockPositions;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;

/**
 * Stops fire from consuming a protected block (e.g. a wooden door someone left
 * unprotected fire spreading into a protected chest room).
 */
public final class ProtectionBurnListener implements Listener {

    private final ProtectionService protectionService;

    public ProtectionBurnListener(ProtectionService protectionService) {
        this.protectionService = protectionService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (protectionService.isProtected(BlockPositions.of(event.getBlock()))) {
            event.setCancelled(true);
        }
    }
}
