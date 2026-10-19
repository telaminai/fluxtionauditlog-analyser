package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static telamin.fluxtion.audit.analyser.analyser.session.LocalAgentSessionTest.*;

class LocalAgentActivityTest {
    @Test
    void streamProgressAgesSilenceSeparatelyFromAStillRunningTool() {
        var a = new FakeSessionAdapter();
        var d = driver(a);
        startToReady(d, a);
        send(d, a, 10);
        d.post(new SessionEvents.LocalAgentToolStarted(1, 1, 41, SessionEvents.ToolClass.BUILT_IN, "Read", 71, 1_000));
        d.post(new SessionEvents.LocalAgentToolStarted(1, 1, 42, SessionEvents.ToolClass.BUILT_IN, "Bash", 72, 2_000));
        d.post(new SessionEvents.LocalAgentProgressObserved(1, 1, 61_000));
        d.post(new SessionEvents.LocalAgentActivityTick(1, 1, 62_000));
        assertEquals(1, state(d).extra().guide().secondsSinceLastEvent());
        assertEquals("Running {step}… 1:00", state(d).extra().guide().currentStep(),
                "a real stream event resets silence, not the tool's own duration");
        assertFalse(state(d).extra().guide().stalled());
        d.post(new SessionEvents.LocalAgentToolFinished(1, 1, 42, false, 63_000));
        assertEquals("Reading {step}… 1:02", state(d).extra().guide().currentStep(),
                "finishing the latest parallel tool leaves the older outstanding one visible");
    }

    @Test
    void aToolStartIsVisibleBeforeItsResultAndIsCountedOnlyOnce() {
        var a = new FakeSessionAdapter();
        var d = driver(a);
        startToReady(d, a);
        send(d, a, 10);
        var start = new SessionEvents.LocalAgentToolStarted(1, 1, 41, SessionEvents.ToolClass.BUILT_IN,
                "Bash", 71, 1_000);
        d.post(start);
        assertTrue(state(d).extra().guide().currentStep().startsWith("Running "),
                "tool START publishes its step before tool FINISHED");
        assertEquals(71, state(d).extra().guide().stepWordsRef());
        d.post(start);
        assertEquals(1, state(d).toolCallCount(), "a replayed tool start is counted once");
        d.post(new SessionEvents.LocalAgentToolFinished(1, 1, 41, false, 2_000));
        assertEquals("Thinking…", state(d).extra().guide().currentStep());
        d.post(new SessionEvents.LocalAgentProgressObserved(1, 1, 2_100, true));
        d.post(new SessionEvents.LocalAgentTextUpdated(1, 1, 50, 1, 2_100));
        assertEquals("Answering…", state(d).extra().guide().currentStep());
    }

    @Test
    void aStallStartsAtSixtyNotFiftyNineAndKeepWaitingNeedsANewStreamEvent() {
        var a = new FakeSessionAdapter();
        var d = driver(a);
        startToReady(d, a);
        send(d, a, 10);
        d.post(new SessionEvents.LocalAgentProgressObserved(1, 1, 1_000));
        d.post(new SessionEvents.LocalAgentActivityTick(1, 1, 60_000));
        assertFalse(state(d).extra().guide().stalled(), "59 seconds is not stalled");
        d.post(new SessionEvents.LocalAgentActivityTick(1, 1, 61_000));
        assertTrue(state(d).extra().guide().stalled(), "60 seconds without a stream event raises the cue");
        assertEquals("No activity for 1:00 — still working, or stalled?", state(d).extra().guide().stallBanner());
        d.post(new SessionEvents.LocalAgentActivityAcknowledged(1, 1));
        d.post(new SessionEvents.LocalAgentActivityTick(1, 1, 241_000));
        assertFalse(state(d).extra().guide().stalled(), "Keep waiting never repeats on a timer in the same episode");
        assertEquals(240, state(d).extra().guide().secondsSinceLastEvent(), "elapsed time keeps counting");
        d.post(new SessionEvents.LocalAgentProgressObserved(1, 1, 242_000));
        d.post(new SessionEvents.LocalAgentActivityTick(1, 1, 301_000));
        assertFalse(state(d).extra().guide().stalled());
        d.post(new SessionEvents.LocalAgentActivityTick(1, 1, 302_000));
        assertTrue(state(d).extra().guide().stalled(), "a new stream event rearms after a further 60 seconds");
    }

    @Test
    void anApprovalSuppressesTheStallAndKeepsItsOwnPendingRequest() {
        var a = new FakeSessionAdapter();
        var d = driver(a);
        var start = LocalAgentPermissionTest.ready(d, a);
        d.post(new SessionEvents.LocalAgentProgressObserved(1, 1, 1_000));
        d.post(new SessionEvents.LocalAgentActivityTick(1, 1, 61_000));
        assertTrue(state(d).extra().guide().stalled(), "the ordinary silent turn would show a stall cue");
        d.post(LocalAgentPermissionTest.report(start.generation(), 1, 41));
        d.post(LocalAgentPermissionTest.report(start.generation(), 1, 42));
        d.post(new SessionEvents.LocalAgentActivityTick(1, 1, 241_000));
        assertTrue(state(d).extra().guide().approvalPending());
        assertFalse(state(d).extra().guide().stalled(), "no stall cue while an approval is pending or queued");
        assertEquals("Needs your approval — report", state(d).extra().guide().currentStep());
        assertEquals(41, d.snapshot().permission().pendingRef());
        assertEquals(1, a.permissionDialogs.size(), "the existing modal question remains the one to answer");
        assertEquals(1, a.permissionClocks.size());
    }

    @Test
    void totalsResetOnlyOnNewChatAndOldTicketFactsCannotChangeActivity() {
        var a = new FakeSessionAdapter();
        var d = driver(a);
        startToReady(d, a);
        send(d, a, 10);
        d.post(new SessionEvents.LocalAgentTurnStarted(1, 1, 1_000));
        d.post(new SessionEvents.LocalAgentTurnStarted(1, 1, 1_000));
        assertEquals(1, state(d).turnCount(), "one count per actual turn, not per replayed started fact");
        d.post(new SessionEvents.LocalAgentToolStarted(1, 1, 41, SessionEvents.ToolClass.BUILT_IN, "Read", 70, 2_000));
        d.post(new SessionEvents.LocalAgentTurnEnded(1, 1, SessionEvents.TurnOutcome.COMPLETED));
        assertEquals("", state(d).extra().guide().currentStep());
        send(d, a, 11);
        assertEquals(2, state(d).turnCount());
        var before = state(d);
        d.post(new SessionEvents.LocalAgentToolStarted(1, 1, 99, SessionEvents.ToolClass.BUILT_IN, "Bash", 90, 90_000));
        d.post(new SessionEvents.LocalAgentActivityTick(1, 1, 500_000));
        d.post(new SessionEvents.LocalAgentActivityAcknowledged(1, 1));
        assertEquals(before.extra().guide(), state(d).extra().guide(), "late facts cannot age or acknowledge a new ticket");
        d.post(new SessionEvents.LocalAgentNewChatRequested("test"));
        assertEquals(0, state(d).turnCount());
        assertEquals(0, state(d).toolCallCount());
    }
}
