package dev.nerzul.entityprotection.paper.listener;

import dev.nerzul.entityprotection.domain.model.BlockPosition;
import dev.nerzul.entityprotection.domain.model.Protection;
import dev.nerzul.entityprotection.domain.service.ProtectionService;
import dev.nerzul.entityprotection.paper.config.Messages;
import dev.nerzul.entityprotection.paper.util.BlockPositions;
import dev.nerzul.entityprotection.paper.util.DoorHalves;
import dev.nerzul.entityprotection.paper.util.PlayerNames;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static dev.nerzul.entityprotection.paper.listener.ProtectionAccessListener.ADMIN_PERMISSION;

/**
 * Denies breaking a protected block to anyone without access, and cleans up
 * protection data (both halves, for a door) once an authorized break goes
 * through. Also denies breaking the block a protected, inaccessible door
 * rests on: Minecraft pops the door off (and drops it) the instant its
 * supporting block is removed, which would otherwise bypass the door's own
 * protection entirely.
 *
 * <p>The deny/cancel decision runs at the default priority, but the data
 * deletion runs separately at {@link EventPriority#MONITOR}: another plugin
 * may still cancel the break at a later priority, and deleting protection
 * data before that final outcome is known would erase it for a block that,
 * after all, survives.
 */
public final class ProtectionBreakListener implements Listener {

    private static final Logger LOGGER = Logger.getLogger(ProtectionBreakListener.class.getName());

    private final ProtectionService protectionService;
    private final Messages messages;

    public ProtectionBreakListener(ProtectionService protectionService, Messages messages) {
        this.protectionService = protectionService;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        BlockPosition position = BlockPositions.of(block);

        Optional<Protection> protection;
        try {
            protection = protectionService.find(position);
        } catch (RuntimeException e) {
            // Fail closed: an unreadable lookup must not look like "unprotected".
            LOGGER.log(Level.SEVERE, "Protection lookup failed for " + position + "; denying the break (fail closed)", e);
            event.setCancelled(true);
            return;
        }
        Player player = event.getPlayer();
        boolean adminBypass = player.hasPermission(ADMIN_PERMISSION);

        if (protection.isPresent() && !protectionService.canAccess(position, player.getUniqueId(), adminBypass)) {
            event.setCancelled(true);
            player.sendMessage(messages.noAccess(PlayerNames.resolve(protection.get().owner())));
            return;
        }

        BlockPosition supportedDoor = supportedInaccessibleDoor(block, player, adminBypass);
        if (supportedDoor != null) {
            event.setCancelled(true);
            protectionService.find(supportedDoor)
                    .ifPresent(doorProtection -> player.sendMessage(messages.noAccess(PlayerNames.resolve(doorProtection.owner()))));
        }
    }

    /**
     * Cleanup only, once the break's final (not-cancelled) outcome is known.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakFinal(BlockBreakEvent event) {
        Block block = event.getBlock();
        BlockPosition position = BlockPositions.of(block);

        Optional<Protection> protection;
        try {
            protection = protectionService.find(position);
        } catch (RuntimeException e) {
            // Nothing safe to do at MONITOR (too late to cancel): leave the data as-is.
            LOGGER.log(Level.SEVERE, "Protection lookup failed for " + position + "; skipping cleanup", e);
            return;
        }
        if (protection.isEmpty()) {
            return;
        }

        protectionService.forceRemoveProtection(position);

        Block otherHalf = DoorHalves.otherHalfOrNull(block);
        if (otherHalf != null && otherHalf.getType() == block.getType()) {
            protectionService.find(BlockPositions.of(otherHalf))
                    .filter(protection.get()::sharesOwnerWith)
                    .ifPresent(ignored -> protectionService.forceRemoveProtection(BlockPositions.of(otherHalf)));
        }
    }

    /**
     * @return the position of the door bottom-half resting on {@code block},
     * if breaking {@code block} would pop it off and the breaker cannot
     * access it; {@code null} otherwise (no door on top, or it's accessible).
     */
    private BlockPosition supportedInaccessibleDoor(Block block, Player player, boolean adminBypass) {
        Block above = block.getRelative(BlockFace.UP);
        if (!(above.getBlockData() instanceof Door door) || door.getHalf() != Bisected.Half.BOTTOM) {
            return null;
        }
        BlockPosition doorPosition = BlockPositions.of(above);
        return protectionService.canAccess(doorPosition, player.getUniqueId(), adminBypass) ? null : doorPosition;
    }
}
