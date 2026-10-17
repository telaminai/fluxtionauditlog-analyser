package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ToolClass;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P3 (spec §5.7): the permission dialog's words, headless. The input is the model's data: every token kept, in
 * order, its invisible and direction-changing characters made visible, nothing elided; the title names the verb and
 * what it changes in the analyser's own words; the server's names are secondary lines.
 */
class PermissionTextTest {

    static final String RLO = new String(Character.toChars(0x202E));
    static final String ZWSP = new String(Character.toChars(0x200B));

    /** ★ Witness: return the input unescaped from {@code visible} — the override reaches the text area as itself. */
    @Test
    @DisplayName("★ a <html> string and a right-to-left override are shown as text and as a visible escape")
    void markupAndBidiAreShownAsText() {
        String raw = "{\"title\":\"<html><img src='http://127.0.0.1:9/x'>\",\"path\":\"fdp." + RLO + "pdf\",\"z\":\"a"
                + ZWSP + "b\"}";
        var c = PermissionText.of(new LocalAgentTranscript.Permission("mcp__fluxtion-analyser-hosted__analyser_report",
                "fluxtion-analyser-hosted", "Analyser Report", raw), ToolClass.HOSTED_DESTRUCTIVE, "report");
        assertTrue(c.input().contains("<html><img src='http://127.0.0.1:9/x'>"), "markup shown as its characters");
        assertFalse(c.input().contains(RLO), "no live override in what the person reads");
        assertTrue(c.input().contains("\\u202E"), c.input());
        assertTrue(c.input().contains("\\u200B"), "zero-width characters too");
        assertTrue(c.escaped(), "and the dialog says it escaped them");
        assertEquals("Claude Code asks to run analyser_report", c.title());
        assertTrue(c.changes().contains("report builds or replaces a named report"), c.changes());
        assertEquals("server: fluxtion-analyser-hosted · named by the server: Analyser Report", c.serverLine());
    }

    /** ★ §8.3 UI. Witness: show only the last " — " step (the P2 rendering) — "approved by you" vanishes behind the ✓. */
    @Test
    @DisplayName("★ a tool line shows who decided and then the tool's result: approved by you · ✓, denied by you")
    void theToolLineShowsTheDecision() throws Exception {
        String shown = TranscriptViewTest.onEdt(() -> {
            TranscriptView v = new TranscriptView();
            v.render(List.of(TranscriptViewTest.answer("a",
                    "▸ analyser_report {\"title\":\"x\"} — approved by you — ✓\n▸ Bash {\"command\":\"ls\"} — denied by you"
                            + "\n▸ analyser_open {} — denied: not answered within 120 s")));
            return v.getText();
        });
        assertTrue(shown.contains("analyser_report  approved by you · ✓"), shown);
        assertTrue(shown.contains("Bash  denied by you"), shown);
        assertTrue(shown.contains("analyser_open  denied: not answered within 120 s"), shown);
    }

    @Test
    @DisplayName("the input is re-indented token by token: every token kept in order, nothing elided")
    void prettyKeepsEveryToken() {
        String raw = "{\"a\": 1.50, \"b\":[true,null,{}],\"c\":\"x, y: {z}\"}";
        String p = PermissionText.pretty(raw);
        assertEquals("{\n  \"a\": 1.50,\n  \"b\": [\n    true,\n    null,\n    {}\n  ],\n  \"c\": \"x, y: {z}\"\n}", p);
        assertEquals(raw.replaceAll("\\s", ""), p.replaceAll("\\s", "").replace("x,y:{z}", "x,y:{z}"),
                "the same tokens, in the same order");
        String big = "{\"k\":\"" + "v".repeat(60_000) + "\"}";
        assertTrue(PermissionText.of(new LocalAgentTranscript.Permission("Bash", null, "Bash", big), ToolClass.BUILT_IN,
                "Bash").input().contains("v".repeat(60_000)), "nothing is elided");
    }

    @Test
    @DisplayName("a person's own tool: titled by its name, with what kind of thing it does; Bash's command shown verbatim")
    void thePersonsOwnTools() {
        var bash = PermissionText.of(new LocalAgentTranscript.Permission("Bash", null, "Bash",
                "{\"command\":\"rm -rf ./build && echo done\",\"description\":\"Clean\"}"), ToolClass.BUILT_IN, "Bash");
        assertEquals("Claude Code asks to use Bash", bash.title());
        assertTrue(bash.changes().contains("runs a command on your computer"));
        assertTrue(bash.input().contains("\"command\": \"rm -rf ./build && echo done\""), bash.input());
        var mcp = PermissionText.of(new LocalAgentTranscript.Permission("mcp__db-tools__drop", "db-tools", "Drop", "{}"),
                ToolClass.OTHER_MCP, null);
        assertEquals("Claude Code asks to use mcp__db-tools__drop", mcp.title());
        assertTrue(mcp.changes().contains("your own MCP servers"));
        assertEquals("Denied automatically in 120 s if not answered", LocalAgentPermissionDialog.countdownText(120_000));
        assertEquals("Denied automatically in 1 s if not answered", LocalAgentPermissionDialog.countdownText(10));
        assertEquals("Not answered — it is being denied", LocalAgentPermissionDialog.countdownText(-5));
    }

    /**
     * ★ M74 UX (P3 leftover b): a built-in tool belongs to Claude Code itself — it must not claim a server. The dialog
     * read "named by the server: Bash" for Bash, whose request carries {@code mcp_server: null}. Witness: return the
     * server line for built-ins as before — the first assertion reads "named by the server: Bash".
     */
    @Test
    @DisplayName("★ a built-in says 'built-in tool', never 'named by the server'; an MCP tool keeps its server line")
    void aBuiltInClaimsNoServer() {
        var bash = PermissionText.of(new LocalAgentTranscript.Permission("Bash", null, "Bash", "{\"command\":\"ls\"}"),
                ToolClass.BUILT_IN, "Bash");
        assertEquals(PermissionText.BUILT_IN_LINE, bash.serverLine());
        assertFalse(bash.serverLine().contains("server"), bash.serverLine());
        var mcp = PermissionText.of(new LocalAgentTranscript.Permission("mcp__db-tools__drop", "db-tools", "Drop", "{}"),
                ToolClass.OTHER_MCP, null);
        assertEquals("server: db-tools · named by the server: Drop", mcp.serverLine(), "the MCP tool keeps its server");
        var hosted = PermissionText.of(new LocalAgentTranscript.Permission("mcp__fluxtion-analyser-hosted__analyser_open",
                "fluxtion-analyser-hosted", null, "{}"), ToolClass.HOSTED_DESTRUCTIVE, "open");
        assertEquals("server: fluxtion-analyser-hosted", hosted.serverLine());
    }
}
