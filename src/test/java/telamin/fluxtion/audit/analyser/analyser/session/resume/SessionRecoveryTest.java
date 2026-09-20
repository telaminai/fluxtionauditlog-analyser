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
        driver.submit(new ResumeEvents.OfferLoaded(generationA,"A",snapshot,null));
        assertEquals("offered",node.echo().get("state")); assertNull(node.plan());
        driver.submit(new ResumeEvents.Requested());
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
        var saved=new SessionResumeStore.Snapshot("A","2026-09-20T00:00:00Z",List.of(a,b,design),Map.of());
        driver.submit(new ResumeEvents.Activated("A"));
        driver.submit(new ResumeEvents.OfferLoaded(node.generation(),"A",saved,null));
        driver.submit(new ResumeEvents.Requested());
        driver.submit(new ResumeEvents.Checked(node.generation(),List.of(new SessionResumeStore.Check(a,"unchanged"),new SessionResumeStore.Check(b,"unavailable"),new SessionResumeStore.Check(design,"unchanged")),null));
        assertEquals(List.of(design),node.plan().available());assertEquals(2,node.plan().omitted().size());
        assertEquals("restoring",node.echo().get("state"),"a plan is not proof any file actually opened");
        driver.submit(new ResumeEvents.Finished(node.generation(),"design opened; log set refused"));
        assertEquals("finished",node.echo().get("state"));
    }
    private static SessionResumeStore.Snapshot snapshot(String key) {
        return new SessionResumeStore.Snapshot(key,"2026-09-20T00:00:00Z",List.of(new SessionResumeStore.Identity("log","/demo/run.yml","a".repeat(64),null)),Map.of());
    }
}
