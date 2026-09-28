package dev.nerzul.blockprotection.paper.listener;

import dev.nerzul.blockprotection.domain.service.ProtectionService;
import dev.nerzul.blockprotection.paper.util.BlockPositions;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Keeps protected blocks out of explosions (creeper/TNT/bed/respawn anchor/...),
 * whether the explosion was caused by an entity or by a block, and out of
 * direct non-explosion entity destruction (a wither or ravager breaking
 * blocks, an enderman picking one up, a zombie breaking a door, ...).
 */
public final class ProtectionExplosionListener implements Listener {

    private final ProtectionService protectionService;

    public ProtectionExplosionListener(ProtectionService protectionService) {
        this.protectionService = protectionService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isProtected);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isProtected);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isProtected(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private boolean isProtected(Block block) {
        return protectionService.isProtected(BlockPositions.of(block));
    }
}
