package dev.nerzul.entityprotection.domain.model;

import java.util.Objects;

/**
 * Immutable, Paper-free identifier of a single block in the world.
 *
 * <p>Used as the key for {@link Protection} storage so the domain never needs
 * to know about Bukkit's {@code Location} or {@code Block} types.
 */
public final class BlockPosition {

    private final String world;
    private final int x;
    private final int y;
    private final int z;

    public BlockPosition(String world, int x, int y, int z) {
        this.world = Objects.requireNonNull(world, "world must not be null");
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String world() {
        return world;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    /**
     * Returns the position offset by the given deltas, in the same world.
     */
    public BlockPosition relative(int dx, int dy, int dz) {
        return new BlockPosition(world, x + dx, y + dy, z + dz);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BlockPosition other)) {
            return false;
        }
        return x == other.x && y == other.y && z == other.z && world.equals(other.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, y, z);
    }

    @Override
    public String toString() {
        return world + ":" + x + "," + y + "," + z;
    }
}
