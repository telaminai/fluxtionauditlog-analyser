package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeResolver;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M77-2 (spec-ai-setup-wizard.md ★ T18, ★ T24 Screen 1's half): the wizard's words. The "not local-only" sentences are
 * the pinned constants, shown by reference on the wizard's screens; the product is named only through
 * {@link AiSetupLines#PRODUCT}; no literal anywhere spells the future name. And the badge's "found" comes from the one
 * resolver, which finds a GUI-launched {@code claude} the PATH-only detection misses.
 */
class AiSetupWordingTest {

    static final Path MAIN = Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser");

    /**
     * ★ T18. Witnesses: copy the sentence into {@code AiSetupLines} instead of referencing the constant; spell "Canvas" in
     * a literal; write "the analyser" in a wizard string instead of {@code PRODUCT}.
     */
    @Test
    @DisplayName("★ T18: the wizard shows the pinned not-local-only sentences, names the product through one constant")
    void theWizardsWords() throws Exception {
        assertSame(AssistantProfiles.CLAUDE_CODE_SENDS, AiSetupLines.SENDS, "the constant itself, by reference");
        assertSame(AssistantProfiles.IN_APP_SENDS, AiSetupLines.IN_APP_SENDS);
        String lines = Files.readString(MAIN.resolve("session/AiSetupLines.java"));
        assertTrue(lines.contains("AssistantProfiles.CLAUDE_CODE_SENDS") && lines.contains("AssistantProfiles.IN_APP_SENDS"));
        String window = Files.readString(MAIN.resolve("ui/AiSetupWindow.java"));
        assertTrue(window.contains("AiSetupLines.SENDS") && window.contains("AiSetupLines.IN_APP_SENDS"),
                "Screen 2a and Other options show them");
        // the product: only PRODUCT says it in the wizard's own strings
        Pattern literal = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
        for (String f : java.util.List.of("session/AiSetupLines.java", "ui/AiSetupWindow.java", "ui/AiSetupBlock.java")) {
            String src = Files.readString(MAIN.resolve(f));
            var m = literal.matcher(src);
            while (m.find()) {
                String text = m.group(1);
                if (f.endsWith("AiSetupLines.java") && text.equals("the analyser")) continue;   // PRODUCT itself
                assertFalse(text.contains("Canvas"), "★ " + f + " spells the new name: " + text);
                assertFalse(text.toLowerCase(java.util.Locale.ROOT).contains("the analyser"),
                        "★ " + f + " spells the product instead of PRODUCT: " + text);
            }
        }
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                var m = literal.matcher(Files.readString(f));
                while (m.find()) {
                    // the right-hand column is already titled "Canvas" (a place, not the product): the PRODUCT's new
                    // name is "Telamin Canvas", which no literal may spell before the rename PR
                    assertFalse(m.group(1).contains("Telamin Canvas"), "★ " + f.getFileName() + " spells the new name: "
                            + m.group(1));
                }
            }
        }
    }

    /**
     * ★ T24 (Screen 1's half). A Finder-launched analyser has a GUI-shaped PATH; claude is in {@code ~/.local/bin}. The
     * one resolver finds it, and the frame's badge reads that resolver. Witness: compute {@code claudeFound} with
     * {@code ClaudeMcpClient.detect()} (PATH only) — the frame no longer calls the resolver, and the PATH-only search
     * finds nothing here.
     */
    @Test
    @DisplayName("★ T24: with a GUI-shaped PATH and claude in ~/.local/bin, the one resolver says found")
    void aGuiShapedPathStillFindsClaude(@TempDir Path tmp) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("win"));
        Path home = Files.createDirectories(tmp.resolve("home"));
        Path claude = Files.createDirectories(home.resolve(".local/bin")).resolve("claude");
        Files.writeString(claude, "#!/bin/sh\nexit 0\n");
        Files.setPosixFilePermissions(claude, PosixFilePermissions.fromString("rwx------"));
        Map<String, String> gui = Map.of("PATH", "/usr/bin:/bin:/usr/sbin:/sbin");
        assertTrue(ClaudeResolver.resolve(gui, home).found(), "★ the one resolver finds it");
        assertEquals(claude.toAbsolutePath().normalize(), ClaudeResolver.resolve(gui, home).executable());
        String frame = Files.readString(MAIN.resolve("ui/MainFrame.java"));
        String found = OneDispatchModelTest.bodyOf(frame, "private boolean claudeFound(");
        assertTrue(found.contains("ClaudeResolver.resolve("), "★ the badge reads the one resolver");
        assertFalse(found.contains("ClaudeMcpClient"), "★ never the PATH-only detection");
    }
}
