package dev.nerzul.entityprotection.paper.listener;

import dev.nerzul.entityprotection.domain.model.BlockPosition;
import dev.nerzul.entityprotection.domain.model.Protection;
import dev.nerzul.entityprotection.domain.service.ProtectionService;
import dev.nerzul.entityprotection.paper.util.BlockPositions;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bypass prevention for hoppers and hopper minecarts.
 *
 * <p>Decision (documented in README): pulling items FROM a protected container
 * is only allowed when the destination inventory (the hopper / hopper minecart)
 * is itself protected by the <em>same</em> owner — otherwise it is cancelled.
 * Pushing items INTO a protected container is always left allowed: the source
 * side is not protected in that direction, so it cannot leak anything out of a
 * protected block, and blocking it would break the common "feed a protected
 * chest from an unprotected hopper" setup.
 */
public final class ProtectionHopperListener implements Listener {

    private static final Logger LOGGER = Logger.getLogger(ProtectionHopperListener.class.getName());

    private final ProtectionService protectionService;

    public ProtectionHopperListener(ProtectionService protectionService) {
        this.protectionService = protectionService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMoveItem(InventoryMoveItemEvent event) {
        try {
            Optional<UUID> sourceOwner = ownerOf(event.getSource());
            if (sourceOwner.isEmpty()) {
                return;
            }

            Optional<UUID> destinationOwner = ownerOf(event.getDestination());
            if (destinationOwner.isPresent() && destinationOwner.get().equals(sourceOwner.get())) {
                return;
            }

            event.setCancelled(true);
        } catch (RuntimeException e) {
            // Fail closed: an unreadable lookup must not look like "nothing protected here".
            LOGGER.log(Level.SEVERE, "Protection lookup failed while checking a hopper move; cancelling (fail closed)", e);
            event.setCancelled(true);
        }
    }

    private Optional<UUID> ownerOf(Inventory inventory) {
        BlockPosition position = BlockPositions.ofLocationOrNull(inventory.getLocation());
        if (position == null) {
            return Optional.empty();
        }
        return protectionService.find(position).map(Protection::owner);
    }
}
