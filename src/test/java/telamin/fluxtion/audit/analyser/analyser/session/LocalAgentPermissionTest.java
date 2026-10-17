package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.FullMode;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.PermissionBy;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ToolClass;
import telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentPermission;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P3 (spec §5.7, §8.3, §10 P3) — {@code localAgentPermission} on the REAL generated processor: which requests reach
 * the person, one dialog at a time, every way a request ends, and the single-use grant a hosted destructive call must
 * consume. Facts go in; the effects asked for and the snapshot come out. The adapter's side (the CLI's lines, the bytes
 * of the answer) is {@code LocalAgentPermissionReplayTest}'s; the HTTP side is {@code HostedGrantServerTest}'s.
 *
 * <p>Each ★ test names its witness: the mutation of the node that turns it red (run, recorded in the spec).
 */
class LocalAgentPermissionTest {

    static final String INPUT = "digest-of-the-input";
    static final String TOOL_USE = "digest-of-toolu_1";

    /** A bound, posture-checked default-posture session with turn 1 open: returns its start. */
    static SessionEffects.StartLocalAgentEffect ready(SessionDriver d, FakeSessionAdapter a) {
        var start = LocalAgentSessionTest.startToReady(d, a);
        var t = LocalAgentSessionTest.send(d, a, 10);
        d.post(LocalAgentSessionTest.goodInit(start, t.ticket()));
        assertEquals(start.generation(), d.snapshot().localAgent().admittedGeneration());
        return start;
    }

    static SessionEvents.LocalAgentPermissionRequested report(long gen, long ticket, long ref) {
        return new SessionEvents.LocalAgentPermissionRequested(gen, ticket, ref, ToolClass.HOSTED_DESTRUCTIVE, "report",
                INPUT, TOOL_USE, true);
    }

    static SessionEvents.LocalAgentPermissionRequested of(long gen, long ticket, long ref, ToolClass cls, String name) {
        return new SessionEvents.LocalAgentPermissionRequested(gen, ticket, ref, cls, name, INPUT, TOOL_USE, true);
    }

    static SessionEffects.AnswerPermissionEffect lastAnswer(FakeSessionAdapter a) {
        return a.permissionAnswers.get(a.permissionAnswers.size() - 1);
    }

    static SessionEvents.HostedCallPresented call(long id, long gen, String verb, String input, String toolUse) {
        return new SessionEvents.HostedCallPresented(id, gen, verb, input, toolUse);
    }

    static String verdict(FakeSessionAdapter a, long call) {
        var e = a.agentAnswers.stream().filter(x -> x.call() == call).reduce((x, y) -> y).orElseThrow();
        return e.refusal();
    }

    // ---- ★ OD-74.16: which requests reach the person --------------------------------------------------------------

    /**
     * ★ Witness: show every request (make {@code LocalAgentPermission.policy} return null) — the default posture's
     * built-in row and the registration rows fail.
     */
    @Test
    @DisplayName("★ the policy table for every posture × mode × tool class: only the listed classes reach the dialog")
    void thePolicyTable() {
        // review B8 (2026-10-17): a LITERAL table — §5.7 / OD-74.16 as written, never recomputed from asks() or isFull(),
        // so a change to those predicates cannot move the oracle with it. Columns: HOSTED_DESTRUCTIVE, BUILT_IN,
        // OTHER_MCP, HOSTED_OTHER, PERSONS_ANALYSER_REGISTRATION, UNKNOWN; true = reaches the person.
        java.util.Map<LocalAgentPosture, boolean[]> table = new java.util.LinkedHashMap<>();
        table.put(LocalAgentPosture.ANALYSER_ASSISTANT, new boolean[]{true, false, false, false, false, false});
        table.put(LocalAgentPosture.full(FullMode.DEFAULT), new boolean[]{true, true, true, false, false, false});
        table.put(LocalAgentPosture.full(FullMode.ACCEPT_EDITS), new boolean[]{true, true, true, false, false, false});
        table.put(LocalAgentPosture.full(FullMode.AUTO), new boolean[]{false, true, true, false, false, false});
        table.put(LocalAgentPosture.full(FullMode.PLAN), new boolean[]{false, true, true, false, false, false});
        ToolClass[] cols = {ToolClass.HOSTED_DESTRUCTIVE, ToolClass.BUILT_IN, ToolClass.OTHER_MCP, ToolClass.HOSTED_OTHER,
                ToolClass.PERSONS_ANALYSER_REGISTRATION, ToolClass.UNKNOWN};
        assertEquals(java.util.Set.of(ToolClass.values()), java.util.Set.of(cols), "every class has a column");
        assertEquals(FullMode.values().length + 1, table.size(), "every posture has a row");
        for (var row : table.entrySet()) {
            for (int i = 0; i < cols.length; i++) {
                String refusal = policy(row.getKey(), cols[i]);
                assertEquals(row.getValue()[i], refusal == null, row.getKey() + " × " + cols[i] + ": " + refusal);
            }
        }
        assertEquals(LocalAgentPermission.DENIED_DEFAULT_POSTURE, policy(LocalAgentPosture.ANALYSER_ASSISTANT, ToolClass.BUILT_IN));
        assertEquals(LocalAgentPermission.DENIED_REGISTRATION,
                policy(LocalAgentPosture.full(FullMode.DEFAULT), ToolClass.PERSONS_ANALYSER_REGISTRATION));
        assertEquals(LocalAgentPermission.DENIED_HIDDEN, policy(LocalAgentPosture.full(FullMode.AUTO), ToolClass.HOSTED_DESTRUCTIVE));
    }

    static String policy(LocalAgentPosture p, ToolClass c) {
        try {
            var m = LocalAgentPermission.class.getDeclaredMethod("policy", LocalAgentPosture.class, ToolClass.class);
            m.setAccessible(true);
            return (String) m.invoke(null, p, c);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** ★ Witness: as above — the Bash request in the default posture opens a dialog. */
    @Test
    @DisplayName("★ on the processor: a hosted destructive request opens the dialog and starts its clock; a Bash request in the default posture is denied without one")
    void aDestructiveRequestAsksAndABuiltInIsDenied() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = ready(d, a);
        d.post(of(start.generation(), 1, 41, ToolClass.BUILT_IN, "Bash"));
        assertEquals(0, a.permissionDialogs.size(), "no dialog for Bash in the analyser assistant's posture");
        assertFalse(lastAnswer(a).allow());
        assertEquals(PermissionBy.POLICY, lastAnswer(a).by());
        assertEquals(LocalAgentPermission.DENIED_DEFAULT_POSTURE, lastAnswer(a).message());
        d.post(report(start.generation(), 1, 42));
        assertEquals(1, a.permissionDialogs.size(), "the person is asked about report");
        assertEquals(42, a.permissionDialogs.get(0).requestRef());
        assertEquals(LocalAgentPermission.DEADLINE_MS, a.permissionDialogs.get(0).deadlineMs());
        assertEquals(1, a.permissionClocks.size(), "the 120 s clock is the node's, asked of the adapter");
        assertTrue(d.snapshot().permission().waiting(), "the tab says Claude Code is waiting for the person");
        assertTrue(d.snapshot().localAgent().entries().stream().anyMatch(e -> e.detail().contains("Bash was denied without "
                + "asking you")), "the transcript names the denial");
    }

    /** ★ Witness: allow the full posture's person's tools by policy without a dialog — the Bash dialog assertion fails. */
    @Test
    @DisplayName("★ full posture, default: the person's Bash and their MCP tools are asked; the registration is denied")
    void theFullPostureAsksAboutThePersonsTools() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = LocalAgentFullPostureTest.startFull(d, a, FullMode.DEFAULT);
        var t = LocalAgentSessionTest.send(d, a, 10, LocalAgentFullPostureTest.full(FullMode.DEFAULT));
        d.post(LocalAgentFullPostureTest.fullInit(start, t.ticket(), "default", List.of(), "connected"));
        assertEquals(start.generation(), d.snapshot().localAgent().admittedGeneration());
        d.post(of(start.generation(), t.ticket(), 51, ToolClass.PERSONS_ANALYSER_REGISTRATION, null));
        assertEquals(LocalAgentPermission.DENIED_REGISTRATION, lastAnswer(a).message());
        d.post(of(start.generation(), t.ticket(), 52, ToolClass.BUILT_IN, "Bash"));
        assertEquals(1, a.permissionDialogs.size(), "Bash is asked of the person");
        d.post(new SessionEvents.LocalAgentPermissionChosen(52, true));
        assertTrue(lastAnswer(a).allow());
        assertEquals(0, lastAnswer(a).grantId(), "a person's own tool gets no server-side grant: the CLI runs it");
        d.post(of(start.generation(), t.ticket(), 53, ToolClass.OTHER_MCP, null));
        assertEquals(2, a.permissionDialogs.size(), "a tool of the person's own MCP server is asked too");
    }

    /**
     * ★ M74 UX (P3 leftover a): in the full posture Claude Code's {@code AskUserQuestion} would ask the person through a
     * terminal dialog the analyser does not have. The policy denies it without a dialog; the model is told to ask in its
     * reply and the transcript tells the person to answer in the conversation. Witness: drop the AskUserQuestion case
     * from {@code policy} — the request reaches the approval dialog as a built-in, and the dialog count fails.
     */
    @Test
    @DisplayName("★ full posture: AskUserQuestion is denied without a dialog — 'Claude asked a question — answer it in the conversation'")
    void askUserQuestionIsAnsweredInTheConversation() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = LocalAgentFullPostureTest.startFull(d, a, FullMode.DEFAULT);
        var t = LocalAgentSessionTest.send(d, a, 10, LocalAgentFullPostureTest.full(FullMode.DEFAULT));
        d.post(LocalAgentFullPostureTest.fullInit(start, t.ticket(), "default", List.of(), "connected"));
        d.post(of(start.generation(), t.ticket(), 61, ToolClass.BUILT_IN, LocalAgentPermission.ASK_USER_QUESTION));
        assertEquals(0, a.permissionDialogs.size(), "a question is never an approval dialog");
        assertFalse(lastAnswer(a).allow());
        assertEquals(PermissionBy.POLICY, lastAnswer(a).by());
        assertEquals(LocalAgentPermission.DENIED_ASK_QUESTION, lastAnswer(a).message(), "the model is told to ask in its reply");
        assertTrue(d.snapshot().localAgent().entries().stream()
                        .anyMatch(e -> LocalAgentPermission.ASK_QUESTION_NOTE.equals(e.detail())),
                "the person reads where to answer: " + d.snapshot().localAgent().entries());
        // control: another built-in in the same posture still reaches the dialog
        d.post(of(start.generation(), t.ticket(), 62, ToolClass.BUILT_IN, "Bash"));
        assertEquals(1, a.permissionDialogs.size(), "control: Bash is still asked of the person");
    }

    /**
     * ★ Review P3 N2 (2026-10-17): in full plan mode ExitPlanMode / EnterPlanMode would change the session's mode under
     * the person; they are denied by policy without a dialog, with a note. Witness: drop the PLAN_MODE_TOOLS case from
     * {@code policy} — ExitPlanMode reaches the dialog as a built-in.
     */
    @Test
    @DisplayName("★ full plan mode: ExitPlanMode and EnterPlanMode are denied by policy, with a note; no dialog")
    void planModeToolsAreDeniedWithANote() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = LocalAgentFullPostureTest.startFull(d, a, FullMode.PLAN);
        var t = LocalAgentSessionTest.send(d, a, 10, LocalAgentFullPostureTest.full(FullMode.PLAN));
        d.post(LocalAgentFullPostureTest.fullInit(start, t.ticket(), "plan", List.of(), "connected"));
        assertEquals(start.generation(), d.snapshot().localAgent().admittedGeneration());
        for (String tool : List.of("ExitPlanMode", "EnterPlanMode")) {
            d.post(of(start.generation(), t.ticket(), tool.length(), ToolClass.BUILT_IN, tool));
            assertEquals(0, a.permissionDialogs.size(), tool + " is never an approval dialog");
            assertEquals(PermissionBy.POLICY, lastAnswer(a).by());
            assertEquals(LocalAgentPermission.DENIED_PLAN_MODE, lastAnswer(a).message());
        }
        assertTrue(d.snapshot().localAgent().entries().stream()
                .anyMatch(e -> LocalAgentPermission.PLAN_MODE_NOTE.equals(e.detail())), "the person reads why");
        d.post(of(start.generation(), t.ticket(), 99, ToolClass.BUILT_IN, "Read"));
        assertEquals(1, a.permissionDialogs.size(), "control: another built-in is still asked");
    }

    /**
     * ★ Review P3 N4: a request that arrives while the turn is STOPPING is denied without a dialog. Witness: drop the
     * {@code stopping()} check in {@code onRequested} — the request opens a dialog after Stop.
     */
    @Test
    @DisplayName("★ a request arriving while the turn is stopping is denied, never asked")
    void aRequestWhileStoppingIsDenied() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = ready(d, a);
        d.post(new SessionEvents.LocalAgentStopRequested("Stop"));
        assertTrue(d.snapshot().localAgent().phase().equals("STOPPING"), d.snapshot().localAgent().phase());
        d.post(report(start.generation(), 1, 77));
        assertEquals(0, a.permissionDialogs.size(), "no dialog after Stop");
        assertFalse(lastAnswer(a).allow());
        assertEquals(LocalAgentPermission.DENIED_STOPPING, lastAnswer(a).message());
    }

    // ---- ★ every way a request ends ---------------------------------------------------------------------------------

    /**
     * ★ Witnesses: answer allow on close (treat {@code Chosen(false)} as Allow) — the close row fails; drop the
     * {@code onStopRequested} handler — Stop leaves the request open and no deny precedes the interrupt.
     */
    @Test
    @DisplayName("★ deny on close, on the 120 s deadline, on Stop (written BEFORE the interrupt), at the turn's end and the generation's")
    void everyWayARequestEndsDenies() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = ready(d, a);
        long g = start.generation();

        d.post(report(g, 1, 61));
        d.post(new SessionEvents.LocalAgentPermissionChosen(61, false));          // Deny, Escape, Enter or close
        assertFalse(lastAnswer(a).allow());
        assertEquals(PermissionBy.PERSON, lastAnswer(a).by());
        assertEquals(LocalAgentPermission.DENIED_BY_PERSON, lastAnswer(a).message());
        assertEquals(61, a.permissionCloses.get(a.permissionCloses.size() - 1).requestRef());

        d.post(report(g, 1, 62));
        d.post(new SessionEvents.LocalAgentPermissionDeadline(g, 62));
        assertEquals(PermissionBy.TIMEOUT, lastAnswer(a).by());
        assertEquals(LocalAgentPermission.DENIED_TIMEOUT, lastAnswer(a).message());
        d.post(new SessionEvents.LocalAgentPermissionDeadline(g, 62));
        assertEquals(2, a.permissionAnswers.size(), "a second deadline for an answered request does nothing");

        d.post(report(g, 1, 63));
        a.agentOrder.clear();
        d.post(new SessionEvents.LocalAgentStopRequested("Stop"));
        assertEquals(PermissionBy.STOP, lastAnswer(a).by());
        int deny = a.agentOrder.indexOf("answerPermission");
        int interrupt = a.agentOrder.indexOf("stop:INTERRUPT");
        assertTrue(deny >= 0 && interrupt > deny, "the request is denied before the interrupt is written: " + a.agentOrder);
        assertFalse(d.snapshot().permission().waiting());
    }

    /** ★ Witness: drop {@code onTurnEnded} / {@code onExited} — the open dialog outlives its turn and its process. */
    @Test
    @DisplayName("★ a request still open when its turn or its process ends is denied, and its dialog closed")
    void theTurnAndTheGenerationEndRequests() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = ready(d, a);
        d.post(report(start.generation(), 1, 71));
        d.post(new SessionEvents.LocalAgentTurnEnded(start.generation(), 1, SessionEvents.TurnOutcome.CANCELLED));
        assertEquals(PermissionBy.POLICY, lastAnswer(a).by());
        assertEquals(LocalAgentPermission.DENIED_ENDED, lastAnswer(a).message());
        assertFalse(d.snapshot().permission().waiting());

        var t2 = LocalAgentSessionTest.send(d, a, 11);
        d.post(report(start.generation(), t2.ticket(), 72));
        d.post(new SessionEvents.LocalAgentExited(start.generation(), 1, 0));
        assertEquals(72, lastAnswer(a).requestRef());
        assertFalse(lastAnswer(a).allow());
        assertFalse(d.snapshot().permission().waiting());
    }

    /**
     * ★ Review M74 usability #10c (amends OD-74.17's "denied at once"). Witnesses: drop the queue (deny the second at
     * once, as before) — no answer is pending for 82 and its dialog never opens; drop {@code askNext} after an answer —
     * 82 waits forever; drop the showable check — the 64 KB input opens one; drop the stale check — the old
     * generation's request opens one.
     */
    @Test
    @DisplayName("★ a second request WAITS behind the open dialog and is shown after it; unshowable and stale are denied")
    void aSecondRequestWaitsUnshowableAndStaleAreDenied() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = ready(d, a);
        long g = start.generation();
        d.post(report(g, 1, 81));
        int answersBefore = a.permissionAnswers.size();
        d.post(report(g, 1, 82));
        assertEquals(1, a.permissionDialogs.size(), "one dialog at a time (OD-74.17)");
        assertEquals(answersBefore, a.permissionAnswers.size(), "the second is not denied: it waits");
        assertEquals(1, d.snapshot().permission().queued(), "and the open dialog can say so");
        d.post(new SessionEvents.LocalAgentPermissionChosen(81, false));
        assertEquals(81, lastAnswer(a).requestRef(), "the person's answer to the first");
        assertEquals(2, a.permissionDialogs.size(), "then the waiting one is shown");
        assertEquals(82, d.snapshot().permission().pendingRef());
        assertEquals(0, d.snapshot().permission().queued());
        d.post(new SessionEvents.LocalAgentPermissionChosen(82, false));
        assertEquals(82, lastAnswer(a).requestRef());
        assertEquals(PermissionBy.PERSON, lastAnswer(a).by(), "asked and answered by the person, never auto-denied");

        d.post(new SessionEvents.LocalAgentPermissionRequested(g, 1, 83, ToolClass.HOSTED_DESTRUCTIVE, "report", null,
                TOOL_USE, false));
        assertEquals(PermissionBy.TOO_LARGE, lastAnswer(a).by());
        assertEquals(LocalAgentPermission.DENIED_TOO_LARGE, lastAnswer(a).message());

        d.post(report(g + 5, 1, 84));
        assertEquals(LocalAgentPermission.DENIED_STALE, lastAnswer(a).message());
        d.post(report(g, 9, 85));
        assertEquals(LocalAgentPermission.DENIED_STALE, lastAnswer(a).message());
        assertEquals(2, a.permissionDialogs.size(), "none of them opened a dialog");
    }

    /** ★ Witness: ignore the consent change — the request stays open after the person's settings moved under it. */
    @Test
    @DisplayName("★ Settings changing the posture while a request is open deny it and drop every grant (a mode switch)")
    void aSettingsChangeEndsRequestsAndGrants() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = ready(d, a);
        d.post(report(start.generation(), 1, 91));
        d.post(new SessionEvents.LocalAgentPermissionChosen(91, true));
        assertEquals(1, d.snapshot().permission().liveGrants());
        d.post(report(start.generation(), 1, 92));
        d.post(new SessionEvents.LocalAgentConsentChanged(LocalAgentSessionTest.consent(true, true, false, "another-posture",
                LocalAgentSessionTest.BUDGET)));
        assertEquals(92, lastAnswer(a).requestRef());
        assertEquals(LocalAgentPermission.DENIED_SETTINGS, lastAnswer(a).message());
        assertEquals(0, d.snapshot().permission().liveGrants(), "the grants went with the settings they were given under");
    }

    /** The CLI withdrew it (P3-2): the dialog closes and nothing is answered. */
    @Test
    @DisplayName("P3-2: the CLI's own cancel closes the dialog and answers nothing")
    void theClisCancelClosesTheDialog() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = ready(d, a);
        d.post(report(start.generation(), 1, 95));
        d.post(new SessionEvents.LocalAgentPermissionCancelled(start.generation(), 95));
        assertTrue(a.permissionAnswers.isEmpty(), "nothing is answered to a withdrawn request");
        assertEquals(95, a.permissionCloses.get(0).requestRef());
        assertFalse(d.snapshot().permission().waiting());
    }

    // ---- ★ OD-74.17: the single-use grant ---------------------------------------------------------------------------

    /**
     * ★ Witnesses: check without consuming (leave the grant in {@code consume}) — the identical second call is admitted;
     * drop the input-digest comparison — another input is admitted; drop the ticket comparison — the next turn's call is
     * admitted.
     */
    @Test
    @DisplayName("★ grants: an allowed report is admitted once; another verb, input, tool_use_id, ticket or generation is refused")
    void aGrantAdmitsExactlyOneMatchingCall() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = ready(d, a);
        long g = start.generation();
        d.post(call(1, g, "report", INPUT, TOOL_USE));
        assertTrue(verdict(a, 1).contains("needs your approval in the analyser for this exact call; none was given"),
                "no grant, no call: " + verdict(a, 1));
        d.post(report(g, 1, 101));
        d.post(new SessionEvents.LocalAgentPermissionChosen(101, true));
        assertTrue(lastAnswer(a).allow());
        assertTrue(lastAnswer(a).grantId() > 0, "Allow once on a hosted destructive request records a grant");

        d.post(call(2, g, "open", INPUT, TOOL_USE));
        assertNotNull(verdict(a, 2), "another verb");
        d.post(call(3, g, "report", "another-input", TOOL_USE));
        assertNotNull(verdict(a, 3), "another input");
        d.post(call(4, g, "report", INPUT, "digest-of-toolu_2"));
        assertNotNull(verdict(a, 4), "another tool_use_id (P3-3)");
        d.post(call(5, g + 1, "report", INPUT, TOOL_USE));
        assertNotNull(verdict(a, 5), "another generation");
        assertEquals(1, d.snapshot().permission().liveGrants(), "none of those consumed it");

        d.post(call(6, g, "report", INPUT, TOOL_USE));
        assertNull(verdict(a, 6), "exactly this call is admitted");
        d.post(call(7, g, "report", INPUT, TOOL_USE));
        assertTrue(verdict(a, 7).contains("has been used"), "the identical second call is refused: " + verdict(a, 7));

        // a grant is for its own turn: given in turn 1, never admitted in turn 2
        d.post(report(g, 1, 102));
        d.post(new SessionEvents.LocalAgentPermissionChosen(102, true));
        d.post(new SessionEvents.LocalAgentTurnEnded(g, 1, SessionEvents.TurnOutcome.COMPLETED));
        assertEquals(0, d.snapshot().permission().liveGrants(), "dropped at the end of its turn");
        LocalAgentSessionTest.send(d, a, 11);
        d.post(call(8, g, "report", INPUT, TOOL_USE));
        assertNotNull(verdict(a, 8), "another ticket");
    }

    /**
     * ★ Holistic review B4 (2026-10-17): a call the session admits AFTER its HTTP wait gave up never ran; the record says
     * ABANDONED with the grant voided, the person reads that the approved call did not run, and the grant stays used.
     * Witness: drop the onHostedCallAbandoned handler — the record holds ADMITTED alone and no note is shown.
     */
    @Test
    @DisplayName("★ an abandoned, admitted call is recorded as abandoned; its grant is void and the person is told")
    void anAbandonedCallVoidsItsGrant() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = ready(d, a);
        long g = start.generation();
        d.post(report(g, 1, 101));
        d.post(new SessionEvents.LocalAgentPermissionChosen(101, true));
        d.post(call(6, g, "report", INPUT, TOOL_USE));
        assertNull(verdict(a, 6), "admitted — but the HTTP call had already given up");
        d.post(new SessionEvents.HostedCallAbandoned(6, g));
        String record = String.join("\n", d.auditSink().records());
        assertTrue(record.contains("ABANDONED") && record.contains("grantVoided: true"), record.substring(
                Math.max(0, record.length() - 600)));
        assertTrue(d.snapshot().localAgent().entries().stream().anyMatch(e -> "ABANDONED".equals(e.status())
                && e.detail().contains("did not run")), "the person reads it");
        d.post(call(7, g, "report", INPUT, TOOL_USE));
        assertTrue(verdict(a, 7).contains("has been used"), "void, consistently: " + verdict(a, 7));
        d.post(new SessionEvents.HostedCallAbandoned(99, g));
        assertTrue(String.join("\n", d.auditSink().records()).contains("grantVoided: false"),
                "an abandoned call that was never admitted voids nothing");
    }

    /** ★ Witness: drop the expired check in {@code consume} — the call 60 s later is admitted. */
    @Test
    @DisplayName("★ a grant expires 60 s after the Allow, and on Stop; an expired one is refused by name")
    void aGrantExpires() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = ready(d, a);
        long g = start.generation();
        d.post(report(g, 1, 111));
        d.post(new SessionEvents.LocalAgentPermissionChosen(111, true));
        long grant = lastAnswer(a).grantId();
        d.post(new SessionEvents.LocalAgentGrantExpired(g, grant));
        d.post(call(1, g, "report", INPUT, TOOL_USE));
        assertTrue(verdict(a, 1).contains("expired"), verdict(a, 1));

        d.post(report(g, 1, 112));
        d.post(new SessionEvents.LocalAgentPermissionChosen(112, true));
        d.post(new SessionEvents.LocalAgentStopRequested("Stop"));
        assertEquals(0, d.snapshot().permission().liveGrants(), "Stop drops every grant");
    }

    /**
     * ★ Witness: admit any destructive call while a dialog was recently allowed (consume on verb alone) — the bypassed
     * call with its own input is admitted.
     */
    @Test
    @DisplayName("★ a destructive call that never had a can_use_tool (a person's allow rule or hook bypassed it, P3-5) is refused")
    void aBypassedCallIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = ready(d, a);
        long g = start.generation();
        d.post(report(g, 1, 121));
        d.post(new SessionEvents.LocalAgentPermissionChosen(121, true));     // the person allowed ONE report call
        d.post(call(1, g, "screenshot", "the-screenshot-input", "digest-of-toolu_9"));
        assertTrue(verdict(a, 1).contains("none was given"), "the rule-approved screenshot is refused: " + verdict(a, 1));
        d.post(call(2, g, "report", "a-different-report", "digest-of-toolu_10"));
        assertNotNull(verdict(a, 2), "a hook-approved report with its own input is refused too");
    }

    /** ★ Witness: admit before the posture check (drop the admittedGeneration comparison in onHostedCall). */
    @Test
    @DisplayName("★ a grant never admits a call of a generation that is not bound and checked")
    void aGrantNeedsAnAdmittedGeneration() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = ready(d, a);
        long g = start.generation();
        d.post(report(g, 1, 131));
        d.post(new SessionEvents.LocalAgentPermissionChosen(131, true));
        // the posture check fails and the session is killed: the bridge is refused from then on
        List<String> tools = LocalAgentSessionTest.expectedTools();
        tools.add("Bash");
        d.post(new SessionEvents.LocalAgentInitObserved(g, 1, LocalAgentPosture.digest(start.sessionId()), "default", tools,
                List.of(LocalAgentPosture.HOSTED_SERVER + ":connected"), LocalAgentPosture.REQUIRED_CAPABILITIES, 0));
        d.post(call(1, g, "report", INPUT, TOOL_USE));
        assertTrue(verdict(a, 1).contains("not connected to this analyser yet"), verdict(a, 1));
    }

    // ---- ★ audit -----------------------------------------------------------------------------------------------------

    /** ★ Witness: log the request's input digest as words (record the name of the tool's input) — see the replay test. */
    @Test
    @DisplayName("★ the session record holds the request's ids, class, decision and who decided — the answer is a fact (R8)")
    void theRecordHoldsIdsAndDecisions() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = ready(d, a);
        d.post(report(start.generation(), 1, 141));
        d.post(new SessionEvents.LocalAgentPermissionChosen(141, true));
        String record = String.join("\n", d.auditSink().records());
        assertTrue(record.contains("LocalAgentPermissionRequested"), "asked: a fact");
        assertTrue(record.contains("LocalAgentPermissionAnswered") && record.contains("PERSON"), "answered: a fact with by");
        assertTrue(record.contains("localAgentGrant"), "the grant is recorded");
        assertTrue(record.contains("HOSTED_DESTRUCTIVE") && record.contains("report"), "the class and the verb");
    }
}
