package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FluxtionKeyStoreTest {

    @TempDir Path temp;

    @Test
    void writesEstablishedFormat_reportsPresence_andWipesCallerArray() throws Exception {
        FluxtionKeyStore store = new FluxtionKeyStore(temp.resolve(".fluxtion"));
        char[] key = "m19-secret-value".toCharArray();

        store.save(key);

        assertTrue(store.keyPresent());
        assertArrayEquals(new char[key.length], key, "the caller-owned credential buffer is wiped");
        Properties p = new Properties();
        try (var in = Files.newInputStream(store.canonicalFile())) { p.load(in); }
        assertEquals("m19-secret-value", p.getProperty("apiKey"));
        if (Files.getFileStore(store.canonicalFile()).supportsFileAttributeView("posix")) {
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(store.canonicalFile()));
        }
    }

    @Test
    void profilesCanBeActivated_withoutAnyReadApiForTheValue() throws Exception {
        FluxtionKeyStore store = new FluxtionKeyStore(temp.resolve(".fluxtion"));
        store.saveProfileAndActivate("work", "work-secret".toCharArray());
        store.saveProfileAndActivate("evaluation", "eval-secret".toCharArray());

        assertEquals(java.util.List.of("evaluation", "work"), store.profiles());
        assertEquals("evaluation", store.activeProfile());
        store.activate("work");
        assertEquals("work", store.activeProfile());
        assertTrue(store.keyPresent());
        assertTrue(java.util.Arrays.stream(FluxtionKeyStore.class.getMethods())
                .noneMatch(m -> m.getName().toLowerCase().contains("readkey")
                        || m.getName().toLowerCase().contains("getkey")));
    }

    @Test
    void aBlankOrPlaceholderPropertyIsNotReportedAsConfigured() throws Exception {
        FluxtionKeyStore store = new FluxtionKeyStore(temp.resolve(".fluxtion"));
        Files.createDirectories(store.canonicalFile().getParent());
        Files.writeString(store.canonicalFile(), "apiKey=MISSING_KEY\n");
        assertFalse(store.keyPresent());
        Files.writeString(store.canonicalFile(), "host=localhost\n");
        assertFalse(store.keyPresent());
    }

    @Test
    void profileNamesCannotEscapeTheProfilesDirectory() {
        FluxtionKeyStore store = new FluxtionKeyStore(temp.resolve(".fluxtion"));
        char[] rejectedKey = "secret".toCharArray();
        assertThrows(IllegalArgumentException.class,
                () -> store.saveProfileAndActivate("../outside", rejectedKey));
        assertArrayEquals(new char[rejectedKey.length], rejectedKey,
                "the credential buffer is wiped even when profile-name validation fails");
        assertThrows(IllegalArgumentException.class, () -> store.activate("work/other"));
    }
}
