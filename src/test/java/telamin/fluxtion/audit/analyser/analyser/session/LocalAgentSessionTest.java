package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P1a (spec-claude-code-provider.md §4, §10) — the local agent's decisions on the REAL generated processor. Facts go
 * in; what {@code localAgentSession} asks the adapter to do, and what the snapshot publishes, come out. There is no CLI
 * here: {@code LocalAgentAdapterReplayTest} drives the adapter with a fake process replaying the P0/P0b fixtures.
 *
 * <p>Witnesses for the ★ rows were run by breaking the node as each test's comment says and seeing the named assertion
 * fail; they are recorded in the spec's implementation notes.
 */
class LocalAgentSessionTest {

    static final long BUDGET = 5_000_000;   // US$5 in micro-dollars

    static SessionEvents.LocalAgentConsent consent() {
        return consent(true, true, false, "posture-a", BUDGET);
    }

    static SessionEvents.LocalAgentConsent consent(boolean allowed, boolean confirmed, boolean bundle,
                                                   String posture, long budget) {
        return new SessionEvents.LocalAgentConsent(allowed, confirmed, bundle, posture, budget);
    }

    static LocalAgentState state(SessionDriver d) {
        return d.snapshot().localAgent();
    }

    static List<String> expectedTools() {
        return new ArrayList<>(LocalAgentPosture.expectedTools());
    }

    static SessionDriver driver(FakeSessionAdapter a) {
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.LOCAL_AGENT, "test"));
        return d;
    }

    /** Start and drive the handshake to READY: returns the start effect. */
    static SessionEffects.StartLocalAgentEffect startToReady(SessionDriver d, FakeSessionAdapter a) {
        d.post(new SessionEvents.LocalAgentStartRequested(1, consent()));
        var start = a.agentStarts.get(a.agentStarts.size() - 1);
        bind(d, start);
        return start;
    }

    static void bind(SessionDriver d, SessionEffects.StartLocalAgentEffect start) {
        d.post(new SessionEvents.LocalAgentSpawned(start.generation(), SessionEvents.SpawnFailure.NONE, 0));
        d.post(new SessionEvents.LocalAgentInitialized(start.generation(), true, "dontAsk", 7));
        d.post(new SessionEvents.LocalAgentStatusObserved(start.generation(), 480,
                List.of(LocalAgentPosture.HOSTED_SERVER), "connected", LocalAgentPosture.digest(start.nonce())));
    }

    static SessionEvents.LocalAgentInitObserved goodInit(SessionEffects.StartLocalAgentEffect start, long ticket) {
        return new SessionEvents.LocalAgentInitObserved(start.generation(), ticket, LocalAgentPosture.digest(start.sessionId()), "dontAsk",
                expectedTools(), List.of(LocalAgentPosture.HOSTED_SERVER + ":connected"),
                LocalAgentPosture.REQUIRED_CAPABILITIES, 0);
    }

    static SessionEffects.SendLocalAgentTurnEffect send(SessionDriver d, FakeSessionAdapter a, long draft) {
        int before = a.agentSends.size();
        d.post(new SessionEvents.LocalAgentSendRequested(draft, draft, SessionEvents.SlashClass.TEXT, null, consent()));
        assertEquals(before + 1, a.agentSends.size(), "the turn was written: " + state(d).reason());
        var sent = a.agentSends.get(a.agentSends.size() - 1);
        d.post(new SessionEvents.LocalAgentTurnStarted(sent.generation(), sent.ticket()));   // the CLI's lifecycle started
        return sent;
    }

    static List<SessionEffects.StopLocalAgentEffect> kills(FakeSessionAdapter a) {
        return a.agentStops.stream().filter(s -> s.how() == SessionEffects.StopHow.KILL).toList();
    }

    // ---- ★ consent ---------------------------------------------------------------------------------------------------

    /** ★ Witness: drop the {@code !c.allowed()} check in {@code startRefusal} — the first assertion fails. */
    @Test
    @DisplayName("★ consent: switch off, in-app mode, a bundle — each refuses Start by name and starts nothing")
    void consentRefusesStart() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        d.post(new SessionEvents.LocalAgentStartRequested(1, consent(false, true, false, "p", BUDGET)));
        assertEquals(0, a.agentStarts.size(), "the switch is off: nothing starts");
        assertEquals("SWITCHED_OFF", state(d).verdict());
        assertTrue(state(d).reason().contains("Allow the analyser to start Claude Code"), state(d).reason());

        d.post(new SessionEvents.LocalAgentStartRequested(2, consent(true, true, true, "p", BUDGET)));
        assertEquals("BUNDLE", state(d).verdict());
        assertEquals(0, a.agentStarts.size());

        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver inApp = new SessionDriver(b);       // the default mode is in-app
        inApp.post(new SessionEvents.LocalAgentStartRequested(1, consent()));
        assertEquals(0, b.agentStarts.size(), "localAgentSession refuses under IN_APP, in its own handler");
        assertEquals("MODE", state(inApp).verdict());
    }

    @Test
    @DisplayName("an unconfirmed folder asks first and starts nothing; a confirmed one starts")
    void anUnconfirmedFolderAsksBeforeStart() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        d.post(new SessionEvents.LocalAgentStartRequested(2, consent(true, false, false, "p", BUDGET)));
        assertEquals(1, a.agentConfirms.size(), "Start asks for the folder confirmation");
        assertEquals(2, a.agentConfirms.get(0).request());
        assertEquals(0, a.agentStarts.size(), "and starts nothing until the person answers");

        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = driver(b);
        e.post(new SessionEvents.LocalAgentStartRequested(3, consent(true, true, false, "p", BUDGET)));
        assertEquals(0, b.agentConfirms.size(), "a confirmed folder is not asked about again");
        assertEquals(1, b.agentStarts.size());
    }

    // ---- the handshake and the binding ------------------------------------------------------------------------------

    @Test
    @DisplayName("Start: spawn → initialize → mcp_status polled every 250 ms → BOUND; the header follows initialize")
    void theHandshake() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        d.post(new SessionEvents.LocalAgentStartRequested(1, consent()));
        var start = a.agentStarts.get(0);
        assertEquals(1, start.generation());
        assertFalse(start.resume(), "a new conversation uses --session-id");
        assertEquals(16, start.nonce().length());
        assertEquals(BUDGET, start.maxBudgetMicros());
        assertEquals("SPAWNING", state(d).phase());
        d.post(new SessionEvents.LocalAgentSpawned(1, SessionEvents.SpawnFailure.NONE, 0));
        d.post(new SessionEvents.LocalAgentInitialized(1, true, "dontAsk", 9));
        assertEquals("BINDING", state(d).phase());
        assertEquals(9, state(d).headerRef());
        assertEquals(0, a.agentPolls.get(0).delayMs(), "the first mcp_status right after initialize answers");
        d.post(new SessionEvents.LocalAgentStatusObserved(1, 470, List.of(LocalAgentPosture.HOSTED_SERVER), "pending", null));
        assertEquals(LocalAgentSession.POLL_MS, a.agentPolls.get(1).delayMs(), "then every 250 ms");
        d.post(new SessionEvents.LocalAgentStatusObserved(1, 2_400, List.of(LocalAgentPosture.HOSTED_SERVER), "pending", null));
        assertEquals(2_400, state(d).bindingElapsedMs(), "the tab says 'connecting…' after 2 s from this");
        d.post(new SessionEvents.LocalAgentStatusObserved(1, 3_290, List.of(LocalAgentPosture.HOSTED_SERVER), "connected",
                LocalAgentPosture.digest(start.nonce())));
        assertEquals("READY", state(d).phase());
        assertEquals("BOUND", state(d).verdict());
        assertEquals(0, state(d).admittedGeneration(), "bound, but the first turn's posture is not yet checked");
        assertTrue(kills(a).isEmpty());
    }

    /** ★ Witness: skip the {@code !e.loggedIn()} refusal in {@code onInitialized} — the verdict assertion fails. */
    @Test
    @DisplayName("★ not logged in at initialize: refused with the login words, the process stopped, no binding")
    void notLoggedInIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        d.post(new SessionEvents.LocalAgentStartRequested(1, consent()));
        d.post(new SessionEvents.LocalAgentSpawned(1, SessionEvents.SpawnFailure.NONE, 0));
        d.post(new SessionEvents.LocalAgentInitialized(1, false, "dontAsk", 3));
        assertEquals("NOT_LOGGED_IN", state(d).verdict());
        assertEquals(LocalAgentSession.NOT_LOGGED_IN_TEXT, state(d).reason());
        assertEquals(1, kills(a).size(), "the process is stopped");
        assertTrue(a.agentPolls.isEmpty(), "nothing is bound");
        assertEquals("OFF", state(d).phase());
    }

    @Test
    @DisplayName("initialize reporting another permission mode → MODE_MISMATCH, killed at once")
    void anotherModeAtInitializeIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        d.post(new SessionEvents.LocalAgentStartRequested(1, consent()));
        d.post(new SessionEvents.LocalAgentSpawned(1, SessionEvents.SpawnFailure.NONE, 0));
        d.post(new SessionEvents.LocalAgentInitialized(1, true, "acceptEdits", 3));
        assertEquals("MODE_MISMATCH", state(d).verdict());
        assertEquals(1, kills(a).size());
    }

    /**
     * ★ Witness: accept any {@code connected} status in {@code onStatusObserved} (skip the nonce comparison) — the
     * NONCE_MISMATCH and OLD_BRIDGE assertions fail.
     */
    @Test
    @DisplayName("★ binding: BOUND only on the exact nonce; NONCE_MISMATCH, OLD_BRIDGE, FAILED, TIMEOUT and an extra server refuse")
    void bindingVerdicts() {
        record Case(String status, String nonce, List<String> servers, long elapsed, String verdict) { }
        List<Case> cases = List.of(
                new Case("connected", LocalAgentPosture.digest("0123456789abcdef"), List.of(LocalAgentPosture.HOSTED_SERVER), 500, "NONCE_MISMATCH"),
                new Case("connected", null, List.of(LocalAgentPosture.HOSTED_SERVER), 500, "OLD_BRIDGE"),
                new Case("failed", null, List.of(LocalAgentPosture.HOSTED_SERVER), 30_480, "FAILED"),
                new Case("pending", null, List.of(LocalAgentPosture.HOSTED_SERVER), 35_000, "TIMEOUT"),
                new Case("connected", "SAME", List.of(LocalAgentPosture.HOSTED_SERVER, "projstub"), 500, "SERVERS_MISMATCH"));
        for (Case c : cases) {
            FakeSessionAdapter a = new FakeSessionAdapter();
            SessionDriver d = driver(a);
            d.post(new SessionEvents.LocalAgentStartRequested(1, consent()));
            var start = a.agentStarts.get(0);
            d.post(new SessionEvents.LocalAgentSpawned(1, SessionEvents.SpawnFailure.NONE, 0));
            d.post(new SessionEvents.LocalAgentInitialized(1, true, "dontAsk", 3));
            d.post(new SessionEvents.LocalAgentStatusObserved(1, c.elapsed(), c.servers(), c.status(),
                    "SAME".equals(c.nonce()) ? LocalAgentPosture.digest(start.nonce()) : c.nonce()));
            assertEquals(c.verdict(), state(d).verdict(), c.toString());
            assertEquals(1, kills(a).size(), "any verdict but BOUND stops the process: " + c);
            assertEquals(0, state(d).admittedGeneration(), c.toString());
        }
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        d.post(new SessionEvents.LocalAgentStartRequested(1, consent()));
        d.post(new SessionEvents.LocalAgentStartDeadline(1));
        assertEquals("TIMEOUT", state(d).verdict(), "the 35 s start deadline ends a start that never answered");
        assertEquals(LocalAgentSession.OLD_BRIDGE_TEXT, "the installed analyser bridge is older than this analyser — update it");
    }

    /**
     * ★ Witness: keep the previous {@code nonce} in {@code spawn()} instead of minting one — "a fresh nonce per spawn"
     * fails, and the replayed previous nonce is BOUND instead of refused.
     */
    @Test
    @DisplayName("★ a fresh nonce for every spawn — Start, resume and the restart after a kill; the previous one is refused")
    void aFreshNoncePerSpawn() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        var first = startToReady(d, a);
        d.post(goodInit(first, 1));
        send(d, a, 10);
        d.post(new SessionEvents.LocalAgentStopRequested("test"));
        d.post(new SessionEvents.LocalAgentStopDeadline(1, 1, SessionEvents.StopDeadline.ACK));   // never acknowledged
        assertEquals(1, kills(a).size(), "the fallback killed the tree");
        assertEquals("OFF", state(d).phase());
        d.post(new SessionEvents.LocalAgentSendRequested(11, 11, SessionEvents.SlashClass.TEXT, null, consent()));
        var second = a.agentStarts.get(1);
        assertEquals(2, second.generation(), "a new generation");
        assertTrue(second.resume(), "the next Send resumes the conversation by id");
        assertEquals(first.sessionId(), second.sessionId(), "the same conversation");
        assertNotEquals(first.nonce(), second.nonce(), "a fresh nonce per spawn");
        d.post(new SessionEvents.LocalAgentSpawned(2, SessionEvents.SpawnFailure.NONE, 0));
        d.post(new SessionEvents.LocalAgentInitialized(2, true, "dontAsk", 3));
        d.post(new SessionEvents.LocalAgentStatusObserved(2, 400, List.of(LocalAgentPosture.HOSTED_SERVER), "connected",
                LocalAgentPosture.digest(first.nonce())));
        assertEquals("NONCE_MISMATCH", state(d).verdict(), "a bridge reporting the previous generation's nonce is refused");
    }

    /** ★ Witness: return {@code generation} from {@code admittedGeneration()} once bound — the first assertion fails. */
    @Test
    @DisplayName("★ hosted calls are admitted only after BOUND and the first turn's posture check, for that generation")
    void hostedAdmissionWaitsForThePosture() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        var start = startToReady(d, a);
        assertEquals(0, state(d).admittedGeneration(), "BOUND alone does not admit the bridge");
        send(d, a, 10);
        assertEquals(0, state(d).admittedGeneration(), "nor does a sent turn");
        d.post(goodInit(start, 1));
        assertEquals(1, state(d).admittedGeneration(), "the first turn's system/init passed the posture check");
        d.post(new SessionEvents.LocalAgentNewChatRequested("test"));
        assertEquals(0, state(d).admittedGeneration(), "a stopped generation is never admitted");
    }

    // ---- turns ----------------------------------------------------------------------------------------------------

    /** ★ Witness: request a new StartLocalAgentEffect in {@code sendTurn} — the one-handshake assertion fails. */
    @Test
    @DisplayName("★ two turns over one process: one handshake, two TurnEnded, tickets in order")
    void multiTurnOverOneProcess() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        var start = startToReady(d, a);
        var t1 = send(d, a, 10);
        d.post(goodInit(start, t1.ticket()));
        d.post(new SessionEvents.LocalAgentTextUpdated(1, t1.ticket(), 20, 1));
        d.post(new SessionEvents.LocalAgentTurnEnded(1, t1.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        var t2 = send(d, a, 11);
        d.post(goodInit(start, t2.ticket()));
        d.post(new SessionEvents.LocalAgentTurnEnded(1, t2.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        assertEquals(1, a.agentStarts.size(), "one handshake");
        assertEquals(List.of(1L, 2L), a.agentSends.stream().map(SessionEffects.SendLocalAgentTurnEffect::ticket).toList());
        long ended = state(d).entries().stream().filter(e -> LocalAgentState.ANSWER.equals(e.kind())
                && "COMPLETED".equals(e.status())).count();
        assertEquals(1, ended, "turn 1's answer completed (turn 2 had no text)");
        assertFalse(state(d).busy());
        assertEquals("READY", state(d).phase());
    }

    /**
     * ★ Witness: drop {@code stale(...)} from {@code onTurnEnded} — the old generation's end closes the new turn (the
     * "late result" assertion fails); and from {@code onTextUpdated} — the killed process's text lands.
     */
    @Test
    @DisplayName("★ a late line from a killed process (old generation) or an old ticket is refused")
    void staleFactsAreRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        var first = startToReady(d, a);
        d.post(goodInit(first, 1));
        send(d, a, 10);
        d.post(new SessionEvents.LocalAgentNewChatRequested("test"));      // kills generation 1
        var second = startToReady(d, a);
        assertEquals(2, second.generation());
        var t = send(d, a, 11);
        d.post(new SessionEvents.LocalAgentTurnEnded(1, 1, SessionEvents.TurnOutcome.COMPLETED));       // old generation
        d.post(new SessionEvents.LocalAgentTurnEnded(1, t.ticket(), SessionEvents.TurnOutcome.COMPLETED)); // old gen, this ticket
        d.post(new SessionEvents.LocalAgentTextUpdated(1, t.ticket(), 99, 1));                           // old generation
        assertTrue(state(d).busy(), "a late result from the killed process does not close the new turn");
        assertTrue(state(d).entries().stream().noneMatch(e -> e.id() == 99), "nor does its text land");
        d.post(new SessionEvents.LocalAgentTurnEnded(2, t.ticket() + 5, SessionEvents.TurnOutcome.COMPLETED));  // no such ticket
        assertTrue(state(d).busy(), "an old or unknown ticket is refused");
        d.post(new SessionEvents.LocalAgentTurnEnded(2, t.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        assertFalse(state(d).busy());
        d.post(new SessionEvents.LocalAgentTurnEnded(2, t.ticket(), SessionEvents.TurnOutcome.PROCESS_EXIT));
        assertEquals(0, state(d).entries().stream().filter(e -> "PROCESS_EXIT".equals(e.status())).count(),
                "a second end signal for the same ticket is ignored");
    }

    /**
     * ★ Witness: remove the {@code openTicket != 0} test from {@code sendRefusal} — the second draft is silently held
     * instead of refused, and the BUSY assertion fails.
     */
    @Test
    @DisplayName("★ Send while a ticket is open is refused by the node, with the OD-74.7 words; nothing is written")
    void sendWhileBusyIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        startToReady(d, a);
        send(d, a, 10);
        d.post(new SessionEvents.LocalAgentSendRequested(11, 11, SessionEvents.SlashClass.TEXT, null, consent()));
        assertEquals(1, a.agentSends.size(), "nothing is queued: the CLI never sees the second line");
        assertEquals("BUSY", state(d).verdict());
        assertEquals("Claude is still answering — Stop or wait.", state(d).answer().reason());
        assertFalse(state(d).answer().accepted());
        assertEquals(11, state(d).answer().request());
        d.post(new SessionEvents.LocalAgentSendRequested(12, 12, SessionEvents.SlashClass.PASS, "compact", consent()));
        assertEquals(1, a.agentSends.size(), "a typed command is a turn, refused the same way");
        // P1a live smoke: the refusal does not outlive the turn it was about
        d.post(new SessionEvents.LocalAgentTurnEnded(1, a.agentSends.get(0).ticket(), SessionEvents.TurnOutcome.COMPLETED));
        assertEquals("", state(d).verdict(), "the BUSY refusal is cleared when the turn ends");
        assertEquals("", state(d).reason());
    }

    @Test
    @DisplayName("slash commands: /clear offers New chat, a blocked or unclassified name is refused by name; none is written")
    void slashRefusals() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        startToReady(d, a);
        d.post(new SessionEvents.LocalAgentSendRequested(1, 1, SessionEvents.SlashClass.NEW_CHAT, "clear", consent()));
        assertTrue(state(d).newChatOffered());
        assertEquals("SLASH_NEW_CHAT", state(d).verdict());
        d.post(new SessionEvents.LocalAgentSendRequested(2, 2, SessionEvents.SlashClass.BLOCK, "config", consent()));
        assertEquals("/config is not available in the analyser — use Claude Code in a terminal for that.",
                state(d).answer().reason());
        d.post(new SessionEvents.LocalAgentSendRequested(3, 3, SessionEvents.SlashClass.UNCLASSIFIED, "brand-new", consent()));
        assertEquals("SLASH_UNCLASSIFIED", state(d).verdict());
        d.post(new SessionEvents.LocalAgentSendRequested(4, 4, SessionEvents.SlashClass.SHORTCUT, null, consent()));
        assertEquals("SLASH_SHORTCUT", state(d).verdict());
        assertEquals(0, a.agentSends.size(), "nothing was written to stdin");
        d.post(new SessionEvents.LocalAgentSendRequested(5, 5, SessionEvents.SlashClass.PASS, "compact", consent()));
        assertEquals(1, a.agentSends.size(), "/compact passes");
    }

    /**
     * ★ Witness: request KILL instead of INTERRUPT in {@code onStopRequested} — "one interrupt" fails; and ignore the
     * ACK deadline — the fallback kill assertion fails.
     */
    @Test
    @DisplayName("★ Stop sends one interrupt for the open ticket; cancelled ends it; no ack in 5 s or no end in 15 s → tree kill")
    void stopInterruptsAndFallsBack() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        var start = startToReady(d, a);
        var t = send(d, a, 10);
        d.post(new SessionEvents.LocalAgentStopRequested("Stop"));
        d.post(new SessionEvents.LocalAgentStopRequested("Stop again"));
        assertEquals(1, a.agentStops.size(), "one interrupt, however often Stop is pressed");
        assertEquals(SessionEffects.StopHow.INTERRUPT, a.agentStops.get(0).how());
        assertEquals(t.ticket(), a.agentStops.get(0).ticket(), "for the one open ticket");
        d.post(new SessionEvents.LocalAgentInterruptAcked(1, t.ticket()));
        d.post(new SessionEvents.LocalAgentStopDeadline(1, t.ticket(), SessionEvents.StopDeadline.ACK));
        assertTrue(kills(a).isEmpty(), "acknowledged in time: no kill");
        d.post(new SessionEvents.LocalAgentTurnEnded(1, t.ticket(), SessionEvents.TurnOutcome.CANCELLED));
        assertFalse(state(d).busy());
        assertTrue(state(d).costUnderReported(), "an interrupted turn may under-report its cost");
        d.post(new SessionEvents.LocalAgentStopDeadline(1, t.ticket(), SessionEvents.StopDeadline.END));
        assertTrue(kills(a).isEmpty(), "a deadline after the end does nothing");

        var t2 = send(d, a, 11);
        d.post(new SessionEvents.LocalAgentStopRequested("Stop"));
        d.post(new SessionEvents.LocalAgentInterruptAcked(1, t2.ticket()));
        d.post(new SessionEvents.LocalAgentStopDeadline(1, t2.ticket(), SessionEvents.StopDeadline.END));
        assertEquals(1, kills(a).size(), "no end within 15 s → the tree is killed");
        assertEquals("OFF", state(d).phase());
        assertFalse(state(d).busy());
        assertTrue(state(d).resumable(), "the conversation survives; the next Send resumes it");
        d.post(new SessionEvents.LocalAgentSendRequested(12, 12, SessionEvents.SlashClass.TEXT, null, consent()));
        assertEquals(start.sessionId(), a.agentStarts.get(1).sessionId(), "resumed by id");
        assertTrue(a.agentStarts.get(1).resume());
    }

    // ---- ★ the posture assertion ------------------------------------------------------------------------------------

    /**
     * ★ Witness: treat TOOLS_MISMATCH like SERVERS_MISMATCH (interrupt first) — the "killed at once, no interrupt"
     * assertion fails.
     */
    @Test
    @DisplayName("★ first turn's init: an extra tool, another mode or a missing capability → killed at once, nothing admitted")
    void aWidenedSessionIsKilledAtOnce() {
        String extra = LocalAgentPosture.toolName("report");
        List<SessionEvents.LocalAgentInitObserved> bad = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            FakeSessionAdapter a = new FakeSessionAdapter();
            SessionDriver d = driver(a);
            var start = startToReady(d, a);
            var t = send(d, a, 10);
            List<String> tools = expectedTools();
            String mode = "dontAsk";
            List<String> caps = LocalAgentPosture.REQUIRED_CAPABILITIES;
            String expect;
            if (i == 0) { tools.add(extra); expect = "TOOLS_MISMATCH"; }
            else if (i == 1) { mode = "bypassPermissions"; expect = "MODE_MISMATCH"; }
            else { caps = List.of("interrupt_receipt_v1"); expect = "CAPABILITY_MISSING"; }
            d.post(new SessionEvents.LocalAgentInitObserved(1, t.ticket(), LocalAgentPosture.digest(start.sessionId()), mode, tools,
                    List.of(LocalAgentPosture.HOSTED_SERVER + ":connected"), caps, 0));
            assertEquals(expect, state(d).verdict());
            assertEquals(List.of(SessionEffects.StopHow.KILL),
                    a.agentStops.stream().map(SessionEffects.StopLocalAgentEffect::how).toList(),
                    expect + ": killed at once, no interrupt first");
            assertEquals(0, state(d).admittedGeneration(), "no verb can run");
            assertEquals("OFF", state(d).phase());
        }
    }

    /** ★ Witness: check the posture only on the first init of a generation — the later SESSION_CHANGED passes. */
    @Test
    @DisplayName("★ a later init with a new session id (e.g. /clear) stops the session: interrupt first, kill after the turn")
    void driftAfterStartStopsTheSession() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        var start = startToReady(d, a);
        var t1 = send(d, a, 10);
        d.post(goodInit(start, t1.ticket()));
        d.post(new SessionEvents.LocalAgentTurnEnded(1, t1.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        var t2 = send(d, a, 11);
        d.post(new SessionEvents.LocalAgentInitObserved(1, t2.ticket(), LocalAgentPosture.digest("00000000-0000-4000-8000-0000000000ff"), "dontAsk",
                expectedTools(), List.of(LocalAgentPosture.HOSTED_SERVER + ":connected"),
                LocalAgentPosture.REQUIRED_CAPABILITIES, 0));
        assertEquals("SESSION_CHANGED", state(d).verdict());
        assertEquals(0, state(d).admittedGeneration(), "the bridge is refused from the moment the drift is seen");
        assertEquals(SessionEffects.StopHow.INTERRUPT, a.agentStops.get(0).how(), "interrupt first");
        d.post(new SessionEvents.LocalAgentTurnEnded(1, t2.ticket(), SessionEvents.TurnOutcome.CANCELLED));
        assertEquals(1, kills(a).size(), "then the process is stopped");
        assertTrue(state(d).reason().contains("session id changed"), state(d).reason());
    }

    // ---- ★ one mode -------------------------------------------------------------------------------------------------

    /**
     * ★ Witness: remove the {@code localAgentMode()} check from {@code AssistantLoop.refusal} — the in-app send under
     * LOCAL_AGENT is accepted.
     */
    @Test
    @DisplayName("★ AssistantMode is enforced in both nodes: in-app Send refused under LOCAL_AGENT, local Send under IN_APP")
    void oneModeInBothNodes() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        d.post(new SessionEvents.AssistantSendRequested(7, 1, AssistantLoopTest.route(true, 3, 20)));
        assertEquals(0, a.assistantContexts.size(), "the in-app assistant prepares nothing under LOCAL_AGENT");
        assertFalse(d.snapshot().assistant().answer().accepted());
        assertTrue(d.snapshot().assistant().answer().reason().contains("Claude Code"));
        d.post(new SessionEvents.AssistantHandoffRequested("journey"));
        assertEquals(1, d.snapshot().assistant().conversation(), "a handoff is ignored under LOCAL_AGENT");

        startToReady(d, a);
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.IN_APP, "test"));
        assertEquals(1, kills(a).size(), "leaving the local agent stops its process");
        d.post(new SessionEvents.LocalAgentSendRequested(9, 9, SessionEvents.SlashClass.TEXT, null, consent()));
        assertEquals(0, a.agentSends.size(), "a local Send is refused under IN_APP");
        assertEquals("MODE", state(d).verdict());
        d.post(new SessionEvents.AssistantSendRequested(8, 2, AssistantLoopTest.route(true, 3, 20)));
        assertEquals(1, a.assistantContexts.size(), "and the in-app assistant works again");
    }

    // ---- ★ budget -----------------------------------------------------------------------------------------------------

    /** ★ Witness: sum every CostObserved instead of keeping the last per session — the remainder is too small. */
    @Test
    @DisplayName("★ budget: the last total_cost_usd per session, summed across New chat; --max-budget-usd is the remainder")
    void budgetArithmetic() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        var start = startToReady(d, a);
        var t1 = send(d, a, 10);
        d.post(new SessionEvents.LocalAgentCostObserved(1, 100_000));
        d.post(new SessionEvents.LocalAgentTurnEnded(1, t1.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        var t2 = send(d, a, 11);
        d.post(new SessionEvents.LocalAgentCostObserved(1, 250_000));      // a running total, not a turn's cost
        d.post(new SessionEvents.LocalAgentTurnEnded(1, t2.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        assertEquals(250_000, state(d).spentMicros(), "the last value of the session, never the sum of turns");
        d.post(new SessionEvents.LocalAgentNewChatRequested("test"));
        var second = startToReady(d, a);
        assertEquals(BUDGET - 250_000, second.maxBudgetMicros(), "the new process gets the remainder");
        assertNotEquals(start.sessionId(), second.sessionId(), "New chat is a new session id");
        assertFalse(second.resume());
        var t3 = send(d, a, 12);
        d.post(new SessionEvents.LocalAgentCostObserved(2, 50_000));
        assertEquals(300_000, state(d).spentMicros(), "summed across sessions");
        d.post(new SessionEvents.LocalAgentTurnEnded(2, t3.ticket(), SessionEvents.TurnOutcome.BUDGET));
        assertEquals(2, kills(a).size(), "error_max_budget_usd stops the process (it stays alive refusing otherwise)");
        assertTrue(state(d).reason().contains("budget"), state(d).reason());
    }

    /** ★ Witness (review M1): {@code costBySession.put} instead of a monotone merge — the 0 resets the total. */
    @Test
    @DisplayName("★ M1: an interrupted turn's total_cost_usd of 0 does not reset the session total; the remainder stays right")
    void anInterruptedTurnsZeroCostDoesNotResetTheTotal() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        startToReady(d, a);
        var t1 = send(d, a, 10);
        d.post(new SessionEvents.LocalAgentCostObserved(1, 250_000));
        d.post(new SessionEvents.LocalAgentTurnEnded(1, t1.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        var t2 = send(d, a, 11);
        d.post(new SessionEvents.LocalAgentCostObserved(1, 0));          // [P0-22]: an interrupted turn once reported 0
        d.post(new SessionEvents.LocalAgentTurnEnded(1, t2.ticket(), SessionEvents.TurnOutcome.CANCELLED));
        assertEquals(250_000, state(d).spentMicros(), "the total is monotone per session");
        d.post(new SessionEvents.LocalAgentNewChatRequested("test"));
        var next = startToReady(d, a);
        assertEquals(BUDGET - 250_000, next.maxBudgetMicros(), "the next process is not given the spent money again");
    }

    /**
     * ★ Witness (review M2): set {@code resumable = true} in {@code onSpawned} again — the restart after a refusal uses
     * --resume on an id with no transcript, and a posture change refuses a conversation that never had a turn.
     */
    @Test
    @DisplayName("★ M2: a conversation is resumable only after its first turn started; before that every start is fresh")
    void resumeOnlyAfterATurnStarted() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        var first = startToReady(d, a);
        assertFalse(state(d).resumable(), "bound, but no turn yet: nothing to resume");
        d.post(new SessionEvents.LocalAgentStopRequested("Stop"));                         // nothing open: no-op
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.IN_APP, "test"));        // kills it
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.LOCAL_AGENT, "test"));
        d.post(new SessionEvents.LocalAgentStartRequested(2, consent(true, true, false, "posture-b", BUDGET)));
        assertEquals(2, a.agentStarts.size(), "a turn-less conversation is never refused for a posture change");
        var second = a.agentStarts.get(1);
        assertFalse(second.resume(), "never --resume a conversation the CLI never started a turn of");
        assertNotEquals(first.sessionId(), second.sessionId(), "and a fresh id, not --session-id on the old one");
        assertEquals("posture-b", second.postureRef(), "under the posture in force now");

        // NOT_LOGGED_IN, then logged in: still fresh
        d.post(new SessionEvents.LocalAgentSpawned(2, SessionEvents.SpawnFailure.NONE, 0));
        d.post(new SessionEvents.LocalAgentInitialized(2, false, "dontAsk", 3));
        d.post(new SessionEvents.LocalAgentStartRequested(3, consent(true, true, false, "posture-b", BUDGET)));
        assertFalse(a.agentStarts.get(2).resume());

        // after a turn has started, a restart resumes the same id
        bind(d, a.agentStarts.get(2));
        var t = send(d, a, 10);
        assertTrue(state(d).resumable());
        d.post(new SessionEvents.LocalAgentStopRequested("Stop"));
        d.post(new SessionEvents.LocalAgentStopDeadline(3, t.ticket(), SessionEvents.StopDeadline.ACK));
        d.post(new SessionEvents.LocalAgentStartRequested(4, consent(true, true, false, "posture-b", BUDGET)));
        var resumed = a.agentStarts.get(3);
        assertTrue(resumed.resume());
        assertEquals(a.agentStarts.get(2).sessionId(), resumed.sessionId());
    }

    /** ★ Witness (review M4, S10): drop the folder-verdict checks in {@code startRefusal} — Start asks or starts. */
    @Test
    @DisplayName("★ M4/S10: a folder exposing credentials, or no folder at all, refuses Start and asks no confirmation")
    void unusableFoldersAreRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        d.post(new SessionEvents.LocalAgentStartRequested(1, new SessionEvents.LocalAgentConsent(true, false, false,
                "p", BUDGET, SessionEvents.FolderVerdict.EXPOSES_SECRETS)));
        assertEquals("FOLDER_EXPOSES_SECRETS", state(d).verdict());
        assertTrue(state(d).reason().contains("home folder"), state(d).reason());
        d.post(new SessionEvents.LocalAgentStartRequested(2, new SessionEvents.LocalAgentConsent(true, true, false,
                "p", BUDGET, SessionEvents.FolderVerdict.UNAVAILABLE)));
        assertEquals("NO_FOLDER", state(d).verdict());
        assertEquals(0, a.agentConfirms.size(), "never asked to trust such a folder");
        assertEquals(0, a.agentStarts.size());
    }

    @Test
    @DisplayName("a spent budget refuses Start")
    void aSpentBudgetRefusesStart() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        startToReady(d, a);
        var t = send(d, a, 10);
        d.post(new SessionEvents.LocalAgentCostObserved(1, BUDGET));
        d.post(new SessionEvents.LocalAgentTurnEnded(1, t.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        d.post(new SessionEvents.LocalAgentNewChatRequested("test"));
        d.post(new SessionEvents.LocalAgentStartRequested(5, consent()));
        assertEquals(1, a.agentStarts.size());
        assertEquals("BUDGET_SPENT", state(d).verdict());
    }

    // ---- ★ posture change -----------------------------------------------------------------------------------------

    /** ★ Witness: drop the {@code postureRef} comparison in {@code startRefusal} — the old session is resumed. */
    @Test
    @DisplayName("★ a posture change never resumes the old session id; it offers New chat")
    void aPostureChangeNeedsNewChat() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        var first = startToReady(d, a);
        var t = send(d, a, 10);                                         // the conversation has a turn: it is resumable
        d.post(new SessionEvents.LocalAgentTurnEnded(1, t.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.IN_APP, "test"));
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.LOCAL_AGENT, "test"));
        d.post(new SessionEvents.LocalAgentStartRequested(2, consent(true, true, false, "posture-b", BUDGET)));
        assertEquals(1, a.agentStarts.size(), "not resumed under another posture");
        assertEquals("POSTURE_CHANGED", state(d).verdict());
        assertTrue(state(d).newChatOffered());
        d.post(new SessionEvents.LocalAgentNewChatRequested("offered"));
        d.post(new SessionEvents.LocalAgentStartRequested(3, consent(true, true, false, "posture-b", BUDGET)));
        var second = a.agentStarts.get(1);
        assertFalse(second.resume());
        assertNotEquals(first.sessionId(), second.sessionId());
        assertEquals("posture-b", second.postureRef());
    }

    @Test
    @DisplayName("Send with nothing running starts it and holds the one draft until BOUND; a spawn failure says why")
    void sendStartsAndHolds() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        d.post(new SessionEvents.LocalAgentSendRequested(1, 10, SessionEvents.SlashClass.TEXT, null, consent()));
        assertEquals(1, a.agentStarts.size());
        assertEquals(0, a.agentSends.size(), "held, never given to the CLI's queue");
        assertEquals(10, state(d).pendingDraft());
        d.post(new SessionEvents.LocalAgentSendRequested(2, 11, SessionEvents.SlashClass.TEXT, null, consent()));
        assertEquals("BUSY", state(d).verdict(), "one held draft at most");
        bind(d, a.agentStarts.get(0));
        assertEquals(1, a.agentSends.size(), "sent once bound");
        assertEquals(10, a.agentSends.get(0).draft());

        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = driver(b);
        e.post(new SessionEvents.LocalAgentStartRequested(1, consent()));
        e.post(new SessionEvents.LocalAgentSpawned(1, SessionEvents.SpawnFailure.LOCKED, 0));
        assertEquals("This conversation is open in another analyser window.", state(e).reason());
        assertEquals("OFF", state(e).phase());
    }

    @Test
    @DisplayName("the process exiting ends the open turn PROCESS_EXIT; the audit record holds no session id or nonce")
    void exitEndsTheTurnAndTheRecordHoldsIdsOnly() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        var start = startToReady(d, a);
        var t = send(d, a, 10);
        d.post(goodInit(start, t.ticket()));
        d.post(new SessionEvents.LocalAgentExited(1, 1, 0));
        assertFalse(state(d).busy());
        assertEquals("OFF", state(d).phase());
        assertTrue(state(d).entries().stream().anyMatch(e -> "PROCESS_EXIT".equals(e.status())));
        String record = String.join("\n", d.auditSink().records());
        assertFalse(record.contains(start.sessionId()), "the session id is never written to the session audit record");
        assertFalse(record.contains(start.nonce()), "nor the nonce");
        assertTrue(record.contains("localAgentTurn"), "the decisions are");
        assertFalse(d.snapshot().toString().contains(start.sessionId()), "nor published in the snapshot");
    }

    // ---- review fixes (holistic M74 review) -------------------------------------------------------------------------

    /**
     * ★ Review H4. Witness: put the REST_OFF refusal back in {@code startRefusal}, or drop the
     * {@code ServeHostedBridgeEffect} from {@code spawn} — Start is refused / no listener is asked for, and the named
     * assertion fails.
     */
    @Test
    @DisplayName("★ H4: Start never needs the external REST switch — the node asks for the bridge's listener first, then the process")
    void startAsksForTheBridgeListenerFirst() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        d.post(new SessionEvents.LocalAgentStartRequested(1, consent()));
        assertEquals(List.of("serveBridge", "start"), a.agentOrder, "the listener, then the process that connects to it");
        assertEquals(a.agentStarts.get(0).generation(), a.agentServes.get(0).generation());
        assertEquals("SPAWNING", state(d).phase(), "nothing refused REST being off: " + state(d).reason());

        // stopping releases the listener for that generation (a hosted-only one then stops)
        d.post(new SessionEvents.LocalAgentStopRequested("Stop"));
        assertEquals(List.of("serveBridge", "start", "stop:KILL", "releaseBridge"), a.agentOrder);
        assertEquals(1, a.agentReleases.get(0).generation());

        // a listener that could not be started refuses the spawn by name and stops it
        FakeSessionAdapter b = new FakeSessionAdapter();
        b.bridgeListening = false;
        SessionDriver e = driver(b);
        e.post(new SessionEvents.LocalAgentStartRequested(1, consent()));
        assertEquals("BRIDGE_UNAVAILABLE", state(e).verdict());
        assertEquals("OFF", state(e).phase());
        assertEquals(List.of(SessionEffects.StopHow.KILL), b.agentStops.stream().map(SessionEffects.StopLocalAgentEffect::how)
                .toList());
    }

    /**
     * ★ Review M8. Witness: in {@code onFolderAnswered}, start without comparing the asked posture with the answered
     * one — the moved folder starts a process and the named assertion fails; or skip the trust-record effect.
     */
    @Test
    @DisplayName("★ M8: the folder answer is a fact — the node requests the trust record and the start; stale, no and moved answers start nothing")
    void theFolderAnswerIsDecidedByTheNode() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        d.post(new SessionEvents.LocalAgentStartRequested(5, consent(true, false, false, "p", BUDGET)));
        assertEquals(1, a.agentConfirms.size());
        assertEquals(0, a.agentStarts.size());
        // an answer to a question the node did not ask is refused
        d.post(new SessionEvents.LocalAgentFolderAnswered(99, true, consent(true, false, false, "p", BUDGET)));
        assertEquals(0, a.agentStarts.size(), "a stale answer starts nothing");
        assertEquals(0, a.agentTrusts.size());
        // yes: the trust record, then the listener and the process
        d.post(new SessionEvents.LocalAgentFolderAnswered(5, true, consent(true, false, false, "p", BUDGET)));
        assertEquals(List.of("confirmFolder", "trustFolder", "serveBridge", "start"), a.agentOrder);
        assertEquals(5, a.agentTrusts.get(0).request());
        assertEquals("p", a.agentStarts.get(0).postureRef());
        // the same answer again is stale: the question is closed
        d.post(new SessionEvents.LocalAgentFolderAnswered(5, true, consent(true, false, false, "p", BUDGET)));
        assertEquals(1, a.agentTrusts.size());

        // no: nothing is recorded or started, and the tab says why
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = driver(b);
        e.post(new SessionEvents.LocalAgentStartRequested(6, consent(true, false, false, "p", BUDGET)));
        e.post(new SessionEvents.LocalAgentFolderAnswered(6, false, consent(true, false, false, "p", BUDGET)));
        assertEquals("FOLDER_NOT_CONFIRMED", state(e).verdict());
        assertEquals(0, b.agentStarts.size());
        assertEquals(0, b.agentTrusts.size());

        // yes, but the folder moved while the dialog was open (a project was opened): nothing is trusted or started
        FakeSessionAdapter c = new FakeSessionAdapter();
        SessionDriver f = driver(c);
        f.post(new SessionEvents.LocalAgentStartRequested(7, consent(true, false, false, "folder-a", BUDGET)));
        f.post(new SessionEvents.LocalAgentFolderAnswered(7, true, consent(true, false, false, "folder-b", BUDGET)));
        assertEquals("FOLDER_CHANGED", state(f).verdict());
        assertEquals(0, c.agentStarts.size(), "a folder nobody was asked about is never started in");
        assertEquals(0, c.agentTrusts.size(), "nor recorded as trusted");

        // yes, but the switch went off meanwhile: the fresh observation is what counts
        FakeSessionAdapter g = new FakeSessionAdapter();
        SessionDriver h = driver(g);
        h.post(new SessionEvents.LocalAgentStartRequested(8, consent(true, false, false, "p", BUDGET)));
        h.post(new SessionEvents.LocalAgentFolderAnswered(8, true, consent(false, false, false, "p", BUDGET)));
        assertEquals("SWITCHED_OFF", state(h).verdict());
        assertEquals(0, g.agentStarts.size());
    }

    /**
     * ★ Review M2. Witness: drop the open-turn refusal in {@code onModeChosen} (kill as before) — the turn is killed and
     * the mode changes; the named assertions fail.
     */
    @Test
    @DisplayName("★ M2: choosing the in-app assistant while Claude is answering is refused by name; the turn and the mode stay")
    void leavingClaudeCodeMidTurnIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        var start = startToReady(d, a);
        var t = send(d, a, 10);
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.IN_APP, "assistant tab"));
        assertEquals("MODE_SWITCH_REFUSED", state(d).verdict());
        assertEquals(LocalAgentSession.MODE_SWITCH_REFUSED_TEXT, state(d).reason());
        assertEquals(AssistantMode.LOCAL_AGENT, state(d).mode(), "the mode is kept");
        assertEquals(0, kills(a).size(), "the turn in progress is not killed");
        assertEquals(t.ticket(), state(d).openTicket());

        // the turn ends: now the switch applies, and the idle process is stopped
        d.post(new SessionEvents.LocalAgentTurnEnded(start.generation(), t.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.IN_APP, "assistant tab"));
        assertEquals(AssistantMode.IN_APP, state(d).mode());
        assertEquals(1, kills(a).size(), "leaving an idle Claude Code stops its process");
        assertEquals("OFF", state(d).phase());
    }

    /**
     * ★ Review M6. Witness: answer the admission from {@code bound} alone (ignore {@code postureChecked}), or read a
     * stale value — the call ordered after the TOOLS_MISMATCH kill is admitted and the named assertion fails.
     */
    @Test
    @DisplayName("★ M6: a hosted call is admitted on the session's thread, in its order — after a posture kill it is refused")
    void hostedAdmissionFollowsTheSessionsOrder() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        var start = startToReady(d, a);
        var t = send(d, a, 10);
        d.post(new SessionEvents.LocalAgentCallAdmissionRequested(1, start.generation()));
        assertNotNull(a.agentAnswers.get(0).refusal(), "bound but the posture not yet checked: refused");
        d.post(goodInit(start, t.ticket()));
        d.post(new SessionEvents.LocalAgentCallAdmissionRequested(2, start.generation()));
        assertNull(a.agentAnswers.get(1).refusal(), "bound and checked: admitted");
        d.post(new SessionEvents.LocalAgentCallAdmissionRequested(3, start.generation() + 1));
        assertNotNull(a.agentAnswers.get(2).refusal(), "another generation: refused");
        List<String> tools = expectedTools();
        tools.add(LocalAgentPosture.toolName("report"));
        d.post(new SessionEvents.LocalAgentInitObserved(start.generation(), t.ticket(),
                LocalAgentPosture.digest(start.sessionId()), "dontAsk", tools,
                List.of(LocalAgentPosture.HOSTED_SERVER + ":connected"), LocalAgentPosture.REQUIRED_CAPABILITIES, 0));
        d.post(new SessionEvents.LocalAgentCallAdmissionRequested(4, start.generation()));
        assertNotNull(a.agentAnswers.get(3).refusal(), "after the TOOLS_MISMATCH kill, nothing is admitted");
        assertEquals(List.of(1L, 2L, 3L, 4L), a.agentAnswers.stream().map(SessionEffects.AnswerHostedCallEffect::call).toList());
    }

    /**
     * ★ Review M6, raced for real. An HTTP-thread call waits in {@code HostedAdmission} while the session's single thread
     * processes a TOOLS_MISMATCH kill. Whatever the interleaving, the verdict agrees with the session's own order:
     * admitted only when the admission was processed before the kill. Witness: answer from a value read on the calling
     * thread before posting (the snapshot) — some admitted calls are ordered after the kill and the assertion fails.
     */
    @Test
    @DisplayName("★ M6: a TOOLS_MISMATCH kill racing an admitted call — the verdict always matches the session's order")
    void aPostureKillRacingAHostedCall() throws Exception {
        for (int round = 0; round < 40; round++) {
            java.util.concurrent.ExecutorService sessionThread = java.util.concurrent.Executors.newSingleThreadExecutor();
            try {
                FakeSessionAdapter a = new FakeSessionAdapter();
                List<String> order = java.util.Collections.synchronizedList(new ArrayList<>());
                SessionDriver d = sessionThread.submit(() -> driver(a)).get();
                var startRef = new java.util.concurrent.atomic.AtomicReference<SessionEffects.StartLocalAgentEffect>();
                sessionThread.submit(() -> {
                    var start = startToReady(d, a);
                    var t = send(d, a, 10);
                    d.post(goodInit(start, t.ticket()));
                    startRef.set(start);
                }).get();
                var start = startRef.get();
                telamin.fluxtion.audit.analyser.analyser.assistant.localagent.HostedAdmission admission =
                        new telamin.fluxtion.audit.analyser.analyser.assistant.localagent.HostedAdmission(
                                fact -> sessionThread.execute(() -> {
                                    order.add("admission");
                                    d.post(fact);
                                }), 5_000);
                a.onAnswer = admission::answer;
                List<String> tools = expectedTools();
                tools.add(LocalAgentPosture.toolName("report"));
                var kill = new SessionEvents.LocalAgentInitObserved(start.generation(), 1,
                        LocalAgentPosture.digest(start.sessionId()), "dontAsk", tools,
                        List.of(LocalAgentPosture.HOSTED_SERVER + ":connected"), LocalAgentPosture.REQUIRED_CAPABILITIES, 0);
                var call = java.util.concurrent.CompletableFuture.supplyAsync(() -> admission.admit(start.generation()));
                if (round % 2 == 0) Thread.onSpinWait();
                sessionThread.execute(() -> {
                    order.add("kill");
                    d.post(kill);
                });
                String verdict = call.get(10, java.util.concurrent.TimeUnit.SECONDS);
                sessionThread.submit(() -> { }).get();
                boolean admittedFirst = order.indexOf("admission") < order.indexOf("kill");
                assertEquals(admittedFirst, verdict == null, "round " + round + ": order " + order + ", verdict " + verdict);
                assertEquals("TOOLS_MISMATCH", sessionThread.submit(() -> state(d).verdict()).get());
            } finally {
                sessionThread.shutdownNow();
            }
        }
    }
}
