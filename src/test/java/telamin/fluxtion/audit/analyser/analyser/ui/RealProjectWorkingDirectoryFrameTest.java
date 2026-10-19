package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;
import static telamin.fluxtion.audit.analyser.analyser.ui.ClaudeCodeTabFrameTest.awaitDialog;
import static telamin.fluxtion.audit.analyser.analyser.ui.ClaudeCodeTabFrameTest.findButton;

/**
 * The gap the owner hit with 1.36.0 (CLAUDE.md rule 8 regression closure): opening a REAL, already-existing project
 * (one with its own {@code CLAUDE.md} and {@code .claude/skills}, never created through the analyser's own onboarding)
 * and starting Claude Code must spawn the real OS process with that project's folder as its cwd — never the
 * analyser's private scratch folder ({@code $TMPDIR/analyser-claude-<n>}, OD-74.22's exemption is for the sample
 * project and a bundle's working copy only).
 *
 * <p>Every other local-agent test either fakes the process in-process ({@code FixtureAgent}, which never calls
 * {@link telamin.fluxtion.audit.analyser.analyser.assistant.localagent.OsAgentLauncher}) or asserts the pure function
 * ({@code LocalAgentFolders.workingDirectory}, {@code ScratchFolderTest}) without ever spawning a real OS process
 * through the frame. This test leaves the default launcher in place (the real {@code OsAgentLauncher}, which pins
 * {@code ProcessBuilder.directory(...)}) and gives it a real, tiny executable that records its own {@code $PWD} and
 * {@code argv} before exiting — the only way to see what the operating system actually received.
 *
 * <p>★ Witness: revert {@code LocalAgentFolders.workingDirectory} to always return the scratch folder (or revert
 * {@code MainFrame.localAgentDirectory} to skip the {@code LocalAgentFolders.workingDirectory} call) — the recorded
 * cwd is the scratch temp folder, not the project directory, and the assertion below fails.
 */
class RealProjectWorkingDirectoryFrameTest {

    @Test
    @DisplayName("★ a real, already-existing project: Claude Code's OS process spawns with cwd = the project root, "
            + "never the scratch folder")
    void realProjectUsesItsOwnFolderAsCwd(@TempDir Path tmp) throws Exception {
        assumeFalse(java.awt.GraphicsEnvironment.isHeadless(), "a real display is required");
        Path home = Files.createDirectories(tmp.resolve("home"));
        Files.writeString(Files.createDirectories(home.resolve(".jbang/bin")).resolve("analyser"), "#!/bin/sh\n");

        // A real, pre-existing coding project: its own CLAUDE.md and .claude/skills, never created through the
        // analyser's onboarding (OD-74.22 does not apply — this is not the sample and not a bundle working copy).
        Path project = Files.createDirectories(tmp.resolve("owner-project"));
        Files.writeString(project.resolve("CLAUDE.md"), "# Project guidance\nUse the house style.\n");
        Files.writeString(Files.createDirectories(project.resolve(".claude/skills")).resolve("SKILL.md"),
                "---\nname: house-style\n---\nFollow the house style.\n");
        Path profile = Files.createDirectories(project.resolve(".analyser")).resolve("project.fluxtion-settings");
        Files.writeString(profile, "share.version=1\n");

        // A real, tiny "claude" that records its own cwd and argv, then exits — no JSON-RPC fakery: this is the one
        // seam (OsAgentLauncher -> ProcessBuilder.directory) none of the FixtureAgent-based tests ever exercises.
        Path record = Files.createDirectories(tmp.resolve("record"));
        Path cwdFile = record.resolve("cwd.txt");
        Path argvFile = record.resolve("argv.txt");
        Path claude = Files.createDirectories(home.resolve(".local/bin")).resolve("claude");
        Files.writeString(claude, "#!/bin/sh\n"
                + "pwd > " + sh(cwdFile) + "\n"
                + "for a in \"$@\"; do printf '%s\\n' \"$a\" >> " + sh(argvFile) + "; done\n"
                + "exit 0\n");
        Files.setPosixFilePermissions(claude, PosixFilePermissions.fromString("rwx------"));

        String before = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        MainFrame[] made = new MainFrame[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                MainFrame f = new MainFrame();
                // the default localAgentLauncher (OsAgentLauncher) is left in place: this test spawns a REAL process.
                // Safety (found the hard way): the inherited PATH on a developer machine may hold the person's own
                // real Claude Code CLI, which ClaudeResolver would then prefer over this test's stub, spawning the
                // PERSON'S OWN live session. The resolver's PATH is pinned to ONLY this isolated bin folder, so it
                // can never resolve to anything but the stub below, in every environment.
                f.localAgentEnvironmentForTest = Map.of("PATH", home.resolve(".local/bin").toString());
                try {
                    var show = MainFrame.class.getDeclaredMethod("showWorkspace", boolean.class);
                    show.setAccessible(true);
                    show.invoke(f, false);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
                made[0] = f;
            });
            MainFrame f = made[0];
            ActionExecutor ex = (ActionExecutor) field(f, "actionExecutor");
            onEdt(() -> { f.setSize(1200, 800); f.setVisible(true); });
            onEdt(() -> ((AppConfig) field(f, "config")).claudeCodeAllowed = true);

            onEdt(() -> render(ex, "open", Map.of("project", profile.toString())));
            AssistantPanel assistant = (AssistantPanel) field(f, "assistantPanel");
            LocalAgentPanel panel = (LocalAgentPanel) field(f, "localAgentPanel");
            onEdt(() -> assistant.chooseModeForTest(AssistantMode.LOCAL_AGENT));

            // Start: the project folder is not yet trusted, so the dialog asks; Yes trusts it and starts the real process
            onEdt(() -> ((JButton) field(panel, "start")).doClick());
            JDialog dialog = awaitDialog(ClaudeCodeTabFrameTest.DIALOG_TITLE);
            AbstractButton yes = findButton(dialog, LocalAgentFolderDialog.SAFE_TRUST);
            assertNotNull(yes, "the dialog offers Yes");
            onEdt(yes::doClick);
            onEdt(() -> assertTrue(((AppConfig) field(f, "config")).claudeCodeTrustedFolders.stream()
                    .anyMatch(p -> p.endsWith("owner-project")),
                    "the confirmed folder remembered on this machine is the PROJECT folder, never the scratch one"));

            // the real OS process ran and recorded its own cwd before this test ever reads LocalAgentState
            awaitFile(cwdFile);
            String recordedCwd = Files.readString(cwdFile).strip();
            assertEquals(project.toRealPath().toString(), recordedCwd,
                    "the wrong result this witnesses: cwd was the analyser's scratch temp folder, not the project");
            assertFalse(recordedCwd.contains("analyser-claude-"),
                    "the process must never run in the private scratch folder for a real, already-open project");

            awaitFile(argvFile);
            List<String> argv = Files.readAllLines(argvFile);
            int addDir = argv.indexOf("--add-dir");
            assertTrue(addDir >= 0 && addDir + 1 < argv.size(), "the default posture confines reads with --add-dir");
            assertEquals(project.toRealPath(), Path.of(argv.get(addDir + 1)).toRealPath(),
                    "--add-dir must name the same folder as cwd, so CLAUDE.md actually loads (spec §4.3)");
        } finally {
            System.setProperty("user.home", before);
            if (made[0] != null) SwingUtilities.invokeAndWait(made[0]::dispose);
        }
    }

    private static String sh(Path p) {
        return "\"" + p.toAbsolutePath() + "\"";
    }

    private static void awaitFile(Path p) throws Exception {
        long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < until) {
            if (Files.exists(p) && Files.size(p) > 0) return;
            Thread.sleep(40);
        }
        throw new AssertionError("never recorded: " + p);
    }
}
