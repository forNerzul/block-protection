package dev.nerzul.blockprotection.paper.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlGlobalTrustRepositoryTest {

    @TempDir
    File dataFolder;

    private UUID owner;
    private UUID trusted;

    @BeforeEach
    void setUp() {
        owner = UUID.randomUUID();
        trusted = UUID.randomUUID();
    }

    private File storageFile() {
        return new File(dataFolder, "global-trust.yml");
    }

    @Test
    void ownerHasNoGlobalTrustByDefault() {
        YamlGlobalTrustRepository repository = new YamlGlobalTrustRepository(storageFile());

        assertTrue(repository.trustedBy(owner).isEmpty());
    }

    @Test
    void trustingAPlayerMakesThemGloballyTrusted() {
        YamlGlobalTrustRepository repository = new YamlGlobalTrustRepository(storageFile());

        repository.trust(owner, trusted);

        assertTrue(repository.trustedBy(owner).contains(trusted));
    }

    @Test
    void untrustingRemovesThePlayer() {
        YamlGlobalTrustRepository repository = new YamlGlobalTrustRepository(storageFile());
        repository.trust(owner, trusted);

        repository.untrust(owner, trusted);

        assertFalse(repository.trustedBy(owner).contains(trusted));
    }

    @Test
    void trustSurvivesReloadingFromDisk() {
        File file = storageFile();
        YamlGlobalTrustRepository first = new YamlGlobalTrustRepository(file);
        first.trust(owner, trusted);

        YamlGlobalTrustRepository reloaded = new YamlGlobalTrustRepository(file);

        Set<UUID> trustedByOwner = reloaded.trustedBy(owner);
        assertTrue(trustedByOwner.contains(trusted));
    }

    @Test
    void untrustingAnUnknownOwnerIsANoOp() {
        YamlGlobalTrustRepository repository = new YamlGlobalTrustRepository(storageFile());

        repository.untrust(owner, trusted);

        assertTrue(repository.trustedBy(owner).isEmpty());
    }

    @Test
    void trustReturnsTrueOnSuccessfulPersist() {
        YamlGlobalTrustRepository repository = new YamlGlobalTrustRepository(storageFile());

        assertTrue(repository.trust(owner, trusted));
    }

    @Test
    void invalidUuidEntriesAreSkippedWithoutThrowing() throws IOException {
        File file = storageFile();
        String yaml = "owners:\n  " + owner + ":\n  - not-a-uuid\n  - " + trusted + "\n";
        Files.writeString(file.toPath(), yaml, StandardCharsets.UTF_8);

        YamlGlobalTrustRepository repository = new YamlGlobalTrustRepository(file);

        Set<UUID> result = repository.trustedBy(owner);
        assertEquals(1, result.size());
        assertTrue(result.contains(trusted));
    }

    @Test
    void aCorruptFileIsBackedUpAndNeverOverwritten() throws IOException {
        File file = storageFile();
        Files.writeString(file.toPath(), "owners: [unterminated flow sequence\n", StandardCharsets.UTF_8);
        long originalLength = file.length();

        YamlGlobalTrustRepository repository = new YamlGlobalTrustRepository(file);
        boolean result = repository.trust(owner, trusted);

        assertFalse(result);
        assertEquals(originalLength, file.length());
        File[] backups = dataFolder.listFiles((dir, name) -> name.startsWith("global-trust.yml.corrupt"));
        assertNotNull(backups);
        assertEquals(1, backups.length);
    }

    @Test
    void aFailedSaveRollsBackTheInMemoryChangeAndReportsFailure() throws IOException {
        File file = storageFile();
        YamlGlobalTrustRepository repository = new YamlGlobalTrustRepository(file);
        assertTrue(repository.trust(owner, trusted));

        // Deterministic, OS- and privilege-independent failure: no file system lets
        // an atomic move replace an existing directory with a regular file, so this
        // fails the same way on every OS and even when running as root, unlike a
        // POSIX read-only permission (which root simply ignores).
        assertTrue(file.delete());
        Files.createDirectory(file.toPath());

        UUID secondTrusted = UUID.randomUUID();
        boolean result = repository.trust(owner, secondTrusted);

        assertFalse(result);
        assertFalse(repository.trustedBy(owner).contains(secondTrusted));
        assertTrue(repository.trustedBy(owner).contains(trusted));
    }
}
