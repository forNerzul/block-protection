package dev.nerzul.entityprotection.paper.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
