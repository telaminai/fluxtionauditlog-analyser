package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
     * A rule: this code may appear in the UI package only inside {@code method} of {@code MainFrame}. A frame-only rule
     * names a MainFrame field ({@code status}, {@code followTimer}) that another panel may have a namesake of.
     */
    private record OnlyIn(String what, Pattern code, String method, boolean frameOnly) {
        OnlyIn(String what, Pattern code, String method) {
            this(what, code, method, false);
        }
    }

    private static final List<OnlyIn> RULES = List.of(
            // witness: add ProducerDiagnostics.of(...) back to pollFollow (the deleted refreshFollowDiagnostics)
            new OnlyIn("the log's producer findings are computed", Pattern.compile("ProducerDiagnostics\\s*\\.of\\("),
                    "private void scanLogEvidence("),
            // witness: validate the time order in a loader again (the W1 defect: validated once, at load)
            new OnlyIn("the log's time order is validated", Pattern.compile("TimeOrderValidator\\s*\\.validate\\("),
                    "private void scanLogEvidence("),
            // witness: compose the Follow line in pollFollow again (the W2 defect: a second assembly)
            new OnlyIn("the log's status line is composed", Pattern.compile("(?<!String )\\bstatusLine\\("),
                    "private void renderLogEvidence("),
            // witness: restore the tooltip set in the load path
            new OnlyIn("the log's findings tooltip is set",
                    Pattern.compile("(?<![A-Za-z])status\\.setToolTipText\\("), "private void renderLogEvidence(", true),
            // witness: restore followTimer.start() in setFollowing
            new OnlyIn("the Follow poll timer is started", Pattern.compile("followTimer\\.(start|restart)\\("),
                    "private void renderFollow("));

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
                    String allowed = isFrame ? bodyOf(src, rule.method()) : "";
                    int total = count(rule.code(), src);
                    int inHome = count(rule.code(), allowed);
                    if (total != inHome) {
                        offenders.add(f.getFileName() + ": " + rule.what() + " outside " + rule.method()
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

    // witness: declare `private ProducerDiagnostics producerDiagnostics;` (the retired frame copy) in MainFrame
    @Test
    @DisplayName("no UI class keeps its own copy of the log's evidence or of Follow")
    void noSurfaceHoldsACopy() throws Exception {
        Pattern copy = Pattern.compile("^\\s*(private|protected|public)?\\s*(static\\s+)?(final\\s+)?(volatile\\s+)?"
                + "[\\w.]*\\b(ProducerDiagnostics|TimeOrderReport|StreamEnd)\\s+\\w+\\s*[;=]", Pattern.MULTILINE);
        Pattern follow = Pattern.compile("^\\s*(private|protected|public)?\\s*(volatile\\s+)?boolean\\s+following\\s*[;=]",
                Pattern.MULTILINE);
        List<String> offenders = new ArrayList<>();
        try (var files = Files.list(UI)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String src = Files.readString(f);
                Matcher m = copy.matcher(src);
                while (m.find()) offenders.add(f.getFileName() + ": " + m.group().strip());
                Matcher g = follow.matcher(src);
                while (g.find()) offenders.add(f.getFileName() + ": " + g.group().strip());
            }
        }
        assertEquals(List.of(), offenders, "the session snapshot is the one copy; read it, do not keep another");
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
