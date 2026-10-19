package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner, 2026-10-18: each turn of the conversation shows its time. The time is the SESSION's (rule 9): the adapter reports
 * when it observed a turn as a fact, the node records it on the entry, and the panel renders the snapshot — it never reads
 * a clock of its own. On the real generated processor, for Claude Code and for the in-app assistant.
 *
 * <p>Witnesses (run 2026-10-18): drop the stamp in {@code LocalAgentSession.onTurnStarted} — the person's turn has no time
 * and the first test fails; take {@code e.atMillis()} off the new ANSWER entry in {@code onTextUpdated} — the answer's
 * does; drop the stamp in {@code AssistantLoop.onContextPrepared} — the second test fails.
 */
class TurnTimesTest {

    static final long SENT = 1_760_788_920_000L;     // 2025-10-18T12:02:00Z
    static final long FIRST_TEXT = SENT + 4_000;

    @Test
    @DisplayName("★ Claude Code: the person's turn has the time it started, the answer the time of its first text, kept to the end")
    void claudeCodeTurnsCarryTheirTimes() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = LocalAgentSessionTest.startToReady(d, a);
        d.post(new SessionEvents.LocalAgentSendRequested(10, 10, SessionEvents.SlashClass.TEXT, null,
                LocalAgentSessionTest.consent()));
        var sent = a.agentSends.get(a.agentSends.size() - 1);
        d.post(new SessionEvents.LocalAgentTurnStarted(sent.generation(), sent.ticket(), SENT));
        d.post(LocalAgentSessionTest.goodInit(start, sent.ticket()));
        d.post(new SessionEvents.LocalAgentTextUpdated(sent.generation(), sent.ticket(), 20, 1, FIRST_TEXT));
        d.post(new SessionEvents.LocalAgentTextUpdated(sent.generation(), sent.ticket(), 20, 2, FIRST_TEXT + 9_000));
        d.post(new SessionEvents.LocalAgentTurnEnded(sent.generation(), sent.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        List<LocalAgentState.Entry> entries = d.snapshot().localAgent().entries();
        LocalAgentState.Entry you = entries.stream().filter(e -> LocalAgentState.USER.equals(e.kind())).findFirst().orElseThrow();
        LocalAgentState.Entry answer = entries.stream().filter(e -> LocalAgentState.ANSWER.equals(e.kind())).findFirst()
                .orElseThrow();
        assertEquals(SENT, you.atMillis(), "the person's turn: when it started");
        assertEquals(FIRST_TEXT, answer.atMillis(), "the answer: its first text, not a later revision");
        assertEquals("COMPLETED", answer.status(), "…and the time survives the turn's end");
    }

    @Test
    @DisplayName("★ in-app assistant: the question has the time it was prepared, the answer the time it arrived")
    void inAppTurnsCarryTheirTimes() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AssistantSendRequested(1, 1, AssistantLoopTest.route(true, 3, 20)));
        var ctx = a.assistantContexts.get(a.assistantContexts.size() - 1);
        d.post(new SessionEvents.AssistantContextPrepared(ctx.ticket(), 2, SENT));
        var request = a.assistantRequests.get(a.assistantRequests.size() - 1);
        d.post(new SessionEvents.AssistantCompletionReceived(request.ticket(), 1, 3, List.of(), FIRST_TEXT));
        List<AssistantState.Entry> entries = d.snapshot().assistant().visibleEntries();
        assertEquals(SENT, entries.stream().filter(e -> AssistantState.USER.equals(e.kind())).findFirst().orElseThrow()
                .atMillis());
        assertEquals(FIRST_TEXT, entries.stream().filter(e -> AssistantState.ANSWER.equals(e.kind())).findFirst()
                .orElseThrow().atMillis());
    }
}
