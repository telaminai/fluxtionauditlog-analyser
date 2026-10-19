package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.node.StartPagePrompt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P2b (spec §5.6 step 1, replacing P2a's interim "S2 opens New project…" in the Claude Code column): S2 toward
 * Claude Code runs the set-up folder's chooser, then composes the SETUP draft — placed and Start armed, nothing sent; the
 * in-app column still opens New project….
 */
class SetupOnboardingRouteTest {

    /** ★ Witness: P2a's branch (S2 always RunStartPageFlowEffect("S2")) — the Claude Code column opens New project…. */
    @Test
    @DisplayName("★ S2 toward Claude Code: the set-up folder's chooser, then the SETUP draft; nothing before the chooser")
    void s2TowardClaudeCodeIsOnboarding() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.LOCAL_AGENT, "t"));
        d.post(StartPagePromptTest.press(1, "S2", 0, true, true, false, false));
        assertEquals(StartPagePrompt.SETUP_FLOW, a.startFlows.get(0).entryId(), "the set-up folder's chooser");
        assertTrue(a.startComposes.isEmpty(), "no draft before the chooser");
        assertTrue(a.agentStarts.isEmpty(), "nothing starts");
        d.post(new SessionEvents.StartPageFlowDone(1, SessionEvents.FlowOutcome.DONE, 0, 0, 0));
        assertEquals("S2", a.startComposes.get(0).entryId(), "the SETUP draft is composed after it");
        assertEquals(AssistantMode.LOCAL_AGENT, a.startComposes.get(0).target());
        assertTrue(a.agentStarts.isEmpty());
    }

    @Test
    @DisplayName("S2 in the in-app column still opens New project…; a cancelled chooser drafts nothing")
    void s2InAppAndCancel() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(StartPagePromptTest.press(1, "S2", 0, false, false, true, false));
        assertEquals("S2", a.startFlows.get(0).entryId(), "New project…");
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = new SessionDriver(b);
        e.post(new SessionEvents.AssistantModeChosen(AssistantMode.LOCAL_AGENT, "t"));
        e.post(StartPagePromptTest.press(1, "S2", 0, true, true, false, false));
        e.post(new SessionEvents.StartPageFlowDone(1, SessionEvents.FlowOutcome.CANCELLED, 0, 0, 0));
        assertTrue(b.startComposes.isEmpty());
    }

    @Test
    @DisplayName("the SETUP draft names propose and carries no record text")
    void theSetupDraft() {
        var d = telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.composeStart("S2",
                telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.StartFacts.none(""));
        assertNotNull(d);
        assertTrue(d.text().contains("analyser_propose"), d.text());
        assertTrue(d.text().contains("Do not change any file"), d.text());
    }

    /** Live smoke finding: the SETUP draft armed Start with the generic words. Witness: drop the S2 arm — START_PAGE. */
    @Test
    @DisplayName("★ the SETUP draft arms Start as 'Start — set up this project'")
    void theSetupDraftArmsItsOwnWords() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        d.post(new SessionEvents.LocalAgentDraftComposed(5, "S2", null, 0, LocalAgentSessionTest.consent(), "start page"));
        assertEquals("armed.SETUP", d.snapshot().localAgent().armedWhy());
    }
}
