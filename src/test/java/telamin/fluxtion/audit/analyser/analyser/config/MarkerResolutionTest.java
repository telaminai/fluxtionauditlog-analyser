package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.graph.MarkerExtractor;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MarkerResolutionTest {
    @Test void preservedLogsCountEventsWithoutCarryingTradesIntoPriceRecords() throws Exception {
        for (String run : List.of("trades", "mongoose-run1", "mongoose-run2")) {
            var path=Path.of("docs/handoff/evidence/spring-authoring-feedback-2026-09-19-round2/evidence",run,"audit.yaml");
            var store=new HeapLogStore(Files.readString(path)); var filter=new FilterState();
            for (var expected : Map.of("positionNode.quantity > 0",5,"positionNode.quantity < 0",6,"rootNode.price",8).entrySet()) {
                var strict=new GraphSpec.MarkerSpec("event","circle",expected.getKey(),"axis",null,"STRICT");
                var points=MarkerExtractor.extract(store,filter,strict,n->null).points();
                assertEquals(expected.getValue(),points.size(),run+": same-record markers must match logged events");
                for (var point:points) {
                    String raw=store.rawText(point.recordIndex());
                    assertTrue(raw.contains(expected.getKey().startsWith("position") ? "quantity:" : "price:"));
                }
                if (expected.getKey().contains("quantity")) {
                    var legacy=new GraphSpec.MarkerSpec("state","circle",expected.getKey(),"axis",null);
                    var result=MarkerExtractor.extract(store,filter,legacy,n->null);
                    assertTrue(result.points().size()>points.size(),"explicit carried state remains available");
                    assertTrue(result.note().contains("not event counts"));
                }
            }
        }
    }
    @Test void savedResolutionSurvivesAndMissingFieldKeepsLegacyMeaning() {
        var spec=new GraphSpec("markers",List.of(),List.of(),null,null,null,null,List.of(),List.of(),List.of(),List.of(),List.of(),
                List.of(new GraphSpec.MarkerSpec("event","circle","n.quantity > 0","axis",null,"STRICT")));
        var source=new AppConfig(); source.savedGraphs.add(spec);
        var share=new SettingsShare(); var target=new AppConfig(); target.savedGraphs.clear();
        var plan=share.preview(share.export(source,java.util.EnumSet.of(SettingsShare.Category.GRAPHS)),target);
        share.apply(plan,plan.present(),target);
        assertEquals("STRICT",target.savedGraphs.getFirst().markers().getFirst().resolve());
        var p=new Properties();ConfigStore.writeGraphs(p,List.of(spec));
        var restored=new ArrayList<GraphSpec>();ConfigStore.readGraphs(p,restored);
        assertEquals("STRICT",restored.getFirst().markers().getFirst().resolve());
        p.remove("graph.0.marker.0.resolve");restored.clear();ConfigStore.readGraphs(p,restored);
        assertEquals("LOCF",restored.getFirst().markers().getFirst().resolve(),"old profile must not change semantics");
    }
}
