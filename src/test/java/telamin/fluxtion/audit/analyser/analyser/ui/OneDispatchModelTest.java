package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M44.5 — the owner's rule, as a static check: ONE way of handling dispatch and orchestration. Session state, and the
 * decision of WHEN it is stale, belong to the generated session processor; an adapter performs the effects it asks
 * for and reports the results as facts; a surface renders the published snapshot. Nothing else.
 *
 * <p><b>Why it is a static check.</b> Every defect found in this area on 2026-09-26/27 was a hand-placed step beside
 * the processor — a refresh gate, a recompute at a second call site, a second assembly of a status line (PR #40's
 * H1–H3, X4, X5; M44.5's W1 and W2). Each was small, each looked safe, and each drifted. A behavioural test catches the
 * drift it was written for; this catches the next hand-placed copy before it can drift at all. CLAUDE.md rule 9.
 *
 * <p>Each rule names the ONE place the thing may happen and fails on any other. Each has a witness: the mutation that
 * must turn it red.
 */
class OneDispatchModelTest {

    private static final Path UI = Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui");

    /**
     * A rule: this code may appear in the UI package only inside {@code methods} of {@code MainFrame} — the first is its
     * home, any others are named exceptions. A frame-only rule names a MainFrame field ({@code status},
     * {@code followTimer}) that another panel may have a namesake of.
     */
    private record OnlyIn(String what, Pattern code, List<String> methods, boolean frameOnly) {
        OnlyIn(String what, Pattern code, String method) {
            this(what, code, List.of(method), false);
        }

        OnlyIn(String what, Pattern code, String method, boolean frameOnly) {
            this(what, code, List.of(method), frameOnly);
        }

        String method() {
            return methods.get(0);
        }
    }

    private static final List<OnlyIn> RULES = List.of(
            // witness: add ProducerDiagnostics.of(...) back to pollFollow (the deleted refreshFollowDiagnostics).
            // Review F3: a method reference (ProducerDiagnostics::of) is a call site too.
            new OnlyIn("the log's producer findings are computed",
                    Pattern.compile("ProducerDiagnostics\\s*(\\.\\s*of\\s*\\(|::\\s*of\\b)"), "private void scanLogEvidence("),
            // witness: validate the time order in a loader again (the W1 defect: validated once, at load);
            // review F3: or hand TimeOrderValidator::validate to something that calls it elsewhere
            new OnlyIn("the log's time order is validated",
                    Pattern.compile("TimeOrderValidator\\s*(\\.\\s*validate\\s*\\(|::\\s*validate\\b)"),
                    "private void scanLogEvidence("),
            // witness: compose the Follow line in pollFollow again (the W2 defect: a second assembly)
            new OnlyIn("the log's status line is composed", Pattern.compile("(?<!String )\\bstatusLine\\("),
                    "private void renderLogEvidence("),
            // witness: restore the tooltip set in the load path
            new OnlyIn("the log's findings tooltip is set",
                    Pattern.compile("(?<![A-Za-z])status\\.setToolTipText\\("), "private void renderLogEvidence(", true),
            // witness: restore followTimer.start() in setFollowing
            new OnlyIn("the Follow poll timer is started", Pattern.compile("followTimer\\.(start|restart)\\("),
                    "private void renderFollow("),
            // review F3 — witness: stop the timer in setFollowing again. The one exception is the exit sequence,
            // which stops every timer the window owns.
            new OnlyIn("the Follow poll timer is stopped", Pattern.compile("followTimer\\.stop\\("),
                    List.of("private void renderFollow(", "private void finishExit("), true));

    @Test
    @DisplayName("each piece of the log's derived state is computed, composed or started in exactly one place")
    void everyDerivedStepHasOneHome() throws Exception {
        List<String> offenders = new ArrayList<>();
        try (var files = Files.list(UI)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String src = Files.readString(f);
                boolean isFrame = f.getFileName().toString().equals("MainFrame.java");
                for (OnlyIn rule : RULES) {
                    if (rule.frameOnly() && !isFrame) continue;
                    String allowed = isFrame ? rule.methods().stream().map(m -> bodyOf(src, m))
                            .reduce("", String::concat) : "";
                    int total = count(rule.code(), src);
                    int inHome = count(rule.code(), allowed);
                    if (total != inHome) {
                        offenders.add(f.getFileName() + ": " + rule.what() + " outside " + rule.methods()
                                + " (" + (total - inHome) + " site(s))");
                    }
                }
            }
        }
        assertEquals(List.of(), offenders, "one dispatch model: route it through the session processor, not a call site");
    }

    @Test
    @DisplayName("each home exists and does its one thing — so a renamed method cannot pass the check vacuously")
    void everyHomeExists() throws Exception {
        String frame = Files.readString(UI.resolve("MainFrame.java"));
        for (OnlyIn rule : RULES) {
            assertTrue(count(rule.code(), bodyOf(frame, rule.method())) >= 1,
                    rule.method() + " must be where " + rule.what());
        }
    }

    /**
     * OA-1 / OA-A17 (spec-onboard-assistant-journeys.md §4.1, §5): the onboard assistant and the external bridge share ONE
     * dispatcher construction, so their schema, scope and identity refusal cannot drift; and no surface in the UI package
     * talks to a provider, parses an action or runs a turn — that is the assistantLoop node's decision and the adapter's
     * effect. Witness: construct an ActionDispatcher in the assistant panel, or call LlmClient from it (LlmPanel did both).
     */
    @Test
    @DisplayName("OA-A17: one dispatcher construction; no provider call, action parsing or turn loop in the UI package")
    void theAssistantHasOneDispatchModel() throws Exception {
        String frame = Files.readString(UI.resolve("MainFrame.java"));
        String home = bodyOf(frame, "telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher actionDispatcher(");
        assertFalse(home.isEmpty(), "actionDispatcher() must exist, or this rule checks nothing");
        Pattern construct = Pattern.compile("new\\s+(telamin\\.fluxtion\\.audit\\.analyser\\.analyser\\.llm\\.)?ActionDispatcher\\s*\\(");
        Pattern provider = Pattern.compile("\\bLlmClient\\b|ActionParser\\s*\\.\\s*extract|PromptBuilder\\s*\\.\\s*systemPrompt");
        List<String> offenders = new ArrayList<>();
        try (var files = Files.list(UI)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String src = Files.readString(f);
                boolean isFrame = f.getFileName().toString().equals("MainFrame.java");
                int built = count(construct, src) - (isFrame ? count(construct, home) : 0);
                if (built != 0) offenders.add(f.getFileName() + " constructs a dispatcher outside actionDispatcher()");
                if (count(provider, src) != 0) offenders.add(f.getFileName() + " talks to a provider or parses actions itself");
            }
        }
        assertEquals(1, count(construct, home), "actionDispatcher() constructs it once");
        assertEquals(List.of(), offenders, "the assistant's decisions are assistantLoop's; its effects are AssistantAdapter's");
    }

    // witness: declare `private ProducerDiagnostics producerDiagnostics;` (the retired frame copy) in MainFrame
    @Test
    @DisplayName("no UI class keeps its own copy of the log's evidence or of Follow")
    void noSurfaceHoldsACopy() throws Exception {
        // Review F3: the evidence type anywhere in a FIELD's declared type — plain, generic (Map<…, TimeOrderReport>,
        // Optional<ProducerDiagnostics>) or array. A field has a modifier, or sits at class-member indentation; a method
        // declaration has a '(' before its name and never matches.
        Pattern copy = Pattern.compile("^(?:\\s*(?:(?:private|protected|public|static|final|volatile|transient)\\s+)+| {4}(?! ))"
                + "[^;=(){}\\n]*\\b(ProducerDiagnostics|TimeOrderReport|StreamEnd)\\b[^;=(){}\\n]*\\s\\w+\\s*[;=]",
                Pattern.MULTILINE);
        Pattern follow = Pattern.compile("^\\s*(private|protected|public)?\\s*(volatile\\s+)?boolean\\s+following\\s*[;=]",
                Pattern.MULTILINE);
        List<String> offenders = new ArrayList<>();
        try (var files = Files.list(UI)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String src = Files.readString(f);
                Matcher m = copy.matcher(src);
                while (m.find()) {
                    // A Supplier holds no value: it is how a panel READS the snapshot on demand (ReportsPanel's
                    // logFindings is `() -> sessionSnapshot().producerFindings()`), so it is not a second copy.
                    if (m.group().contains("Supplier<")) continue;
                    offenders.add(f.getFileName() + ": " + m.group().strip());
                }
                Matcher g = follow.matcher(src);
                while (g.find()) offenders.add(f.getFileName() + ": " + g.group().strip());
            }
        }
        assertEquals(List.of(), offenders, "the session snapshot is the one copy; read it, do not keep another");
    }

    /**
     * Review F3: the frame's status bar carries many explanations, but the LOG's line has one composer. In the methods
     * that handle the log's lifecycle, a status write must be one of the explanation lines named here; and nowhere in
     * the UI package may a "Following …" line be assembled outside renderLogEvidence (the W2 defect, a second
     * assembly in pollFollow).
     */
    private static final Map<String, List<String>> LIFECYCLE_EXPLANATIONS = Map.of(
            "private void onLoaded(", List.of("status.setText(\"Discarded \"",
                    "status.setText(status.getText() + \"  ·  the previous log's source-supplied graph closed"),
            "private void pollFollow(", List.of("status.setText(\"⚠ \" + displayName(followPath) + \" was replaced on disk"),
            "private void setFollowing(", List.of("status.setText(\"Follow is available for heap-loaded"),
            "private void reportIdentityToSession(", List.of("status.setText(\"⚠ \" + displayName(followPath) + \": \""),
            "private void reportContentToSession(", List.of(),
            "private void scanLogEvidence(", List.of(),
            "private void renderFollow(", List.of(),
            "private void onSessionSnapshot(", List.of());

    // witness: compose `status.setText("Following " + displayName(followPath))` in pollFollow again
    @Test
    @DisplayName("the log's status line has one composer; the lifecycle methods write only their named explanations")
    void theLogLineHasOneComposer() throws Exception {
        String frame = Files.readString(UI.resolve("MainFrame.java"));
        List<String> offenders = new ArrayList<>();
        Pattern write = Pattern.compile("(?<![A-Za-z])status\\.setText\\(");
        for (var e : LIFECYCLE_EXPLANATIONS.entrySet()) {
            String body = bodyOf(frame, e.getKey());
            assertFalse(body.isEmpty(), e.getKey() + " must exist, or this rule checks nothing");
            Matcher m = write.matcher(body);
            while (m.find()) {
                String site = body.substring(m.start(), Math.min(body.length(), m.start() + 160));
                if (e.getValue().stream().noneMatch(site::startsWith)) {
                    offenders.add(e.getKey() + " writes the status bar: " + site.lines().findFirst().orElse(site));
                }
            }
        }
        Pattern followLine = Pattern.compile("\"Following \"");
        try (var files = Files.list(UI)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String src = Files.readString(f);
                String home = f.getFileName().toString().equals("MainFrame.java") ? bodyOf(src, "static String statusLine(") : "";
                if (count(followLine, src) != count(followLine, home)) {
                    offenders.add(f.getFileName() + " assembles a \"Following …\" line outside statusLine");
                }
            }
        }
        assertEquals(List.of(), offenders, "the log's line is rendered from the snapshot by one composer");
    }

    /**
     * M73 (spec-create-project.md §7): a project is created, adopted into and given its reference guide in ONE place —
     * the session's create effect, performed in {@code performSessionEffect} — so the menu and the socket cannot grow a
     * second, frame-side create. Qualified calls only, so a definition never matches.
     *
     * <p>Witnesses, one per pattern: call {@code project.createNew(…)} from {@code createProjectAt}; call
     * {@code ProjectAdoption.apply(…)} from {@code createProjectAt} (the shape the pre-M73 code had); call
     * {@code NewProjectDiscovery.writeReferenceGuide(…)} after {@code requestProject} in {@code createProjectAt}
     * (likewise); call {@code project.create(…)} anywhere in the UI.
     */
    private static final String PERFORMER =
            "private telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Result performSessionEffect(";
    private static final List<Pattern> CREATE_ONLY_IN_THE_PERFORMER = List.of(
            Pattern.compile("\\bproject\\s*\\.\\s*createNew\\s*\\("),
            Pattern.compile("\\bProjectAdoption\\s*\\.\\s*apply\\s*\\("),
            Pattern.compile("\\bNewProjectDiscovery\\s*\\.\\s*writeReferenceGuide\\s*\\("));

    @Test
    @DisplayName("M73: a project is created, adopted into and given its guide only by the session's create effect")
    void aProjectIsCreatedOnlyByTheCreateEffect() throws Exception {
        String frame = Files.readString(UI.resolve("MainFrame.java"));
        String home = bodyOf(frame, PERFORMER);
        assertFalse(home.isEmpty(), "performSessionEffect must exist, or this rule checks nothing");
        assertEquals(1, count(CREATE_ONLY_IN_THE_PERFORMER.get(0), home), "the create effect calls project.createNew once");
        assertEquals(1, count(CREATE_ONLY_IN_THE_PERFORMER.get(2), home), "and writes the guide there, after it");
        List<String> offenders = new ArrayList<>();
        Pattern oldCreate = Pattern.compile("\\bproject\\s*\\.\\s*create\\s*\\(");
        // review n2: the whole main tree, not only ui/ — the one other home is ProjectSession.createNew, where the
        // adoption runs inside the create (witness: call ProjectAdoption.apply( from any other main class)
        Path session = Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/config/ProjectSession.java");
        String adoptionHome = bodyOf(Files.readString(session), "public Created createNew(");
        assertEquals(1, count(CREATE_ONLY_IN_THE_PERFORMER.get(1), adoptionHome), "createNew adopts, once");
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String src = Files.readString(f);
                boolean isFrame = f.equals(UI.resolve("MainFrame.java"));
                boolean isSession = f.equals(session);
                for (Pattern p : CREATE_ONLY_IN_THE_PERFORMER) {
                    int outside = count(p, src) - (isFrame ? count(p, home) : 0) - (isSession ? count(p, adoptionHome) : 0);
                    if (outside != 0) offenders.add(f.getFileName() + ": " + p.pattern() + " outside its home");
                }
                if (f.startsWith(UI) && count(oldCreate, src) != 0) {
                    offenders.add(f.getFileName() + ": project.create( — the overwriting create");
                }
            }
        }
        assertEquals(List.of(), offenders, "one create: OpenProjectRequested{CREATE} → CreateProfileEffect");
        // both surfaces submit the event, rather than creating
        for (String surface : List.of("private void createProjectAt(",
                "public telamin.fluxtion.audit.analyser.analyser.llm.ActionResult createProject(")) {
            String body = bodyOf(frame, surface);
            assertFalse(body.isEmpty(), surface + " must exist");
            assertTrue(body.contains("TransitionKind.CREATE"), surface + " submits OpenProjectRequested{CREATE}");
        }
    }

    // ---- M74: the local agent (spec-claude-code-provider.md §4.1) ---------------------------------------------------

    private static final Path LOCAL_AGENT = Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/assistant/localagent");

    /**
     * One launcher, one performer. Witnesses: construct a {@code ProcessBuilder} in {@code LocalAgentAdapter}; add a
     * second {@code localAgentAdapter.perform(} call outside {@code performLocalAgent}; build the command line anywhere but
     * the adapter's spawn.
     */
    @Test
    @DisplayName("M74: the agent's ProcessBuilder, its command line and the StartLocalAgentEffect performer each have one home")
    void theLocalAgentHasOneLauncherAndOnePerformer() throws Exception {
        Pattern processBuilder = Pattern.compile("\\bProcessBuilder\\b");
        Pattern commandLine = Pattern.compile("\\.command\\(\\s*new\\s+LocalAgentProvider\\.LaunchSpec\\(");
        List<String> offenders = new ArrayList<>();
        try (var files = Files.list(LOCAL_AGENT)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String src = Files.readString(f);
                String name = f.getFileName().toString();
                if (!name.equals("OsAgentLauncher.java") && count(processBuilder, src) != 0) {
                    offenders.add(name + " builds a process: only OsAgentLauncher may");
                }
                if (!name.equals("LocalAgentAdapter.java") && count(commandLine, src) != 0) {
                    offenders.add(name + " builds the agent's command line: only the adapter's spawn may");
                }
                if (src.contains("import javax.swing") || src.contains("import java.awt") || src.contains("javax.swing.")
                        || src.contains("SwingUtilities")) {
                    offenders.add(name + " touches Swing: the adapter never writes to the UI (§4.1)");
                }
            }
        }
        String adapter = Files.readString(LOCAL_AGENT.resolve("LocalAgentAdapter.java"));
        assertEquals(1, count(commandLine, bodyOf(adapter, "private void spawn(")), "the spawn builds the line, once");
        assertEquals(1, count(Pattern.compile("new\\s+ProcessBuilder\\s*\\("),
                Files.readString(LOCAL_AGENT.resolve("OsAgentLauncher.java"))), "OsAgentLauncher builds it, once");
        // the one performer: MainFrame routes the effects to performLocalAgent, which is the only call of the adapter
        String frame = Files.readString(UI.resolve("MainFrame.java"));
        String performer = bodyOf(frame, "private telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Result performLocalAgent(");
        Pattern perform = Pattern.compile("\\blocalAgentAdapter\\s*\\.\\s*perform\\s*\\(");
        assertEquals(1, count(perform, performer), "performLocalAgent performs");
        assertEquals(1, count(perform, frame), "and nothing else in the frame does");
        Pattern startCase = Pattern.compile("case\\s+[\\w.]*StartLocalAgentEffect\\b");
        assertEquals(1, count(startCase, bodyOf(frame, PERFORMER)), "performSessionEffect routes the start, once");
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String src = Files.readString(f);
                int n = count(startCase, src);
                boolean home = f.equals(UI.resolve("MainFrame.java")) || f.equals(LOCAL_AGENT.resolve("LocalAgentAdapter.java"))
                        || f.getFileName().toString().equals("SessionDriver.java");
                if (n != 0 && !home) offenders.add(f.getFileName() + " performs StartLocalAgentEffect");
                if (!f.startsWith(LOCAL_AGENT) && !f.equals(UI.resolve("MainFrame.java"))
                        && src.contains("new telamin.fluxtion.audit.analyser.analyser.assistant.localagent.OsAgentLauncher(")) {
                    offenders.add(f.getFileName() + " constructs the agent launcher");
                }
            }
        }
        // review S8: the ProcessBuilder rule covers the whole main tree, not only the localagent package — every process the
        // analyser can build is in a named home, so a second agent launcher anywhere fails here
        java.util.Set<String> processHomes = java.util.Set.of("OsAgentLauncher.java", "S3Source.java", "CodexMcpClient.java",
                "McpConnectionProbe.java", "ClaudeMcpClient.java");
        Pattern newProcess = Pattern.compile("new\\s+ProcessBuilder\\s*\\(|Runtime\\.getRuntime\\(\\)\\s*\\.\\s*exec\\s*\\(");
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                if (count(newProcess, Files.readString(f)) != 0 && !processHomes.contains(f.getFileName().toString())) {
                    offenders.add(f.getFileName() + " builds a process outside the named homes");
                }
            }
        }
        assertEquals(List.of(), offenders, "one dispatch model for the local agent");
    }

    /**
     * The tab renders and reports; it never decides or performs. Witness: call {@code localAgentAdapter} or post a
     * session fact from {@code LocalAgentPanel}.
     */
    @Test
    @DisplayName("M74: the Claude Code panel renders the snapshot and reports intents; it starts, posts and decides nothing")
    void theLocalAgentPanelOnlyRenders() throws Exception {
        String panel = Files.readString(UI.resolve("LocalAgentPanel.java"));
        for (String forbidden : List.of("LocalAgentAdapter", "ClaudeCodeProvider", "ProcessBuilder", "OsAgentLauncher",
                ".post(", "SessionDriver", "SessionEvents")) {
            assertFalse(panel.contains(forbidden), "LocalAgentPanel must not use " + forbidden);
        }
    }

    /**
     * OD-74.1 amendment: warm start only on the person's direct selection. Witness: post the warm Start from
     * {@code revealAssistant()}, or drop the {@code WarmStart.isDirectSelection} guard.
     */
    @Test
    @DisplayName("M74: a warm start is posted in one place, behind the direct-selection check; revealAssistant starts nothing")
    void warmStartHasOneGuardedHome() throws Exception {
        String frame = Files.readString(UI.resolve("MainFrame.java"));
        String home = bodyOf(frame, "private void onAssistantTabSelected(");
        assertFalse(home.isEmpty(), "onAssistantTabSelected must exist");
        // P1b smoke finding: the event being dispatched, and the tab strip's own held input against the queue's
        // most recent input time (the current event can be overwritten by a nested dispatch)
        assertTrue(home.contains("WarmStart.isDirectSelection(java.awt.EventQueue.getCurrentEvent(),")
                        && home.contains("warmStartTracker.held()") && home.contains("getMostRecentEventTime()"),
                "the guard reads the event being dispatched and the tab strip's held input");
        Pattern warm = Pattern.compile("LocalAgentStartRequested\\([^;]*?,\\s*true\\)\\s*\\)", Pattern.DOTALL);
        assertEquals(1, count(warm, frame), "one warm start in the frame");
        assertEquals(1, count(warm, home), "and it is the guarded one");
        assertFalse(bodyOf(frame, "private void revealAssistant(").contains("LocalAgentStartRequested"),
                "a programmatic reveal never starts a process");
        assertFalse(bodyOf(frame, "private void revealAssistant(").contains("onAssistantTabSelected"));
    }

    // ---- M74 P2 (spec §8.2's rules; r8 F12, F14, F21) ---------------------------------------------------------------

    /**
     * ★ The start page decides no route: neither the page nor its prompt names the assistant mode, posts a fact or reads
     * the session's machinery — it renders {@code StartPageState} and reports the person's button through its Actions.
     * Witness: a {@code StartPanel} method that branches on {@code AssistantMode} (or posts a fact) fails this.
     */
    @Test
    @DisplayName("★ M74 P2: the start page and its prompt decide no route — no mode branch, no fact posted")
    void theStartPageDecidesNoRoute() throws Exception {
        for (String file : List.of("StartPanel.java", "StartPromptView.java", "StartFallbackWindow.java")) {
            String src = Files.readString(UI.resolve(file));
            for (String forbidden : List.of("AssistantMode", ".post(", "SessionDriver", "SessionEvents", "localAgentConsent",
                    "claudeCodeAllowed")) {
                assertFalse(src.contains(forbidden), file + " must not use " + forbidden + ": the route is startPagePrompt's");
            }
        }
    }

    /**
     * ★ D-L3 for the transcript's chips: the navigator the frame gives the transcript calls REVEAL methods only — select a
     * record, select a tab, select a node, select a chart, say a sentence. Witness: a chip handler that calls a mutating
     * frame method ({@code openFile}, {@code goto}, {@code applyProjectSettings}) fails the allow-list.
     */
    @Test
    @DisplayName("★ M74 P2: the transcript's chips navigate and never mutate (an allow-list of reveal calls)")
    void chipsAreRevealOnly() throws Exception {
        String frame = Files.readString(UI.resolve("MainFrame.java"));
        String body = bodyOf(frame, "private TranscriptView.Navigator transcriptNavigator(");
        assertFalse(body.isBlank(), "the navigator has one home");
        java.util.Set<String> allowed = java.util.Set.of("Navigator", "revealRecord", "revealNode", "revealChart", "say",
                "selectModelRow", "selectSideTab", "selectNode", "selectGraph", "sayToStatus");
        Matcher m = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(").matcher(body);
        List<String> calls = new ArrayList<>();
        while (m.find()) if (!allowed.contains(m.group(1))) calls.add(m.group(1));
        assertEquals(List.of(), calls, "a chip only reveals: " + body);
        String view = Files.readString(UI.resolve("TranscriptView.java"));
        assertFalse(view.contains("MainFrame"), "the view knows no frame: its chips call the Navigator only");
    }

    /**
     * ★ r8 F12: the registry is the one source for every Explain entrance — no {@code primeDraft(} call and no
     * {@code "Explain} literal anywhere in {@code ui/}. Witness: the detail panel's own "Explain with LLM" button and
     * tooltip (the P1 code) fail this.
     */
    @Test
    @DisplayName("★ M74 P2: no draft or Explain label is written outside the Ask catalogue")
    void theRegistryIsTheOneSource() throws Exception {
        List<String> offenders = new ArrayList<>();
        try (var files = Files.list(UI)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String src = Files.readString(f);
                if (src.contains("primeDraft(")) offenders.add(f.getFileName() + ": primeDraft(");
                if (src.contains("\"Explain")) offenders.add(f.getFileName() + ": \"Explain");
            }
        }
        assertEquals(List.of(), offenders, "every question and its label comes from AskCatalogue");
    }

    /**
     * ★ r8 F14: the start page's Copy prompt copies the composed draft only. Witness: reusing {@code copyAssistantPrompt}
     * (record text, the glossary and, with REST on, the token) fails this.
     */
    @Test
    @DisplayName("★ M74 P2: the start page's Copy prompt reads no record, glossary or token")
    void theStartPageCopyIsTheDraftOnly() throws Exception {
        String frame = Files.readString(UI.resolve("MainFrame.java"));
        String copy = bodyOf(frame, "private void copyStartPrompt(") + bodyOf(frame, "String startPromptCopyText(");
        assertFalse(copy.isBlank());
        for (String forbidden : List.of("copyAssistantPrompt", "PromptBuilder", "restToken", "restUrl", "selectedRecords",
                "vocabularyText", "rawText")) {
            assertFalse(copy.contains(forbidden), "the start page's Copy prompt must not read " + forbidden);
        }
    }

    private static int count(Pattern p, String s) {
        Matcher m = p.matcher(s);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    /** The body of the method whose declaration starts with {@code signature}, by brace matching; "" when absent. */
    static String bodyOf(String src, String signature) {
        int at = src.indexOf(signature);
        if (at < 0) return "";
        int open = src.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return src.substring(open, i + 1);
        }
        return "";
    }
}
