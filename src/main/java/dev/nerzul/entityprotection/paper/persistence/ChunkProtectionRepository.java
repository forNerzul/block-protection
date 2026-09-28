package dev.nerzul.entityprotection.paper.persistence;

import dev.nerzul.entityprotection.domain.model.BlockPosition;
import dev.nerzul.entityprotection.domain.model.Protection;
import dev.nerzul.entityprotection.domain.port.ProtectionRepository;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Paper adapter storing protections in the {@code PersistentDataContainer} of
 * the chunk each protected block belongs to, keyed by block position within
 * that chunk's serialized blob (see {@link ChunkProtectionCodec}).
 *
 * <p>Chunk PDC works even for blocks that are not a {@code TileState} (doors,
 * trapdoors, fence gates have no block entity / PDC of their own), which is
 * why this plugin does not use per-block PDC.
 *
 * <p>Hot path: the decoded per-chunk map is cached by chunk coordinates so a
 * chunk with heavy hopper/access traffic is decoded at most once between
 * writes; the cache entry is replaced (not merely invalidated) on every write
 * and dropped entirely when the chunk unloads, so stale data cannot linger.
 * A chunk with no protection data at all is never decoded: its cache entry is
 * an empty map created directly from the PDC's {@code has()} check.
 */
public final class ChunkProtectionRepository implements ProtectionRepository, Listener {

    private final NamespacedKey key;
    private final Logger logger;
    private final Map<ChunkKey, Map<BlockPosition, Protection>> cache = new ConcurrentHashMap<>();

    public ChunkProtectionRepository(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "protections");
        this.logger = plugin.getLogger();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public Optional<Protection> find(BlockPosition position) {
        Chunk chunk = chunkOf(position);
        if (chunk == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(readAll(chunk).get(position));
    }

    @Override
    public void save(BlockPosition position, Protection protection) {
        Chunk chunk = chunkOf(position);
        if (chunk == null) {
            throw new IllegalStateException("Cannot save protection: world '" + position.world() + "' is not loaded");
        }
        Map<BlockPosition, Protection> all = new HashMap<>(readAll(chunk));
        all.put(position, protection);
        writeAll(chunk, all);
    }

    @Override
    public void remove(BlockPosition position) {
        Chunk chunk = chunkOf(position);
        if (chunk == null) {
            return;
        }
        Map<BlockPosition, Protection> all = new HashMap<>(readAll(chunk));
        if (all.remove(position) != null) {
            writeAll(chunk, all);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        cache.remove(ChunkKey.of(event.getChunk()));
    }

    private Chunk chunkOf(BlockPosition position) {
        World world = Bukkit.getWorld(position.world());
        if (world == null) {
            return null;
        }
        return world.getChunkAt(position.x() >> 4, position.z() >> 4);
    }

    private Map<BlockPosition, Protection> readAll(Chunk chunk) {
        return cache.computeIfAbsent(ChunkKey.of(chunk), unused -> decode(chunk));
    }

    private Map<BlockPosition, Protection> decode(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (!pdc.has(key, PersistentDataType.STRING)) {
            return new HashMap<>();
        }
        String raw = pdc.get(key, PersistentDataType.STRING);
        return new HashMap<>(ChunkProtectionCodec.decode(raw, logger));
    }

    private void writeAll(Chunk chunk, Map<BlockPosition, Protection> all) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (all.isEmpty()) {
            pdc.remove(key);
        } else {
            pdc.set(key, PersistentDataType.STRING, ChunkProtectionCodec.encode(all));
        }
        cache.put(ChunkKey.of(chunk), all);
    }

    private record ChunkKey(String world, int x, int z) {
        static ChunkKey of(Chunk chunk) {
            return new ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        }
    }
}
