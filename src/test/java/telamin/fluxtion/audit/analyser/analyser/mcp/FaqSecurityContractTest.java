package telamin.fluxtion.audit.analyser.analyser.mcp;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState;
import telamin.fluxtion.audit.analyser.analyser.ui.setup.FlowDiagram;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The governance rule from review_handoff_16_aug_2026 D2: any verb marked destructive to MCP clients
 * MUST be named in the security statement — the page a security evaluator reads. This test makes
 * the security answer mechanically un-forgettable: add a mutating/file-writing verb without documenting
 * it there and the build fails.
 *
 * <p>Docs restructure, phase 6 (2026-10-18): the security answer moved from the FAQ's "Is the assistant's action
 * socket safe to enable?" to its own page, {@code docs/site/admin/security.md}, and every pin moved with it. The FAQ
 * keeps a short answer that links there, and its offline answer and old-name checks stay pinned here.
 */
class FaqSecurityContractTest {

    /** The security statement (B11's one-page model), where the FAQ's security answer now lives. */
    static final Path SECURITY = Path.of("docs/site/admin/security.md");

    @Test
    void everyDestructiveVerbIsNamedInTheFaqSecurityAnswer() throws Exception {
        String faq = Files.readString(Path.of("docs/site/faq.md"));
        String page = Files.readString(SECURITY);
        assertTrue(page.startsWith("# Security model and limits"), "the security page moved or was renamed — update this "
                + "test AND make sure the statement still exists; it is the user-facing security contract");
        assertTrue(faq.contains("## Is the assistant's action socket safe to enable?")
                        && faq.contains("[security model](admin/security.md)"),
                "the FAQ's security question must stay, and point at the security page");
        String answer = page.replaceAll("\\s+", " ");   // prose reflows; the CONTRACT must survive a line-wrap
        // M29 D-F4 (review F1): the read rule is part of the security contract — external reads share
        // the write opt-in and its directory, and the FAQ must keep saying so
        assertTrue(answer.contains("read only from that same directory")
                        || answer.contains("reads are confined"),
                "the FAQ security answer no longer states the external-read confinement rule");
        // M31 D-P3: the plugin trust boundary is part of the security contract
        assertTrue(answer.contains("arbitrary code execution"),
                "the FAQ security answer must state the plugin trust boundary in plain words");
        assertTrue(answer.contains("cannot add verbs"),
                "…and that plugins can never widen the verb surface");
        // M73 (spec-create-project.md §5): creating a project — the switch, every write, and the preview's read reach
        assertTrue(answer.contains("Allow the assistant to create projects"),
                "the FAQ security answer must name the switch that gates an assistant's create");
        assertTrue(answer.contains("never in a project profile, a shared settings file or an evidence bundle"),
                "…and that it is machine-only");
        for (String write : new String[]{"<dir>/.analyser/project.fluxtion-settings", "<dir>/CLAUDE.md",
                "pending edits", "recent-projects", "activated at the next start", "session-recovery journal"}) {
            assertTrue(answer.contains(write), "the FAQ security answer must state every write a create causes: " + write);
        }
        assertTrue(answer.contains("skills to depth 7") && answer.contains("GraphML to depth 12")
                        && answer.contains("32 MB"),
                "…and the preview's read reach");
        // M74 P1a (spec-claude-code-provider.md §7): what Start does, the default posture's limits, and that the analyser
        // runs the person's own CLI and never touches its credentials
        for (String claim : new String[]{"Allow the analyser to start Claude Code", "your own installed `claude`",
                "never reads or stores your Claude Code credentials", "before your first Send",
                "no record content is sent until you press Send", "Read, Grep and Glob confined to the folder",
                "no Bash", "none of your skills, hooks, settings or MCP servers",
                "never treated as Claude Code's", "asks you before it deletes or replaces a chart",
                "a bundle's working copy is refused"}) {
            assertTrue(answer.contains(claim), "the FAQ security answer must state, for Claude Code: " + claim);
        }
        // M74 P1b (spec-claude-code-provider.md §7): the full posture is a separate switch, and the FAQ says what it runs,
        // what it may use, that the analyser does not confine it, that bypassPermissions is never offered and that only the
        // Start button starts it (warm start cut, OD-74.25); the preview label went with P3 (OD-74.26, r12)
        for (String claim : new String[]{"Full Claude Code is a separate switch with its own warning",
                "your settings, skills, hooks and MCP servers", "the folder's hooks and MCP servers run when you press Start",
                "asks again if those files change", "your own tools, including Bash",
                "as far as your permission rules and the mode you chose allow",
                "the analyser does not confine it to the folder", "bypassPermissions is never offered",
                // owner 2026-10-18: the analyser's standing instruction, said as what it is
                "it adds one standing instruction", "a request to the model, not a sandbox",
                // owner 2026-10-18: the one narrow open that needs no dialog, and what still asks
                "Under **Full Claude Code only**", "inside the session's project folder",
                "Safe analysis asks for every `open`",
                // owner 2026-10-18: Full's in-project report writes, and what still asks
                "a `report` call that **writes a new report inside the active project**", "**this session wrote**",
                "It never writes over an existing file or a report you made",
                "refuses a report that appeared or changed meanwhile",
                "**every `report` call in Safe analysis or from an external MCP or REST client** are asked or refused as before",
                "*allowed: writes a new report inside this project*",
                "Start button starts full Claude Code",
                // owner 2026-10-18 (Option 2): the one web read Full makes without asking, and its exact rule
                "Full may read fluxtion-playground.dev documentation without asking",
                "`WebFetch(domain:fluxtion-playground.dev)`", "never `WebSearch`, another domain or a network command",
                "any other fetch is asked",
                // the analyser's note to Claude Code, and a proposed starter
                "The analyser's note to Claude Code", "fixed text with nothing interpolated",
                "Claude names a catalogue id and never a path", "only then is the starter downloaded",
                "the *Allow the assistant to create projects* switch does not gate this path"}) {
            assertTrue(answer.contains(claim), "the FAQ security answer must state, for full Claude Code: " + claim);
        }
        assertFalse(answer.contains("(preview)") || answer.contains("not for customer release"),
                "P3 shipped: the full posture is no longer called a preview (OD-74.26, r12)");
        // M74 P3 (§7): the analyser asks before Claude Code runs a verb that changes what is open or writes files, an
        // approval covers one call, Deny is the default, and a call the person's own rules or hooks approve is not asked
        for (String claim : new String[]{"before Claude Code runs any of these verbs the analyser asks you",
                "an approval covers one call", "Deny is the default",
                "a call your own rules or hooks approve is not asked in the analyser"}) {
            assertTrue(answer.toLowerCase(java.util.Locale.ROOT).contains(claim.toLowerCase(java.util.Locale.ROOT)),
                    "the FAQ security answer must state, for approvals in the analyser: " + claim);
        }
        // Docs audit 2026-10-17 §4 item 8: claims the substring pins above could not see were wrong — walk "writes no
        // file", "nothing outside the loaded log" though source rereads the source roots, no word on propose, a control
        // called "Profile…" that no longer exists. Witness: restore any of those sentences; its assertion names it.
        assertTrue(answer.contains("`walk {reel}`") && answer.contains("writes a walk as an HTML page"),
                "the FAQ must say walk {reel} writes a file");
        assertFalse(answer.contains("writes no file of its own"), "walk {reel} writes a file");
        assertTrue(answer.contains("`source` can reread the XML and Java files **under the source roots**"),
                "the FAQ must say source reads beyond the loaded log, and where");
        // PR #152 review M1: a source root can be ANY readable folder an assistant adds, and open reads any file — the
        // page must say so, and must never again promise "no arbitrary file reads". Witness: restore that phrase, or
        // drop the any-folder sentence; the assertion names which.
        // Owner decision 2026-10-18 (P5, restorable charts): the any-folder sentence now names its exception, the
        // tiers are stated, and a deleted chart is restorable. Witness: restore "cannot delete a chart", "cannot be
        // undone" for graph {delete}, or the unqualified any-folder sentence; the assertion names which.
        assertTrue(answer.contains("`source_root` can add **any folder you can read except your home folder and your "
                        + "credential folders** as a source root")
                        && answer.contains("`open` can open any file you can read as a log"),
                "the security page must say what source_root may add and that open opens any readable file");
        assertTrue(answer.contains("**For every caller**") && answer.contains("`~/.m2/repository` and below stay allowed")
                        && answer.contains("**An assistant is also held to the open project**")
                        && answer.contains("\"outside this project\""),
                "the security page must state both source-root tiers and the ~/.m2/repository allowance");
        assertTrue(answer.contains("**A deleted chart can be restored**") && answer.contains("`graph {restore}`"),
                "the security page must say a deleted or replaced chart is restorable");
        assertFalse(answer.contains("cannot delete a chart") || answer.contains("`graph {delete}` cannot be undone")
                        || answer.contains("is an open owner decision"),
                "claims the restorable-charts and P5 decisions made untrue");
        assertFalse(answer.contains("no arbitrary file reads") || answer.contains("no other file reads"),
                "source_root + source and open reach any readable file: 'no arbitrary file reads' is an overclaim");
        assertFalse(answer.contains("nothing outside the loaded log"), "source reads the source roots");
        assertTrue(answer.contains("`propose` is offered only to Claude Code in the Assistant tab")
                        && answer.contains("never on `/manifest`") && answer.contains("**changes nothing**"),
                "the FAQ must say what propose is and who is offered it");
        // M78 D2: the header control is the mode chip, named by its real labels
        assertTrue(answer.contains("mode chip") && answer.contains("**Safe ▾**"), "the header control's real label");
        assertFalse(answer.contains("**Assistant: … ▾**"), "the old control label is gone");
        assertFalse(answer.contains("Profile…"), "Profile… was replaced by Assistant: <profile> ▾");
        assertTrue(answer.contains("*External MCP/REST access*"), "the REST switch's one name");
        assertFalse(answer.contains("REST transport that lets"), "the switch is External MCP/REST access");
        assertTrue(answer.contains("Claude Code does not need External MCP/REST access on"));
        String whole = faq.replaceAll("\\s+", " ");
        // owner 2026-10-18: the FAQ names Full's in-project report write and what still asks
        assertTrue(whole.contains("under Full Claude Code, a `report` call that writes a new report inside the open project "
                + "needs no approval, while a write over an existing report or file, a delete, and anything outside the "
                + "project are still asked"), "the FAQ states the in-project report rule");
        assertFalse(whole.contains("local transport") || whole.contains("Assistant / LLM"), "the FAQ's old switch and tab names");
        assertFalse(whole.contains("Profile…") || whole.contains("REST transport that lets"),
                "the slimmed FAQ must not bring back the old control or switch names (phase 6 review)");
        int offline = whole.indexOf("## Does it work offline?");
        String offlineAnswer = whole.substring(offline, whole.indexOf(" ## ", offline + 1));
        assertTrue(offlineAnswer.contains("Claude Code") && offlineAnswer.contains("contacts Anthropic"),
                "offline: Start contacts Anthropic");
        for (String verb : McpTools.destructiveVerbs()) {
            assertTrue(answer.contains("`" + verb + "`"),
                    "destructive verb '" + verb + "' is not named in the FAQ's security answer — "
                            + "document what it can touch (docs/site/admin/security.md) in the same change that adds it");
        }
    }

    /**
     * Review P3+UX S3 and holistic B6 (2026-10-17): the claims P3 made untrue are gone from every surface that made them,
     * and what replaced them is pinned. Witness: put back the old Safe card line ("Claude Code reads the evidence only")
     * — the first assertion and the scan both fail.
     */
    @Test
    void theProfileClaimsSayWhatP3MadeTrue() throws Exception {
        String safe = "reads your evidence; it changes what is open or writes a file only after you approve each call";
        assertTrue(telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.QUICK_SAFE_TIP.contains(safe),
                "the quick switch's Safe analysis (M77-4: the card's line)");
        assertTrue(telamin.fluxtion.audit.analyser.analyser.ui.LocalAgentPanel.SAFE_LIMITS.contains(safe),
                "the Safe analysis limits line");
        assertTrue(telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.QUICK_FULL_TIP
                .contains("When Claude Code asks for approval, the analyser asks you"), "the quick switch's Full Claude Code");
        assertTrue(telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.QUICK_OFF_TIP
                .startsWith("No assistant in the analyser"), "Off is about the assistant in the analyser (B6)");
        assertTrue(telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.QUICK_OFF_TIP
                .contains("external MCP and REST clients are not affected"), "…and says the external loop is apart");
        String changelog = Files.readString(Path.of("CHANGELOG.md"));
        String unreleased = changelog.substring(changelog.indexOf("## [Unreleased]"), changelog.indexOf("\n## [", changelog
                .indexOf("## [Unreleased]") + 1));
        // the assistant guide, split by task (docs restructure, phase 3)
        String guide = java.util.stream.Stream.of("admin/profiles.md", "inapp/assistant-panel.md", "inapp/claude-code.md",
                "inapp/approvals.md", "inapp/ask.md", "inapp/transcript.md", "inapp/index.md").map(f -> {
                    try { return Files.readString(Path.of("docs/site").resolve(f)); }
                    catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                }).collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(guide.replaceAll("\\s+", " ").contains(safe), "the guide's Safe analysis row");
        String ui = "src/main/java/telamin/fluxtion/audit/analyser/analyser/";
        java.util.Map<String, String> surfaces = new java.util.LinkedHashMap<>();
        surfaces.put("CHANGELOG [Unreleased]", unreleased);
        surfaces.put("the assistant pages (inapp/, admin/profiles.md)", guide);
        for (String f : new String[]{"session/AiSetupLines.java", "ui/LocalAgentPanel.java", "ui/ConfigPanel.java",
                "session/AssistantProfile.java"}) {
            surfaces.put(f, Files.readString(Path.of(ui + f)));
        }
        for (var e : surfaces.entrySet()) {
            String text = e.getValue().replaceAll("\\s+", " ").replace("\" + \"", "");
            for (String untrue : new String[]{"reads the evidence only", "Safe analysis reads only",
                    "approve each action in the analyser", "denied until approvals", "non-destructive verbs"}) {
                assertFalse(text.toLowerCase(java.util.Locale.ROOT).contains(untrue.toLowerCase(java.util.Locale.ROOT)),
                        e.getKey() + " still says \"" + untrue + "\", which P3 made untrue");
            }
        }
    }

    /**
     * External utility review (owner-approved 2026-10-17): "Safe analysis" is not local-only, and every surface that
     * describes a profile says where your words and evidence go. Witness: drop the not-local-only bullet from the FAQ's
     * security answer, or the provider sentence from the Safe card's details — the assertion that names it fails.
     */
    @Test
    void noProfileClaimsToBeLocalOnly() throws Exception {
        String cc = telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles.CLAUDE_CODE_SENDS;
        String inApp = telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles.IN_APP_SENDS;
        assertTrue(cc.contains("what you ask, and the evidence Claude reads through the analyser, is sent to the model "
                + "provider (Anthropic, on your Claude plan or API key), as in any Claude Code session"), cc);
        assertTrue(inApp.contains("is sent to the model provider you chose"), inApp);
        assertTrue(telamin.fluxtion.audit.analyser.analyser.ui.LocalAgentPanel.SAFE_LIMITS.contains(cc), "the limits line");
        // M78 D1 (spec-quiet-assistant-panel.md TQ7): the limits line moved to About this session…; the test follows the
        // sentence there — the panel's About lines carry the same pinned constants
        String panel = Files.readString(Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/LocalAgentPanel.java"));
        String about = panel.substring(panel.indexOf("java.util.List<String> aboutLines()"));
        about = about.substring(0, about.indexOf("\n    }\n") + 6);
        assertTrue(about.contains("out.add(limitsNow());"), "About shows the limits line (SAFE_LIMITS, with " + "CLAUDE_CODE_SENDS)");
        String faq = Files.readString(Path.of("docs/site/faq.md")).replaceAll("\\s+", " ");
        String answer = Files.readString(SECURITY).replaceAll("\\s+", " ");
        assertTrue(answer.contains("No assistant profile is local-only, Safe analysis included."), "the security page");
        assertTrue(answer.contains("is sent to the model provider (Anthropic, on your Claude plan or API key), as in any "
                + "Claude Code session"), "…names the provider for Claude Code");
        assertTrue(answer.contains("to the model provider you chose"), "…and for the in-app assistant");
        int offline = faq.indexOf("## Does it work offline?");
        assertTrue(faq.substring(offline, faq.indexOf(" ## ", offline + 1)).contains("No assistant profile is local-only"),
                "the offline answer");
        String guide = (java.util.stream.Stream.of("admin/profiles.md", "inapp/assistant-panel.md", "inapp/claude-code.md",
                "inapp/approvals.md", "inapp/ask.md", "inapp/transcript.md", "inapp/index.md").map(f -> {
                    try { return Files.readString(Path.of("docs/site").resolve(f)); }
                    catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                }).collect(java.util.stream.Collectors.joining("\n"))).replaceAll("\\s+", " ");
        assertTrue(guide.contains("| **Safe analysis** (the default) |") && guide.contains("**Not local-only:** what you ask, "
                + "and the evidence Claude reads through the analyser, is sent to the model provider (Anthropic"),
                "the guide's Safe analysis row");
        assertTrue(guide.contains("the evidence the assistant reads through the analyser, is sent to the model provider you "
                + "chose"), "the guide's In-app row");
        assertTrue(guide.contains("**no profile is local-only**"), "the guide's limits-line bullet");
        String help = Files.readString(Path.of("src/main/resources/help/help.html")).replaceAll("\\s+", " ");
        assertTrue(help.contains("<b>No profile is local-only:</b>"), "the in-app help");
        String settings = Files.readString(Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/ConfigPanel.java"));
        assertTrue(settings.contains("AssistantProfiles.CLAUDE_CODE_SENDS") && settings.contains("AssistantProfiles.IN_APP_SENDS"),
                "Settings ▸ Assistant says it for Claude Code and for the in-app assistant");
        for (String untrue : new String[]{"local-only analysis", "nothing leaves this machine", "stays on your machine",
                "never leaves your machine"}) {
            for (String f : new String[]{"docs/site/faq.md", "docs/site/admin/security.md", "docs/site/admin/data.md",
                    "docs/site/admin/external-access.md", "docs/site/admin/profiles.md", "docs/site/admin/managed-policy.md",
                    "docs/site/inapp/index.md", "docs/site/inapp/assistant-panel.md", "docs/site/inapp/claude-code.md",
                    "docs/site/inapp/ask.md", "docs/site/inapp/transcript.md", "docs/site/inapp/approvals.md",
                    "docs/site/user-guide/start-page.md", "docs/site/getting-started.md"}) {
                assertFalse(Files.readString(Path.of(f)).replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT)
                        .contains(untrue), f + " claims \"" + untrue + "\"");
            }
        }
    }
    /**
     * ★ M77-5 T28 (spec-ai-setup-wizard.md §6.1a, OD-77.17): <i>Where data goes</i> says no more than is true. The four
     * sent lines and Full's suffix are pinned verbatim — each fixes an overclaim the prototype made (No AI does not say
     * "nothing leaves this computer": the analyser fetches the starter catalogue; the external line says the CLIENT
     * sends, and nothing about the analyser) — and no drawn or spoken diagram string says MCP, REST or CLI. Claude Code's
     * own usage data is a link to the FAQ entry, which says what it is. Witness: restore "not the analyser's" in the
     * external line — the verbatim assertion and the overclaim scan both fail.
     */
    @Test
    void theOptionDiagramSaysNoMoreThanIsTrue() throws Exception {
        assertEquals("Nothing is sent to an AI.", AiSetupLines.DIAGRAM_SENT_NO_AI);
        assertEquals("Sent to AI: what your client reads, sent by your client.", AiSetupLines.DIAGRAM_SENT_CLIENT);
        assertEquals("Sent to AI: your question + what Claude reads, to Anthropic.", AiSetupLines.DIAGRAM_SENT_INSIDE);
        assertEquals("; your own tools may send more.", AiSetupLines.DIAGRAM_SENT_FULL_SUFFIX);
        assertEquals("Sent to AI: your question + what Claude reads, to Anthropic; your own tools may send more.",
                AiSetupLines.diagramSentInside(true));
        assertEquals("Sent to AI: Claude to Anthropic; your client to its own service.", AiSetupLines.DIAGRAM_SENT_BOTH);
        // OD-77.18: the card sentence says what a person knows, not a protocol
        assertTrue(AiSetupLines.CLIENT_TEXT.startsWith("Claude Code, Codex or another AI app that can connect to "
                + AiSetupLines.PRODUCT + " reads records"), AiSetupLines.CLIENT_TEXT);
        Pattern jargon = Pattern.compile("\\b(MCP|REST|CLI)\\b", Pattern.CASE_INSENSITIVE);
        List<String> words = new ArrayList<>();
        for (Field f : AiSetupLines.class.getFields()) {
            if (f.getName().startsWith("DIAGRAM_") && f.getType() == String.class) words.add((String) f.get(null));
        }
        for (FlowDiagram.Option o : FlowDiagram.Option.values()) {
            for (AiSetupState.Posture p : AiSetupState.Posture.values()) {
                for (FlowDiagram.Access a : FlowDiagram.Access.values()) {
                    FlowDiagram.Model m = FlowDiagram.model(o, p, a, new AiSetupState.Auth(AiSetupState.SignedIn.SIGNED_IN,
                            AiSetupState.Method.CLAUDE_AI, AiSetupState.Plan.PRO, AiSetupState.KeySource.NONE));
                    words.addAll(m.drawnWords());
                    words.addAll(m.text());
                }
            }
        }
        assertTrue(words.size() > 100, "the scan sees the diagram's words: " + words.size());
        words.add(AiSetupLines.CLIENT_TEXT);
        for (String w : words) {
            assertFalse(jargon.matcher(w).find(), "a diagram string names a protocol: " + w);
            String lower = w.toLowerCase(Locale.ROOT);
            for (String overclaim : new String[]{"not the analyser", "nothing leaves", "never leaves", "stays on",
                    "local-only", "never sends"}) {
                assertFalse(lower.contains(overclaim), "a diagram string overclaims (\"" + overclaim + "\"): " + w);
            }
        }
        // OD-77.17: the link and the entry it opens
        String faq = Files.readString(Path.of("docs/site/faq.md"));
        String heading = "## What does Claude Code itself send to Anthropic?";
        assertTrue(faq.contains(heading), "the FAQ entry the diagram links to");
        assertEquals("faq/#what-does-claude-code-itself-send-to-anthropic", AiSetupLines.DIAGRAM_USAGE_PAGE,
                "the link's anchor is the heading's slug");
        int start = faq.indexOf(heading);
        String entry = faq.substring(start, faq.indexOf("\n## ", start + 1)).replaceAll("\\s+", " ");
        for (String claim : new String[]{"https://code.claude.com/docs/en/data-usage", "never your code, prompts or file paths",
                "`DISABLE_TELEMETRY=1`", "`DISABLE_ERROR_REPORTING=1`", "`CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1`",
                "`/feedback`", "The analyser turns none of these on or off", "Safe analysis does not load them"}) {
            assertTrue(entry.contains(claim), "the FAQ entry on Claude Code's own data must say: " + claim);
        }
        assertTrue(AiSetupLines.DIAGRAM_USAGE_LINK.startsWith("What Claude Code itself sends"), "the link's words");
    }

    /**
     * ★ Owner 2026-10-18: the standing instruction a Full session carries is FIXED analyser text — pinned here word for
     * word, so a change to it is a reviewed change to the security contract — with no placeholder a person's or a
     * model's words could be put into, and plain text (no markup the CLI could read as more than words). Witness: put a
     * {@code %s} or a folder name into {@code ClaudeCodeProvider.STAY_IN_PROJECT_NOTE} — this fails.
     */
    @Test
    void theFullSessionsStandingInstructionIsFixedText() {
        String note = telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeCodeProvider.STAY_IN_PROJECT_NOTE;
        assertTrue(note.equals("Work only inside this project folder: do not list, search or read files outside it, or "
                + "the environment. If the build needs the Fluxtion API key, the Fluxtion Maven plugin reads it; never "
                + "search for it, print it or read key files. Use ./mvnw and the project's own scripts to build and "
                + "generate."), "the pinned instruction changed: review it as a security-contract change");
        for (char ch : "{}<>%$`\\\n\r".toCharArray()) {
            assertTrue(note.indexOf(ch) < 0, "plain text: no placeholder, markup or escape: " + ch);
        }
        assertTrue(telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeCodeProvider.FULL_SYSTEM_NOTE
                .endsWith(note), "the Full note carries it");
        assertFalse(telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeCodeProvider.SYSTEM_NOTE
                .contains(note), "Safe has no shell and --restricted: it does not");
    }

    /**
     * ★ Option 2 (owner, 2026-10-18): the analyser's note that begins BOTH postures' sessions, and Full's starter rule, are
     * FIXED analyser text — pinned word for word, no placeholder — and every surface that discloses Full says it may read
     * the docs site without asking. Witness: change a word of {@code ClaudeCodeProvider.IDENTITY_NOTE}, put a {@code %s}
     * into it, or drop {@code FullFolderTrust.DOCS_FETCH} from the folder-trust or About text — this fails.
     */
    @Test
    void theIdentityNoteIsFixedTextAndTheDocsReadIsDisclosed() {
        var p = telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeCodeProvider.class;
        String identity = telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeCodeProvider.IDENTITY_NOTE;
        String full = telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeCodeProvider.FULL_STARTER_NOTE;
        assertTrue(identity.equals("You are the assistant inside the Fluxtion Audit Log Analyser, the desktop app the "
                + "person is using now. Here, 'project', 'starter' or 'template', 'log', 'graph' and 'report' mean the "
                + "analyser's: a project is an analyser project folder, a starter is one of the analyser's starter "
                + "templates, and a log is the Fluxtion audit log open in it. Call analyser_context first: it says what "
                + "is open and lists the starters."), "the pinned note changed: review it as a security-contract change");
        assertTrue(full.equals("To begin a new application, offer a starter with analyser_propose; do not search the web "
                + "or scaffold by hand. Until a project exists you are in an empty scratch folder: create nothing there. "
                + "Do not use gh, curl or other network commands to look for examples."), "the pinned Full rule changed");
        for (char ch : "{}<>%$`\\\n\r".toCharArray()) {
            assertTrue(identity.indexOf(ch) < 0 && full.indexOf(ch) < 0, "plain text: no placeholder or markup: " + ch);
        }
        assertTrue(telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeCodeProvider.SYSTEM_NOTE
                .startsWith(identity) && telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeCodeProvider
                .FULL_SYSTEM_NOTE.startsWith(identity + " " + full), "both notes begin with it; Full adds its rule");
        String docs = telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FullFolderTrust.DOCS_FETCH;
        assertTrue(docs.startsWith("Full may read fluxtion-playground.dev documentation without asking"), docs);
        assertTrue(telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FullFolderTrust.ADDS_INSTRUCTION
                .contains(docs), "the Full folder-trust disclosure says it");
        assertTrue(telamin.fluxtion.audit.analyser.analyser.ui.LocalAgentPanel.ABOUT_NOTE_FULL.contains(docs),
                "About this session… says it for Full");
        assertFalse(telamin.fluxtion.audit.analyser.analyser.ui.LocalAgentPanel.ABOUT_NOTE_SAFE.contains("fluxtion-playground"),
                "and Safe, which has no web, does not");
        assertTrue(p.getSimpleName().length() > 0);
    }
}
