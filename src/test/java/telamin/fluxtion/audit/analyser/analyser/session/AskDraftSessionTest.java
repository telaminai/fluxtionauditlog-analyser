package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P2 (spec §5.5, §8.2, §10) — a draft from the Ask catalogue on the REAL generated processor: it is placed and never
 * sent; Start is armed only when it would be accepted; a bound draft whose log moved is refused at Send in BOTH
 * assistants, in their own nodes; and a composed draft never carries the live selection's record text.
 *
 * <p>Witnesses (each run by breaking the node as the comment says and seeing the named assertion fail) are recorded in
 * the spec's implementation notes (P2).
 */
class AskDraftSessionTest {

    static final String LOG = "/logs/demo.yaml";

    static SessionDriver localAgent(FakeSessionAdapter a) {
        return LocalAgentSessionTest.driver(a);
    }

    /** Open {@code path} as a fresh generation. */
    static long open(SessionDriver d, String path) {
        d.submit(new SessionEvents.OpenLogRequested(d.nextOpId(), path, null, "DECLARED", false));
        return d.snapshot().logGeneration();
    }

    static String identity(String path) {
        return LocalAgentPosture.digest(path);
    }

    // ---- ★ draft-not-send, both modes --------------------------------------------------------------------------------

    /** ★ Witness: request a SendLocalAgentTurnEffect (or a Start) from onDraftComposed — the zero-sends assertion fails. */
    @Test
    @DisplayName("★ Claude Code: a composed draft is placed and armed, never sent and never started")
    void aDraftIsPlacedNotSent() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = localAgent(a);
        long gen = open(d, LOG);
        d.post(new SessionEvents.LocalAgentDraftComposed(11, "Q1", identity(LOG), gen, LocalAgentSessionTest.consent(),
                "test"));
        assertEquals(1, a.agentPrimes.size(), "the draft is placed");
        assertEquals(11, a.agentPrimes.get(0).draftRef());
        assertEquals("armed.ASK", a.agentPrimes.get(0).armedWhy(), "Start is armed: nothing runs");
        assertEquals("armed.ASK", d.snapshot().localAgent().armedWhy());
        assertEquals(0, a.agentStarts.size(), "an armed Start starts nothing (OD-74.1)");
        assertEquals(0, a.agentSends.size(), "nothing is written to stdin before Send");

        // pressing the armed Start is the person's Start; the draft stays placed, still not sent
        d.post(new SessionEvents.LocalAgentStartRequested(1, LocalAgentSessionTest.consent(), false));
        assertEquals(1, a.agentStarts.size());
        assertNull(d.snapshot().localAgent().armedWhy(), "a Start disarms");
        assertEquals(0, a.agentSends.size());
    }

    /** ★ Witness: arm whatever the consent says — the REST-off row is armed and its assertion fails. */
    @Test
    @DisplayName("★ Start is armed only when it would be accepted; otherwise the refusal is shown with the draft placed")
    void armedOnlyWhenAccepted() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = localAgent(a);
        var restOff = LocalAgentSessionTest.consent(true, true, false, false, "p", 0);
        d.post(new SessionEvents.LocalAgentDraftComposed(5, "S6", null, 0, restOff, "start page"));
        assertEquals(1, a.agentPrimes.size(), "the draft is still placed");
        assertNull(a.agentPrimes.get(0).armedWhy(), "Start would be refused, so it is not armed");
        assertEquals("REST_OFF", d.snapshot().localAgent().verdict(), "the refusal names its action (Turn on REST)");
        var off = LocalAgentSessionTest.consent(false, true, false, true, "p", 0);
        d.post(new SessionEvents.LocalAgentDraftComposed(6, "S6", null, 0, off, "start page"));
        assertNull(a.agentPrimes.get(1).armedWhy());
        assertEquals("SWITCHED_OFF", d.snapshot().localAgent().verdict());
        // an unconfirmed folder is armable: pressing Start asks the folder question, Start's own path
        var unconfirmed = LocalAgentSessionTest.consent(true, false, false, true, "p", 0);
        d.post(new SessionEvents.LocalAgentDraftComposed(7, "JOURNEY", null, 0, unconfirmed, "journey"));
        assertEquals("armed.JOURNEY", a.agentPrimes.get(2).armedWhy());
        assertEquals(0, a.agentStarts.size());
        assertEquals(0, a.agentConfirms.size(), "arming asks nothing");
    }

    @Test
    @DisplayName("New chat and a mode switch replace an armed Start; another armed flow replaces it")
    void armedIsReplaced() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = localAgent(a);
        d.post(new SessionEvents.LocalAgentDraftComposed(5, "Q2", null, 0, LocalAgentSessionTest.consent(), "t"));
        assertEquals("armed.ASK", d.snapshot().localAgent().armedWhy());
        d.post(new SessionEvents.LocalAgentDraftComposed(6, "JOURNEY", null, 0, LocalAgentSessionTest.consent(), "t"));
        assertEquals("armed.JOURNEY", d.snapshot().localAgent().armedWhy(), "another armed flow replaces it");
        d.post(new SessionEvents.LocalAgentNewChatRequested("t"));
        assertNull(d.snapshot().localAgent().armedWhy());
        d.post(new SessionEvents.LocalAgentDraftComposed(7, "Q1", null, 0, LocalAgentSessionTest.consent(), "t"));
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.IN_APP, "t"));
        assertNull(d.snapshot().localAgent().armedWhy());
    }

    @Test
    @DisplayName("a draft composed for one assistant is ignored by the other, in its own node")
    void oneModeOneComposer() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver inApp = new SessionDriver(a);            // in-app is the default
        inApp.post(new SessionEvents.LocalAgentDraftComposed(5, "Q1", null, 0, LocalAgentSessionTest.consent(), "t"));
        assertEquals(0, a.agentPrimes.size(), "localAgentSession ignores it under IN_APP");
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver cc = localAgent(b);
        cc.post(new SessionEvents.AssistantDraftComposed(5, "Q1", null, 0, "t"));
        assertEquals(0, b.assistantPrimes.size(), "assistantLoop ignores it under LOCAL_AGENT");
    }

    // ---- ★ the binding, both modes ------------------------------------------------------------------------------------

    /**
     * ★ Witness: compare the log identity only (not the generation) in {@code LocalAgentSession.staleDraft} — the
     * reloaded-log Send goes through and the refusal assertion fails.
     */
    @Test
    @DisplayName("★ Claude Code: a bound draft is refused at Send after a reload; an unbound or deleted draft is sent")
    void claudeCodeRefusesAMovedDraft() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = localAgent(a);
        long gen = open(d, LOG);
        var start = LocalAgentSessionTest.startToReady(d, a);
        d.post(new SessionEvents.LocalAgentDraftComposed(21, "Q2", identity(LOG), gen, LocalAgentSessionTest.consent(), "t"));
        long reloaded = open(d, LOG);
        assertTrue(reloaded > gen, "the reload is a new generation");
        d.post(new SessionEvents.LocalAgentSendRequested(9, 30, SessionEvents.SlashClass.TEXT, null,
                LocalAgentSessionTest.consent(), 21));
        assertEquals(0, a.agentSends.size(), "a stale draft is never written to stdin");
        LocalAgentState s = d.snapshot().localAgent();
        assertEquals("STALE_DRAFT", s.verdict());
        assertTrue(s.reason().contains("(generation " + gen + " → " + reloaded + ")"), s.reason());
        assertFalse(s.answer().accepted());

        // the person deleted the whole draft: the panel reports no binding, and the words are sent
        d.post(new SessionEvents.LocalAgentSendRequested(10, 31, SessionEvents.SlashClass.TEXT, null,
                LocalAgentSessionTest.consent(), 0));
        assertEquals(1, a.agentSends.size(), "an unbound draft is sent");
        assertEquals(start.generation(), a.agentSends.get(0).generation());
    }

    @Test
    @DisplayName("Claude Code: a bound draft on the SAME generation is sent; a draft for another log is refused")
    void sameGenerationIsSent() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = localAgent(a);
        long gen = open(d, LOG);
        LocalAgentSessionTest.startToReady(d, a);
        d.post(new SessionEvents.LocalAgentDraftComposed(21, "Q1", identity(LOG), gen, LocalAgentSessionTest.consent(), "t"));
        d.post(new SessionEvents.LocalAgentSendRequested(9, 30, SessionEvents.SlashClass.TEXT, null,
                LocalAgentSessionTest.consent(), 21));
        assertEquals(1, a.agentSends.size(), "the log has not moved: sent");
        var ticket = a.agentSends.get(0);
        d.post(new SessionEvents.LocalAgentTurnEnded(ticket.generation(), ticket.ticket(), SessionEvents.TurnOutcome.COMPLETED));

        d.post(new SessionEvents.LocalAgentDraftComposed(22, "Q1", identity("/logs/other.yaml"), gen,
                LocalAgentSessionTest.consent(), "t"));
        d.post(new SessionEvents.LocalAgentSendRequested(11, 32, SessionEvents.SlashClass.TEXT, null,
                LocalAgentSessionTest.consent(), 22));
        assertEquals(1, a.agentSends.size(), "another log's draft is refused");
        assertEquals("STALE_DRAFT", d.snapshot().localAgent().verdict());
    }

    /** ★ Witness: drop the {@code staleDraft} call in {@code AssistantLoop.refusal} — the in-app route sends. */
    @Test
    @DisplayName("★ in-app: the same refusal, in assistantLoop's own handler, with the same words")
    void inAppRefusesAMovedDraft() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        long gen = open(d, LOG);
        d.post(new SessionEvents.AssistantDraftComposed(41, "Q1", identity(LOG), gen, "t"));
        assertEquals(1, a.assistantPrimes.size(), "placed");
        assertEquals(0, a.assistantContexts.size(), "and nothing is prepared, let alone sent, before Send");
        long reloaded = open(d, LOG);
        d.post(new SessionEvents.AssistantSendRequested(3, 50, AssistantLoopTest.route(true, 3, 20), 41));
        assertEquals(0, a.assistantContexts.size(), "a stale draft never reaches the provider");
        AssistantState s = d.snapshot().assistant();
        assertFalse(s.answer().accepted());
        assertTrue(s.answer().reason().contains("(generation " + gen + " → " + reloaded + ")"), s.answer().reason());
        assertTrue(s.answer().reason().startsWith("This question names records of a log that has changed"));
    }

    /**
     * ★ r8 F4. Witness: compute {@code includeRecordContext} as {@code history.isEmpty()} alone (the P1 code) — the
     * composed draft's request then asks for the live selection's record context and the assertion fails.
     */
    @Test
    @DisplayName("★ in-app: a composed draft carries no record context; a typed first question still does")
    void aComposedDraftCarriesNoRecordContext() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        long gen = open(d, LOG);
        d.post(new SessionEvents.AssistantDraftComposed(41, "Q2", identity(LOG), gen, "t"));
        d.post(new SessionEvents.AssistantSendRequested(3, 50, AssistantLoopTest.route(true, 3, 20), 41));
        assertEquals(1, a.assistantContexts.size());
        assertFalse(a.assistantContexts.get(0).includeRecordContext(),
                "the draft names the records to read; the live selection's text is never attached");

        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver typed = new SessionDriver(b);
        open(typed, LOG);
        typed.post(new SessionEvents.AssistantSendRequested(3, 50, AssistantLoopTest.route(true, 3, 20)));
        assertTrue(b.assistantContexts.get(0).includeRecordContext(), "an unbound typed question keeps today's behaviour");
    }

    @Test
    @DisplayName("no draft text, record text or path reaches the session audit record")
    void theRecordHoldsNoWords() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = localAgent(a);
        long gen = open(d, LOG);
        d.post(new SessionEvents.LocalAgentDraftComposed(21, "Q1", identity(LOG), gen, LocalAgentSessionTest.consent(), "t"));
        String record = String.join("\n", d.auditSink().records());
        assertTrue(record.contains("LocalAgentDraftComposed"), "the fact is recorded");
        assertFalse(record.contains("What happened"), "a draft's words are never in a fact");
        List<String> facts = d.auditSink().records().stream().filter(r -> r.contains("LocalAgentDraftComposed")).toList();
        assertFalse(facts.isEmpty());
        assertTrue(facts.stream().noneMatch(r -> r.contains(LOG)), "the log is named by digest, never its path");
    }
}
