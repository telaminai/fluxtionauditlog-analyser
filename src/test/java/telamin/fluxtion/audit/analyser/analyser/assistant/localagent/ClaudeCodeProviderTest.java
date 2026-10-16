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
     * ★ Witness: drop {@code --restricted} (or read the person's {@code defaultMode} into {@code --permission-mode}) —
     * the exact-line assertion fails.
     */
    @Test
    @DisplayName("★ the default command line is exactly §4.3, whatever the person's own settings say")
    void theDefaultCommandLine() throws Exception {
        List<String> c = new ClaudeCodeProvider().command(spec(false));
        String allowed = String.join(",", LocalAgentPosture.preAllowedVerbs().stream()
                .map(LocalAgentPosture::toolName).toList());
        String disallowed = String.join(",", LocalAgentPosture.disallowedVerbs().stream()
                .map(LocalAgentPosture::toolName).toList());
        List<String> expected = List.of("/opt/claude/bin/claude", "-p", "--input-format", "stream-json", "--output-format",
                "stream-json", "--verbose", "--include-partial-messages", "--replay-user-messages",
                "--session-id", "11111111-2222-4333-8444-555555555555", "--model", "haiku",
                "--tools", "Read,Grep,Glob", "--restricted", "--add-dir", "/work/proj", "--strict-mcp-config",
                "--mcp-config", c.get(c.indexOf("--mcp-config") + 1),
                "--allowedTools", allowed, "--disallowedTools", disallowed,
                "--permission-mode", "dontAsk", "--permission-prompts", "none",
                "--max-budget-usd", "4.25", "--append-system-prompt", ClaudeCodeProvider.SYSTEM_NOTE);
        assertEquals(expected, c);
        assertFalse(allowed.contains("*") || disallowed.contains("*"), "no wildcard [P0-30]");
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
     * ★ Witness: compute the disallow list as {@code McpTools.destructiveVerbs()} instead of "all − pre-allowed" — a
     * verb added to VerbSchemas stays visible and the second assertion fails.
     */
    @Test
    @DisplayName("★ --allowedTools ∩ destructive = ∅, and --disallowedTools = every verb − the pre-allowed ones")
    void theVerbLists() {
        List<String> pre = LocalAgentPosture.preAllowedVerbs();
        Set<String> overlap = new HashSet<>(pre);
        overlap.retainAll(McpTools.destructiveVerbs());
        assertEquals(Set.of(), overlap, "no destructive verb is auto-approved");
        Set<String> all = new LinkedHashSet<>(VerbSchemas.all().keySet());
        Set<String> union = new HashSet<>(pre);
        union.addAll(LocalAgentPosture.disallowedVerbs());
        assertEquals(all, union, "every verb is either pre-allowed or hidden");
        assertEquals(List.of("read", "aggregate", "series", "context", "coverage", "source", "filter", "goto", "flag",
                        "graph", "topology", "spotlight").stream().sorted().toList(), pre.stream().sorted().toList(),
                "today's pre-allowed set (§3)");
        // a verb added to VerbSchemas but not classified lands in the disallow list
        List<String> withNew = new ArrayList<>(all);
        withNew.add("brand_new_verb");
        List<String> classifiedPre = LocalAgentPosture.preAllowedVerbs(all, McpTools.destructiveVerbs());
        assertTrue(LocalAgentPosture.disallowedVerbs(withNew, classifiedPre).contains("brand_new_verb"),
                "a new verb is hidden from Claude until someone classifies it");
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
                new Case("summarise @/etc/hosts", SlashClass.SHORTCUT, null),
                new Case("@CLAUDE.md what does it say", SlashClass.SHORTCUT, null),
                new Case("what is a@b in this log", SlashClass.TEXT, null),
                new Case("Why did node 3 not log?", SlashClass.TEXT, null))) {
            LocalAgentProvider.Slash s = ClaudeCodeProvider.classifyLine(c.typed(), commands);
            assertEquals(c.expected(), s.slash(), c.typed());
            assertEquals(c.name(), s.command(), c.typed());
        }
        // a built-in a newer CLI adds is unclassified, and named in the diagnostics line
        List<StreamJson.Command> newer = new ArrayList<>(commands);
        newer.add(new StreamJson.Command("teleport", List.of(), true));
        assertEquals(List.of("teleport"), ClaudeCodeProvider.unclassified(newer));
        LocalAgentProvider.Slash s = ClaudeCodeProvider.classifyLine("/teleport", newer);
        assertEquals(SlashClass.UNCLASSIFIED, s.slash());
        assertEquals("teleport", s.command(), "a name the CLI knows may be named");
    }
}
