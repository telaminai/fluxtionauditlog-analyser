package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.PermissionBy;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ToolClass;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P3 (spec §5.7, §10 P3) — the REAL adapter and parser and the REAL session processor, driven by a fake process
 * replaying the P3 probe captures ({@code docs/specs/evidence/m74-p3/fixtures}, the real bridge, the real login,
 * sanitised). A recorded turn pauses at its {@code can_use_tool} until the adapter answers, as the CLI does (P3-1), and
 * an interrupt while one is pending withdraws it first (P3-2). The person's button is the
 * {@link SessionEvents.LocalAgentPermissionChosen} fact the frame would post.
 */
class LocalAgentPermissionReplayTest {

    static final Path P3_FIXTURES = Path.of("docs/specs/evidence/m74-p3/fixtures");

    @TempDir
    Path home;

    private Harness bound(String fixture) throws Exception {
        FixtureAgent agent = new FixtureAgent(P3_FIXTURES, fixture);
        Harness h = new Harness(agent, home);
        h.start();
        assertEquals("READY", h.state().phase(), "bound: " + h.state().reason());
        return h;
    }

    private static List<SessionEffects.ShowPermissionDialogEffect> dialogs(Harness h) {
        return h.others.stream().filter(SessionEffects.ShowPermissionDialogEffect.class::isInstance)
                .map(SessionEffects.ShowPermissionDialogEffect.class::cast).toList();
    }

    /** The CLI's can_use_tool lines this process emitted, in order. */
    private static List<String> asked(Harness h) {
        return h.agent.last().emitted.stream().filter(l -> l.contains("\"can_use_tool\"")).toList();
    }

    /** The adapter's answers to them, in order. */
    private static List<String> answers(Harness h) {
        return h.agent.last().stdin.stream().filter(l -> l.contains("\"control_response\"")).toList();
    }

    // ---- ★ the answer is exactly the input shown -------------------------------------------------------------------

    /**
     * ★ Witness: in {@code StreamJson.allow}, write {@code Json.write(Json.parse(inputRaw))} (a re-serialised input) — the
     * byte-equality assertion fails, because the recorded input has the CLI's own spacing. And a Deny's words reach the
     * CLI as the node chose them.
     */
    @Test
    @DisplayName("★ allow carries updatedInput byte-equal to the request's input (p3-0); never updatedPermissions; deny says who")
    void theAnswerIsTheInputShown() throws Exception {
        Harness h = bound("p3-0-default-real.jsonl");
        List<String> line = h.agent.commands.get(0);
        assertEquals("default", line.get(line.indexOf("--permission-mode") + 1), "the P3 line (P3-0)");
        assertEquals("stdio", line.get(line.indexOf("--permission-prompt-tool") + 1));

        // turn 1: the person denies
        h.send("deny it");
        assertEquals(1, asked(h).size(), "the recorded turn waits at its can_use_tool");
        var requested = h.facts(SessionEvents.LocalAgentPermissionRequested.class);
        assertEquals(ToolClass.HOSTED_DESTRUCTIVE, requested.get(0).toolClass());
        assertEquals("report", requested.get(0).name());
        assertTrue(requested.get(0).showable());
        assertEquals(1, dialogs(h).size(), "the node asked for the dialog");
        h.driver.post(new SessionEvents.LocalAgentPermissionChosen(requested.get(0).requestRef(), false));
        h.drain();
        String deny = answers(h).get(0);
        assertTrue(deny.contains("\"behavior\":\"deny\"") && deny.contains("Denied by the person in the analyser"), deny);
        assertEquals(requestId(asked(h).get(0)), Json.at(Json.parse(deny), "response", "request_id"));
        assertEquals(SessionEvents.TurnOutcome.COMPLETED, h.facts(SessionEvents.LocalAgentTurnEnded.class).get(0).outcome(),
                "the recorded turn continued from the answer and completed");

        // turn 2: the person allows once
        h.send("allow it");
        var second = h.facts(SessionEvents.LocalAgentPermissionRequested.class).get(1);
        h.driver.post(new SessionEvents.LocalAgentPermissionChosen(second.requestRef(), true));
        String request = asked(h).get(1);
        String allow = answers(h).get(1);
        String shown = StreamJson.rawMember(request, "request", "input");
        String echoed = StreamJson.rawMember(allow, "response", "response", "updatedInput");
        assertNotNull(shown);
        assertEquals(shown, echoed, "updatedInput is the request's input, byte for byte");
        assertTrue(shown.contains(": "), "the recorded input kept the CLI's own spacing (the witness depends on it): " + shown);
        assertFalse(allow.contains("updatedPermissions"), "an approval never widens beyond the call");
        assertFalse(allow.contains("addRules") || allow.contains("permission_suggestions"), "no suggestion is ever applied");
        var answered = answered(h);
        assertEquals(List.of(false, true), answered.stream().map(SessionEvents.LocalAgentPermissionAnswered::allow).toList());
        assertEquals(List.of(PermissionBy.PERSON, PermissionBy.PERSON),
                answered.stream().map(SessionEvents.LocalAgentPermissionAnswered::by).toList());
        assertTrue(answered.stream().allMatch(SessionEvents.LocalAgentPermissionAnswered::written));

        // the tool lines say who decided
        h.clock.advance(200);
        String text = String.join("\n", h.answers());
        assertTrue(text.contains("analyser_report") && text.contains("denied by you"), text);
        assertTrue(text.contains("approved by you"), text);
    }

    /** The answers as the adapter wrote them (R8: the result of each AnswerPermissionEffect, recorded as a fact). */
    private static List<SessionEvents.LocalAgentPermissionAnswered> answered(Harness h) {
        return h.results.stream().filter(SessionEvents.LocalAgentPermissionAnswered.class::isInstance)
                .map(SessionEvents.LocalAgentPermissionAnswered.class::cast).toList();
    }

    private static Object requestId(String line) {
        return Json.at(Json.parse(line), "request_id");
    }

    /** ★ Witness: in the adapter, write the interrupt before the deny (or drop the node's Stop handler) — order fails. */
    @Test
    @DisplayName("★ Stop while a request is pending: the deny is written BEFORE the interrupt; the CLI withdraws it; the turn is cancelled")
    void stopDeniesBeforeTheInterrupt() throws Exception {
        Harness h = bound("p3-0-default-real.jsonl");
        h.agent.last().resumeOnAnswer = false;      // the CLI is still working when the interrupt arrives
        h.send("ask");
        assertEquals(1, dialogs(h).size());
        h.driver.post(new SessionEvents.LocalAgentStopRequested("Stop"));
        h.drain();
        List<String> stdin = h.agent.last().stdin;
        int deny = -1;
        int interrupt = -1;
        for (int i = 0; i < stdin.size(); i++) {
            if (deny < 0 && stdin.get(i).contains("\"behavior\":\"deny\"")) deny = i;
            if (interrupt < 0 && stdin.get(i).contains("\"interrupt\"")) interrupt = i;
        }
        assertTrue(deny >= 0 && interrupt > deny, "deny at " + deny + ", interrupt at " + interrupt + ": " + stdin);
        assertTrue(stdin.get(deny).contains("Stop"), stdin.get(deny));
        assertEquals(SessionEvents.TurnOutcome.CANCELLED, h.facts(SessionEvents.LocalAgentTurnEnded.class).get(0).outcome());
        assertFalse(h.driver.snapshot().permission().waiting());
    }

    /** ★ Witness: drop the adapter's StartPermissionClockEffect schedule — 120 s pass and the request stays open. */
    @Test
    @DisplayName("★ an unanswered request is denied at 120 s on the adapter's clock (the CLI never ends one itself, P3-1)")
    void theDeadlineDenies() throws Exception {
        Harness h = bound("p3-1-unanswered-real.jsonl");
        h.send("ask and wait");
        assertTrue(h.driver.snapshot().permission().waiting());
        h.clock.advance(119_000);
        assertTrue(answers(h).isEmpty(), "not before 120 s");
        h.clock.advance(1_500);
        h.drain();
        assertEquals(1, answers(h).size());
        assertTrue(answers(h).get(0).contains("Not answered in the analyser within 120 s"), answers(h).get(0));
        assertEquals(PermissionBy.TIMEOUT, answered(h).get(0).by());
        h.clock.advance(200);
        assertTrue(String.join("\n", h.answers()).contains("denied: not answered within 120 s"));
    }

    /** P3-6: the CLI asks one at a time — the second request arrives after the first is answered, and is its own dialog. */
    @Test
    @DisplayName("P3-6: two tool uses in one message are asked one after the other; each answer reaches its own request")
    void twoRequestsAreAskedInTurn() throws Exception {
        Harness h = bound("p3-6-parallel-real.jsonl");
        h.send("two at once");
        assertEquals(1, asked(h).size(), "the second is not asked while the first is open");
        var first = h.facts(SessionEvents.LocalAgentPermissionRequested.class).get(0);
        h.driver.post(new SessionEvents.LocalAgentPermissionChosen(first.requestRef(), true));
        assertEquals(2, asked(h).size(), "then the second");
        var second = h.facts(SessionEvents.LocalAgentPermissionRequested.class).get(1);
        assertEquals("screenshot", second.name());
        assertEquals(2, dialogs(h).size());
        h.driver.post(new SessionEvents.LocalAgentPermissionChosen(second.requestRef(), false));
        assertEquals(requestId(asked(h).get(0)), Json.at(Json.parse(answers(h).get(0)), "response", "request_id"));
        assertEquals(requestId(asked(h).get(1)), Json.at(Json.parse(answers(h).get(1)), "response", "request_id"));
        assertEquals(SessionEvents.TurnOutcome.COMPLETED, h.facts(SessionEvents.LocalAgentTurnEnded.class).get(0).outcome());
    }

    // ---- ★ what is recorded --------------------------------------------------------------------------------------

    /**
     * ★ Witness: put the request's words in the fact (pass {@code r.inputRaw()} as the fact's {@code name}) — the marker
     * reaches the session record and the facts.
     */
    @Test
    @DisplayName("★ the session record and the facts hold ids, a class, digests and decisions — never the input's words")
    void theInputsWordsNeverReachTheRecord() throws Exception {
        FixtureAgent agent = new FixtureAgent(P3_FIXTURES, "p3-0-default-real.jsonl");
        agent.rewrite = l -> l.replace("p3-zero-deny", "INPUT-WORDS-MARKER");
        Harness h = new Harness(agent, home);
        h.start();
        h.send("ask");
        var req = h.facts(SessionEvents.LocalAgentPermissionRequested.class).get(0);
        assertTrue(agent.last().emitted.stream().anyMatch(l -> l.contains("INPUT-WORDS-MARKER") && l.contains("can_use_tool")),
                "the request really carried the marker");
        assertTrue(h.store.permission(req.requestRef()).inputRaw().contains("INPUT-WORDS-MARKER"),
                "the dialog's words are in the bounded store");
        h.driver.post(new SessionEvents.LocalAgentPermissionChosen(req.requestRef(), false));
        String record = String.join("\n", h.driver.auditSink().records());
        String facts = h.facts.stream().filter(f -> f.getClass().getSimpleName().contains("Permission")).toList().toString();
        assertFalse(record.contains("INPUT-WORDS-MARKER"), "no input word in the session record");
        assertFalse(facts.contains("INPUT-WORDS-MARKER"), "nor in a permission fact");
        assertTrue(record.contains("LocalAgentPermissionAnswered") && record.contains("HOSTED_DESTRUCTIVE"), record.length() + "");
    }

    /** ★ Witness: drop the showable check in the node (or the 64 KB judge in CanonicalJson) — the dialog opens. */
    @Test
    @DisplayName("★ an input over 64 KB is denied without a dialog, by name; one nested deeper than 32 is too")
    void anUnshowableInputIsDenied() throws Exception {
        FixtureAgent agent = new FixtureAgent(P3_FIXTURES, "p3-0-default-real.jsonl");
        String big = "x".repeat(70_000);
        agent.rewrite = l -> l.contains("can_use_tool") ? l.replace("\"p3-zero-deny\"", "\"" + big + "\"") : l;
        Harness h = new Harness(agent, home);
        h.start();
        h.send("ask big");
        assertTrue(dialogs(h).isEmpty(), "no dialog for an input that cannot be shown in full");
        assertTrue(answers(h).get(0).contains("too large to show the person in full"), answers(h).get(0));
        assertEquals(PermissionBy.TOO_LARGE, answered(h).get(0).by());
        String deep = "[".repeat(40) + "]".repeat(40);
        assertFalse(telamin.fluxtion.audit.analyser.analyser.llm.CanonicalJson.judge("{\"a\":" + deep + "}").showable());
        assertTrue(telamin.fluxtion.audit.analyser.analyser.llm.CanonicalJson.judge("{\"a\":[[1]]}").showable());
    }

    // ---- ★ the P3 posture, from the recorded lines ----------------------------------------------------------------

    /**
     * ★ Witness: the P2 expected set (pre-allowed only) for the default posture — the recorded P3 init (all nineteen
     * hosted verbs, P3-0) is TOOLS_MISMATCH; and the full posture's auto set with the destructive verbs — its recorded
     * init is refused.
     */
    @Test
    @DisplayName("★ the posture check's P3 sets match the recorded P3 lines: default posture all hosted verbs; auto hides the six")
    void theP3PostureMatchesTheRecordedLines() throws Exception {
        Map<?, ?> d = init("p3-lines-default-posture-iso.jsonl");
        assertEquals(LocalAgentPosture.Verdict.OK, LocalAgentPosture.ANALYSER_ASSISTANT.verdict("s", "default",
                strings(d.get("tools")), List.of(LocalAgentPosture.HOSTED_SERVER + ":connected"),
                LocalAgentPosture.REQUIRED_CAPABILITIES, LocalAgentPosture.digest("s")));
        Map<?, ?> auto = init("p3-lines-full-auto-iso.jsonl");
        var full = LocalAgentPosture.full(LocalAgentPosture.FullMode.AUTO);
        assertEquals(LocalAgentPosture.Verdict.OK, full.verdict("s", "auto", strings(auto.get("tools")),
                List.of(LocalAgentPosture.HOSTED_SERVER + ":connected"), LocalAgentPosture.REQUIRED_CAPABILITIES,
                LocalAgentPosture.digest("s")));
        assertFalse(strings(auto.get("tools")).contains(LocalAgentPosture.toolName("report")), "auto hides report");
    }

    private static Map<?, ?> init(String fixture) throws Exception {
        for (String l : Files.readAllLines(P3_FIXTURES.resolve(fixture))) {
            if (l.contains("\"subtype\": \"init\"")) return (Map<?, ?>) Json.parse(l);
        }
        throw new AssertionError("no init in " + fixture);
    }

    private static List<String> strings(Object o) {
        return ((List<?>) o).stream().map(String::valueOf).toList();
    }

    /** Every line of every P3 capture parses to a known kind (control_cancel_request included). */
    @Test
    @DisplayName("every recorded line of the P3 captures parses to a known kind")
    void everyP3LineParses() throws Exception {
        int n = 0;
        try (var files = Files.list(P3_FIXTURES)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".jsonl") && !p.toString().endsWith(".bridge.jsonl"))
                    .sorted().toList()) {
                for (String line : FixtureAgent.lines(P3_FIXTURES, f.getFileName().toString())) {
                    n++;
                    assertFalse(StreamJson.parse(line) instanceof StreamJson.Unknown, f.getFileName() + ": " + line.substring(0,
                            Math.min(160, line.length())));
                }
            }
        }
        assertTrue(n > 300, "read " + n);
        assertInstanceOf(StreamJson.ControlCancelRequest.class,
                StreamJson.parse("{\"type\": \"control_cancel_request\", \"request_id\": \"r1\"}"));
    }
}
