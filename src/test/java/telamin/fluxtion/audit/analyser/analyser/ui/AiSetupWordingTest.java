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

    static telamin.fluxtion.audit.analyser.analyser.session.AiSetupState state(
            telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Step step,
            telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice choice, boolean waiting) {
        return new telamin.fluxtion.audit.analyser.analyser.session.AiSetupState(true, false,
                telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Origin.ASK, step, choice,
                telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice.NONE,
                telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Posture.SAFE, "default", null, null, null,
                waiting ? new telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Pending(
                        telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.PendingKind.ASK_DRAFT, 7) : null,
                0, "", "", "", "", true, false, false, null,
                new telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.External(null,
                        telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Access.ON, null, null, true, false,
                        true, false));
    }

    /**
     * ★ The scorecard's T1/T2 fix (2026-10-18): the wizard's primary button says what pressing it does. Screen 1 names
     * the check it starts or the screen it opens; the last screen, while a question waits, says the question comes next
     * (it is placed, never sent, from this window); a bare Finish only where nothing waits, and your own client alone
     * (which turns the panel off) never claims to place a question. The views take the label from here, with no literal
     * of their own. Witnesses: put back {@code case CHOICE -> "Continue"} or {@code "Finish"} for 2a in
     * {@code primaryLabel}; a {@code "Continue"} literal in AiSetupWindow or AiSetupBlock.
     */
    @Test
    @DisplayName("★ the wizard's primary button says what it does: Check Claude Code, Set up my client, Finish, then send")
    void thePrimaryButtonSaysWhatItDoes() throws Exception {
        var CHOICE = telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Step.CHOICE;
        var INSIDE = telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Step.INSIDE;
        var CLIENT = telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Step.CLIENT;
        var OTHER = telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Step.OTHER;
        var inside = telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice.INSIDE;
        var client = telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice.CLIENT;
        var both = telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice.BOTH;
        var inApp = telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice.IN_APP;
        var noAi = telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice.NO_AI;

        assertEquals(AiSetupLines.CHECK_CLAUDE_CODE, AiSetupLines.primaryLabel(state(CHOICE, inside, true)), "★ Screen 1");
        assertEquals(AiSetupLines.CHECK_CLAUDE_CODE, AiSetupLines.primaryLabel(state(CHOICE, both, false)));
        assertEquals(AiSetupLines.SET_UP_MY_CLIENT, AiSetupLines.primaryLabel(state(CHOICE, client, false)), "★ T6");
        assertEquals(AiSetupLines.CONTINUE_WITHOUT_AI, AiSetupLines.primaryLabel(state(CHOICE, noAi, false)));
        assertEquals(AiSetupLines.FINISH_THEN_SEND, AiSetupLines.primaryLabel(state(INSIDE, inside, true)), "★ 2a, waiting");
        assertEquals(AiSetupLines.FINISH, AiSetupLines.primaryLabel(state(INSIDE, inside, false)), "nothing waits: Finish");
        assertEquals(AiSetupLines.CONTINUE_TO_MY_CLIENT, AiSetupLines.primaryLabel(state(INSIDE, both, true)),
                "Both: 2b places it, not 2a");
        assertEquals(AiSetupLines.FINISH_THEN_SEND, AiSetupLines.primaryLabel(state(CLIENT, both, true)), "Both, on 2b");
        assertEquals(AiSetupLines.FINISH, AiSetupLines.primaryLabel(state(CLIENT, client, true)),
                "★ your own client alone places nothing: it never says it does");
        assertEquals(AiSetupLines.FINISH_THEN_SEND, AiSetupLines.primaryLabel(state(OTHER, inApp, true)));
        // the start page's first-run block says the same thing as Screen 1
        for (var c : telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice.values()) {
            assertEquals(AiSetupLines.choiceProceedLabel(c), AiSetupLines.primaryLabel(state(CHOICE, c, false)));
        }
        // the scorecard's own rule, as a floor: T1/T2's last wizard press and T6's Screen 1 press hold a task's word
        assertTrue(AiSetupLines.FINISH_THEN_SEND.toLowerCase().contains("question"));
        assertTrue(AiSetupLines.SET_UP_MY_CLIENT.toLowerCase().contains("client"));
        assertTrue(AiSetupLines.CHECK_CONNECTION.toLowerCase().contains("connect"));
        // no surface composes a label of its own
        Pattern bare = Pattern.compile("\"(Continue|Finish)\"");
        for (String f : java.util.List.of("ui/AiSetupWindow.java", "ui/AiSetupBlock.java")) {
            assertFalse(bare.matcher(Files.readString(MAIN.resolve(f))).find(), "★ " + f + " spells its own label");
        }
    }
}
