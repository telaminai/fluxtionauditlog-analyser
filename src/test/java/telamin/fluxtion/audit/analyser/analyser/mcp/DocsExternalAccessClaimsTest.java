package telamin.fluxtion.audit.analyser.analyser.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.llm.VerbSchemas;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The docs audit's "Fix in M74" list (docs/handoff/docs-audit-2026-10-17.md §4), held statically: what the user guide,
 * the connection and install pages, the evidence-bundle pages, the in-app help and the CHANGELOG tell an external-MCP
 * user must match the code. Every claim here was wrong on the site at once — a switch with four names, 17 tools for 18,
 * four destructive verbs for six, a paraphrased bridge error, a console that "names the endpoint file" — and nothing
 * failed, because the only test of the docs pinned substrings that could not see any of it (rule 8).
 *
 * <p>Witnesses, one per claim: put back "seventeen in all", "Four are marked", "localhost REST transport", the old
 * bridge message ("REST transport disabled"), "click **Explain**" or "two headless commands" — each assertion names the file and the words.
 */
class DocsExternalAccessClaimsTest {

    static final Path SITE = Path.of("docs/site");
    static final Path SRC = Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser");

    static String read(Path p) throws IOException {
        return Files.readString(p);
    }

    static String flat(String s) {
        return s.replaceAll("\\s+", " ");
    }

    /** Every page a person reads: the site's Markdown (but not the stamped release notes), the in-app help, the README. */
    static Map<Path, String> pages() throws IOException {
        Map<Path, String> out = new LinkedHashMap<>();
        try (Stream<Path> tree = Files.walk(SITE)) {
            for (Path p : tree.filter(f -> f.toString().endsWith(".md")).sorted().toList()) {
                if (p.endsWith("release-notes.md") || p.toString().contains("/assets/")) continue;
                out.put(p, read(p));
            }
        }
        out.put(Path.of("src/main/resources/help/help.html"), read(Path.of("src/main/resources/help/help.html")));
        out.put(Path.of("README.md"), read(Path.of("README.md")));
        return out;
    }

    static List<String> offenders(String... phrases) throws IOException {
        List<String> found = new ArrayList<>();
        for (var e : pages().entrySet()) {
            String text = flat(e.getValue());
            for (String phrase : phrases) if (text.contains(phrase)) found.add(e.getKey() + ": \"" + phrase + "\"");
        }
        return found;
    }

    @Test
    @DisplayName("★ one name for the REST switch: the code's label, and none of the four old names, on any page")
    void oneSwitchName() throws Exception {
        assertTrue(read(SRC.resolve("ui/ConfigPanel.java")).contains("\"External MCP/REST access (for an AI client on this machine)\""));
        assertTrue(read(SRC.resolve("ui/MainFrame.java")).contains("new JCheckBoxMenuItem(\"External MCP/REST access\")"));
        assertEquals(List.of(), offenders("localhost REST transport", "local transport", "Local MCP / REST",
                "drive the UI over localhost", "Settings ▸ Assistant / LLM", "the REST transport is off"));
        String connect = flat(read(SITE.resolve("connect-an-llm.md")));
        assertTrue(connect.contains("Settings ▸ Assistant ▸ External MCP/REST access (for an AI client on this machine)"));
        assertTrue(connect.contains("AI ▸ External MCP/REST access"));
    }

    @Test
    @DisplayName("★ the tool count and list are VerbSchemas': every verb as analyser_<verb>, propose named hosted-only and never listed")
    void theToolsAreTheVerbs() throws Exception {
        Set<String> verbs = VerbSchemas.all().keySet();
        assertFalse(verbs.contains(VerbSchemas.PROPOSE), "propose is hosted-only");
        for (Path page : List.of(SITE.resolve("connect-an-llm.md"), SITE.resolve("user-guide/assistant.md"))) {
            String text = read(page);
            Matcher m = Pattern.compile("connected · (\\d+) tools\\n((?:\\s+analyser_[^\\n]*\\n)+)").matcher(text);
            assertTrue(m.find(), page + ": the /mcp listing");
            assertEquals(verbs.size(), Integer.parseInt(m.group(1)), page + ": the tool count");
            for (String verb : verbs) {
                assertTrue(Pattern.compile("\\banalyser_" + verb + "\\b").matcher(m.group(2)).find(),
                        page + ": the /mcp listing is missing analyser_" + verb);
            }
            assertFalse(m.group(2).contains("analyser_propose"), page + ": propose is never served to an external client");
            assertTrue(text.contains("`propose`"), page + " must say propose exists and is Claude Code in the tab's only");
        }
        assertTrue(flat(read(SITE.resolve("connect-an-llm.md"))).contains("eighteen in all"), "the prose count");
        assertEquals(18, verbs.size(), "the prose says eighteen: change it with the verb set");
        assertEquals(List.of(), offenders("seventeen in all", "17 tools", "17 including"));
        assertTrue(read(Path.of("docs/ONBOARDING.md")).contains("18 — through M69 `walk` and `import`"));
    }

    @Test
    @DisplayName("★ six destructive verbs, each named where the guide lists them; source is read-only")
    void theDestructiveVerbs() throws Exception {
        Set<String> destructive = McpTools.destructiveVerbs();
        assertEquals(6, destructive.size(), "the guide says six: change the words with the set");
        assertTrue(McpTools.READ_ONLY.contains("source"));
        String guide = flat(read(SITE.resolve("user-guide/assistant.md")));
        int at = guide.indexOf("Six are marked **destructive**");
        assertTrue(at >= 0, "the guide's count of destructive verbs");
        String sentence = guide.substring(at, guide.indexOf("never to an external client", at));
        for (String verb : destructive) assertTrue(sentence.contains("`" + verb + "`"), "the guide's list misses " + verb);
        assertTrue(guide.contains("`context`, `coverage` and `source` are marked read-only"));
        String connect = flat(read(SITE.resolve("connect-an-llm.md")));
        for (String verb : destructive) assertTrue(connect.contains("`" + verb + "`"), "connect-an-llm misses " + verb);
        assertEquals(List.of(), offenders("Four are marked **destructive**"));
    }

    @Test
    @DisplayName("★ the bridge's not-running message is quoted exactly as McpBridge says it")
    void theBridgeErrorIsQuotedVerbatim() throws Exception {
        for (String page : List.of("connect-an-llm.md", "user-guide/assistant.md")) {
            assertTrue(flat(read(SITE.resolve(page))).contains(McpBridge.NOT_RUNNING), page + " must quote: "
                    + McpBridge.NOT_RUNNING);
        }
        assertTrue(McpBridge.NOT_RUNNING.startsWith("analyser not running, or External MCP/REST access is off — turn it on in Settings ▸ Assistant"),
                "the bridge names the switch as Settings does");
        // the message before the fix list's one-name rule, in full and in its short quoted form
        assertEquals(List.of(), offenders("REST transport disabled", "(REST on, no first-run dialog"));
    }

    @Test
    @DisplayName("★ automation reads the token from the endpoint file and checks its pid; the console shows 8 characters")
    void theTokenNote() throws Exception {
        String frame = read(SRC.resolve("ui/MainFrame.java"));
        assertTrue(frame.contains("actionToken.substring(0, Math.min(8, actionToken.length()))"), "the console's hint is 8 characters");
        assertTrue(read(SRC.resolve("net/RestEndpointFile.java")).contains("m.put(\"pid\""));
        for (String page : List.of("install.md", "connect-an-llm.md")) {
            String text = flat(read(SITE.resolve(page)));
            assertTrue(text.contains("~/.fluxtion-analyser/rest-endpoint"), page);
            assertTrue(text.contains("first 8 characters"), page + ": the console shows a hint, not the token");
            assertTrue(text.contains("mode 600"), page);
            assertTrue(text.contains("`pid`"), page + ": check pid before trusting the file");
        }
        assertEquals(List.of(), offenders("the console names the endpoint file", "and prints where the endpoint file is"));
    }

    @Test
    @DisplayName("★ external access while Claude Code runs in the tab: the refusals are quoted and the choice named")
    void externalAccessWhileClaudeCodeRuns() throws Exception {
        String connect = flat(read(SITE.resolve("connect-an-llm.md")));
        Class<?> server = Class.forName("telamin.fluxtion.audit.analyser.analyser.net.ActionServer");
        for (String name : List.of("EXTERNAL_OFF", "EXTERNAL_CLOSED_FOR_SESSION")) {
            var f = server.getDeclaredField(name);
            f.setAccessible(true);
            String words = (String) f.get(null);
            assertTrue(connect.contains(words), "connect-an-llm must quote ActionServer." + name + ": " + words);
        }
        assertTrue(connect.contains(telamin.fluxtion.audit.analyser.analyser.session.node.ActionListener.CLOSED_FOR_SESSION));
        assertTrue(connect.contains("Close external access while this session runs"));
        assertTrue(connect.contains("## While Claude Code runs in the analyser"));
        assertTrue(connect.contains("[While Claude Code runs in the analyser](#while-claude-code-runs-in-the-analyser)"),
                "the troubleshooting points at the section");
    }

    @Test
    @DisplayName("★ Connect an AI client is where the code puts it: the AI menu and Settings ▸ Assistant ▸ Every assistant")
    void whereConnectLives() throws Exception {
        assertTrue(read(SRC.resolve("ui/ConfigPanel.java")).contains("group(\"Every assistant — the bridge, files and projects\")"));
        assertTrue(read(SRC.resolve("ui/ConfigPanel.java")).contains("new JButton(\"Connect an AI client…\")"));
        for (String page : List.of("connect-an-llm.md", "faq.md", "user-guide/assistant.md")) {
            String text = flat(read(SITE.resolve(page)));
            assertTrue(text.contains("AI ▸ Connect an AI client…"), page);
            assertTrue(text.contains("Settings ▸ Assistant ▸ Every assistant ▸ Connect an AI client…"), page);
        }
    }

    @Test
    @DisplayName("★ Ask, not Explain, wherever a page says what to click — the site, the in-app help and the README")
    void askNotExplain() throws Exception {
        assertEquals(List.of(), offenders("click **Explain**", "*Explain* prompt", "<b>Explain</b>", "Explain with LLM",
                "Toolbar shortcuts are unchanged", "hit **Explain**", "Diff / Explain / Flag", "every *Explain*"));
        assertTrue(read(Path.of("src/main/resources/help/help.html")).contains("<b>Ask</b>"));
    }

    @Test
    @DisplayName("★ bundle capture needs file exchange; the reader has three commands")
    void theBundlePages() throws Exception {
        assertTrue(flat(read(SITE.resolve("evidence-bundles/sending.md"))).contains("refused unless *Allow assistant file exchange* is on"));
        assertTrue(read(SITE.resolve("evidence-bundles/reference.md")).contains("read by three headless commands"));
        assertEquals(List.of(), offenders("two headless commands", "the two commands"));
    }

    @Test
    @DisplayName("★ the CHANGELOG's [Unreleased] describes the shipped header, not the mode selector it replaced")
    void theChangelogReadsCoherently() throws Exception {
        String log = read(Path.of("CHANGELOG.md"));
        int at = log.indexOf("## [Unreleased]");
        String unreleased = flat(log.substring(at, log.indexOf("\n## [", at + 1)));
        for (String gone : List.of("Choose **Claude Code** at the top of the Assistant tab", "analyser-assistant posture",
                "Or choose directly", "its switch is shown, disabled", "Profile…", "Claude Code · your plan",
                "In-app · API key", "the questions need in-process actions")) {
            assertFalse(unreleased.contains(gone), "CHANGELOG [Unreleased] still says: " + gone);
        }
        assertTrue(unreleased.contains("**Assistant: <profile> ▾**"));
        assertTrue(unreleased.contains("*Turn on External MCP/REST access…*, formerly *Enable local transport…*"));
    }
}
