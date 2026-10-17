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
        String approved = (String) words.invoke(null, true, PermissionBy.PERSON, null);
        drawn.add(approved + " · ✓");                 // TranscriptView joins the decision and the result with " · "
        drawn.add((String) words.invoke(null, true, PermissionBy.NON_MUTATING, null));
        for (PermissionBy by : PermissionBy.values()) {
            // a NON_MUTATING decision is always an allow (McpTools.hostedCallKind); its deny branch is unreachable
            if (by == PermissionBy.NON_MUTATING) continue;
            drawn.add((String) words.invoke(null, false, by, null));
        }
        for (String policy : List.of(LocalAgentPermission.DENIED_DEFAULT_POSTURE, LocalAgentPermission.DENIED_HIDDEN,
                LocalAgentPermission.DENIED_ASK_QUESTION, LocalAgentPermission.DENIED_PLAN_MODE)) {
            drawn.add((String) words.invoke(null, false, PermissionBy.POLICY, policy));
        }
        Set<String> documented = tableMarks(Files.readString(PAGE), "**Claude Code's marks:**");
        List<String> missing = new ArrayList<>(drawn);
        missing.removeAll(documented);
        List<String> invented = new ArrayList<>(documented);
        invented.removeAll(drawn);
        assertEquals(List.of(), missing, "marks the adapter draws that " + PAGE + " does not list");
        assertEquals(List.of(), invented, "rows on " + PAGE + " that the adapter never draws");
    }
}
