package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OA-1 (spec-onboard-assistant-journeys.md §4–§5) — the onboard assistant's decisions on the REAL generated processor.
 * Facts go in; what {@code assistantLoop} asks the adapter to do, and what the snapshot publishes, come out. There is no
 * provider and no frame here: those are {@code AssistantAdapterTest} and the frame suites.
 */
class AssistantLoopTest {

    static SessionEvents.AssistantRoute route(boolean key, int rounds, int perReply) {
        return route(key, rounds, perReply, 100);
    }

    static SessionEvents.AssistantRoute route(boolean key, int rounds, int perReply, int perTurn) {
        return new SessionEvents.AssistantRoute("anthropic", "", "", key, true, rounds, perReply, perTurn);
    }

    private static AssistantState state(SessionDriver d) {
        return d.snapshot().assistant();
    }

    /** Send entry {@code draft} and answer its context at once; returns the request of round 1. */
    private static SessionEffects.RequestAssistantCompletionEffect sendAndPrepare(SessionDriver d, FakeSessionAdapter a,
                                                                                 long draft, long prompt) {
        d.post(new SessionEvents.AssistantSendRequested(draft, draft, route(true, 3, 20)));
        var ctx = a.assistantContexts.get(a.assistantContexts.size() - 1);
        d.post(new SessionEvents.AssistantContextPrepared(ctx.ticket(), prompt));
        return a.assistantRequests.get(a.assistantRequests.size() - 1);
    }

    @Test
    @DisplayName("OA-A3: with no provider key, Send is refused by name and nothing is prepared or requested")
    void noKeyNeverSends() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AssistantSendRequested(7, 1, route(false, 3, 20)));
        assertEquals(0, a.assistantContexts.size(), "no context was assembled");
        assertEquals(0, a.assistantRequests.size(), "no request was made");
        assertFalse(state(d).answer().accepted());
        assertTrue(state(d).answer().reason().startsWith("no provider is configured"), state(d).answer().reason());
        assertEquals("IDLE", state(d).phase());
    }

    @Test
    @DisplayName("one Send is one turn: the context is prepared, then exactly one round-1 request is made")
    void oneSendIsOneRequest() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        var request = sendAndPrepare(d, a, 1, 2);
        assertEquals(1, a.assistantContexts.size());
        assertTrue(a.assistantContexts.get(0).includeManifest(), "actions are on: the manifest rides on the first turn");
        assertTrue(a.assistantContexts.get(0).includeRecordContext(), "the first question carries the record context");
        assertEquals(1, a.assistantRequests.size(), "exactly one provider request");
        assertEquals(1, request.round());
        assertEquals(List.of(new SessionEffects.HistoryMessage("user", "TEXT", List.of(2L))), request.history());
        assertEquals("REQUESTING", state(d).phase());
        assertTrue(state(d).answer().accepted());
    }

    @Test
    @DisplayName("a reply without actions completes the turn, and a second Send is a follow-up in the same conversation")
    void aFollowUpContinuesTheConversation() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        var r1 = sendAndPrepare(d, a, 1, 2);
        d.post(new SessionEvents.AssistantCompletionReceived(r1.ticket(), 1, 3, List.of()));
        assertEquals("COMPLETE", state(d).phase());
        var r2 = sendAndPrepare(d, a, 4, 5);
        assertFalse(a.assistantContexts.get(1).includeManifest(), "the manifest is sent once per conversation");
        assertFalse(a.assistantContexts.get(1).includeRecordContext(), "record context rides on the first question only");
        assertEquals(List.of(
                new SessionEffects.HistoryMessage("user", "TEXT", List.of(2L)),
                new SessionEffects.HistoryMessage("assistant", "TEXT", List.of(3L)),
                new SessionEffects.HistoryMessage("user", "TEXT", List.of(5L))), r2.history());
        assertEquals(state(d).conversation(), 1L, "the same conversation");
    }

    @Test
    @DisplayName("OA-A2: actions run one at a time, and EVERY result — a render success too — is fed back next round")
    void everyResultIsFedBack() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        a.assistantVerbs.put(10L, "aggregate");
        a.assistantVerbs.put(11L, "spotlight");
        SessionDriver d = new SessionDriver(a);
        var r1 = sendAndPrepare(d, a, 1, 2);
        d.post(new SessionEvents.AssistantCompletionReceived(r1.ticket(), 1, 3, List.of(10L, 11L)));
        assertEquals(1, a.assistantActions.size(), "one action at a time");
        assertEquals(10L, a.assistantActions.get(0).action());
        assertEquals("RUNNING_ACTION", state(d).phase());
        assertEquals("aggregate", state(d).runningVerb(), "the adapter named the verb it is running");
        d.post(new SessionEvents.AssistantActionFinished(r1.ticket(), 10, "aggregate", true, 20));
        assertEquals(2, a.assistantActions.size(), "the second action is asked for only after the first answered");
        d.post(new SessionEvents.AssistantActionFinished(r1.ticket(), 11, "spotlight", true, 21));
        assertEquals(2, a.assistantRequests.size(), "a second round was requested");
        var r2 = a.assistantRequests.get(1);
        assertEquals(new SessionEffects.HistoryMessage("user", "RESULTS", List.of(20L, 21L)),
                r2.history().get(r2.history().size() - 1),
                "the query result AND the render success are both sent back — the actual results, by id");
        var actions = state(d).entries().stream().filter(e -> AssistantState.ACTION.equals(e.kind())).toList();
        assertEquals(List.of("OK", "OK"), actions.stream().map(AssistantState.Entry::status).toList());
        assertEquals(List.of(20L, 21L), actions.stream().map(AssistantState.Entry::result).toList());
    }

    @Test
    @DisplayName("OA-A4: after Cancel a late reply is refused — no action, no text, no busy state — and a new Send works")
    void aLateReplyAfterCancelDoesNothing() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        var r1 = sendAndPrepare(d, a, 1, 2);
        d.post(new SessionEvents.AssistantCancelRequested("test"));
        assertEquals("CANCELLED", state(d).phase());
        assertEquals(List.of(r1.ticket()), a.assistantCancels.stream().map(SessionEffects.CancelAssistantTransportEffect::ticket).toList(),
                "the transport is asked to stop the cancelled ticket");
        int entries = state(d).entries().size();
        d.post(new SessionEvents.AssistantCompletionReceived(r1.ticket(), 1, 3, List.of(10L)));
        assertEquals(0, a.assistantActions.size(), "a late reply's action never runs");
        assertEquals(entries, state(d).entries().size(), "a late reply appends nothing");
        assertEquals("CANCELLED", state(d).phase(), "a late reply does not revive the busy state");
        sendAndPrepare(d, a, 4, 5);
        assertEquals("REQUESTING", state(d).phase(), "a fresh Send after the cancel succeeds");
        assertEquals(List.of(new SessionEffects.HistoryMessage("user", "TEXT", List.of(5L))),
                a.assistantRequests.get(a.assistantRequests.size() - 1).history(),
                "the cancelled, unanswered question is not left in the history");
    }

    @Test
    @DisplayName("OA-A4: a cancelled turn's late reply cannot answer the NEXT turn, which is waiting in the same phase")
    void aLateReplyCannotAnswerTheNextTurn() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        var old = sendAndPrepare(d, a, 1, 2);
        d.post(new SessionEvents.AssistantCancelRequested("test"));
        var next = sendAndPrepare(d, a, 4, 5);
        assertEquals("REQUESTING", state(d).phase());
        assertNotEquals(old.ticket(), next.ticket());
        d.post(new SessionEvents.AssistantCompletionReceived(old.ticket(), 1, 3, List.of(10L)));
        assertEquals(0, a.assistantActions.size(), "the old turn's reply ran no action in the new turn");
        assertTrue(state(d).entries().stream().noneMatch(e -> e.id() == 3L), "the old reply is not shown as the new answer");
        assertEquals("REQUESTING", state(d).phase(), "the new turn is still waiting for ITS reply");
    }

    @Test
    @DisplayName("OA-A4: New chat during an action refuses its late result and starts an empty conversation")
    void newChatRefusesALateActionResult() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        var r1 = sendAndPrepare(d, a, 1, 2);
        d.post(new SessionEvents.AssistantCompletionReceived(r1.ticket(), 1, 3, List.of(10L, 11L)));
        long before = state(d).conversation();
        d.post(new SessionEvents.AssistantNewChatRequested(""));
        assertEquals(before + 1, state(d).conversation());
        assertEquals(List.of(), state(d).entries());
        d.post(new SessionEvents.AssistantActionFinished(r1.ticket(), 10, "aggregate", true, 20));
        assertEquals(1, a.assistantActions.size(), "the second action of the old turn never runs");
        assertEquals(1, a.assistantRequests.size(), "no further round of the old turn is requested");
        assertEquals(List.of(), state(d).entries(), "the new conversation stays empty");
    }

    @Test
    @DisplayName("OA-A5: a log opened by someone else while a reply is held supersedes the turn and freezes the thread")
    void aWorkspaceChangeSupersedesTheTurn() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        SessionFixtures.openLog(d, a, "/logs/one.yaml");
        var r1 = sendAndPrepare(d, a, 1, 2);
        SessionFixtures.openLog(d, a, "/logs/two.yaml");
        assertEquals("SUPERSEDED", state(d).phase());
        assertTrue(state(d).frozen());
        d.post(new SessionEvents.AssistantCompletionReceived(r1.ticket(), 1, 3, List.of(10L)));
        assertEquals(0, a.assistantActions.size(), "the old reply's action cannot operate on the new log");
        d.post(new SessionEvents.AssistantSendRequested(9, 4, route(true, 3, 20)));
        assertFalse(state(d).answer().accepted(), "the frozen thread refuses Send until a new chat");
        d.post(new SessionEvents.AssistantNewChatRequested(""));
        sendAndPrepare(d, a, 5, 6);
        assertEquals("REQUESTING", state(d).phase());
        assertTrue(state(d).basis().contains("two.yaml"), state(d).basis());
    }

    @Test
    @DisplayName("OA-A5: the turn's OWN open action changes the workspace without superseding itself")
    void theTurnsOwnOpenContinues() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        a.assistantVerbs.put(10L, "open");
        SessionDriver d = new SessionDriver(a);
        var r1 = sendAndPrepare(d, a, 1, 2);
        d.post(new SessionEvents.AssistantCompletionReceived(r1.ticket(), 1, 3, List.of(10L)));
        SessionFixtures.openLog(d, a, "/logs/opened-by-the-action.yaml");
        assertEquals("RUNNING_ACTION", state(d).phase(), "its own open does not end the turn");
        d.post(new SessionEvents.AssistantActionFinished(r1.ticket(), 10, "open", true, 20));
        assertEquals(2, a.assistantRequests.size(), "the turn goes on to its next round");
        assertFalse(state(d).frozen());
        assertTrue(state(d).basis().contains("opened-by-the-action.yaml"), state(d).basis());
    }

    @Test
    @DisplayName("OA-A16: the per-reply cap stops extra actions, visibly")
    void thePerReplyCapIsEnforced() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AssistantSendRequested(1, 1, route(true, 3, 2)));
        d.post(new SessionEvents.AssistantContextPrepared(a.assistantContexts.get(0).ticket(), 2));
        long t = a.assistantRequests.get(0).ticket();
        d.post(new SessionEvents.AssistantCompletionReceived(t, 1, 3, List.of(10L, 11L, 12L)));
        d.post(new SessionEvents.AssistantActionFinished(t, 10, "context", true, 20));
        d.post(new SessionEvents.AssistantActionFinished(t, 11, "context", true, 21));
        assertEquals(2, a.assistantActions.size(), "only the cap's worth ran");
        var notRun = state(d).entries().stream().filter(e -> "NOT_RUN".equals(e.status())).toList();
        assertEquals(1, notRun.size());
        assertTrue(notRun.get(0).detail().contains("per-reply action cap (2)"), notRun.get(0).detail());
    }

    @Test
    @DisplayName("OA-A16: the round limit ends the turn with a stated limit, and no further request")
    void theRoundLimitEndsTheTurn() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AssistantSendRequested(1, 1, route(true, 1, 20)));
        d.post(new SessionEvents.AssistantContextPrepared(a.assistantContexts.get(0).ticket(), 2));
        long t = a.assistantRequests.get(0).ticket();
        d.post(new SessionEvents.AssistantCompletionReceived(t, 1, 3, List.of(10L)));
        d.post(new SessionEvents.AssistantActionFinished(t, 10, "context", true, 20));
        assertEquals(1, a.assistantRequests.size(), "no second round");
        assertEquals("LIMIT_REACHED", state(d).phase());
        assertTrue(state(d).reason().contains("action-round limit (1)"), state(d).reason());
    }

    @Test
    @DisplayName("OA-A16: the per-turn budget binds on its own — below what the rounds and per-reply caps would allow")
    void thePerTurnBudgetEndsTheTurn() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        // rounds x per-reply would allow 5 x 3 = 15; the turn's own budget is 4
        d.post(new SessionEvents.AssistantSendRequested(1, 1, route(true, 5, 3, 4)));
        d.post(new SessionEvents.AssistantContextPrepared(a.assistantContexts.get(0).ticket(), 2));
        long t = a.assistantRequests.get(0).ticket();
        d.post(new SessionEvents.AssistantCompletionReceived(t, 1, 3, List.of(10L, 11L, 12L)));
        for (long x = 10; x <= 12; x++) d.post(new SessionEvents.AssistantActionFinished(t, x, "context", true, x + 10));
        d.post(new SessionEvents.AssistantCompletionReceived(t, 2, 4, List.of(13L, 14L, 15L)));
        d.post(new SessionEvents.AssistantActionFinished(t, 13, "context", true, 23));
        assertEquals(4, a.assistantActions.size(), "exactly the turn's budget ran");
        assertEquals(2, a.assistantRequests.size(), "no further round was requested");
        assertEquals("LIMIT_REACHED", state(d).phase());
        assertTrue(state(d).reason().contains("action budget (4"), state(d).reason());
        assertEquals(2, state(d).entries().stream().filter(e -> "NOT_RUN".equals(e.status())).count(),
                "the two actions beyond the budget are shown as not run");
    }

    @Test
    @DisplayName("OA-A16: a provider failure ends the turn visibly; the next Send succeeds")
    void aFailureIsTerminalAndRecoverable() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        var r1 = sendAndPrepare(d, a, 1, 2);
        d.post(new SessionEvents.AssistantCompletionFailed(r1.ticket(), 1, "HTTP 429: rate limited"));
        assertEquals("FAILED", state(d).phase());
        var last = state(d).entries().get(state(d).entries().size() - 1);
        assertEquals(AssistantState.NOTE, last.kind());
        assertTrue(last.detail().contains("HTTP 429"), last.detail());
        sendAndPrepare(d, a, 4, 5);
        assertEquals("REQUESTING", state(d).phase());
    }

    @Test
    @DisplayName("the snapshot holds ids and statuses, never words: nothing the person typed can reach the audit record")
    void noTextInTheSession() throws Exception {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        sendAndPrepare(d, a, 1, 2);
        for (var c : AssistantState.class.getRecordComponents()) {
            assertNotEquals(String.class, c.getType() == String.class && c.getName().matches("(?i).*(text|draft|key).*")
                    ? String.class : Void.class, c.getName() + " must not carry text");
        }
        for (var c : AssistantState.Entry.class.getRecordComponents()) {
            assertFalse(c.getName().matches("(?i)text|body|content"), "an entry names its text, it does not hold it");
        }
    }
}
