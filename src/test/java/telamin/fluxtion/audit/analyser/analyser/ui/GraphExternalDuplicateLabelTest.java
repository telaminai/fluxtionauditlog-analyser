package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ledger review 2026-09-17, F2: {@code external} is replace-by-label, but one {@code graph} call carrying the
 * same label twice drew TWO identical legend rows — which the spotlight then rightly refused to point at
 * (re-review R4). A label names ONE series: the later entry applies, and the echo says so.
 */
class GraphExternalDuplicateLabelTest {

    private final HeapLogStore store = new HeapLogStore(Samples.sample());

    private ActionExecutor executor(GraphTabs tabs, Path exchangeDir) {
        tabs.bind(store, new FilterState());
        FilterState filter = new FilterState();
        ActionExecutor ex = new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(), (r, n, f) -> { });
        AppConfig cfg = new AppConfig();
        cfg.assistantExports = true;                 // the CSV must sit inside the exchange directory to be readable
        cfg.assistantExportDir = exchangeDir.toString();
        ex.bindExportPolicy(() -> cfg);
        return ex;
    }

    private static Map<String, Object> external(Path csv, String label) {
        return Map.of("path", csv.toString(), "label", label, "time", "time", "timeFormat", "epochMillis",
                "zone", "UTC", "value", "value");
    }

    @Test
    void theSameExternalLabelTwiceInOneCall_drawsOneSeries_andTheEchoSaysTheLaterOneApplied(@TempDir Path tmp) throws Exception {
        Path first = tmp.resolve("first.csv");
        Path second = tmp.resolve("second.csv");
        Files.writeString(first, "time,value\n1750000000000,1\n1750000001000,2\n", StandardCharsets.UTF_8);
        Files.writeString(second, "time,value\n1750000000000,5\n1750000001000,6\n1750000002000,7\n", StandardCharsets.UTF_8);
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs, tmp);

        var r = ex.render("graph", Map.of("newTab", true, "name", "dup",
                "series", List.of("bidMakerOrder.price"),
                "external", List.of(external(first, "x"), external(second, "x"))));
        assertTrue(r.ok(), r::toString);

        GraphPanel g = tabs.graphNamed("dup");
        assertNotNull(g);
        assertEquals(1, g.externalSpecs().size(), "a label names ONE series");
        assertEquals(second.toString(), g.externalSpecs().get(0).path(), "the later entry applied");

        String echo = String.valueOf(r.toMap());
        assertTrue(echo.contains("given twice"), "the echo says what happened: " + echo);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ext = (List<Map<String, Object>>) ((Map<String, Object>) r.toMap().get("applied")).get("external");
        assertEquals(1, ext.size(), "one external in the echo, not two");
        assertEquals(3, ext.get(0).get("rows"), "and it is the later file's rows");
    }

    @Test
    void twoDifferentLabels_areStillTwoSeries(@TempDir Path tmp) throws Exception {
        Path a = tmp.resolve("a.csv");
        Files.writeString(a, "time,value\n1750000000000,1\n", StandardCharsets.UTF_8);
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs, tmp);
        var r = ex.render("graph", Map.of("newTab", true, "name", "two",
                "series", List.of("bidMakerOrder.price"),
                "external", List.of(external(a, "x"), external(a, "y"))));
        assertTrue(r.ok(), r::toString);
        assertEquals(2, tabs.graphNamed("two").externalSpecs().size());
        assertFalse(String.valueOf(r.toMap()).contains("given twice"));
    }
}
