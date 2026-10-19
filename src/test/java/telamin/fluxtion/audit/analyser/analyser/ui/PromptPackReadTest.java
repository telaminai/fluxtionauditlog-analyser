package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.PromptPack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** Review P2 N-c (2026-10-17): a dropped pack is read only if it is an ordinary file, and a failed read is said as that. */
class PromptPackReadTest {

    @TempDir
    Path tmp;

    /**
     * ★ Witness: drop the regular-file check in {@code MainFrame.readPromptPack} — the named pipe blocks the read (the
     * preemptive timeout fails), and the folder is reported NOT_READABLE instead of NOT_A_FILE.
     */
    @Test
    @DisplayName("★ a folder or a named pipe is refused before any read; it never blocks")
    void onlyAnOrdinaryFile() throws Exception {
        assertEquals(PromptPack.Cause.NOT_A_FILE, MainFrame.readPromptPack(tmp).refusal().cause());
        Path fifo = tmp.resolve("pack.md");
        Process mk = new ProcessBuilder("mkfifo", fifo.toString()).start();
        assertEquals(0, mk.waitFor());
        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                assertEquals(PromptPack.Cause.NOT_A_FILE, MainFrame.readPromptPack(fifo).refusal().cause()));
    }

    @Test
    @DisplayName("an unreadable file is NOT_READABLE, never 'too large'; a good file parses")
    void honestCauses() throws Exception {
        Path locked = Files.writeString(tmp.resolve("locked.md"), "# T\n\n1. **A** — b\n");
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
        try {
            if (!Files.isReadable(locked)) {
                assertEquals(PromptPack.Cause.NOT_READABLE, MainFrame.readPromptPack(locked).refusal().cause());
            }
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rw-------"));
        }
        assertTrue(MainFrame.readPromptPack(locked).ok());
        assertEquals(PromptPack.Cause.NOT_A_FILE, MainFrame.readPromptPack(tmp.resolve("missing.md")).refusal().cause());
    }
}
