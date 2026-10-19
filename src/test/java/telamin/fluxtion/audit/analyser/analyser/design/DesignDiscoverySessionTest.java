package telamin.fluxtion.audit.analyser.analyser.design;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import telamin.fluxtion.audit.analyser.analyser.session.node.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DesignDiscoverySessionTest {
    @Test void followPresentationBelongsOnlyToAcceptedReadsIncludingFailures() {
        var shown=new ArrayList<telamin.fluxtion.audit.analyser.analyser.session.SessionEffects.PresentDesignRefreshEffect>();
        var driver=new telamin.fluxtion.audit.analyser.analyser.session.SessionDriver(effect->{
            assertInstanceOf(telamin.fluxtion.audit.analyser.analyser.session.SessionEffects.PresentDesignRefreshEffect.class,effect);
            shown.add((telamin.fluxtion.audit.analyser.analyser.session.SessionEffects.PresentDesignRefreshEffect)effect);
            return new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.StatusShown(effect.opId(),"presentDesignRefresh");
        });
        var state=driver.processor().designSession;
        var first=BuilderDocument.parse("DEMO.java","class DEMO {}");
        driver.submit(new DesignEvents.ReadCompleted(first.file(),null,"",true,state.generation(),first));
        long old=state.generation();
        var revised=BuilderDocument.parse("DEMO.java","class DEMO { /* revised */ }");
        driver.submit(new DesignEvents.FollowReadCompleted(new DesignEvents.ReadCompleted(first.file(),null,"",false,old,revised)));
        assertEquals(1,shown.size(),"an accepted follow read requests exactly one presentation");
        assertEquals(state.generation(),shown.getFirst().generation());
        assertEquals(revised.revision(),state.revision());
        driver.submit(new DesignEvents.FollowReadCompleted(new DesignEvents.ReadCompleted(first.file(),null,"",false,old,first)));
        assertEquals(1,shown.size(),"stale follow completion cannot request presentation");
        assertEquals(revised.revision(),state.revision());
        driver.submit(new DesignEvents.FollowReadCompleted(new DesignEvents.ReadCompleted("Other.java",null,"",false,state.generation(),first)));
        assertEquals(1,shown.size(),"another file cannot borrow the current read generation");
        driver.submit(new DesignEvents.FollowReadCompleted(new DesignEvents.ReadCompleted(first.file(),null,"read failed",false,state.generation())));
        assertEquals(2,shown.size(),"an accepted failure must present its qualification");
        assertEquals("read failed",state.error());
        assertEquals(revised.revision(),state.revision(),"a failed refresh retains the last good revision");
        old=state.generation();
        driver.submit(new DesignEvents.Cleared("DEMO"));
        driver.submit(new DesignEvents.FollowReadCompleted(new DesignEvents.ReadCompleted(first.file(),null,"",false,old,first)));
        assertEquals(2,shown.size(),"clear retires pending follow presentation");
        assertNull(state.path());
    }
    @Test void aFailedRefreshKeepsTheLastGoodJavaFormButANewFailedFileDoesNot() {
        var state=new DesignSession(new OperationGate(),new EffectQueue());
        var builder=BuilderDocument.parse("DEMO.java","class DEMO {}");
        state.read(new DesignEvents.ReadCompleted(builder.file(),null,"",true,state.generation(),builder));
        state.read(new DesignEvents.ReadCompleted(builder.file(),null,"read failed",false,state.generation()));
        assertEquals("builder",state.echo().get("kind"),"the retained good revision is still a builder");
        assertEquals(builder.revision(),state.echo().get("revision"));
        state.read(new DesignEvents.ReadCompleted("Other.xml",null,"read failed",true,state.generation()));
        assertFalse(state.echo().containsKey("kind"),"a different failed file has no loaded form");
    }
    @Test void emptyDiscoveryUsesAShortHumanMessageWithoutJsonSyntax() {
        var state=new DesignSession(new OperationGate(),new EffectQueue());
        assertEquals("No design found. Add a source folder or open a design.",state.discoveryNote());
        assertFalse(state.discoveryNote().contains("{") || state.discoveryNote().contains("source_root"));
        assertTrue(state.discoveryNote().split("\\s+").length<=15,"the human line stays short");
    }
    @Test void failedDesignReadNeverInventsASpringForm(@TempDir Path root) throws Exception {
        for (String extension : List.of("java", "xml")) {
            Path unreadable = Files.write(root.resolve("DEMO."+extension), new byte[]{(byte)0xff});
            var state = new DesignSession(new OperationGate(),new EffectQueue());
            var fact = DesignWorkspace.read(new DesignFiles(List.of(root.toString()),null), unreadable.toString(),true,state.generation());
            assertTrue(state.read(fact));
            assertFalse(state.error().isEmpty(),"the failed read is disclosed");
            assertFalse(state.echo().containsKey("kind"),"neither form was loaded: do not invent spring from a null builder");
            assertEquals("explicit",state.echo().get("source"));
        }
    }

    @Test void aLaterRequestOrClearRetiresAnOldDiscoveryButAnExplicitReadDoesNot() {
        var state = new DesignSession(new OperationGate(),new EffectQueue());
        var found = new DesignDiscovery("builder",List.of(new DesignDiscovery.FileRef("DEMO.java","builder")),"source-scan");
        state.discover(new DesignEvents.DiscoveryRequested()); long first=state.discoveryGeneration();
        state.requested(new DesignEvents.ReadRequested("source"));
        assertTrue(state.discovered(new DesignEvents.DiscoveryCompleted(found,first)),"a glance is not a root change");
        state.discover(new DesignEvents.DiscoveryRequested());
        assertFalse(state.discovered(new DesignEvents.DiscoveryCompleted(found,first)),"old root scan cannot land");
        long latest=state.discoveryGeneration(); state.cleared(new DesignEvents.Cleared("DEMO"));
        assertFalse(state.discovered(new DesignEvents.DiscoveryCompleted(found,latest)),"clear retires discovery");
        assertEquals("none",state.echo().get("kind")); assertFalse(state.echo().containsKey("files"));
    }
    @Test void xmlsReleasedValuesAndTheirOrderAreUnchanged() throws Exception {
        var state = new DesignSession(new OperationGate(),new EffectQueue());
        var xml = DesignDocument.parse("DEMO.xml","<beans><bean id='a'/><bean id='b'/></beans>");
        state.read(new DesignEvents.ReadCompleted(xml.file(),xml,"",true,state.generation()));
        Map<String,Object> old = new LinkedHashMap<>();
        old.put("relationship","unverified");old.put("note","working copy; matched by name; relationship to this run unknown");
        old.put("file",xml.file());old.put("revision",xml.revision());old.put("beans",List.of("a","b"));
        Map<String,Object> released=new LinkedHashMap<>(state.echo());
        for(String key:List.of("kind","files","nodes","source")) released.remove(key);
        assertEquals(old,released,"★ T8 every released XML value is unchanged");
        assertEquals(List.copyOf(old.keySet()),List.copyOf(released.keySet()),"★ T8 released ordering is unchanged");
        assertEquals("explicit",state.echo().get("source"));
    }
}
