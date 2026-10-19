package telamin.fluxtion.audit.analyser.analyser.design;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.node.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DesignDiscoverySessionTest {
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
