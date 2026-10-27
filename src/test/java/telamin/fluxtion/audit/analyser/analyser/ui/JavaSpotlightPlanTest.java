package telamin.fluxtion.audit.analyser.analyser.ui;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import javax.swing.SwingUtilities;
import telamin.fluxtion.audit.analyser.analyser.source.*;
import static org.junit.jupiter.api.Assertions.*;

class JavaSpotlightPlanTest {
    @Test void grammarIsExplicitAndCasePreserving() {
        var p=SpotlightTarget.parse("SOURCE:JAVA:com.acme.Node:LINE:3");
        assertTrue(p.ok()); assertEquals("com.acme.Node",p.target().sourceFqn()); assertEquals(3,p.target().number());
        for(String s:List.of("source:node:child","source:java:","source:java:../Foo","source:java:a..B","source:java:a.B:line:0","source:java:a.B:line:-1","source:java:a.B:line:2147483648","source:java:a.B:method:f"))
            assertFalse(SpotlightTarget.parse(s).ok(),s);
    }
    @Test void aPinnedSourceRevisionIsComparedWithTheDocumentActuallyRead(@TempDir Path tmp) throws Exception {
        Path file=tmp.resolve("demo/Node.java");Files.createDirectories(file.getParent());
        String text="package demo;\npublic class Node { }\n";Files.writeString(file,text);
        var service=new SourceService();service.configure(List.of(tmp.toString()),null);var lookup=service.captureLookup();
        var requests=SpotlightTarget.requests(Map.of("target","source:java:demo.Node:line:2"));
        String shipped="sha256:"+SourceDocument.digest(text);
        assertEquals(1,JavaSpotlightPlan.read(lookup,requests.requests(),List.of(),Map.of("source:java:demo.Node:line:2",shipped)).targets().size(),
                "the shipped revision is pointed at");
        Files.writeString(file,"package demo;\npublic class Node { int edited; }\n");
        var refused=assertThrows(IllegalArgumentException.class,()->JavaSpotlightPlan.read(lookup,requests.requests(),List.of(),
                Map.of("source:java:demo.Node:line:2",shipped)),"an edited file is never captioned with the shipped words");
        assertTrue(refused.getMessage().contains("not the source revision"),refused.getMessage());
    }
    @Test void preparesOncePerFqnAndRefusesAbsentOrOutOfRangeBeforeAnyViewWork(@TempDir Path tmp) throws Exception {
        Path file=tmp.resolve("demo/Outer.java");Files.createDirectories(file.getParent());Files.writeString(file,"package demo;\npublic class Outer { class Inner {} }\n");
        var service=new SourceService();service.configure(List.of(tmp.toString()),null);var lookup=service.captureLookup();
        var requests=SpotlightTarget.requests(Map.of("targets",List.of("source:java:demo.Outer:line:2","source:java:demo.Outer.Inner")));
        var plan=JavaSpotlightPlan.read(lookup,requests.requests(),List.of());assertEquals(2,plan.targets().size());
        assertEquals(1,plan.targets().values().stream().map(p->p.document().identity()).distinct().count());
        for(String invalid:List.of("source:java:demo.NoSuch","source:java:demo.Outer:line:9")) {
            var bad=SpotlightTarget.requests(Map.of("targets",List.of("source:java:demo.Outer",invalid)));
            assertThrows(IllegalArgumentException.class,()->JavaSpotlightPlan.read(lookup,bad.requests(),List.of()));
        }
        // Line numbering follows Swing: one empty editor line after a trailing newline, not two.
        var finalEmpty=SpotlightTarget.requests(Map.of("target","source:java:demo.Outer:line:3"));
        assertEquals(1,JavaSpotlightPlan.read(lookup,finalEmpty.requests(),List.of()).targets().size());
        var pastEnd=SpotlightTarget.requests(Map.of("target","source:java:demo.Outer:line:4"));
        assertThrows(IllegalArgumentException.class,()->JavaSpotlightPlan.read(lookup,pastEnd.requests(),List.of()));
        Files.writeString(file,"package demo;\npublic class Outer { class Inner {} }");
        assertThrows(IllegalArgumentException.class,()->JavaSpotlightPlan.read(lookup,finalEmpty.requests(),List.of()),"no phantom line without a trailing newline");
        Path other=Files.writeString(tmp.resolve("demo/Other.java"),"package demo; class Other {}\n");
        var different=SpotlightTarget.requests(Map.of("targets",List.of("source:java:demo.Outer","source:java:demo.Other")));
        assertThrows(IllegalArgumentException.class,()->JavaSpotlightPlan.read(lookup,different.requests(),List.of()));
        assertThrows(IllegalArgumentException.class,()->JavaSpotlightPlan.read(lookup,requests.requests(),List.of(
                lookup.freshDocumentForSpotlight("demo.Other").orElseThrow())),"retained add targets participate in the one-document rule");
        SwingUtilities.invokeAndWait(()->assertThrows(IllegalStateException.class,()->JavaSpotlightPlan.read(lookup,requests.requests(),List.of())));
    }

    /** M80: a line RANGE follows the same one-document and bounds rules as a single line. */
    @Test void rangeTargetsBindOneDocumentAndRefuseWholeWhenPastEnd(@TempDir Path tmp) throws Exception {
        Path file=tmp.resolve("demo/Outer.java");Files.createDirectories(file.getParent());
        Files.writeString(file,"package demo;\npublic class Outer { class Inner {} }\n");
        var service=new SourceService();service.configure(List.of(tmp.toString()),null);var lookup=service.captureLookup();
        // a range inside the document, alongside the plain whole-document target: same document, both prepared
        var inRange=SpotlightTarget.requests(Map.of("targets",List.of("source:java:demo.Outer","source:java:demo.Outer:line:1-3")));
        var plan=JavaSpotlightPlan.read(lookup,inRange.requests(),List.of());
        assertEquals(2,plan.targets().size());
        assertEquals(1,plan.targets().values().stream().map(p->p.document().identity()).distinct().count());
        var rangeTarget=plan.targets().get("source:java:demo.Outer:line:1-3").target();
        assertEquals(1,rangeTarget.lineFrom());assertEquals(3,rangeTarget.lineTo());
        // a range whose LAST line is past the document's end is refused WHOLE — even though its first line exists
        var pastEnd=SpotlightTarget.requests(Map.of("target","source:java:demo.Outer:line:2-4"));
        assertThrows(IllegalArgumentException.class,()->JavaSpotlightPlan.read(lookup,pastEnd.requests(),List.of()));
    }
}
