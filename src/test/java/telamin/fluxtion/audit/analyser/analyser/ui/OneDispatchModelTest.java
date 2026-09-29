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
