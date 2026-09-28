package dev.nerzul.blockprotection.paper.persistence;

import dev.nerzul.blockprotection.domain.model.BlockPosition;
import dev.nerzul.blockprotection.domain.model.Protection;
import dev.nerzul.blockprotection.domain.policy.AutoProtectPolicy;
import dev.nerzul.blockprotection.domain.policy.ProtectableCategory;
import dev.nerzul.blockprotection.domain.policy.ProtectableTypePolicy;
import dev.nerzul.blockprotection.domain.port.ProtectionRepository;
import dev.nerzul.blockprotection.paper.config.ProtectionConfig;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
 * <p>Hot path: the decoded per-chunk record is cached by chunk coordinates so a
 * chunk with heavy hopper/access traffic is decoded at most once between
 * writes; the cache entry is replaced (not merely invalidated) on every write
 * and dropped entirely when the chunk unloads, so stale data cannot linger.
 * A chunk with no protection data at all is never decoded: its cache entry is
 * an empty record created directly from the PDC's {@code has()} check.
 *
 * <p>Corrupt data fails closed and is never erased (see README "Corrupt data"):
 * every write re-embeds the chunk's undecodable raw lines verbatim, exactly as
 * {@link ChunkProtectionCodec} returned them, alongside whatever entries
 * changed. A position recovered from an otherwise-malformed line, or — for a
 * line whose position could not be recovered at all — every unrecorded
 * protectable block in that chunk, is reported as protected by
 * {@link Protection#UNKNOWN_OWNER}, which denies every non-admin (admin
 * bypass, checked first by {@code AccessPolicy}, is unaffected).
 */
public final class ChunkProtectionRepository implements ProtectionRepository, Listener {

    private final NamespacedKey key;
    private final Logger logger;
    private final ProtectableTypePolicy classifier;
    private final ProtectionConfig config;
    private final Map<ChunkKey, ChunkRecord> cache = new ConcurrentHashMap<>();

    public ChunkProtectionRepository(Plugin plugin, ProtectableTypePolicy classifier, ProtectionConfig config) {
        this.key = new NamespacedKey(plugin, "protections");
        this.logger = plugin.getLogger();
        this.classifier = classifier;
        this.config = config;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public Optional<Protection> find(BlockPosition position) {
        Chunk chunk = chunkOf(position);
        if (chunk == null) {
            return Optional.empty();
        }
        ChunkRecord record = readAll(chunk);

        Protection direct = record.entries().get(position);
        if (direct != null) {
            return Optional.of(direct);
        }
        if (record.unknownOwnerPositions().contains(position)) {
            return Optional.of(Protection.unknownOwner());
        }
        if (record.chunkWideCorruption() && isProtectable(chunk, position)) {
            return Optional.of(Protection.unknownOwner());
        }
        return Optional.empty();
    }

    @Override
    public void save(BlockPosition position, Protection protection) {
        Chunk chunk = chunkOf(position);
        if (chunk == null) {
            throw new IllegalStateException("Cannot save protection: world '" + position.world() + "' is not loaded");
        }
        ChunkRecord record = readAll(chunk);
        Map<BlockPosition, Protection> entries = new HashMap<>(record.entries());
        entries.put(position, protection);
        writeAll(chunk, record.withEntries(entries));
    }

    @Override
    public void remove(BlockPosition position) {
        Chunk chunk = chunkOf(position);
        if (chunk == null) {
            return;
        }
        ChunkRecord record = readAll(chunk);
        Map<BlockPosition, Protection> entries = new HashMap<>(record.entries());
        if (entries.remove(position) != null) {
            writeAll(chunk, record.withEntries(entries));
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

    private boolean isProtectable(Chunk chunk, BlockPosition position) {
        Block block = chunk.getWorld().getBlockAt(position.x(), position.y(), position.z());
        ProtectableCategory category = classifier.classify(block.getType().name());
        return AutoProtectPolicy.isAutoProtectable(category, config.protectDoors());
    }

    private ChunkRecord readAll(Chunk chunk) {
        return cache.computeIfAbsent(ChunkKey.of(chunk), unused -> decode(chunk));
    }

    private ChunkRecord decode(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (!pdc.has(key, PersistentDataType.STRING)) {
            return new ChunkRecord(Map.of(), List.of(), Set.of(), false);
        }
        String raw = pdc.get(key, PersistentDataType.STRING);
        ChunkProtectionCodec.ChunkDecodeResult decoded = ChunkProtectionCodec.decode(raw, logger);
        if (decoded.chunkWideCorruption()) {
            logger.severe(() -> "Chunk " + chunk.getWorld().getName() + " (" + chunk.getX() + "," + chunk.getZ()
                    + ") has unrecoverable corrupt protection data: every protectable block without its own"
                    + " record in this chunk is denied to non-admins until an operator fixes the data"
                    + " (see README \"Corrupt data\").");
        }
        return new ChunkRecord(new HashMap<>(decoded.entries()), decoded.unparseableLines(),
                decoded.unknownOwnerPositions(), decoded.chunkWideCorruption());
    }

    private void writeAll(Chunk chunk, ChunkRecord record) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        String encoded = ChunkProtectionCodec.encode(record.entries(), record.preservedRawLines());
        if (encoded.isEmpty()) {
            pdc.remove(key);
        } else {
            pdc.set(key, PersistentDataType.STRING, encoded);
        }
        cache.put(ChunkKey.of(chunk), record);
    }

    private record ChunkKey(String world, int x, int z) {
        static ChunkKey of(Chunk chunk) {
            return new ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        }
    }

    /**
     * A chunk's full decoded state: valid entries, plus everything needed to
     * keep failing closed and never erasing corrupt data across writes.
     */
    private record ChunkRecord(Map<BlockPosition, Protection> entries, List<String> preservedRawLines,
                                Set<BlockPosition> unknownOwnerPositions, boolean chunkWideCorruption) {
        ChunkRecord withEntries(Map<BlockPosition, Protection> newEntries) {
            return new ChunkRecord(newEntries, preservedRawLines, unknownOwnerPositions, chunkWideCorruption);
        }
    }
}
