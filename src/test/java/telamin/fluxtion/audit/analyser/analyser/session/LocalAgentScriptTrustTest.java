package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalAgentScriptTrustTest {
    private static SessionEvents.LocalAgentConsent full(boolean ticked) {
        var c = LocalAgentFullPostureTest.full(LocalAgentPosture.FullMode.DEFAULT);
        return new SessionEvents.LocalAgentConsent(c.allowed(), c.folderConfirmed(), false, c.postureRef(), 0, c.folder(),
                true, c.fullMode(), true, null, "DEMO-folder-digest", ticked, false, true, ticked ? 1 : 0);
    }

    private static SessionDriver ready(FakeSessionAdapter a, boolean ticked) {
        var d = LocalAgentSessionTest.driver(a);
        d.post(new SessionEvents.LocalAgentStartRequested(1, full(ticked)));
        var s = a.agentStarts.getLast();
        LocalAgentFullPostureTest.bindFull(d, s, LocalAgentPosture.FullMode.DEFAULT);
        var t = LocalAgentSessionTest.send(d, a, 10, full(ticked));
        d.post(LocalAgentFullPostureTest.fullInit(s, t.ticket(), "default", java.util.List.of(), "connected"));
        return d;
    }

    private static void request(SessionDriver d, long ref) {
        d.post(new SessionEvents.LocalAgentPermissionRequested(1, 1, ref, SessionEvents.ToolClass.BUILT_IN, "Bash",
                "DEMO-input-digest", "DEMO-tool-use-digest", true, SessionEvents.HostedCallKind.PRE_ALLOWED));
    }

    @Test
    void anUntickedNodeNeverTrustsTheAdaptersPreAllowedClassification() {
        var a = new FakeSessionAdapter();
        a.scriptMatches = true;
        var d = ready(a, false);
        request(d, 41);
        assertEquals(1, a.permissionDialogs.size(), "★ B2: unticked means ask even if classification says PRE_ALLOWED");
        assertTrue(a.scriptChecks.isEmpty(), "no captured opt-in, no automatic check");
        assertTrue(a.permissionAnswers.isEmpty());
    }

    @Test
    void aTickedNodeRequiresItsSeparateLiveCheckBeforeAllowing() {
        var a = new FakeSessionAdapter();
        a.holdScriptChecks = true;
        var d = ready(a, true);
        request(d, 41);
        assertEquals(1, a.scriptChecks.size());
        assertTrue(a.permissionAnswers.isEmpty(), "★ B1: the classifier cannot itself allow");
        d.post(new SessionEvents.LocalAgentScriptTrustChecked(0, 1, 1, 41, true, 1));
        assertEquals(0, a.permissionDialogs.size());
        assertTrue(a.permissionAnswers.getLast().allow());
        assertEquals(SessionEvents.PermissionBy.PRE_ALLOWED, a.permissionAnswers.getLast().by());
        d.post(new SessionEvents.LocalAgentScriptTrustChecked(0, 1, 1, 41, true, 1));
        assertEquals(1, a.permissionAnswers.size(), "duplicate result does not answer twice");
    }

    @Test
    void stopSupersedesChecksAndStaleFactsCannotConsumeTheCurrentOne() {
        var a = new FakeSessionAdapter();
        a.holdScriptChecks = true;
        var d = ready(a, true);
        request(d, 41);
        d.post(new SessionEvents.LocalAgentScriptTrustChecked(0, 9, 1, 41, true, 1));
        assertTrue(a.permissionAnswers.isEmpty());
        d.post(new SessionEvents.LocalAgentStopRequested("DEMO Stop"));
        assertFalse(a.permissionAnswers.getLast().allow());
        d.post(new SessionEvents.LocalAgentScriptTrustChecked(0, 1, 1, 41, true, 999));
        assertTrue(a.permissionAnswers.stream().noneMatch(SessionEffects.AnswerPermissionEffect::allow));
        assertEquals(1, d.snapshot().localAgent().extra().scripts().trustedCount(), "Stop's late check cannot change About");
    }

    @Test
    void newChatAndSamePostureSettingsSupersedeAnInFlightIdentityRead() {
        for (boolean chat : new boolean[]{true, false}) {
            var a = new FakeSessionAdapter();
            a.holdScriptChecks = true;
            var d = ready(a, true);
            request(d, 41);
            if (chat) d.post(new SessionEvents.LocalAgentNewChatRequested("DEMO New chat"));
            else d.post(new SessionEvents.LocalAgentConsentChanged(full(false)));
            assertFalse(a.permissionAnswers.getLast().allow());
            d.post(new SessionEvents.LocalAgentScriptTrustChecked(0, 1, 1, 41, true, 1));
            assertTrue(a.permissionAnswers.stream().noneMatch(SessionEffects.AnswerPermissionEffect::allow),
                    "a pre-save/pre-New-chat observation cannot resurrect a grant");
        }
    }

    @Test
    void safeAnalysisStillDeniesBashEvenWhenTheClassifierSaysPreAllowed() {
        var a = new FakeSessionAdapter();
        a.scriptMatches = true;
        var d = LocalAgentSessionTest.driver(a);
        LocalAgentPermissionTest.ready(d, a);
        request(d, 41);
        assertTrue(a.permissionDialogs.isEmpty());
        assertTrue(a.scriptChecks.isEmpty());
        assertFalse(a.permissionAnswers.getLast().allow());
        assertEquals(telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentPermission.DENIED_DEFAULT_POSTURE,
                a.permissionAnswers.getLast().message());
    }

    @Test
    void revokeIsScopedToTheShownProjectAndSupersedesAnInFlightRead() {
        var a = new FakeSessionAdapter();
        a.holdScriptChecks = true;
        var d = ready(a, true);
        request(d, 41);
        d.post(new SessionEvents.LocalAgentScriptsRevokeRequested("DEMO-other-project"));
        assertEquals(0, a.countOf(SessionEffects.RevokeLocalAgentScriptsEffect.class));
        d.post(new SessionEvents.LocalAgentScriptsRevokeRequested("DEMO-folder-digest"));
        assertEquals(1, a.countOf(SessionEffects.RevokeLocalAgentScriptsEffect.class));
        assertEquals(1, a.permissionDialogs.size(), "the in-flight request becomes an ordinary question");
        d.post(new SessionEvents.LocalAgentScriptTrustChecked(0, 1, 1, 41, true, 1));
        assertTrue(a.permissionAnswers.isEmpty(), "the pre-revocation result cannot silently allow");
        assertEquals(0, d.snapshot().localAgent().extra().scripts().trustedCount());
    }

    @Test
    void aScriptChangingInsideTheFolderQuestionHasItsOwnHonestRefusal() {
        var a = new FakeSessionAdapter();
        var d = LocalAgentSessionTest.driver(a);
        var c = full(false);
        var changed = new SessionEvents.LocalAgentConsent(c.allowed(), c.folderConfirmed(), false, c.postureRef(), 0,
                c.folder(), true, c.fullMode(), true, null, c.folderRef(), false, true, true, 0);
        d.post(new SessionEvents.LocalAgentStartRequested(41, changed));
        assertEquals(1, a.agentConfirms.size());
        d.post(new SessionEvents.LocalAgentFolderAnswered(41, true, true, changed, true, 1, false));
        assertTrue(a.agentStarts.isEmpty());
        assertTrue(a.agentTrusts.isEmpty());
        assertTrue(d.snapshot().localAgent().reason().contains("build scripts changed while the confirmation was open"));
        d.post(new SessionEvents.LocalAgentFolderAnswered(41, true, true, changed, true, 1, true));
        assertTrue(a.agentStarts.isEmpty(), "the rejected question cannot be reused");
    }
}
