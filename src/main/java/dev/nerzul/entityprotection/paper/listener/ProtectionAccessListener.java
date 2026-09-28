package dev.nerzul.entityprotection.paper.listener;

import dev.nerzul.entityprotection.domain.model.BlockPosition;
import dev.nerzul.entityprotection.domain.model.Protection;
import dev.nerzul.entityprotection.domain.service.ProtectionService;
import dev.nerzul.entityprotection.paper.config.Messages;
import dev.nerzul.entityprotection.paper.util.BlockPositions;
import dev.nerzul.entityprotection.paper.util.PlayerNames;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

/**
 * Denies opening/using a protected block (chest, furnace, door, ...) to anyone
 * who is not the owner, per-block trusted, globally trusted by the owner, or
 * an admin.
 */
public final class ProtectionAccessListener implements Listener {

    public static final String ADMIN_PERMISSION = "entityprotection.admin";

    private final ProtectionService protectionService;
    private final Messages messages;

    public ProtectionAccessListener(ProtectionService protectionService, Messages messages) {
        this.protectionService = protectionService;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        BlockPosition position = BlockPositions.of(block);
        Player player = event.getPlayer();
        boolean adminBypass = player.hasPermission(ADMIN_PERMISSION);

        if (protectionService.canAccess(position, player.getUniqueId(), adminBypass)) {
            return;
        }

        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);

        Optional<Protection> protection = protectionService.find(position);
        protection.ifPresent(value -> player.sendMessage(messages.noAccess(PlayerNames.resolve(value.owner()))));
    }
}
