package dev.nerzul.entityprotection.paper.persistence;

import dev.nerzul.entityprotection.domain.model.BlockPosition;
import dev.nerzul.entityprotection.domain.model.Protection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serializes the protections stored in a single chunk to/from a compact string,
 * suitable for a single {@link org.bukkit.persistence.PersistentDataType#STRING}
 * entry on the chunk's {@code PersistentDataContainer}.
 *
 * <p>No Bukkit imports: this is plain text processing over the domain model, so
 * it is unit-tested directly without a running server.
 *
 * <p>Format (one line per protected block): the world name is length-prefixed
 * ({@code <charCount>:<world>}) so it can safely contain the field separator
 * itself, followed by the numeric fields:
 * {@code <len>:world|x|y|z|ownerMsb|ownerLsb|trustedMsb1:trustedLsb1,trustedMsb2:trustedLsb2}
 *
 * <p>This plugin has never shipped a release, so no server has persisted data
 * in the older (unprefixed) format; this format is not backward compatible
 * with it, and there is deliberately no migration path for that reason.
 *
 * <p>Every line is decoded independently: a malformed or truncated line (for
 * example from disk corruption) is logged and skipped rather than discarding
 * every other protection in the same chunk.
 */
public final class ChunkProtectionCodec {

    private static final Logger DEFAULT_LOGGER = Logger.getLogger(ChunkProtectionCodec.class.getName());

    private static final String LINE_SEPARATOR = "\n";
    private static final char FIELD_SEPARATOR_CHAR = '|';
    private static final String FIELD_SEPARATOR = "|";
    private static final String FIELD_SEPARATOR_REGEX = "\\|";
    private static final char WORLD_LENGTH_SEPARATOR = ':';
    private static final String TRUSTED_SEPARATOR = ",";
    private static final String UUID_PART_SEPARATOR = ":";

    private ChunkProtectionCodec() {
    }

    public static String encode(Map<BlockPosition, Protection> protections) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<BlockPosition, Protection> entry : protections.entrySet()) {
            if (!first) {
                builder.append(LINE_SEPARATOR);
            }
            first = false;
            appendLine(builder, entry.getKey(), entry.getValue());
        }
        return builder.toString();
    }

    private static void appendLine(StringBuilder builder, BlockPosition position, Protection protection) {
        String world = position.world();
        builder.append(world.length()).append(WORLD_LENGTH_SEPARATOR).append(world).append(FIELD_SEPARATOR)
                .append(position.x()).append(FIELD_SEPARATOR)
                .append(position.y()).append(FIELD_SEPARATOR)
                .append(position.z()).append(FIELD_SEPARATOR)
                .append(protection.owner().getMostSignificantBits()).append(FIELD_SEPARATOR)
                .append(protection.owner().getLeastSignificantBits()).append(FIELD_SEPARATOR);

        boolean firstTrusted = true;
        for (UUID trusted : protection.trustedPlayers()) {
            if (!firstTrusted) {
                builder.append(TRUSTED_SEPARATOR);
            }
            firstTrusted = false;
            builder.append(trusted.getMostSignificantBits()).append(UUID_PART_SEPARATOR)
                    .append(trusted.getLeastSignificantBits());
        }
    }

    public static Map<BlockPosition, Protection> decode(String data) {
        return decode(data, DEFAULT_LOGGER);
    }

    /**
     * @param logger receives one warning per skipped malformed/truncated line.
     */
    public static Map<BlockPosition, Protection> decode(String data, Logger logger) {
        Map<BlockPosition, Protection> result = new HashMap<>();
        if (data == null || data.isEmpty()) {
            return result;
        }
        for (String line : data.split(LINE_SEPARATOR)) {
            if (line.isBlank()) {
                continue;
            }
            try {
                decodeLine(line, result);
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Skipping malformed protection entry: " + line, e);
            }
        }
        return result;
    }

    private static void decodeLine(String line, Map<BlockPosition, Protection> result) {
        int separatorIndex = line.indexOf(WORLD_LENGTH_SEPARATOR);
        if (separatorIndex < 0) {
            throw new IllegalArgumentException("Missing world length prefix");
        }
        int worldLength = Integer.parseInt(line.substring(0, separatorIndex));
        int worldStart = separatorIndex + 1;
        int worldEnd = worldStart + worldLength;
        // substring() throws StringIndexOutOfBoundsException on a truncated/lying length prefix.
        String world = line.substring(worldStart, worldEnd);
        if (worldEnd >= line.length() || line.charAt(worldEnd) != FIELD_SEPARATOR_CHAR) {
            throw new IllegalArgumentException("Malformed world field terminator");
        }

        String[] fields = line.substring(worldEnd + 1).split(FIELD_SEPARATOR_REGEX, -1);
        if (fields.length < 5) {
            throw new IllegalArgumentException("Not enough fields after the world name");
        }
        int x = Integer.parseInt(fields[0]);
        int y = Integer.parseInt(fields[1]);
        int z = Integer.parseInt(fields[2]);
        long ownerMsb = Long.parseLong(fields[3]);
        long ownerLsb = Long.parseLong(fields[4]);
        UUID owner = new UUID(ownerMsb, ownerLsb);

        Set<UUID> trusted = new HashSet<>();
        if (fields.length > 5 && !fields[5].isEmpty()) {
            for (String pair : fields[5].split(TRUSTED_SEPARATOR)) {
                String[] parts = pair.split(UUID_PART_SEPARATOR);
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Malformed trusted player entry: " + pair);
                }
                trusted.add(new UUID(Long.parseLong(parts[0]), Long.parseLong(parts[1])));
            }
        }

        result.put(new BlockPosition(world, x, y, z), new Protection(owner, trusted));
    }
}
