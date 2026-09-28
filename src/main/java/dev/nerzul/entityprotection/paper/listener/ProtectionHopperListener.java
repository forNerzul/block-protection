package dev.nerzul.entityprotection.paper.listener;

import dev.nerzul.entityprotection.domain.model.BlockPosition;
import dev.nerzul.entityprotection.domain.service.ProtectionService;
import dev.nerzul.entityprotection.paper.util.BlockPositions;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

/**
 * Bypass prevention for hoppers and hopper minecarts, per
 * {@link dev.nerzul.entityprotection.domain.policy.HopperTransferPolicy}:
 * pulling items out of a protected source is only allowed when the
 * destination is itself protected and its owner already has access to the
 * source (owner, per-block trusted, or globally trusted by the source's
 * owner). A hopper minecart's inventory has no block position, so it can
 * never be protected and can therefore never drain a protected source.
 * Pushing items into a protected destination from an unprotected source is
 * unaffected: there is nothing to leak out of an unprotected source.
 */
public final class ProtectionHopperListener implements Listener {

    private final ProtectionService protectionService;

    public ProtectionHopperListener(ProtectionService protectionService) {
        this.protectionService = protectionService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMoveItem(InventoryMoveItemEvent event) {
        BlockPosition source = BlockPositions.ofLocationOrNull(event.getSource().getLocation());
        BlockPosition destination = BlockPositions.ofLocationOrNull(event.getDestination().getLocation());
        if (!protectionService.canTransfer(source, destination)) {
            event.setCancelled(true);
        }
    }
}
