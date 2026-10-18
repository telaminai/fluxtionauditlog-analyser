package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.llm.VerbSchemas;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.SlashClass;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** M74 P1a §10: the default command line, the verb lists, and the slash-command classification — pure, headless. */
class ClaudeCodeProviderTest {

    @TempDir
    Path tmp;

    private LocalAgentProvider.LaunchSpec spec(boolean resume) {
        return new LocalAgentProvider.LaunchSpec(Path.of("/opt/claude/bin/claude"), "11111111-2222-4333-8444-555555555555",
                resume, "haiku", Path.of("/work/proj"), List.of("/usr/bin/java", "-jar", "/opt/analyser/analyser.jar"),
                Path.of("/home/u/.fluxtion-analyser/local-agent/endpoint-1-1.json"), 4_250_000);
    }

    /**
     * ★ Witnesses: drop {@code --restricted} (or read the person's {@code defaultMode} into {@code --permission-mode}) —
     * the exact-line assertion fails. P3 (§10): {@code --permission-prompt-tool mcp__…} (an approval the model could
     * call) or a line without {@code --permission-prompts host} (one that never asks) fails it too — both checked in place.
     */
    @Test
    @DisplayName("★ the default command line is exactly §4.3 (P3: default mode, host prompts over stdio, nothing hidden)")
    void theDefaultCommandLine() throws Exception {
        List<String> c = new ClaudeCodeProvider().command(spec(false));
        String allowed = String.join(",", LocalAgentPosture.preAllowedVerbs().stream()
                .map(LocalAgentPosture::toolName).toList());
        assertEquals(List.of(), LocalAgentPosture.disallowedVerbs(), "P3: the default posture hides no hosted verb today");
        List<String> expected = List.of("/opt/claude/bin/claude", "-p", "--input-format", "stream-json", "--output-format",
                "stream-json", "--verbose", "--include-partial-messages", "--replay-user-messages",
                "--session-id", "11111111-2222-4333-8444-555555555555", "--model", "haiku",
                "--tools", "Read,Grep,Glob", "--restricted", "--add-dir", "/work/proj", "--strict-mcp-config",
                "--mcp-config", c.get(c.indexOf("--mcp-config") + 1),
                "--allowedTools", allowed,
                "--permission-mode", "default", "--permission-prompts", "host", "--permission-prompt-tool", "stdio",
                "--max-budget-usd", "4.25", "--append-system-prompt", ClaudeCodeProvider.SYSTEM_NOTE);
        assertEquals(expected, c);
        assertFalse(c.contains("--disallowedTools"), "P3: nothing hidden, so the flag is omitted (§4.3)");
        assertFalse(allowed.contains("*"), "no wildcard [P0-30]");
        assertPromptsGoToTheAnalyser(c);
        // the witnesses, in place: an MCP permission tool, and a line that never asks
        List<String> mcpTool = new ArrayList<>(c);
        mcpTool.set(mcpTool.indexOf("stdio"), "mcp__fluxtion-analyser-hosted__approve");
        assertThrows(org.opentest4j.AssertionFailedError.class, () -> assertPromptsGoToTheAnalyser(mcpTool));
        List<String> noHost = new ArrayList<>(c);
        noHost.set(noHost.indexOf("host"), "none");
        assertThrows(org.opentest4j.AssertionFailedError.class, () -> assertPromptsGoToTheAnalyser(noHost));
        assertEquals(Map.of("CLAUDE_CODE_ADDITIONAL_DIRECTORIES_CLAUDE_MD", "1"), new ClaudeCodeProvider().environment());

        List<String> resumed = new ClaudeCodeProvider().command(spec(true));
        assertEquals("--resume", resumed.get(9));

        // the person's settings never reach the line: a user settings file asking for bypassPermissions changes nothing
        Path claudeDir = Files.createDirectories(tmp.resolve(".claude"));
        Files.writeString(claudeDir.resolve("settings.json"), "{\"permissions\":{\"defaultMode\":\"bypassPermissions\"}}");
        String before = System.getProperty("user.home");
        System.setProperty("user.home", tmp.toString());
        try {
            assertEquals(expected, new ClaudeCodeProvider().command(spec(false)));
        } finally {
            System.setProperty("user.home", before);
        }
    }

    /** P3 (§5.7): every request the mode asks about goes to the analyser over stdio — never to an MCP tool, never nowhere. */
    static void assertPromptsGoToTheAnalyser(List<String> line) {
        int p = line.indexOf("--permission-prompts");
        int t = line.indexOf("--permission-prompt-tool");
        assertTrue(p >= 0 && "host".equals(line.get(p + 1)), "--permission-prompts host: " + line);
        assertTrue(t >= 0 && "stdio".equals(line.get(t + 1)), "--permission-prompt-tool stdio, never an MCP tool: " + line);
        int m = line.indexOf("--permission-mode");
        assertFalse(m < 0 || List.of("dontAsk", "bypassPermissions").contains(line.get(m + 1)), "a mode that asks: " + line);
    }

    // ---- P1b: the full posture (OD-74.10) -----------------------------------------------------------------------------

    private LocalAgentProvider.LaunchSpec fullSpec(LocalAgentPosture.FullMode mode, long budget) {
        return new LocalAgentProvider.LaunchSpec(Path.of("/opt/claude/bin/claude"), "11111111-2222-4333-8444-555555555555",
                false, "sonnet", Path.of("/work/proj"), List.of("/usr/bin/java", "-jar", "/opt/analyser/analyser.jar"),
                Path.of("/home/u/.fluxtion-analyser/local-agent/endpoint-1-1.json"), budget, LocalAgentPosture.full(mode));
    }

    /** OD-74.10: none of the flags that confine may appear on a full-posture line. */
    static void assertNothingConfines(List<String> line) {
        for (String flag : List.of("--restricted", "--strict-mcp-config", "--tools", "--add-dir", "--setting-sources",
                "--settings", "--safe-mode")) {
            assertFalse(line.contains(flag), "the full posture never carries " + flag + ": " + line);
        }
        String disallowed = line.get(line.indexOf("--disallowedTools") + 1);
        assertTrue(List.of(disallowed.split(",")).contains("mcp__fluxtion-analyser"),
                "the person's own registration is hidden at server level: " + disallowed);
    }

    /**
     * ★ Witnesses: build the full line with the default posture's flags (keep {@code --restricted} and the rest) — the
     * exact-line and "nothing confines" assertions fail; drop {@code mcp__fluxtion-analyser} from the disallow list —
     * the server-level assertion fails. Both are also checked in place below, against the lines that would be wrong.
     */
    @Test
    @DisplayName("★ the full command line is exactly §4.3 for each of the four modes: nothing confines, the registration hidden")
    void theFullCommandLine() {
        String allowed = String.join(",", LocalAgentPosture.preAllowedVerbs().stream().map(LocalAgentPosture::toolName).toList());
        // owner decision 2026-10-18: graph is judged call by call — named in --allowedTools only where the mode never
        // asks (the server refuses its calls that would need an approval), never hidden
        String allowedNoAsk = String.join(",", VerbSchemas.hosted().keySet().stream()
                .filter(v -> LocalAgentPosture.preAllowed(v) || "graph".equals(v)).map(LocalAgentPosture::toolName).toList());
        String destructive = String.join(",", VerbSchemas.hosted().keySet().stream()
                .filter(McpTools.destructiveVerbs()::contains).map(LocalAgentPosture::toolName).toList());
        for (LocalAgentPosture.FullMode mode : LocalAgentPosture.FullMode.values()) {
            List<String> c = new ClaudeCodeProvider().command(fullSpec(mode, 0));
            // P3 (OD-74.16): the destructive verbs are visible (and asked) only in the modes that ask
            boolean asks = mode == LocalAgentPosture.FullMode.DEFAULT || mode == LocalAgentPosture.FullMode.ACCEPT_EDITS;
            List<String> expected = List.of("/opt/claude/bin/claude", "-p", "--input-format", "stream-json",
                    "--output-format", "stream-json", "--verbose", "--include-partial-messages", "--replay-user-messages",
                    "--session-id", "11111111-2222-4333-8444-555555555555", "--model", "sonnet",
                    "--mcp-config", c.get(c.indexOf("--mcp-config") + 1),
                    // owner, 2026-10-18: Full reads the public Fluxtion docs at exactly one host without a prompt
                    "--allowedTools", (asks ? allowed : allowedNoAsk) + ",WebFetch(domain:fluxtion-playground.dev)",
                    "--disallowedTools", asks ? "mcp__fluxtion-analyser" : "mcp__fluxtion-analyser," + destructive,
                    "--permission-mode", mode.cli(), "--permission-prompts", "host", "--permission-prompt-tool", "stdio",
                    "--append-system-prompt", ClaudeCodeProvider.FULL_SYSTEM_NOTE);
            assertEquals(expected, c, "full " + mode);
            assertNothingConfines(c);
            assertPromptsGoToTheAnalyser(c);
            Map<?, ?> servers = (Map<?, ?>) ((Map<?, ?>) Json.parse(c.get(c.indexOf("--mcp-config") + 1))).get("mcpServers");
            assertEquals(Set.of(LocalAgentPosture.HOSTED_SERVER), servers.keySet(), "the analyser adds only its own bridge");
        }
        // the witnesses, in place: the default posture's line and a line without the registration both fail the rule
        List<String> confined = new ClaudeCodeProvider().command(spec(false));
        assertThrows(org.opentest4j.AssertionFailedError.class, () -> assertNothingConfines(confined));
        List<String> unhidden = new ArrayList<>(new ClaudeCodeProvider().command(fullSpec(LocalAgentPosture.FullMode.DEFAULT, 0)));
        int k = unhidden.indexOf("--disallowedTools") + 1;
        unhidden.set(k, unhidden.get(k).equals("mcp__fluxtion-analyser") ? "mcp__other"     // P3: the only hidden name
                : unhidden.get(k).replace("mcp__fluxtion-analyser,", ""));
        assertThrows(org.opentest4j.AssertionFailedError.class, () -> assertNothingConfines(unhidden));
    }

    @Test
    @DisplayName("the full posture's environment: no CLAUDE.md flag; the default posture's is unchanged and has no PATH")
    void theFullEnvironment() {
        ClaudeCodeProvider p = new ClaudeCodeProvider();
        Map<String, String> gui = Map.of("PATH", "/usr/bin:/bin:/usr/sbin:/sbin");
        assertEquals(Map.of(ClaudeCodeProvider.CLAUDE_MD_ENV, "1"),
                p.environment(LocalAgentPosture.ANALYSER_ASSISTANT, gui, tmp), "the default posture is unchanged");
        Map<String, String> full = p.environment(LocalAgentPosture.full(LocalAgentPosture.FullMode.DEFAULT), gui, tmp);
        assertFalse(full.containsKey(ClaudeCodeProvider.CLAUDE_MD_ENV),
                "CLAUDE.md loads through the project setting source in the full posture (PB-4)");
    }

    /**
     * ★ owner, 2026-10-17: the budget is optional. Witness: always add {@code --max-budget-usd} (P1a) — the unset line
     * carries {@code --max-budget-usd 0} and the first assertion fails.
     */
    @Test
    @DisplayName("★ --max-budget-usd only when a limit is set, in both postures")
    void theBudgetFlagIsOptional() {
        for (LocalAgentProvider.LaunchSpec unset : List.of(fullSpec(LocalAgentPosture.FullMode.DEFAULT, 0),
                new LocalAgentProvider.LaunchSpec(Path.of("/c"), "s", false, "haiku", Path.of("/w"), List.of("/j"),
                        Path.of("/e"), 0))) {
            assertFalse(new ClaudeCodeProvider().command(unset).contains("--max-budget-usd"), "no limit set: no flag");
        }
        List<String> set = new ClaudeCodeProvider().command(fullSpec(LocalAgentPosture.FullMode.PLAN, 2_500_000));
        assertEquals("2.5", set.get(set.indexOf("--max-budget-usd") + 1), "a limit set: its remainder");
    }

    /**
     * ★ §5.1: bypassPermissions and dontAsk can never be a full-posture mode. Witness: let {@code FullMode.parse} pass
     * an unknown stored string through (or add a BYPASS constant) — the enum assertion fails.
     */
    @Test
    @DisplayName("★ the full posture's mode is one of exactly four; bypassPermissions and dontAsk cannot be built")
    void onlyTheFourModes() {
        assertEquals(List.of("default", "acceptEdits", "auto", "plan"),
                java.util.Arrays.stream(LocalAgentPosture.FullMode.values()).map(LocalAgentPosture.FullMode::cli).toList());
        for (String never : List.of("bypassPermissions", "dontAsk", "manual", "BYPASSPERMISSIONS", "", "auto ;rm")) {
            assertTrue(LocalAgentPosture.FullMode.parse(never).isEmpty(), never + " is not a mode the analyser offers");
        }
        for (LocalAgentPosture.FullMode m : LocalAgentPosture.FullMode.values()) {
            List<String> c = new ClaudeCodeProvider().command(fullSpec(m, 0));
            String passed = c.get(c.indexOf("--permission-mode") + 1);
            assertFalse(passed.equals("bypassPermissions") || passed.equals("dontAsk"), passed);
        }
    }

    @Test
    @DisplayName("the inline --mcp-config names only the hosted bridge, its launcher, --mcp and the endpoint file's path")
    void theInlineMcpConfig() {
        List<String> c = new ClaudeCodeProvider().command(spec(false));
        Map<?, ?> m = (Map<?, ?>) Json.parse(c.get(c.indexOf("--mcp-config") + 1));
        Map<?, ?> servers = (Map<?, ?>) m.get("mcpServers");
        assertEquals(Set.of(LocalAgentPosture.HOSTED_SERVER), servers.keySet());
        Map<?, ?> s = (Map<?, ?>) servers.get(LocalAgentPosture.HOSTED_SERVER);
        assertEquals("/usr/bin/java", s.get("command"));
        assertEquals(List.of("-jar", "/opt/analyser/analyser.jar", "--mcp", "--endpoint-file",
                "/home/u/.fluxtion-analyser/local-agent/endpoint-1-1.json"), s.get("args"));
    }

    /**
     * ★ Review H5. Witness: compute the pre-allow list as "every verb minus {@code McpTools.destructiveVerbs()}" (the old
     * deny-list) in {@code LocalAgentPosture.preAllowedVerbs(Collection)} — the verb added to the REAL schema list below
     * lands in {@code --allowedTools} and in the expected tool set, and the named assertions fail.
     */
    @Test
    @DisplayName("★ H5: the pre-allow list is explicit — a verb added to the schema is hidden, unexpected and refused until listed")
    void theVerbLists() {
        List<String> pre = LocalAgentPosture.preAllowedVerbs();
        Set<String> overlap = new HashSet<>(pre);
        overlap.retainAll(McpTools.destructiveVerbs());
        assertEquals(Set.of(), overlap, "no destructive verb is auto-approved");
        // M74 P2b (§8.2 (a)): the hosted bridge's verbs are VerbSchemas.hosted() — all() and then propose
        Set<String> all = new LinkedHashSet<>(VerbSchemas.hosted().keySet());
        Set<String> union = new HashSet<>(pre);
        union.addAll(LocalAgentPosture.disallowedVerbs());
        union.addAll(McpTools.destructiveVerbs());
        union.addAll(McpTools.HOSTED_PER_CALL);
        assertEquals(all, union, "every verb is pre-allowed, asked of the person (P3), judged call by call, or hidden");
        assertEquals(List.of("read", "aggregate", "series", "context", "coverage", "source", "filter", "goto", "flag",
                        "topology", "spotlight", "propose").stream().sorted().toList(), pre.stream().sorted().toList(),
                "today's pre-allowed set (§3; P2b adds propose, which only records an offer; graph is judged call by "
                        + "call since 2026-10-18)");
        assertEquals(List.of("graph"), McpTools.HOSTED_PER_CALL, "graph's delete and replacements are asked, its creates not");
        assertFalse(pre.contains("graph"), "graph is not blanket-allowed: the CLI asks the analyser about every call");
        assertTrue(all.containsAll(LocalAgentPosture.PRE_ALLOWED), "every listed verb is a real verb");

        // the REAL inputs, with one verb more: the schema list the command line and the posture check are built from
        List<String> schemaWithNew = new ArrayList<>(all);
        schemaWithNew.add("brand_new_verb");
        String newTool = LocalAgentPosture.toolName("brand_new_verb");
        List<String> line = new ClaudeCodeProvider().command(spec(false), schemaWithNew);
        String allowedArg = line.get(line.indexOf("--allowedTools") + 1);
        String disallowedArg = line.get(line.indexOf("--disallowedTools") + 1);
        assertFalse(List.of(allowedArg.split(",")).contains(newTool), "an unlisted verb is never auto-approved");
        assertTrue(List.of(disallowedArg.split(",")).contains(newTool), "it is hidden from Claude until listed");
        assertFalse(LocalAgentPosture.expectedTools(schemaWithNew).contains(newTool),
                "and a CLI that offered it fails the posture check");
        List<String> toolsWithNew = new ArrayList<>(LocalAgentPosture.expectedTools(schemaWithNew));
        toolsWithNew.add(newTool);
        assertEquals(LocalAgentPosture.Verdict.TOOLS_MISMATCH, LocalAgentPosture.check(schemaWithNew, "s", "default",
                toolsWithNew, List.of(LocalAgentPosture.HOSTED_SERVER + ":connected"),
                LocalAgentPosture.REQUIRED_CAPABILITIES, LocalAgentPosture.digest("s")));
        assertFalse(LocalAgentPosture.preAllowed("brand_new_verb"), "the server-side admission refuses it too");
    }

    /** ★ Witness (review S7): {@code validModel} returning true — the flag-shaped names pass. */
    @Test
    @DisplayName("★ S7: a model setting is a model name, never something the CLI could read as a flag")
    void modelNames() {
        for (String ok : List.of("sonnet", "opus[1m]", "claude-haiku-4-5", "claude-sonnet-5.1", "", "  "))
            assertTrue(ClaudeCodeProvider.validModel(ok), ok);
        for (String bad : List.of("-p", "--dangerously-skip-permissions", "sonnet --tools Bash", "a;b", "x$(y)", "a/b"))
            assertFalse(ClaudeCodeProvider.validModel(bad), bad);
        assertThrows(IllegalArgumentException.class, () -> new ClaudeCodeProvider().command(
                new LocalAgentProvider.LaunchSpec(Path.of("/c"), "11111111-2222-4333-8444-555555555555", false, "-x",
                        Path.of("/w"), List.of("/j"), Path.of("/e"), 1)));
    }

    @Test
    @DisplayName("budgets are formatted as plain dollars; a spent budget is 0")
    void budgetFormat() {
        assertEquals("4.25", ClaudeCodeProvider.usd(4_250_000));
        assertEquals("0.000001", ClaudeCodeProvider.usd(1));
        assertEquals("0", ClaudeCodeProvider.usd(-5));
        assertEquals("5", ClaudeCodeProvider.usd(5_000_000));
    }

    // ---- ★ slash commands -------------------------------------------------------------------------------------------

    private static List<StreamJson.Command> p0bCommands() throws Exception {
        for (String l : FixtureAgent.lines("p0b-slash.jsonl")) {
            if (StreamJson.parse(l) instanceof StreamJson.ControlResponse r && r.initialize() != null) {
                return r.initialize().commands();
            }
        }
        throw new AssertionError("p0b-slash.jsonl has no initialize answer");
    }

    /**
     * ★ Witness: replace the table lookup with a DENY-list (block what is listed, pass the rest) — the made-up built-in
     * passes and the "unclassified" assertion fails.
     */
    @Test
    @DisplayName("★ slash classification pinned against p0b-slash's initialize.commands: every built-in classified; blocks hold")
    void slashClassificationIsPinned() throws Exception {
        List<StreamJson.Command> commands = p0bCommands();
        assertEquals(54, commands.size(), "the recorded list");
        assertEquals(List.of(), ClaudeCodeProvider.unclassified(commands), "every recorded built-in is classified");
        record Case(String typed, SlashClass expected, String name) { }
        for (Case c : List.of(
                new Case("/config permissionMode=auto", SlashClass.BLOCK, "config"),
                new Case("/settings", SlashClass.BLOCK, "config"),
                new Case("/mcp disable fluxtion-analyser-hosted", SlashClass.BLOCK, "mcp"),
                new Case("/mcp", SlashClass.PASS, "mcp"),
                new Case("/output-style Proactive", SlashClass.BLOCK, "output-style"),
                new Case("/add-dir /", SlashClass.BLOCK, "add-dir"),
                new Case("/clear", SlashClass.NEW_CHAT, "clear"),
                new Case("/reset", SlashClass.NEW_CHAT, "clear"),
                new Case("/new", SlashClass.NEW_CHAT, "clear"),
                new Case("/compact", SlashClass.PASS, "compact"),
                new Case("/model sonnet", SlashClass.PASS, "model"),
                new Case("/review", SlashClass.PASS, "code-review"),        // the CLI's own alias
                new Case("/checkup", SlashClass.PASS, "doctor"),
                new Case("/proactive", SlashClass.BLOCK, "loop"),
                new Case("/routines", SlashClass.BLOCK, "schedule"),
                new Case("/brand-new-builtin", SlashClass.UNCLASSIFIED, null),
                // r6: normalised as the CLI reads it — leading whitespace, case, first token
                new Case("   /CONFIG model=opus", SlashClass.BLOCK, "config"),
                new Case("\t/Clear", SlashClass.NEW_CHAT, "clear"),
                new Case("/MCP   ", SlashClass.PASS, "mcp"),
                new Case("/", SlashClass.UNCLASSIFIED, null),
                // r6: refused until the P1a probes show what stream-json does with them
                new Case("!ls -la", SlashClass.SHORTCUT, null),
                new Case("  # remember this", SlashClass.SHORTCUT, null),
                // owner decision 2026-10-16: an @path mention is sent (the CLI inlines only in-folder files)
                new Case("summarise @/etc/hosts", SlashClass.TEXT, null),
                new Case("@CLAUDE.md what does it say", SlashClass.TEXT, null),
                new Case("what is a@b in this log", SlashClass.TEXT, null),
                // review S2: Unicode blanks String.strip misses
                new Case("\u00A0/config model=opus", SlashClass.BLOCK, "config"),
                new Case("\uFEFF/clear", SlashClass.NEW_CHAT, "clear"),
                new Case("\u2007!ls", SlashClass.SHORTCUT, null),
                new Case("\u202F# remember", SlashClass.SHORTCUT, null),
                new Case("\u200B/settings", SlashClass.BLOCK, "config"),
                new Case("/mcp\u00A0disable x", SlashClass.BLOCK, "mcp"),
                new Case("Why did node 3 not log?", SlashClass.TEXT, null))) {
            LocalAgentProvider.Slash s = ClaudeCodeProvider.classifyLine(c.typed(), commands);
            assertEquals(c.expected(), s.slash(), c.typed());
            assertEquals(c.name(), s.command(), c.typed());
        }
        // P1a live smoke: the CLI reads a line with leading blanks as prose, so a passed command is SENT normalised
        assertEquals("/usage", ClaudeCodeProvider.classifyLine("   /usage", commands).line("   /usage"));
        assertEquals("/model sonnet", ClaudeCodeProvider.classifyLine("\u00A0/MODEL   sonnet", commands).line("x"));
        assertEquals("/mcp", ClaudeCodeProvider.classifyLine(" /mcp ", commands).line(" /mcp "));
        assertEquals("Why did node 3 not log?", ClaudeCodeProvider.classifyLine("Why did node 3 not log?", commands)
                .line("Why did node 3 not log?"), "prose is sent as typed");
        // a built-in a newer CLI adds is unclassified, and named in the diagnostics line
        List<StreamJson.Command> newer = new ArrayList<>(commands);
        newer.add(new StreamJson.Command("teleport", List.of(), true));
        assertEquals(List.of("teleport"), ClaudeCodeProvider.unclassified(newer));
        LocalAgentProvider.Slash s = ClaudeCodeProvider.classifyLine("/teleport", newer);
        assertEquals(SlashClass.UNCLASSIFIED, s.slash());
        assertEquals("teleport", s.command(), "a name the CLI knows may be named");
    }

    // ---- Option 2 (owner, 2026-10-18): who Claude Code is here, and the docs it may read --------------------------------

    /**
     * ★ The identity note begins BOTH system notes, word for word, and Full alone adds the starter rule. Witness: change a
     * word of {@code IDENTITY_NOTE}, or put {@code FULL_STARTER_NOTE} into the Safe note — the pinned text or the Safe
     * assertion fails.
     */
    @Test
    @DisplayName("★ the identity note is pinned and begins both notes; Full alone says to offer a starter and stay off the web")
    void theIdentityNoteIsPinned() {
        String identity = "You are the assistant inside the Fluxtion Audit Log Analyser, the desktop app the person is "
                + "using now. Here, 'project', 'starter' or 'template', 'log', 'graph' and 'report' mean the analyser's: a "
                + "project is an analyser project folder, a starter is one of the analyser's starter templates, and a log "
                + "is the Fluxtion audit log open in it. Call analyser_context first: it says what is open and lists the "
                + "starters.";
        String full = "To begin a new application, offer a starter with analyser_propose; do not search the web or "
                + "scaffold by hand. Until a project exists you are in an empty scratch folder: create nothing there. Do "
                + "not use gh, curl or other network commands to look for examples.";
        assertEquals(identity, ClaudeCodeProvider.IDENTITY_NOTE);
        assertEquals(full, ClaudeCodeProvider.FULL_STARTER_NOTE);
        assertTrue(ClaudeCodeProvider.SYSTEM_NOTE.startsWith(identity + " "), "Safe begins with it");
        assertTrue(ClaudeCodeProvider.FULL_SYSTEM_NOTE.startsWith(identity + " " + full + " "), "Full begins with both");
        assertFalse(ClaudeCodeProvider.SYSTEM_NOTE.contains(full), "Safe has no web and no shell: it carries no such rule");
        for (char ch : "{}<>%$`\\\n\r".toCharArray()) {
            assertTrue(identity.indexOf(ch) < 0 && full.indexOf(ch) < 0, "fixed text, nothing interpolated: " + ch);
        }
    }

    /** The note is part of the posture ref: a session made before it is never resumed under it (CHANGELOG says so). */
    @Test
    @DisplayName("the identity note is part of postureRef, so an earlier session is not resumed")
    void theNoteIsInThePostureRef() {
        Path dir = tmp;
        String before = LocalAgentPosture.ANALYSER_ASSISTANT.ref(ClaudeCodeProvider.realPath(dir),
                ClaudeCodeProvider.SYSTEM_NOTE.substring(ClaudeCodeProvider.IDENTITY_NOTE.length() + 1));
        assertNotEquals(before, ClaudeCodeProvider.postureRef(dir, LocalAgentPosture.ANALYSER_ASSISTANT));
    }

    /**
     * ★ Owner, 2026-10-18: Full's argv carries exactly ONE docs rule — WebFetch, exactly fluxtion-playground.dev — and
     * Safe's carries none (Safe has no web tool at all). Witness: widen the rule to {@code WebFetch} or
     * {@code WebFetch(domain:*.fluxtion-playground.dev)}, or add WebSearch — the exact-rule assertions fail.
     */
    @Test
    @DisplayName("★ Full's argv pre-allows exactly WebFetch(domain:fluxtion-playground.dev); Safe's has no web rule")
    void onlyFullReadsTheDocsWithoutAsking() {
        assertEquals("WebFetch(domain:fluxtion-playground.dev)", LocalAgentPosture.DOCS_FETCH_RULE);
        for (LocalAgentPosture.FullMode mode : LocalAgentPosture.FullMode.values()) {
            List<String> c = new ClaudeCodeProvider().command(fullSpec(mode, 0));
            String allowed = c.get(c.indexOf("--allowedTools") + 1);
            List<String> web = java.util.Arrays.stream(allowed.split(",")).filter(t -> t.startsWith("Web")).toList();
            assertEquals(List.of("WebFetch(domain:fluxtion-playground.dev)"), web, "exactly one web rule: " + mode);
            assertFalse(String.join(" ", c).contains("WebSearch"), "never WebSearch");
            assertFalse(String.join(" ", c).contains("Bash("), "never a Bash network rule");
        }
        List<String> safe = new ClaudeCodeProvider().command(spec(false));
        assertFalse(String.join(" ", safe).contains("WebFetch"), "Safe carries no web rule");
        assertEquals("Read,Grep,Glob", safe.get(safe.indexOf("--tools") + 1), "Safe's tools stay the three");
    }

    /**
     * ★ The analyser's own approval layer pre-allows only an https URL whose host is exactly the docs host. Witness: make
     * {@code docsFetch} compare with {@code endsWith(DOCS_DOMAIN)} — the subdomain and look-alike rows fail; drop the
     * scheme check — the http row fails.
     */
    @Test
    @DisplayName("★ docsFetch: https and exactly fluxtion-playground.dev; another domain, a subdomain trick and http:// are not")
    void theDocsFetchRuleIsExact() {
        assertTrue(LocalAgentPosture.docsFetch("https://fluxtion-playground.dev/spring-authoring/skill.md"));
        assertTrue(LocalAgentPosture.docsFetch("https://FLUXTION-PLAYGROUND.dev/claude.txt"));
        assertTrue(LocalAgentPosture.docsFetch("https://fluxtion-playground.dev:443/x"));
        for (String no : List.of("http://fluxtion-playground.dev/claude.txt", "https://example.com/",
                "https://docs.fluxtion-playground.dev/x", "https://fluxtion-playground.dev.example.com/x",
                "https://evil-fluxtion-playground.dev/x", "https://fluxtion-playground.dev@example.com/x",
                "https://user@fluxtion-playground.dev/x", "https://fluxtion-playground.dev:8443/x", "ftp://fluxtion-playground.dev/",
                "fluxtion-playground.dev/x", "", "https://")) {
            assertFalse(LocalAgentPosture.docsFetch(no), no);
        }
        assertFalse(LocalAgentPosture.docsFetch(null));
        assertFalse(LocalAgentPosture.docsFetch(42));
    }

    /** The adapter observes a docs fetch as PRE_ALLOWED and every other built-in call as one that changes something. */
    @Test
    @DisplayName("the adapter observes a docs WebFetch as PRE_ALLOWED; another URL, another tool or bad JSON is asked")
    void theAdapterObservesTheDocsFetch() {
        var pre = telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallKind.PRE_ALLOWED;
        var changes = telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallKind.CHANGES;
        assertEquals(pre, LocalAgentAdapter.builtInKind("WebFetch",
                "{\"url\":\"https://fluxtion-playground.dev/claude.txt\",\"prompt\":\"x\"}"));
        assertEquals(changes, LocalAgentAdapter.builtInKind("WebFetch", "{\"url\":\"https://example.com/\"}"));
        assertEquals(changes, LocalAgentAdapter.builtInKind("WebFetch", "{\"url\":\"http://fluxtion-playground.dev/\"}"));
        assertEquals(changes, LocalAgentAdapter.builtInKind("WebSearch", "{\"query\":\"fluxtion-playground.dev\"}"));
        assertEquals(changes, LocalAgentAdapter.builtInKind("Bash", "{\"command\":\"curl https://fluxtion-playground.dev\"}"));
        assertEquals(changes, LocalAgentAdapter.builtInKind("WebFetch", "not json"));
    }
}
