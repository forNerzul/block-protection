package dev.nerzul.entityprotection.paper.listener;

import dev.nerzul.entityprotection.domain.model.BlockPosition;
import dev.nerzul.entityprotection.domain.service.ProtectionService;
import dev.nerzul.entityprotection.paper.config.Messages;
import dev.nerzul.entityprotection.paper.util.BlockPositions;
import dev.nerzul.entityprotection.paper.util.PlayerNames;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/**
 * Denies opening/using a protected block (chest, furnace, door, ...) to anyone
 * who is not the owner, per-block trusted, globally trusted by the owner, or
 * an admin. A double chest requires passing the check on BOTH halves, so a
 * pre-existing mixed-owner double chest can't be opened from either side.
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

        Player player = event.getPlayer();
        boolean adminBypass = player.hasPermission(ADMIN_PERMISSION);

        for (Block half : doubleChestHalves(block)) {
            BlockPosition position = BlockPositions.of(half);
            if (!protectionService.canAccess(position, player.getUniqueId(), adminBypass)) {
                deny(event, player, position);
                return;
            }
        }
    }

    /**
     * @return both halves of {@code block}'s double chest if it is joined, or just {@code block} otherwise.
     */
    private List<Block> doubleChestHalves(Block block) {
        if (!(block.getState() instanceof Container container)
                || !(container.getInventory().getHolder() instanceof DoubleChest doubleChest)) {
            return List.of(block);
        }
        Block left = blockOf(doubleChest.getLeftSide());
        Block right = blockOf(doubleChest.getRightSide());
        return left == null || right == null ? List.of(block) : List.of(left, right);
    }

    private Block blockOf(InventoryHolder holder) {
        return holder instanceof BlockState state ? state.getBlock() : null;
    }

    private void deny(PlayerInteractEvent event, Player player, BlockPosition position) {
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        protectionService.find(position)
                .ifPresent(value -> player.sendMessage(messages.noAccess(PlayerNames.resolve(value.owner()))));
    }
}
