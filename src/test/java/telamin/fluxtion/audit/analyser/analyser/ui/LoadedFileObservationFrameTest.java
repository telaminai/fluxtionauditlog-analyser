package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.GraphicsEnvironment;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.*;

/** Constructed overwritten snapshots; does not assert a content hash from metadata. */
class LoadedFileObservationFrameTest {
    @Test void overwrittenLogAndGraphAreObservedWithoutReplacingTheLoadedSnapshot(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path log=Files.writeString(tmp.resolve("audit.yaml"),log("child"));
        Path graph=Files.writeString(tmp.resolve("graph.graphml"),graph("child"));long originalSize=Files.size(log);
        try(var f=new Frame(tmp)) {
            assertTrue(f.ex.render("open",Map.of("log",log.toString(),"graphml",graph.toString())).ok());
            awaitLoaded(f.ex);
            Map<String,Object> before=f.ex.render("context",Map.of()).payload();
            assertEquals(originalSize,((Map<?,?>)before.get("log")).get("sizeBytes"));
            Files.writeString(log,log("other")+log("other"));Files.writeString(graph,graph("other"));
            Map<String,Object> changed=f.ex.render("context",Map.of()).payload();
            var loaded=(Map<?,?>)changed.get("log");
            assertEquals(originalSize,loaded.get("sizeBytes"),"loaded size must not be a new stat beside old records");
            assertEquals(1,loaded.get("records"));
            assertEquals("changed-on-disk",((Map<?,?>)loaded.get("freshness")).get("state"),"overwritten log must be disclosed");
            assertEquals("changed-on-disk",((Map<?,?>)((Map<?,?>)changed.get("graphPairing")).get("freshness")).get("state"));
            assertTrue(ProjectModel.from(changed).toString().contains("changed-on-disk"));
            assertTrue(f.ex.render("open",Map.of("log",log.toString(),"graphml",graph.toString())).ok());
            awaitLoaded(f.ex);
            var reopened=f.ex.render("context",Map.of()).payload();
            assertEquals(Files.size(log),((Map<?,?>)reopened.get("log")).get("sizeBytes"));
            assertEquals(2,((Map<?,?>)reopened.get("log")).get("records"));
            assertEquals("unchanged-metadata",((Map<?,?>)((Map<?,?>)reopened.get("graphPairing")).get("freshness")).get("state"));
            Files.delete(graph);
            var missing=f.ex.render("context",Map.of()).payload();
            assertTrue(((Map<?,?>)missing.get("graphPairing")).get("freshness").toString().contains("missing"));
        }
    }
}
