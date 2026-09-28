package dev.nerzul.entityprotection.paper.persistence;

import dev.nerzul.entityprotection.domain.model.BlockPosition;
import dev.nerzul.entityprotection.domain.model.Protection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
 * example from disk corruption) is logged and never discarded — its raw text
 * is kept verbatim in {@link ChunkDecodeResult#unparseableLines()} so the
 * caller can re-embed it on every future write instead of erasing it. If the
 * line's position (world/x/y/z) can still be read, it is reported in
 * {@link ChunkDecodeResult#unknownOwnerPositions()}; otherwise
 * {@link ChunkDecodeResult#chunkWideCorruption()} is set. See
 * {@link ChunkProtectionRepository} for how callers fail closed on both.
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

    /**
     * The result of decoding a chunk's raw protection blob.
     *
     * @param entries                successfully decoded protections
     * @param unparseableLines       raw lines that could not be decoded, verbatim, to be re-embedded on the next {@link #encode}
     * @param unknownOwnerPositions  positions recovered from an otherwise-malformed line; callers should deny non-admins there
     * @param chunkWideCorruption    true if at least one malformed line's position could not be recovered at all
     */
    public record ChunkDecodeResult(Map<BlockPosition, Protection> entries, List<String> unparseableLines,
                                     Set<BlockPosition> unknownOwnerPositions, boolean chunkWideCorruption) {
    }

    public static String encode(Map<BlockPosition, Protection> protections) {
        return encode(protections, List.of());
    }

    /**
     * @param preservedRawLines undecodable lines (from a prior {@link #decode}) to re-embed verbatim, so they are never lost.
     */
    public static String encode(Map<BlockPosition, Protection> protections, List<String> preservedRawLines) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<BlockPosition, Protection> entry : protections.entrySet()) {
            if (!first) {
                builder.append(LINE_SEPARATOR);
            }
            first = false;
            appendLine(builder, entry.getKey(), entry.getValue());
        }
        for (String raw : preservedRawLines) {
            if (!first) {
                builder.append(LINE_SEPARATOR);
            }
            first = false;
            builder.append(raw);
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

    public static ChunkDecodeResult decode(String data) {
        return decode(data, DEFAULT_LOGGER);
    }

    /**
     * @param logger receives one warning per malformed/truncated line.
     */
    public static ChunkDecodeResult decode(String data, Logger logger) {
        Map<BlockPosition, Protection> entries = new HashMap<>();
        List<String> unparseableLines = new ArrayList<>();
        Set<BlockPosition> unknownOwnerPositions = new HashSet<>();
        boolean chunkWideCorruption = false;

        if (data != null && !data.isEmpty()) {
            for (String line : data.split(LINE_SEPARATOR)) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    decodeLine(line, entries);
                } catch (RuntimeException e) {
                    logger.log(Level.WARNING, "Preserving unparseable protection entry (never erased): " + line, e);
                    unparseableLines.add(line);
                    BlockPosition position = tryExtractPosition(line);
                    if (position != null) {
                        unknownOwnerPositions.add(position);
                    } else {
                        chunkWideCorruption = true;
                    }
                }
            }
        }
        return new ChunkDecodeResult(entries, unparseableLines, unknownOwnerPositions, chunkWideCorruption);
    }

    /**
     * Best-effort recovery of just the position (world/x/y/z) from a line that
     * failed full decoding, e.g. because only the owner/trusted fields are corrupt.
     */
    private static BlockPosition tryExtractPosition(String line) {
        try {
            int separatorIndex = line.indexOf(WORLD_LENGTH_SEPARATOR);
            if (separatorIndex < 0) {
                return null;
            }
            int worldLength = Integer.parseInt(line.substring(0, separatorIndex));
            int worldStart = separatorIndex + 1;
            int worldEnd = worldStart + worldLength;
            String world = line.substring(worldStart, worldEnd);
            if (worldEnd >= line.length() || line.charAt(worldEnd) != FIELD_SEPARATOR_CHAR) {
                return null;
            }
            String[] fields = line.substring(worldEnd + 1).split(FIELD_SEPARATOR_REGEX, -1);
            if (fields.length < 3) {
                return null;
            }
            int x = Integer.parseInt(fields[0]);
            int y = Integer.parseInt(fields[1]);
            int z = Integer.parseInt(fields[2]);
            return new BlockPosition(world, x, y, z);
        } catch (RuntimeException e) {
            return null;
        }
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
