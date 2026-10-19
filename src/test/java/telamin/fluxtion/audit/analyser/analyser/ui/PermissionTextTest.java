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

    /**
     * ★ Review PR151 F1 (2026-10-18): a request whose words are not held is never shown as approvable — no input, the
     * reason in their place, and {@code held} false, which keeps the dialog's Allow disabled. Witness: return
     * {@code held = true} for a missing request — the assertion fails.
     */
    @Test
    @DisplayName("★ missing words: the dialog's content says so and is not approvable")
    void missingWordsAreNotApprovable() {
        var c = PermissionText.of(null, ToolClass.HOSTED_DESTRUCTIVE, "walk");
        assertFalse(c.held());
        assertEquals("", c.input());
        assertEquals(PermissionText.NOT_HELD, c.changes());
        assertTrue(c.title().contains("analyser_walk"), c.title());
        assertTrue(PermissionText.of(new LocalAgentTranscript.Permission("Bash", null, "Bash", "{\"command\":\"ls\"}"),
                ToolClass.BUILT_IN, "Bash").held(), "control: held words are approvable");
    }

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
        // review M74 usability #6: the title says what THIS call does, its quoted value escaped like the input
        assertEquals("Claude Code asks to write a finding to \u201Cfdp.\\u202Epdf\u201D", c.title());
        assertFalse(c.title().contains(RLO), "the sentence never carries a live override either");
        assertTrue(c.changes().contains("exchange folder"), c.changes());
        assertEquals("the analyser's own tool (analyser_report)", c.serverLine(), "no bridge name, no 'named by'");
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
        // review P3 N5: whitespace stripped from both sides — the one string inside quotes loses its spaces on both
        assertEquals(raw.replaceAll("\\s", ""), p.replaceAll("\\s", ""), "the same tokens, in the same order");
        String big = "{\"k\":\"" + "v".repeat(60_000) + "\"}";
        assertTrue(PermissionText.of(new LocalAgentTranscript.Permission("Bash", null, "Bash", big), ToolClass.BUILT_IN,
                "Bash").input().contains("v".repeat(60_000)), "nothing is elided");
    }

    @Test
    @DisplayName("a person's own tool: titled by its name, with what kind of thing it does; Bash's command shown verbatim")
    void thePersonsOwnTools() {
        var bash = PermissionText.of(new LocalAgentTranscript.Permission("Bash", null, "Bash",
                "{\"command\":\"rm -rf ./build && echo done\",\"description\":\"Clean\"}"), ToolClass.BUILT_IN, "Bash");
        assertEquals("Claude Code asks to run a command on your computer", bash.title());
        assertTrue(bash.changes().contains("the exact command is below"), bash.changes());
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
        assertEquals("server: db-tools · its name for the tool: Drop", mcp.serverLine(), "the MCP tool keeps its server");
        var hosted = PermissionText.of(new LocalAgentTranscript.Permission("mcp__fluxtion-analyser-hosted__analyser_open",
                "fluxtion-analyser-hosted", null, "{}"), ToolClass.HOSTED_DESTRUCTIVE, "open");
        assertEquals("the analyser's own tool (analyser_open)", hosted.serverLine(), "review D1: no '…-hosted' jargon");
    }

    /**
     * ★ Review P3 N5 (2026-10-17): look-alike spaces and blank-drawing characters are escaped, so two commands that differ
     * only by them never read the same. Witness: drop the SPACE_SEPARATOR / BLANK_LOOKING clause in {@code visible} —
     * the no-break space and the Hangul filler pass through.
     */
    @Test
    @DisplayName("★ a no-break space, an en space, a Hangul filler and a blank Braille cell are shown escaped")
    void lookAlikeSpacesAreEscaped() {
        for (int cp : new int[]{0x00A0, 0x1680, 0x2000, 0x2005, 0x200A, 0x202F, 0x205F, 0x3000, 0x3164, 0x115F, 0x1160,
                0xFFA0, 0x2800, 0x034F}) {
            var v = PermissionText.visible("rm" + new String(Character.toChars(cp)) + "-rf");
            assertTrue(v.escaped(), Integer.toHexString(cp));
            assertEquals("rm" + String.format("\\u%04X", cp) + "-rf", v.text());
        }
        var plain = PermissionText.visible("rm -rf ./build\n");
        assertFalse(plain.escaped(), "an ordinary space and the newline are kept");
        assertEquals("rm -rf ./build\n", plain.text());
    }

    // ---- review M74 usability #6: the dialog says what THIS call does, from its input ------------------------------

    static PermissionText.Content hosted(String verb, String input) {
        return PermissionText.of(new LocalAgentTranscript.Permission("mcp__fluxtion-analyser-hosted__analyser_" + verb,
                "fluxtion-analyser-hosted", "Analyser " + verb, input), ToolClass.HOSTED_DESTRUCTIVE, verb);
    }

    /**
     * ★ One sentence per sub-action, the exact input still below. Witness: return the verb's general rationale
     * ({@code McpTools.destructiveRationale}) as before — every title reads "asks to run analyser_walk" and this fails
     * at the first row; the "a delete reads as a delete" assertion is the one the review found missing.
     */
    @org.junit.jupiter.params.ParameterizedTest(name = "{0} {1}")
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', textBlock = """
            walk       | {"name":"x","delete":true}                         | Claude Code asks to delete the saved walk “x”
            walk       | {"name":"x","play":true}                           | Claude Code asks to play the walk “x”
            walk       | {"name":"x","play":true,"step":3}                  | Claude Code asks to play the walk “x” from step “3”
            walk       | {"end":true}                                       | Claude Code asks to end the walk that is showing
            walk       | {"name":"x","rename":"y"}                          | Claude Code asks to rename the walk “x” to “y”
            walk       | {"restore":true}                                   | Claude Code asks to list the deleted walks
            walk       | {"restore":"x"}                                    | Claude Code asks to restore the deleted walk “x”
            walk       | {"name":"x","steps":[]}                            | Claude Code asks to save the walk “x”
            walk       | {"name":"x","conversation":null}                   | Claude Code asks to remove the conversation from the walk “x”
            walk       | {"name":"x","reel":"x.html"}                       | Claude Code asks to record the walk “x” as a page, written to “x.html”
            report     | {"name":"y","sections":[]}                         | Claude Code asks to build the report “y”
            report     | {"name":"y","sections":[],"path":"y.pdf"}          | Claude Code asks to build the report “y” and write it to “y.pdf”
            report     | {"name":"y","delete":true}                         | Claude Code asks to delete the report “y”
            report     | {"name":"y","rename":"z"}                          | Claude Code asks to rename the report “y” to “z”
            report     | {"restore":true}                                   | Claude Code asks to list the deleted reports
            report     | {"restore":"y"}                                    | Claude Code asks to restore the deleted report “y”
            report     | {"name":"y","csv":2,"path":"t.csv"}                | Claude Code asks to write table “2” of the report “y” as CSV to “t.csv”
            report     | {"recordIndex":16,"path":"f.pdf"}                  | Claude Code asks to write a finding for record “16” to “f.pdf”
            report     | {"bundle":{"path":"case.fexp"}}                    | Claude Code asks to write an evidence bundle to “case.fexp”
            screenshot | {"path":"s.png"}                                   | Claude Code asks to write a screenshot of the window to “s.png”
            open       | {"log":"other.yaml"}                               | Claude Code asks to open the audit log “other.yaml”
            open       | {"project":"p"}                                    | Claude Code asks to open the project “p”
            source_root| {"add":["src"]}                                    | Claude Code asks to add the source folder(s) “[src]”
            import     | {"bundle":"b.fexp"}                                | Claude Code asks to preview what the bundle “b.fexp” would bring
            import     | {"bundle":"b.fexp","categories":["REPORTS"]}       | Claude Code asks to borrow “[REPORTS]” from the bundle “b.fexp” into this project
            """)
    void eachSubActionSaysWhatThisCallDoes(String verb, String input, String title) {
        var c = hosted(verb.strip(), input.strip());
        assertEquals(title.strip(), c.title());
        assertFalse(c.changes().isBlank(), "and its consequence");
        assertEquals(PermissionText.visible(PermissionText.pretty(input.strip())).text(), c.input(),
                "the exact input is still shown, unchanged");
    }

    /**
     * ★ Final review #2 (2026-10-17): the sentence is chosen with the EXECUTOR's predicates ({@code VerbOperations},
     * {@code WalkVerb.choose}), so what the person approves is the call that runs. Witness: put back the dialog's own
     * predicate for a report delete ({@code if (has(p, "delete")) return … "delete the report …"} first in the report
     * case) — {@code delete: false} beside sections reads as a delete, while the executor builds and replaces it.
     */
    @org.junit.jupiter.params.ParameterizedTest(name = "{0} {1}")
    @org.junit.jupiter.params.provider.CsvSource(delimiter = '|', textBlock = """
            report     | {"name":"y","delete":false,"sections":[]}          | Claude Code asks to build the report “y”      | BUILD
            report     | {"name":"y","delete":null,"rename":"z"}            | Claude Code asks to rename the report “y” to “z” | RENAME
            report     | {"delete":true,"path":"f.pdf"}                     | Claude Code asks to write a finding to “f.pdf” | FINDING
            report     | {"name":"y","rename":null}                         | Claude Code asks to build the report “y”      | BUILD
            report     | {"restore":"y","name":"y"}                         | Claude Code asks to run analyser_report       | INVALID
            walk       | {"name":"x","delete":false}                        | Claude Code asks to run analyser_walk         |
            walk       | {"name":"x","play":true,"delete":true}             | Claude Code asks to run analyser_walk         |
            open       | {"log":"a.yaml","close":"log"}                     | Claude Code asks to close the “log”           |
            open       | {"project":"/p","create":true}                     | Claude Code asks to preview creating a project in “/p” |
            open       | {"follow":true}                                    | Claude Code asks to follow the open log as it grows |
            open       | {"bundle":"b.fexp","log":"a.yaml"}                 | Claude Code asks to open the evidence bundle “b.fexp” |
            """)
    @DisplayName("★ the sentence is the executor's choice: delete:false with sections is a build")
    void theSentenceIsTheExecutorsChoice(String verb, String input, String title, String op) {
        var c = hosted(verb.strip(), input.strip());
        assertEquals(title.strip(), c.title());
        if (op != null && !op.isBlank()) {
            var p = (java.util.Map<?, ?>) telamin.fluxtion.audit.analyser.analyser.llm.Json.parse(input.strip());
            assertEquals(telamin.fluxtion.audit.analyser.analyser.mcp.VerbOperations.ReportOp.valueOf(op.strip()),
                    telamin.fluxtion.audit.analyser.analyser.mcp.VerbOperations.report(p).op(),
                    "the executor runs the operation the sentence names");
        }
    }

    @Test
    @DisplayName("★ a delete reads as a delete and says it is restorable; a play says nothing saved changes")
    void deleteAndPlayAreDifferent() {
        var delete = hosted("walk", "{\"delete\":true,\"name\":\"DEMO first breach\"}");
        var play = hosted("walk", "{\"play\":true,\"name\":\"DEMO first breach\"}");
        assertNotEquals(delete.title(), play.title(), "the review's finding: both read the verb's whole rationale");
        assertTrue(delete.changes().contains("restored"), delete.changes());
        assertTrue(play.changes().contains("nothing saved changes"), play.changes());
        String long60 = "n".repeat(200);
        var longName = hosted("walk", "{\"delete\":true,\"name\":\"" + long60 + "\"}");
        assertTrue(longName.title().length() < 140, "a long value is cut in the sentence: " + longName.title().length());
        assertTrue(longName.input().contains(long60), "and never in the input below");
    }

    @Test
    @DisplayName("an input that names no sub-action falls back to the verb's rationale; built-ins name their file or URL")
    void fallbacksAndBuiltIns() {
        var unknown = hosted("walk", "{}");
        assertEquals("Claude Code asks to run analyser_walk", unknown.title());
        var write = PermissionText.of(new LocalAgentTranscript.Permission("Write", null, "Write",
                "{\"file_path\":\"/tmp/DEMO.txt\",\"content\":\"x\"}"), ToolClass.BUILT_IN, "Write");
        assertEquals("Claude Code asks to write the file \u201C/tmp/DEMO.txt\u201D", write.title());
        var fetch = PermissionText.of(new LocalAgentTranscript.Permission("WebFetch", null, "WebFetch",
                "{\"url\":\"https://example.com\"}"), ToolClass.BUILT_IN, "WebFetch");
        assertEquals("Claude Code asks to fetch \u201Chttps://example.com\u201D", fetch.title());
    }
}
