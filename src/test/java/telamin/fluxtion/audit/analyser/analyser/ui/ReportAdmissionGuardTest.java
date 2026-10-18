package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The final authority is the generated session's node, including when no display is available. */
class ReportAdmissionGuardTest {
    @Test void theWriteRunsOnlyAsJudged() {
        List<SessionEffects.AnswerHostedReportApplyEffect> answers = new ArrayList<>();
        var driver = ReportTestSession.create(effect -> {
            if (effect instanceof SessionEffects.AnswerHostedReportApplyEffect a) answers.add(a);
            return new SessionEvents.LocalAgentEffectStarted(effect.opId(), 0, "test");
        });
        long g = driver.snapshot().localAgent().admittedGeneration();
        java.util.function.Function<SessionEvents.HostedReportApplyRequested, String> refusal = e -> {
            driver.submit(e);
            assertFalse(answers.isEmpty(), "the generated processor answered");
            return answers.getLast().refusal();
        };
        assertNull(refusal.apply(request(g, 0, "root", "root", "out", "out", "input", "input", null, null, false)),
                "control: a new report in the same project and destination runs");
        assertNotNull(refusal.apply(request(g, 0, "root", "other", "out", "out", "input", "input", null, null, false)),
                "a project switch refuses");
        assertNotNull(refusal.apply(request(g, 1, "root", "root", "out", "out", "input", "input", null, null, false)),
                "a changed project lifetime refuses even at the same path");
        assertNotNull(refusal.apply(request(g, 0, "root", "root", "out", "other", "input", "input", null, null, false)),
                "a changed destination refuses");
        assertNotNull(refusal.apply(request(g, 0, "root", "root", "out", "out", "input", "other", null, null, false)),
                "changed parameters refuse");
        assertNotNull(refusal.apply(request(g, 0, "root", "root", null, null, "input", "input", null, "new", true)),
                "a report that appeared after a create refuses");
        assertNull(refusal.apply(request(g, 0, "root", "root", null, null, "input", "input", "old", "old", true)),
                "control: the own revision is still present");
        assertNotNull(refusal.apply(request(g, 0, "root", "root", null, null, "input", "input", "old", "new", true)),
                "a changed revision refuses");
        assertNotNull(refusal.apply(request(g, 0, "root", "root", null, null, "input", "input", "old", null, false)),
                "a deleted report is not recreated unasked");
        assertNotNull(refusal.apply(request(g + 1, 0, "root", "root", null, null, "input", "input", null, null, false)),
                "another generation refuses");
        List<SessionEffects.AnswerHostedReportApplyEffect> safeAnswers = new ArrayList<>();
        var safe = ReportTestSession.create(effect -> {
            if (effect instanceof SessionEffects.AnswerHostedReportApplyEffect a) safeAnswers.add(a);
            return new SessionEvents.LocalAgentEffectStarted(effect.opId(), 0, "test");
        }, false);
        safe.submit(request(safe.snapshot().localAgent().admittedGeneration(), 0, "root", "root", null, null,
                "input", "input", null, null, false));
        assertNotNull(safeAnswers.getLast().refusal(), "Safe mode cannot apply a Full pre-allow");
    }

    private static SessionEvents.HostedReportApplyRequested request(long g, long project, String root, String actualRoot,
            String out, String actualOut, String input, String actualInput, String revision, String actualRevision, boolean exists) {
        return new SessionEvents.HostedReportApplyRequested(9, g, project, root, actualRoot, out, actualOut,
                input, actualInput, "name-digest", revision, actualRevision, exists);
    }
}
