package dev.nerzul.entityprotection.paper.persistence;

import dev.nerzul.entityprotection.domain.model.BlockPosition;
import dev.nerzul.entityprotection.domain.model.Protection;
import dev.nerzul.entityprotection.domain.port.ProtectionRepository;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Optional;

/**
 * Paper adapter storing protections in the {@code PersistentDataContainer} of
 * the chunk each protected block belongs to, keyed by block position within
 * that chunk's serialized blob (see {@link ChunkProtectionCodec}).
 *
 * <p>Chunk PDC works even for blocks that are not a {@code TileState} (doors,
 * trapdoors, fence gates have no block entity / PDC of their own), which is
 * why this plugin does not use per-block PDC.
 */
public final class ChunkProtectionRepository implements ProtectionRepository {

    private final NamespacedKey key;

    public ChunkProtectionRepository(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "protections");
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
        Map<BlockPosition, Protection> all = readAll(chunk);
        all.put(position, protection);
        writeAll(chunk, all);
    }

    @Override
    public void remove(BlockPosition position) {
        Chunk chunk = chunkOf(position);
        if (chunk == null) {
            return;
        }
        Map<BlockPosition, Protection> all = readAll(chunk);
        if (all.remove(position) != null) {
            writeAll(chunk, all);
        }
    }

    private Chunk chunkOf(BlockPosition position) {
        World world = Bukkit.getWorld(position.world());
        if (world == null) {
            return null;
        }
        return world.getChunkAt(position.x() >> 4, position.z() >> 4);
    }

    private Map<BlockPosition, Protection> readAll(Chunk chunk) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        String raw = pdc.get(key, PersistentDataType.STRING);
        return ChunkProtectionCodec.decode(raw);
    }

    private void writeAll(Chunk chunk, Map<BlockPosition, Protection> all) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (all.isEmpty()) {
            pdc.remove(key);
        } else {
            pdc.set(key, PersistentDataType.STRING, ChunkProtectionCodec.encode(all));
        }
    }
}
