package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import java.nio.file.Path;
import java.util.*;
import javax.swing.SwingUtilities;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed navigation case on the committed demo graph; not a participant replay. */
class TopologyShowAllTest {
    @Test void showAllExitsNestedFocusWithEitherScaffoldingChoice() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (var params : List.of(Map.<String,Object>of("showAll", true),
                    Map.<String,Object>of("showAll", true, "scaffolding", false),
                    Map.<String,Object>of("showAll", true, "scaffolding", true))) {
                var panel = new TopologyPanel();
                try { panel.load(Path.of("src/test/resources/topology/demo-quote-processor.graphml")); }
                catch (Exception e) { throw new RuntimeException(e); }
                var filter = new FilterState();
                var executor = new ActionExecutor(() -> null, () -> filter, new GraphTabs(),
                        new LogTablePanel(), (r,n,f,k) -> {});
                executor.bind(panel, null);
                call(executor, Map.of("select", "quotePublisher", "scope", "neighbours", "focus", true));
                call(executor, Map.of("select", "quotePublisher", "scope", "node", "focus", true));
                assertEquals(2, panel.cursorState().get("contextDepth"));
                call(executor, Map.of("showAll", false));
                assertEquals(2, panel.cursorState().get("contextDepth"), "false must leave focus intact");
                var log = new HeapLogStore("eventLogRecord:\n  event: MarketDataEvent\n  nodeLogs:\n    - quotePublisher: {v: 1}\n---\n");
                panel.showRecord(log.record(0));
                panel.selectNode("quotePublisher");
                assertNotNull(panel.canvas().executionOf("quotePublisher"));
                var result = executor.render("topology", params);
                assertTrue(result.ok(), result.toMap().toString());
                assertEquals(0, panel.cursorState().get("contextDepth"), "showAll must exit every context");
                assertEquals(false, panel.cursorState().get("focus"));
                assertEquals(List.of(), panel.cursorState().get("selected"));
                assertEquals(Set.of(), panel.canvas().emphasis());
                assertNull(panel.canvas().executionOf("quotePublisher"), "cycle shading is cleared");
                var expected = new TopologyPanel();
                try { expected.load(Path.of("src/test/resources/topology/demo-quote-processor.graphml")); }
                catch (Exception e) { throw new RuntimeException(e); }
                expected.setScaffoldingVisible(Boolean.TRUE.equals(params.get("scaffolding")));
                assertEquals(expected.cursorState().get("visibleNodes"), panel.cursorState().get("visibleNodes"));
                assertEquals(expected.cursorState().get("context"), panel.cursorState().get("context"));
                assertEquals(panel.cursorState(), result.payload(), "echo must state the resulting view");
            }
        });
    }
    private static void call(ActionExecutor ex, Map<String,Object> params) {
        var r = ex.render("topology", params); assertTrue(r.ok(), r.toMap().toString());
    }
}
