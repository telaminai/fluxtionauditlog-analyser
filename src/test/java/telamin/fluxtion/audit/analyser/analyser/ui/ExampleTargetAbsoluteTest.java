package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review PR #150 R6: a relative folder typed into the example confirmation resolved against the analyser's working
 * directory — a place the person never sees. It is refused; an absolute new folder is still accepted.
 */
class ExampleTargetAbsoluteTest {

    /** ★ Witness: drop the NOT_ABSOLUTE check — a relative name is accepted (null) and this fails. */
    @Test
    @DisplayName("★ a relative target folder is refused; an absolute new one is accepted")
    void relativeIsRefused(@TempDir Path dir) throws Exception {
        assertEquals("NOT_ABSOLUTE", ExampleDownloadDialog.targetProblem(Path.of("my-example")));
        assertEquals("NOT_ABSOLUTE", ExampleDownloadDialog.targetProblem(Path.of("..", "elsewhere")));
        assertNull(ExampleDownloadDialog.targetProblem(dir.resolve("new-folder")));
        Files.createDirectory(dir.resolve("full"));
        Files.writeString(dir.resolve("full/x"), "x");
        assertEquals("EXISTS_NOT_EMPTY", ExampleDownloadDialog.targetProblem(dir.resolve("full")));
    }

    /** ★ Static witness: the frame must judge the folder AS TYPED, not after making it absolute. */
    @Test
    @DisplayName("★ the confirm performer judges the typed folder, not its absolute form")
    void theFrameJudgesTheTypedFolder() throws Exception {
        String src = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        assertTrue(src.contains("ExampleDownloadDialog.targetProblem(target.isAbsolute() ? target.normalize() : target)"));
        assertFalse(src.contains("ExampleDownloadDialog.targetProblem(abs)"), "never the folder after resolving it");
    }
}
