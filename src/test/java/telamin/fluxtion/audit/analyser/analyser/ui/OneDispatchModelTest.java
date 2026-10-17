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
     * Warm start was cut (owner, 2026-10-17): Start is always the explicit button. Witnesses: post
     * {@code LocalAgentStartRequested} from a tab-selection listener, or from {@code revealAssistant()} — the counts fail.
     */
    @Test
    @DisplayName("M74: only the Start button posts a Start; selecting or revealing the Assistant tab starts nothing")
    void onlyTheStartButtonStarts() throws Exception {
        String frame = Files.readString(UI.resolve("MainFrame.java"));
        Pattern start = Pattern.compile("new\\s+[\\w.]*LocalAgentStartRequested\\(");
        assertEquals(1, count(start, frame), "one Start in the frame");
        assertEquals(1, count(start, bodyOf(frame, "private void installLocalAgent(")), "and it is the Start intent's");
        assertFalse(bodyOf(frame, "private void revealAssistant(").contains("LocalAgentStartRequested"),
                "a programmatic reveal never starts a process");
        assertFalse(frame.contains("onAssistantTabSelected") || frame.contains("WarmStart"), "no warm-start path remains");
        assertFalse(Files.exists(UI.resolve("WarmStart.java")));
    }

    /**
     * Review M8 and M6: the folder answer is a fact and the hosted admission is the node's. Witnesses: post
     * {@code LocalAgentStartRequested} from the dialog's callback, re-run {@code LocalAgentFolders.check} there, write the
     * trusted folder before the node accepts the answer, or admit a hosted call from {@code admittedGeneration()} in the
     * frame — each fails its named assertion.
     */
    @Test
    @DisplayName("M74 M8/M6: the folder answer and the hosted admission are decided by the node; the frame reports and performs")
    void theFolderAnswerAndTheAdmissionAreTheNodes() throws Exception {
        String frame = Files.readString(UI.resolve("MainFrame.java"));
        String confirm = bodyOf(frame, "private telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Result confirmLocalAgentFolderNow(");
        assertFalse(confirm.isEmpty(), "confirmLocalAgentFolderNow must exist");
        assertFalse(confirm.contains("LocalAgentStartRequested"), "the dialog reports an answer; it never starts anything");
        assertFalse(confirm.contains("LocalAgentFolders"), "and never re-checks the folder: the node already decided");
        assertFalse(confirm.contains("claudeCodeTrustedFolders"), "nor records trust before the node accepts the answer");
        assertTrue(confirm.contains("LocalAgentFolderAnswered("), "the answer is a fact");
        // P1b: the consent observation lives in LocalAgentConsents.of, the frame's localAgentConsent() calls it
        assertEquals(0, count(Pattern.compile("LocalAgentFolders\\s*\\.\\s*check\\("), frame),
                "no folder check in the frame: the consent observation is LocalAgentConsents'");
        assertEquals(1, count(Pattern.compile("LocalAgentFolders\\s*\\.\\s*check\\("),
                Files.readString(LOCAL_AGENT.resolve("LocalAgentConsents.java"))), "and it is checked there, once");
        assertTrue(bodyOf(frame, "private telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.LocalAgentConsent localAgentConsent(")
                .contains("LocalAgentConsents.of("), "the frame's consent is that observation");
        assertEquals(1, count(Pattern.compile("claudeCodeTrustedFolders\\s*\\.\\s*add\\("), frame),
                "the trust record has one writer");
        assertTrue(bodyOf(frame, "private telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Result trustLocalAgentFolder(")
                .contains("claudeCodeTrustedFolders.add("), "the performer of the node's TrustLocalAgentFolderEffect");
        assertFalse(frame.contains("admittedGeneration()"), "the frame never admits a hosted call from the snapshot (M6)");
        assertTrue(frame.contains("hostedCredentials.gate(hostedAdmission)"), "it asks the session instead");
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

    // ---- M74 P2b (§8.2 rule 9 inventory): propose, the switch hold and an example's download -------------------------

    /**
     * ★ Witness: let the dispatcher's propose case or the proposer call a project-changing frame method (requestProject,
     * openFile, openGraphml, selectProcessor) — the forbidden-call assertion fails.
     */
    @Test
    @DisplayName("★ M74 P2b: propose records an offer — neither the dispatcher nor the proposer applies a project change")
    void proposeAppliesNothing() throws Exception {
        String dispatcher = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/llm/ActionDispatcher.java"));
        assertFalse(dispatcher.contains("MainFrame"), "the dispatcher names no frame");
        String frame = Files.readString(UI.resolve("MainFrame.java"));
        String proposer = bodyOf(frame, "private telamin.fluxtion.audit.analyser.analyser.llm.ActionResult proposeFromHosted(");
        assertFalse(proposer.isBlank());
        for (String forbidden : List.of("requestProject(", "openFile(", "openGraphml(", "selectProcessor(", "sourceRoots.add",
                "runbooks.put", "addRunbookPointer(")) {
            assertFalse(proposer.contains(forbidden), "the proposer must not call " + forbidden);
        }
        assertTrue(proposer.contains("proposalFact("), "the offer is a fact (built by proposalFact, R6)");
        assertTrue(bodyOf(frame, "proposalFact(long request").contains("ProposalReceived("), "…a ProposalReceived");
        // the person paths are reached from ONE performer: the apply effect's (besides the menus that are their homes)
        assertEquals(1, count(Pattern.compile("PointerDialog\\s*\\.\\s*addRunbookPointer\\("), frame),
                "the runbook pointer path is called only by the apply performer");
        assertTrue(bodyOf(frame, "private String[] applyProposalItemNow(").contains("addRunbookPointer("));
    }

    /** ★ Witness: open the review dialog from the banner's render (renderProposal) — a model call would raise a modal. */
    @Test
    @DisplayName("★ M74 P2b: the banner renders; only the person's click opens the review; the panel composes no proposal decision")
    void theBannerOnlyRenders() throws Exception {
        String panel = Files.readString(UI.resolve("LocalAgentPanel.java"));
        String render = bodyOf(panel, "public void renderProposal(");
        assertFalse(render.isBlank());
        assertFalse(render.contains("ProposalReviewDialog"), "a render never opens the review");
        assertFalse(render.contains("intents."), "a render asks for nothing");
        assertFalse(panel.contains("ProposalApplyRequested"), "the panel posts no fact itself: its intents do");
    }

    /**
     * ★ Witness: fetch or unpack from the start page or from the confirm performer — the fetch-home assertion fails.
     */
    @Test
    @DisplayName("★ M74 P2b: an example is fetched only by the fetch effect's performer and unpacked only by the unpack effect's")
    void theExampleHasOneFetchAndOneUnpack() throws Exception {
        String frame = Files.readString(UI.resolve("MainFrame.java"));
        assertEquals(1, count(Pattern.compile("\\.fetch\\(parsed\\.source\\(\\)"), frame), "one fetch home");
        assertTrue(bodyOf(frame, "private telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Result fetchExample(")
                .contains(".fetch(parsed.source()"));
        assertEquals(1, count(Pattern.compile("TemplateArchive\\.forExamples\\(\\)"), frame), "one unpack home");
        assertTrue(bodyOf(frame, "private telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Result unpackExample(")
                .contains("TemplateArchive.forExamples()"));
        String view = Files.readString(UI.resolve("StartPromptView.java"));
        assertFalse(view.contains("ExampleArchiveClient") || view.contains("TemplateArchive"), "the view fetches nothing");
        String download = bodyOf(frame, "private void downloadExample(");
        assertTrue(download.contains("ExampleDownloadRequested("), "the button is a fact");
        assertFalse(download.contains("fetch(") || download.contains("install("), "and fetches nothing itself");
    }

    /** ★ Witness: decide the switch in the frame (e.g. check localAgent().running() before requestProject) — fails. */
    @Test
    @DisplayName("★ M74 P2b: the project switch is held by sessionBoundary; the frame only asks and reports the choice")
    void theSwitchIsTheNodes() throws Exception {
        String frame = Files.readString(UI.resolve("MainFrame.java"));
        String request = bodyOf(frame, "private boolean requestProject(Path file,\n");
        String all = frame.substring(frame.indexOf("private boolean requestProject(Path file,"));
        all = all.substring(0, all.indexOf("private void syncBusyWithGate()"));
        assertFalse(all.contains("localAgent()"), "requestProject never looks at the local agent: the node holds");
        String offer = bodyOf(frame, "private telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Result offerLocalAgentSwitch(");
        assertTrue(offer.contains("LocalAgentSwitchChosen("));
        assertFalse(offer.contains("requestProject(") || offer.contains("StopLocalAgent"), "the choice is a fact; the nodes act");
    }

    /**
     * ★ M74 P3 (§5.7, rule 9): the permission dialog shows and reports; the node decides. The dialog's file posts no fact
     * and writes nothing to Claude Code; the frame's performer reports only the person's button or Stop; the answer to
     * the CLI is the adapter's, performed only on the node's {@code AnswerPermissionEffect}. Witness: answer the CLI from
     * the dialog's Allow (call the adapter, or write a control_response, in {@code showPermissionDialog}) — this fails.
     */
    @Test
    @DisplayName("★ M74 P3: the permission dialog renders and reports; it decides nothing and never answers Claude Code")
    void thePermissionDialogOnlyReports() throws Exception {
        String dialog = Files.readString(UI.resolve("LocalAgentPermissionDialog.java"));
        for (String never : List.of("session()", ".post(", "SessionEvents", "SessionEffects", "StreamJson", "control_response",
                "LocalAgentAdapter")) {
            assertFalse(dialog.contains(never), "the dialog names " + never);
        }
        String frame = Files.readString(UI.resolve("MainFrame.java"));
        String show = bodyOf(frame, "private telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Result showPermissionDialog(");
        assertFalse(show.isEmpty());
        assertTrue(show.contains("LocalAgentPermissionChosen(") && show.contains("LocalAgentStopRequested("), "it reports");
        for (String never : List.of("localAgentAdapter", "AnswerPermission", "StreamJson", "writeLine", "HostedCallPresented")) {
            assertFalse(show.contains(never), "the dialog's performer must not use " + never);
        }
        String close = bodyOf(frame, "private telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Result closePermissionDialog(");
        assertFalse(close.contains(".post("), "closing posts nothing: it is the node's effect");
        String adapter = Files.readString(LOCAL_AGENT.resolve("LocalAgentAdapter.java"));
        assertEquals(1, count(Pattern.compile("StreamJson\\s*\\.\\s*allow\\("), adapter),
                "one place writes an allow: the AnswerPermissionEffect's performer");
        assertTrue(bodyOf(adapter, "private SessionEvents.Result answerPermission(").contains("StreamJson.allow("));
    }

    /**
     * ★ M74 UX (owner, 2026-10-17; rule 9): the ONE assistant panel and the profile cards render and report — they post no
     * fact, name no session event and write no setting — and the settings a profile means are written in one place, the
     * performer of {@code assistantProfile}'s {@code ApplyAssistantProfileEffect}. Witness: write the profile from the
     * overlay's Apply (call {@code AssistantProfiles.apply} in {@code ProfileOverlay}), or apply it in the frame's intent
     * instead of posting {@code AssistantProfileChosen} — each fails its named assertion.
     */
    @Test
    @DisplayName("★ M74 UX: the one assistant panel and the profile cards only render and report; one place writes a profile")
    void theProfileIsWrittenOnlyByItsEffect() throws Exception {
        for (String file : List.of("AssistantPanel.java", "ProfileOverlay.java", "AssistantSurface.java")) {
            String src = Files.readString(UI.resolve(file));
            for (String never : List.of(".post(", "SessionEvents", "SessionEffects", "SessionDriver", "AssistantProfiles.apply(",
                    "saveConfig", "ConfigStore.save", "LocalAgentAdapter")) {
                assertFalse(src.contains(never), file + " must not use " + never);
            }
        }
        String frame = Files.readString(UI.resolve("MainFrame.java"));
        Pattern apply = Pattern.compile("AssistantProfiles\\s*\\.\\s*apply\\(");
        assertEquals(1, count(apply, frame), "one place in the frame writes a profile");
        String performer = bodyOf(frame, "private telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.Result applyAssistantProfile(");
        assertFalse(performer.isEmpty(), "applyAssistantProfile must exist");
        assertEquals(1, count(apply, performer), "and it is the node's ApplyAssistantProfileEffect performer");
        assertTrue(performer.contains("LocalAgentConsentChanged(") || performer.contains("reportAssistantSettings()"),
                "it reports the same consent facts a Settings save reports");
        String intents = bodyOf(frame, "public void applyProfile(long request,");
        assertTrue(intents.contains("AssistantProfileChosen("), "Apply is the person's act, reported as a fact");
        assertFalse(intents.contains("AssistantProfiles"), "the intent writes nothing itself");
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
