package telamin.fluxtion.audit.analyser.analyser.session.resume;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SessionRecoveryTest {
    @Test void theGeneratedProcessorRequiresAcceptanceAndRejectsOldProjectCompletions() {
        SessionAuditSink audit = new SessionAuditSink();
        var driver = new SessionDriver(e -> { throw new AssertionError("no I/O effect before acceptance"); }, audit);
        var node = driver.processor().sessionRecovery;
        driver.submit(new ResumeEvents.Activated("A")); long generationA=node.generation();
        var snapshot=snapshot("A");
        driver.submit(new ResumeEvents.OfferLoaded(generationA, "A", snapshot, null, nonce("A")));
        assertEquals("offered",node.echo().get("state")); assertNull(node.plan());
        driver.submit(new ResumeEvents.Requested(node.generation(), true));
        assertTrue(node.verifying()); assertNull(node.plan());
        driver.submit(new ResumeEvents.Activated("B"));
        driver.submit(new ResumeEvents.Checked(generationA,List.of(new SessionResumeStore.Check(snapshot.inputs().getFirst(),"unchanged")),null));
        assertNull(node.plan());assertEquals("checking",node.echo().get("state"));
        assertTrue(audit.records().stream().anyMatch(s -> s.contains("stale completion ignored")));
    }
    @Test void oneMissingRollMemberRefusesTheEntireLogSetButNotIndependentDesign() {
        var driver = new SessionDriver(e -> { throw new AssertionError(); }, new SessionAuditSink());
        var node=driver.processor().sessionRecovery;
        var a=new SessionResumeStore.Identity("log","/demo/a.yml","a".repeat(64),null);
        var b=new SessionResumeStore.Identity("log","/demo/b.yml","b".repeat(64),null);
        var design=new SessionResumeStore.Identity("design","/demo/design.xml","c".repeat(64),null);
        var saved=new SessionResumeStore.Snapshot("A", "2026-09-20T00:00:00Z", List.of(a,b,design), Map.of(), nonce("A"));
        driver.submit(new ResumeEvents.Activated("A"));
        driver.submit(new ResumeEvents.OfferLoaded(node.generation(), "A", saved, null, nonce("A")));
        driver.submit(new ResumeEvents.Requested(node.generation(), true));
        driver.submit(new ResumeEvents.Checked(node.generation(),List.of(new SessionResumeStore.Check(a,"unchanged"),new SessionResumeStore.Check(b,"unavailable"),new SessionResumeStore.Check(design,"unchanged")),null));
        assertEquals(List.of(design),node.plan().available());assertEquals(2,node.plan().omitted().size());
        assertEquals("restoring",node.echo().get("state"),"a plan is not proof any file actually opened");
        driver.submit(new ResumeEvents.Finished(node.generation(),"design opened; log set refused"));
        assertEquals("finished",node.echo().get("state"));
    }
    @Test void dismissalOpensNothingAndRepeatedAcceptanceCannotEraseAnInFlightPlan() {
        var driver = new SessionDriver(e -> { throw new AssertionError(); }, new SessionAuditSink());
        var node = driver.processor().sessionRecovery;
        driver.submit(new ResumeEvents.Activated("A"));
        var saved = snapshot("A");
        driver.submit(new ResumeEvents.OfferLoaded(node.generation(), "A", saved, null, nonce("A")));
        driver.submit(new ResumeEvents.Requested(node.generation(), false));
        assertEquals("dismissed", node.echo().get("state")); assertNull(node.plan());
        driver.submit(new ResumeEvents.Finished(node.generation(), "unexpected"));
        assertEquals("dismissed", node.echo().get("state"));
        driver.submit(new ResumeEvents.Activated("A"));
        driver.submit(new ResumeEvents.OfferLoaded(node.generation(), "A", saved, null, nonce("A")));
        driver.submit(new ResumeEvents.Requested(node.generation(), true));
        driver.submit(new ResumeEvents.Checked(node.generation(),List.of(new SessionResumeStore.Check(saved.inputs().getFirst(),"unchanged")),null));
        var plan = node.plan();
        driver.submit(new ResumeEvents.Requested(node.generation(), true));
        assertSame(plan, node.plan());
    }

    @Test void staleAcceptanceAndDismissalCannotAnswerANewerOffer() {
        var driver = new SessionDriver(e -> { throw new AssertionError(); }, new SessionAuditSink());
        var node = driver.processor().sessionRecovery;
        driver.submit(new ResumeEvents.Activated("A"));
        long old = node.generation();
        driver.submit(new ResumeEvents.OfferLoaded(old, "A", snapshot("A"), null, nonce("A")));
        driver.submit(new ResumeEvents.Activated("B"));
        driver.submit(new ResumeEvents.OfferLoaded(node.generation(), "B", snapshot("B"), null, nonce("B")));
        for (boolean accept : List.of(true, false)) {
            driver.submit(new ResumeEvents.Requested(old, accept));
            assertEquals("offered", node.echo().get("state"));
            assertTrue(node.echo().get("message").toString().contains("superseded"));
            assertEquals("B", node.candidate().key());
        }
        driver.submit(new ResumeEvents.Requested(node.generation(), false));
        assertEquals("dismissed", node.echo().get("state"));
    }

    @Test void recheckWithdrawsTheWholeSetAndCannotResurrectAnOmittedMember() {
        var driver = new SessionDriver(e -> { throw new AssertionError(); }, new SessionAuditSink());
        var node = driver.processor().sessionRecovery;
        var a = snapshot("A").inputs().getFirst();
        var b = new SessionResumeStore.Identity("log", "/demo/b.yml", "b".repeat(64), null);
        var d = new SessionResumeStore.Identity("design", "/demo/design.xml", "c".repeat(64), null);
        var saved = new SessionResumeStore.Snapshot("A", "2026-09-20T00:00:00Z", List.of(a,b,d), Map.of(), nonce("A"));
        var unchanged = saved.inputs().stream().map(i -> new SessionResumeStore.Check(i,"unchanged")).toList();
        driver.submit(new ResumeEvents.Activated("A"));
        driver.submit(new ResumeEvents.OfferLoaded(node.generation(), "A", saved, null, nonce("A")));
        driver.submit(new ResumeEvents.Requested(node.generation(), true));
        driver.submit(new ResumeEvents.Checked(node.generation(), unchanged, null));
        assertEquals(List.of(a,b,d), node.plan().available());
        driver.submit(new ResumeEvents.Checked(node.generation(), List.of(new SessionResumeStore.Check(a,"content changed"),
                unchanged.get(1), unchanged.get(2)), null, driver.processor().operationGate.expectedOpId()));
        assertEquals(List.of(d), node.plan().available());
        assertEquals(2, node.plan().omitted().size());
        driver.submit(new ResumeEvents.Checked(node.generation(), unchanged, null, driver.processor().operationGate.expectedOpId()));
        assertEquals(List.of(d), node.plan().available());
    }

    /**
     * Edit-loop spec §E, first fixture at the node: a session captured under profile P at path X, then the
     * project deleted and a new profile created at X. Same key, and the input bytes may well be unchanged, but
     * the creation nonces differ: withheld with its capture time, and it cannot be accepted. The same profile
     * (same nonce) is offered, naming when it was captured.
     */
    @Test void aSessionCapturedByADifferentProfileAtThisPathIsWithheldNotOffered() {
        var driver = new SessionDriver(e -> { throw new AssertionError("nothing may open"); }, new SessionAuditSink());
        var node = driver.processor().sessionRecovery;
        var byOldProfile = new SessionResumeStore.Snapshot(PROFILE_KEY, "2026-09-24T22:28:33Z",
                snapshot("x").inputs(), Map.of(), "3f1c0de2-0000-4000-8000-00000000000a");
        driver.submit(new ResumeEvents.Activated(PROFILE_KEY));
        driver.submit(new ResumeEvents.OfferLoaded(node.generation(), PROFILE_KEY, byOldProfile, null,
                "9b2e7a41-0000-4000-8000-00000000000b"));
        var echo = node.echo();
        assertEquals("unavailable", echo.get("state"), "a different profile's session must be withheld: " + echo);
        assertEquals(false, echo.get("available"));
        assertEquals("different profile at this path", echo.get("capturedBy"), echo.toString());
        assertEquals("2026-09-24T22:28:33Z", echo.get("capturedAt"));
        assertTrue(echo.get("message").toString().contains("captured by a different profile"), echo.toString());
        assertNull(node.candidate());
        driver.submit(new ResumeEvents.Requested(node.generation(), true));
        assertEquals("unavailable", node.echo().get("state"), "a withheld session cannot be accepted");

        driver.submit(new ResumeEvents.Activated(PROFILE_KEY));
        driver.submit(new ResumeEvents.OfferLoaded(node.generation(), PROFILE_KEY, byOldProfile, null,
                byOldProfile.profileIdentity()));
        assertEquals("offered", node.echo().get("state"));
        assertEquals("same profile", node.echo().get("capturedBy"));
        assertTrue(node.echo().get("message").toString().contains("2026-09-24T22:28:33Z"), "the offer names when it was captured");
    }

    /**
     * PR #28 review finding 3: a missing identity never implies a match, but nor does it establish a DIFFERENT
     * profile. A snapshot saved before profile identity existed, or a current profile with no creation nonce,
     * is withheld and labelled {@code capturedBy: unknown}.
     */
    @Test void aSessionWithoutAProfileIdentityIsWithheldAsCapturedByAnUnknownProfile() {
        var driver = new SessionDriver(e -> { throw new AssertionError("nothing may open"); }, new SessionAuditSink());
        var node = driver.processor().sessionRecovery;
        var legacy = new SessionResumeStore.Snapshot(PROFILE_KEY, "2026-09-24T22:28:33Z", snapshot("x").inputs(), Map.of());
        var captured = new SessionResumeStore.Snapshot(PROFILE_KEY, "2026-09-24T22:28:33Z", snapshot("x").inputs(), Map.of(),
                "3f1c0de2-0000-4000-8000-00000000000a");
        // a legacy snapshot under a profile that has a nonce; a captured snapshot under a profile that has none
        for (var pair : List.of(Map.entry(legacy, Optional.of("3f1c0de2-0000-4000-8000-00000000000a")),
                                Map.entry(captured, Optional.<String>empty()))) {
            driver.submit(new ResumeEvents.Activated(PROFILE_KEY));
            driver.submit(new ResumeEvents.OfferLoaded(node.generation(), PROFILE_KEY, pair.getKey(), null,
                    pair.getValue().orElse(null)));
            var echo = node.echo();
            assertEquals("unavailable", echo.get("state"), "a session without a profile identity must be withheld: " + echo);
            assertEquals("unknown", echo.get("capturedBy"), "no identity is unknown, not a different profile: " + echo);
            assertEquals("2026-09-24T22:28:33Z", echo.get("capturedAt"));
            assertNull(node.candidate());
            driver.submit(new ResumeEvents.Requested(node.generation(), true));
            assertEquals("unavailable", node.echo().get("state"), "a withheld session cannot be accepted");
        }
        // the no-project bucket has no profile, so no identity is expected there and it is still offered
        var own = new SessionResumeStore.Snapshot(SessionResumeStore.NO_PROJECT, "2026-09-24T22:28:33Z", snapshot("x").inputs(), Map.of());
        driver.submit(new ResumeEvents.Activated(null));
        driver.submit(new ResumeEvents.OfferLoaded(node.generation(), SessionResumeStore.NO_PROJECT, own, null, null));
        assertEquals("offered", node.echo().get("state"));
        assertEquals("no project", node.echo().get("capturedBy"));
    }

    /**
     * PR #28 review finding 5: §E asks for input origin to be disclosed, and a withheld offer is exactly where
     * the person needs to see what the other profile's session would have opened.
     */
    @Test void aWithheldOfferStillDisclosesItsInputOrigin() {
        var driver = new SessionDriver(e -> { throw new AssertionError("nothing may open"); }, new SessionAuditSink());
        var node = driver.processor().sessionRecovery;
        var external = new SessionResumeStore.Identity("log", "/elsewhere/external.yml", "a".repeat(64), null);
        var design = new SessionResumeStore.Identity("design", "/p/design.xml", null, "unreadable at capture");
        var byOldProfile = new SessionResumeStore.Snapshot(PROFILE_KEY, "2026-09-24T22:28:33Z", List.of(external, design),
                Map.of(), "3f1c0de2-0000-4000-8000-00000000000a");
        driver.submit(new ResumeEvents.Activated(PROFILE_KEY));
        driver.submit(new ResumeEvents.OfferLoaded(node.generation(), PROFILE_KEY, byOldProfile, null,
                "9b2e7a41-0000-4000-8000-00000000000b"));
        var echo = node.echo();
        assertEquals("unavailable", echo.get("state"), echo.toString());
        assertEquals(List.of(Map.of("role", "log", "path", "/elsewhere/external.yml", "identity", "sha256"),
                        Map.of("role", "design", "path", "/p/design.xml", "identity", "unverified")),
                echo.get("inputs"), "a withheld offer discloses the inputs it would have restored: " + echo);
    }

    private static final String PROFILE_KEY = "/p/.analyser/project.fluxtion-settings";

    /** A same-profile capture: the snapshot and the active profile carry one creation nonce per key. */
    private static String nonce(String key) {
        return "0000000" + key.toLowerCase() + "-nonce";
    }

    private static SessionResumeStore.Snapshot snapshot(String key) {
        return new SessionResumeStore.Snapshot(key,"2026-09-20T00:00:00Z",List.of(new SessionResumeStore.Identity("log","/demo/run.yml","a".repeat(64),null)),Map.of(),nonce(key));
    }
}
