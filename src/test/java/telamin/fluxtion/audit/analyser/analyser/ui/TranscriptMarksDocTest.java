package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.PermissionBy;
import telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentPermission;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Docs restructure, phase 7 (the phase 3 review's open item): the two mark tables on {@code docs/site/inapp/transcript.md}
 * are pinned against the code that draws the marks — {@link AssistantPanel#toolLine} for the in-app assistant, and
 * {@code LocalAgentAdapter.markWords} plus the result marks the adapter appends ({@code ✓}, {@code refused}) for Claude
 * Code. Both directions: every mark the code can draw has a row, and every row is a mark the code can draw.
 *
 * <p>The Claude Code side feeds every POLICY message {@code LocalAgentPermission} can send, found by reflection and a
 * source scan, so a new denial message fails here until the page lists its mark (phase 7 review).
 *
 * <p>Wrong-result witness: rename a mark in {@code markWords} (say "denied: Stop" to "denied: stopped"), or add a row the
 * code never draws; the assertion names the mark.
 */
class TranscriptMarksDocTest {

    static final Path PAGE = Path.of("docs/site/inapp/transcript.md");

    /** The first-column marks of the table that follows {@code heading} on the page. */
    static Set<String> tableMarks(String page, String heading) {
        int at = page.indexOf(heading);
        assertTrue(at >= 0, PAGE + " lost its table heading " + heading);
        Set<String> marks = new LinkedHashSet<>();
        boolean inTable = false;
        for (String line : page.substring(at + heading.length()).split("\\R")) {
            if (line.startsWith("|")) {
                inTable = true;
                Matcher m = Pattern.compile("^\\|\\s*`([^`]+)`\\s*\\|").matcher(line);
                if (m.find()) marks.add(m.group(1));
            } else if (inTable) {
                break;
            }
        }
        return marks;
    }

    static final Path PERMISSION = Path.of(
            "src/main/java/telamin/fluxtion/audit/analyser/analyser/session/node/LocalAgentPermission.java");

    /**
     * Every DENIED_* message the permission node can send with a POLICY decision: all of its DENIED_* constants except
     * those it passes only with another decision (the person, a timeout, Stop, a waiting request, a request too large).
     * Read by reflection and from the source, so a new constant is covered without editing this test.
     */
    static Set<String> policyMessages() throws Exception {
        String source = Files.readString(PERMISSION);
        Set<String> out = new LinkedHashSet<>();
        for (java.lang.reflect.Field f : LocalAgentPermission.class.getFields()) {
            if (!f.getName().startsWith("DENIED_") || f.getType() != String.class) continue;
            boolean withPolicy = Pattern.compile("PermissionBy\\.POLICY,\\s*" + f.getName() + "\\b").matcher(source).find()
                    || Pattern.compile("(return|->|[?:])\\s*" + f.getName() + "\\b").matcher(source).find();
            boolean withOther = Pattern.compile("PermissionBy\\.(PERSON|TIMEOUT|STOP|BUSY|TOO_LARGE),\\s*" + f.getName()
                    + "\\b").matcher(source).find();
            if (withPolicy || !withOther) out.add((String) f.get(null));
        }
        return out;
    }

    /** Rule 8: the skip above rests on NON_MUTATING never being passed to a denial. */
    @Test
    void aChangesNothingDecisionIsNeverADenial() throws Exception {
        String source = Files.readString(PERMISSION);
        assertTrue(!Pattern.compile("deny[A-Za-z]*\\([^;]*PermissionBy\\.NON_MUTATING").matcher(source).find()
                        && !Pattern.compile("resolvePending\\(\\s*PermissionBy\\.NON_MUTATING").matcher(source).find(),
                "LocalAgentPermission denies with NON_MUTATING: markWords' deny branch for it is now drawn; document it");
    }

    @Test
    void theInAppMarksAreTheOnesToolLineDraws() throws Exception {
        Set<String> drawn = new LinkedHashSet<>();
        for (String status : List.of("OK", "REFUSED", "NOT_RUN")) {
            String line = AssistantPanel.toolLine(new AssistantState.Entry(1, "ACTION", 1, status, "read", "…", 0), null);
            drawn.add(line.substring(line.indexOf(" — ") + 3));
        }
        assertEquals(drawn, tableMarks(Files.readString(PAGE), "**The in-app assistant's marks:**"),
                PAGE + "'s in-app table must list exactly the marks AssistantPanel.toolLine draws (detail shown as …)");
    }

    @Test
    void theClaudeCodeMarksAreTheOnesTheAdapterDraws() throws Exception {
        Class<?> adapter = Class.forName("telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentAdapter");
        Method words = adapter.getDeclaredMethod("markWords", boolean.class, PermissionBy.class, String.class);
        words.setAccessible(true);
        Set<String> drawn = new LinkedHashSet<>();
        drawn.add("✓");                               // the tool's own result, appended by toolOutcome
        drawn.add("refused");
        // TranscriptView joins the decision and the tool's result with " · " (an allow is followed by the result)
        drawn.add(words.invoke(null, true, PermissionBy.PERSON, null) + " · ✓");
        drawn.add(words.invoke(null, true, PermissionBy.NON_MUTATING, null) + " · ✓");
        // owner decision 2026-10-18: a graph call that creates or shows a chart is allowed without asking
        drawn.add(words.invoke(null, true, PermissionBy.PRE_ALLOWED, null) + " · ✓");
        // owner 2026-10-18: Full's in-project report write, allowed without asking — the adapter's own mark for it
        var reportMark = adapter.getDeclaredField("REPORT_IN_PROJECT_MARK");
        reportMark.setAccessible(true);
        drawn.add(reportMark.get(null) + " · ✓");
        Set<String> policy = policyMessages();
        assertTrue(policy.size() >= 10, "the scan of LocalAgentPermission found too few policy denials: " + policy);
        for (PermissionBy by : PermissionBy.values()) {
            // a NON_MUTATING or PRE_ALLOWED decision is only ever an allow (LocalAgentPermission.allowWithoutAsking), and
            // every POLICY denial carries a message (each one is fed below), so no such null-message branch is drawn
            if (by == PermissionBy.NON_MUTATING || by == PermissionBy.PRE_ALLOWED || by == PermissionBy.POLICY) continue;
            drawn.add((String) words.invoke(null, false, by, null));
        }
        for (String message : policy) drawn.add((String) words.invoke(null, false, PermissionBy.POLICY, message));
        Set<String> documented = tableMarks(Files.readString(PAGE), "**Claude Code's marks:**");
        List<String> missing = new ArrayList<>(drawn);
        missing.removeAll(documented);
        List<String> invented = new ArrayList<>(documented);
        invented.removeAll(drawn);
        assertEquals(List.of(), missing, "marks the adapter draws that " + PAGE + " does not list");
        assertEquals(List.of(), invented, "rows on " + PAGE + " that the adapter never draws");
    }
}
