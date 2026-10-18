package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P1b, OD-74.11 (§10 P1b ★ PATH): the full posture's child {@code PATH}. PB-2 showed why — with the minimal GUI
 * {@code PATH} a planted hook found neither the marker binary nor {@code npx}/{@code node}, and an {@code npx} server
 * and an {@code env node} server both {@code failed}; with these directories appended both connected.
 */
class ChildPathTest {

    static final Path HOME = Path.of("/home/someone");
    static final String GUI = "/usr/bin:/bin:/usr/sbin:/sbin";

    /**
     * ★ Witnesses: prepend the appended directories (the order assertion fails: the person's PATH no longer wins); add
     * a directory without checking it exists (the "/usr/local/bin is missing" assertion fails).
     */
    @Test
    @DisplayName("★ appended in order, only directories that exist and are not already present, never prepended")
    void appendedInOrderOnlyWhatExists() {
        Set<Path> exist = Set.of(HOME.resolve(".local/bin"), Path.of("/opt/homebrew/bin"), Path.of("/npm/prefix/bin"),
                HOME.resolve(".npm-global/bin"));
        Map<String, String> env = Map.of("PATH", GUI, "NPM_CONFIG_PREFIX", "/npm/prefix");
        var x = ChildPath.extend(env, HOME, false, ":", exist::contains);
        assertEquals(List.of("/home/someone/.local/bin", "/opt/homebrew/bin", "/npm/prefix/bin",
                "/home/someone/.npm-global/bin"), x.appended(), "OD-74.11's order; /usr/local/bin is missing so is not added");
        assertTrue(x.path().startsWith(GUI + ":"), "the analyser's own PATH first, untouched: " + x.path());
        assertEquals("PATH", x.key());
        assertEquals(GUI + ":/home/someone/.local/bin:/opt/homebrew/bin:/npm/prefix/bin:/home/someone/.npm-global/bin",
                x.path());

        // already present (with or without a trailing slash): kept where the person put it, not appended again
        var y = ChildPath.extend(Map.of("PATH", "/opt/homebrew/bin/:/usr/bin"), HOME, false, ":", exist::contains);
        assertFalse(y.appended().contains("/opt/homebrew/bin"));
        assertTrue(y.path().startsWith("/opt/homebrew/bin/:/usr/bin"), y.path());

        // nothing exists: nothing changes
        var z = ChildPath.extend(Map.of("PATH", GUI), HOME, false, ":", p -> false);
        assertEquals(List.of(), z.appended());
        assertEquals(GUI, z.path());

        // review finding 7: no PATH at all: the OS default first, never appended to nothing
        var w = ChildPath.extend(Map.of(), HOME, false, ":", exist::contains);
        assertEquals(GUI + ":" + String.join(":", w.appended()), w.path());
        // review finding 13: a relative NPM_CONFIG_PREFIX is never used
        var r = ChildPath.extend(Map.of("PATH", GUI, "NPM_CONFIG_PREFIX", "relative/prefix"), HOME, false, ":", p -> true);
        assertTrue(r.appended().stream().noneMatch(d -> d.contains("relative")), r.appended().toString());
    }

    /** ★ Review finding 7. Witness: look up "PATH" exactly — the Windows "Path" is not found and C:\\Windows is lost. */
    @Test
    @DisplayName("★ Windows spells it Path: found without regard to case and written back under that key")
    void windowsPathKey() {
        var x = ChildPath.extend(Map.of("Path", "C:\\Windows", "APPDATA", "C:\\Users\\u\\AppData\\Roaming"),
                Path.of("C:\\Users\\u"), true, ";", p -> true);
        assertEquals("Path", x.key());
        assertTrue(x.path().startsWith("C:\\Windows;"), x.path());
        var env = new ClaudeCodeProvider().environment(LocalAgentPosture.full(LocalAgentPosture.FullMode.DEFAULT), x);
        assertEquals(java.util.Set.of("Path"), env.keySet(), "never a second, upper-case PATH beside the person's Path");
    }

    @Test
    @DisplayName("Windows appends %APPDATA%\\npm only (§11)")
    void windowsAppendsAppDataNpmOnly() {
        var x = ChildPath.extend(Map.of("PATH", "C:\\Windows", "APPDATA", "C:\\Users\\u\\AppData\\Roaming"),
                Path.of("C:\\Users\\u"), true, ";", p -> true);
        assertEquals(1, x.appended().size(), x.appended().toString());
        assertTrue(x.appended().get(0).endsWith("npm"));
    }

    /** ★ Witness: compute the PATH by running {@code $SHELL -lc 'echo $PATH'} — the source assertion fails. */
    @Test
    @DisplayName("★ no process and no shell computes it; the default posture's environment carries no PATH")
    void noShellIsRun() throws Exception {
        String src = Files.readString(Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/assistant/localagent/ChildPath.java"));
        String code = src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
        for (String forbidden : List.of("ProcessBuilder", "Runtime.getRuntime", ".exec(", "SHELL", "bash", "zsh")) {
            assertFalse(code.contains(forbidden), "ChildPath must not run anything to find a PATH: " + forbidden);
        }
        assertFalse(new ClaudeCodeProvider().environment(LocalAgentPosture.ANALYSER_ASSISTANT, Map.of("PATH", GUI), HOME)
                .containsKey("PATH"), "the default posture runs no Bash and no hooks: its environment is unchanged");
    }

    @Test
    @DisplayName("the header names an appended directory under the home folder as ~/…")
    void shownUnderHome(@TempDir Path home) {
        assertEquals("~/.local/bin", ChildPath.shown(home.resolve(".local/bin").toString(), home));
        assertEquals("/opt/homebrew/bin", ChildPath.shown("/opt/homebrew/bin", home));
    }

    /**
     * ★ Review of PR #147, info. Witness: keep every entry (the P1b split) — ".", "bin" and the empty entries stay in the
     * child's PATH, where they resolve inside the working folder.
     */
    @Test
    @DisplayName("★ PR #147: empty and relative PATH entries are dropped from the child's PATH")
    void relativeEntriesAreDropped() {
        var x = ChildPath.extend(Map.of("PATH", ".:bin::/usr/bin:"), HOME, false, ":", p -> false);
        assertEquals("/usr/bin", x.path());
        assertEquals(List.of(".", "bin", "", ""), x.dropped());
        assertTrue(x.changed(), "the child's PATH differs, so it is written");
        var only = ChildPath.extend(Map.of("PATH", "./node_modules/.bin"), HOME, false, ":", p -> false);
        assertEquals(GUI, only.path(), "nothing usable left: the OS default, never an empty PATH");
        var w = ChildPath.extend(Map.of("Path", "C:\\Windows;.\\tools;\\\\server\\share"), Path.of("C:\\Users\\u"),
                true, ";", p -> false);
        assertEquals("C:\\Windows;\\\\server\\share", w.path());
    }
}
