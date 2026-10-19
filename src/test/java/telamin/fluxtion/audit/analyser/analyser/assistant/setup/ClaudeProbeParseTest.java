package telamin.fluxtion.audit.analyser.analyser.assistant.setup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M77 (spec-ai-setup-wizard.md §8.6, §9, ★ T7, ★ T22): the parse boundary and the probes' working folder. DEMO values
 * only: the personal fields of the fixtures are made up ({@code .invalid} address, DEMO organisation).
 */
class ClaudeProbeParseTest {

    static final String EMAIL = "demo.person@example.invalid";
    static final String ORG_ID = "00000000-0000-4000-8000-00000000DEMO";
    static final String ORG_NAME = "DEMO Organisation";

    /** The four shapes verified on 2026-10-18 against Claude Code 2.1.282 — the personal fields replaced. */
    static final String SUBSCRIPTION = "{\"loggedIn\":true,\"authMethod\":\"claude.ai\",\"apiProvider\":\"firstParty\","
            + "\"email\":\"" + EMAIL + "\",\"orgId\":\"" + ORG_ID + "\",\"orgName\":\"" + ORG_NAME + "\","
            + "\"subscriptionType\":\"max\"}";
    static final String SIGNED_OUT = "{\"loggedIn\":false,\"authMethod\":\"none\",\"apiProvider\":\"firstParty\"}";
    static final String API_KEY = "{\"loggedIn\":true,\"authMethod\":\"api_key\",\"apiProvider\":\"firstParty\","
            + "\"apiKeySource\":\"ANTHROPIC_API_KEY\"}";
    static final String HELPER = "{\"loggedIn\":true,\"authMethod\":\"api_key_helper\",\"apiProvider\":\"firstParty\","
            + "\"apiKeySource\":\"apiKeyHelper\"}";

    // ---- ★ T7: the parse boundary -------------------------------------------------------------------------------------

    /**
     * ★ T7. Witness: keep the whole JSON map in {@link ClaudeAuthParse.Auth} (or a raw field of it) — the personal fields
     * reach its string form and the fact built from it.
     */
    @Test
    @DisplayName("★ T7: the four auth shapes give their verdicts, and no e-mail or organisation leaves the parser")
    void theParseBoundary() {
        ClaudeAuthParse.Auth sub = ClaudeAuthParse.auth(0, SUBSCRIPTION, false);
        assertEquals(SessionEvents.AuthOutcome.READ, sub.outcome());
        assertTrue(sub.loggedIn());
        assertEquals(AiSetupState.Method.CLAUDE_AI, sub.method());
        assertEquals(AiSetupState.Plan.MAX, sub.plan());

        ClaudeAuthParse.Auth out = ClaudeAuthParse.auth(1, SIGNED_OUT, false);
        assertEquals(SessionEvents.AuthOutcome.READ, out.outcome(), "exit 1 WITH parseable JSON is read: signed out");
        assertFalse(out.loggedIn());
        assertEquals(AiSetupState.Method.NONE, out.method());

        ClaudeAuthParse.Auth key = ClaudeAuthParse.auth(0, API_KEY, false);
        assertEquals(AiSetupState.Method.API_KEY, key.method());
        assertEquals(AiSetupState.KeySource.ANTHROPIC_API_KEY, key.keySource());
        ClaudeAuthParse.Auth helper = ClaudeAuthParse.auth(0, HELPER, false);
        assertEquals(AiSetupState.Method.API_KEY_HELPER, helper.method());
        assertEquals(AiSetupState.KeySource.API_KEY_HELPER, helper.keySource());

        assertEquals(SessionEvents.AuthOutcome.UNREADABLE, ClaudeAuthParse.auth(1, "Error: something", false).outcome(),
                "exit 1 without JSON: could not tell");
        assertEquals(SessionEvents.AuthOutcome.UNREADABLE, ClaudeAuthParse.auth(0, "[1,2]", false).outcome());
        assertEquals(SessionEvents.AuthOutcome.UNREADABLE, ClaudeAuthParse.auth(0, "{\"loggedIn\":\"yes\"}", false).outcome());
        assertEquals(SessionEvents.AuthOutcome.TIMED_OUT, ClaudeAuthParse.auth(-1, "", true).outcome());
        assertEquals(AiSetupState.Method.OTHER, ClaudeAuthParse.auth(0,
                "{\"loggedIn\":true,\"authMethod\":\"something-new\"}", false).method(), "an unverified method is OTHER");
        assertEquals(AiSetupState.Plan.OTHER, ClaudeAuthParse.auth(0,
                "{\"loggedIn\":true,\"authMethod\":\"claude.ai\",\"subscriptionType\":\"" + ORG_NAME + "\"}", false).plan(),
                "a plan not on the list is never echoed");

        // nothing personal in the parsed value, the fact built from it, or the words shown for it
        var fact = new SessionEvents.ClaudeAuthObserved(1, sub.outcome(), sub.loggedIn(), sub.method(), sub.plan(),
                sub.keySource());
        var shown = AiSetupLines.signedInText(new AiSetupState.Auth(AiSetupState.SignedIn.SIGNED_IN, sub.method(),
                sub.plan(), sub.keySource()));
        for (String text : List.of(sub.toString(), fact.toString(), shown)) {
            for (String personal : List.of(EMAIL, ORG_ID, ORG_NAME, "example.invalid", "DEMO")) {
                assertFalse(text.contains(personal), "★ " + personal + " leaked into: " + text);
            }
        }
        assertEquals("Signed in with your Claude account (Max plan).", shown);
        assertTrue(AiSetupLines.signedInText(new AiSetupState.Auth(AiSetupState.SignedIn.KEY, key.method(), key.plan(),
                key.keySource())).startsWith("Uses an API key (ANTHROPIC_API_KEY)"), "never 'signed in' for a key");
        // the record has no field that could hold a string at all
        for (var c : ClaudeAuthParse.Auth.class.getRecordComponents()) {
            assertNotEquals(String.class, c.getType(), "a String component could carry raw output: " + c.getName());
        }
    }

    @Test
    @DisplayName("claude --version: the digits of N.N.N at the start of the first line, and only with exit 0")
    void theVersion() {
        assertEquals("2.1.282", ClaudeAuthParse.version(0, "2.1.282 (Claude Code)\n"));
        assertEquals("", ClaudeAuthParse.version(1, "2.1.282 (Claude Code)"));
        assertEquals("", ClaudeAuthParse.version(0, "error: unknown option"));
        assertEquals("", ClaudeAuthParse.version(0, ""));
    }

    // ---- ★ T22: a fresh empty folder per probe ----------------------------------------------------------------------

    /** A {@code claude} file on a made-up PATH: the resolver finds it; the recording runner never executes it. */
    static Map<String, String> pathWithClaude(Path tmp) throws Exception {
        Path bin = Files.createDirectories(tmp.resolve("bin"));
        Path claude = bin.resolve("claude");
        Files.writeString(claude, "#!/bin/sh\nexit 0\n");
        assertTrue(claude.toFile().setExecutable(true));
        return Map.of("PATH", bin.toString());
    }

    record Call(List<String> command, Map<String, String> extra, Path dir, boolean existed, long entries, Duration timeout) {
    }

    /**
     * ★ T22. Witness: start the probe in the process's working directory (or the project root) instead of
     * {@code freshEmptyDirectory} — the folder assertions fail.
     */
    @Test
    @DisplayName("★ T22: every probe runs in its own fresh, empty folder under the probe root — never the project or the cwd")
    @DisabledOnOs(OS.WINDOWS)
    void everyProbeRunsInAFreshEmptyFolder(@TempDir Path tmp) throws Exception {
        Map<String, String> env = pathWithClaude(tmp);
        Path root = tmp.resolve("probe-root");
        List<Call> calls = new ArrayList<>();
        ClaudeProbes probes = new ClaudeProbes((command, extra, removed, dir, timeout) -> {
            long n;
            try (var l = Files.list(dir)) {
                n = l.count();
            }
            calls.add(new Call(command, extra, dir, Files.isDirectory(dir), n, timeout));
            boolean auth = command.contains("auth");
            return new ClaudeProbes.Ran(0, auth ? SUBSCRIPTION : "2.1.282 (Claude Code)", false);
        }, () -> root);
        ClaudeProbes.Version v = probes.version(env, tmp.resolve("home"), false);
        assertEquals(SessionEvents.ProbeOutcome.FOUND, v.outcome());
        assertEquals("2.1.282", v.version());
        ClaudeAuthParse.Auth a = probes.auth(env, tmp.resolve("home"), true);
        assertEquals(AiSetupState.Method.CLAUDE_AI, a.method());
        assertEquals(2, calls.size());
        Path cwd = Path.of("").toAbsolutePath();
        for (Call c : calls) {
            assertTrue(c.existed(), "the folder exists when the probe starts");
            assertEquals(0, c.entries(), "★ and it is empty: no settings, no .mcp.json — nothing to trust");
            assertTrue(c.dir().startsWith(root), "★ under the probe root: " + c.dir());
            assertNotEquals(cwd, c.dir(), "★ never the process's working directory");
            assertFalse(c.dir().startsWith(cwd), "★ nor anything inside the checkout / project");
            assertFalse(Files.exists(c.dir()), "removed afterwards");
        }
        assertNotEquals(calls.get(0).dir(), calls.get(1).dir(), "★ a new folder per probe");
        assertEquals(List.of("--version"), calls.get(0).command().subList(1, 2));
        assertEquals(List.of("auth", "status", "--json"), calls.get(1).command().subList(1, 4), "--json always explicit");
        assertEquals(ClaudeProbes.VERSION_TIMEOUT, calls.get(0).timeout());
        assertEquals(ClaudeProbes.AUTH_TIMEOUT, calls.get(1).timeout());
        // the posture's environment: Safe carries the CLAUDE.md flag Start passes; Full does not
        assertTrue(calls.get(0).extra().containsKey(
                telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeCodeProvider.CLAUDE_MD_ENV));
        assertFalse(calls.get(1).extra().containsKey(
                telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeCodeProvider.CLAUDE_MD_ENV));
    }

    @Test
    @DisplayName("not found: no process at all; a runner that fails is DID_NOT_RUN; a time-out is TIMED_OUT")
    @DisabledOnOs(OS.WINDOWS)
    void theFailStates(@TempDir Path tmp) throws Exception {
        List<Call> calls = new ArrayList<>();
        ClaudeProbes none = new ClaudeProbes((c, e, r, d, t) -> {
            calls.add(new Call(c, e, d, true, 0, t));
            return new ClaudeProbes.Ran(0, "", false);
        }, () -> tmp.resolve("root"));
        Map<String, String> empty = Map.of("PATH", tmp.resolve("nothing-here").toString());
        assertEquals(SessionEvents.ProbeOutcome.NOT_FOUND, none.version(empty, tmp.resolve("no-home"), false).outcome());
        assertEquals(SessionEvents.AuthOutcome.DID_NOT_RUN, none.auth(empty, tmp.resolve("no-home"), false).outcome());
        assertTrue(calls.isEmpty(), "nothing was started");

        Map<String, String> env = pathWithClaude(tmp);
        ClaudeProbes failing = new ClaudeProbes((c, e, r, d, t) -> {
            throw new java.io.IOException("cannot start");
        }, () -> tmp.resolve("root"));
        assertEquals(SessionEvents.ProbeOutcome.DID_NOT_RUN, failing.version(env, tmp, false).outcome());
        ClaudeProbes slow = new ClaudeProbes((c, e, r, d, t) -> new ClaudeProbes.Ran(-1, "", true), () -> tmp.resolve("root"));
        assertEquals(SessionEvents.ProbeOutcome.TIMED_OUT, slow.version(env, tmp, false).outcome());
        assertEquals(SessionEvents.AuthOutcome.TIMED_OUT, slow.auth(env, tmp, false).outcome());
        ClaudeProbes garbled = new ClaudeProbes((c, e, r, d, t) -> new ClaudeProbes.Ran(0, "hello", false),
                () -> tmp.resolve("root"));
        assertEquals(SessionEvents.ProbeOutcome.DID_NOT_RUN, garbled.version(env, tmp, false).outcome(),
                "found, but no version line: did not run");
    }

    /** The real runner: the child's working folder is the one given, its output is bounded, a slow child is killed. */
    @Test
    @DisplayName("the real runner: runs in the folder given, reads stdout only, and kills a child that overruns")
    @DisabledOnOs(OS.WINDOWS)
    void theRealRunner(@TempDir Path tmp) throws Exception {
        Path dir = ClaudeProbes.freshEmptyDirectory(tmp.resolve("root"));
        ClaudeProbes.Ran pwd = ClaudeProbes.runProcess(List.of("/bin/sh", "-c", "pwd -P; ls -A | wc -l; echo oops 1>&2"),
                Map.of(), java.util.Set.of(), dir, Duration.ofSeconds(5));
        assertFalse(pwd.timedOut());
        List<String> lines = pwd.stdout().lines().map(String::trim).toList();
        assertEquals(dir.toRealPath().toString(), lines.get(0));
        assertEquals("0", lines.get(1), "the folder is empty");
        assertFalse(pwd.stdout().contains("oops"), "stderr is never read");
        long t0 = System.nanoTime();
        ClaudeProbes.Ran slow = ClaudeProbes.runProcess(List.of("/bin/sh", "-c", "sleep 30"), Map.of(),
                java.util.Set.of(), dir, Duration.ofMillis(300));
        assertTrue(slow.timedOut());
        assertTrue(System.nanoTime() - t0 < 10_000_000_000L, "killed, not waited for");
    }
}
