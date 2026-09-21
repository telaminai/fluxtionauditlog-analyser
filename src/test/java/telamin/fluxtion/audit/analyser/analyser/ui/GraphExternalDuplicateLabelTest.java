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
        ActionExecutor ex = new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(), (r, n, f, k) -> { });
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

    /** Main review 2026-09-18 F1: x, y, x must keep x in its FIRST slot, or the legend's colours name the other line. */
    @Test
    void anInterleavedDuplicate_keepsItsFirstSlot_soTheLegendOrderIsThePlotOrder(@TempDir Path tmp) throws Exception {
        Path x1 = tmp.resolve("x1.csv"), y = tmp.resolve("y.csv"), x2 = tmp.resolve("x2.csv");
        Files.writeString(x1, "time,value\n1750000000000,1\n1750000001000,2\n", StandardCharsets.UTF_8);
        Files.writeString(y, "time,value\n1750000000000,10\n1750000001000,20\n", StandardCharsets.UTF_8);
        Files.writeString(x2, "time,value\n1750000000000,5\n1750000001000,6\n1750000002000,7\n", StandardCharsets.UTF_8);
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs, tmp);
        var r = ex.render("graph", Map.of("newTab", true, "name", "xyx",
                "series", List.of("bidMakerOrder.price"),
                "external", List.of(external(x1, "x"), external(y, "y"), external(x2, "x"))));
        assertTrue(r.ok(), r::toString);
        GraphPanel g = tabs.graphNamed("xyx");
        assertEquals(List.of("x", "y"), g.externalSpecs().stream().map(s -> s.label()).toList(), "specs (the legend's order): x keeps its first slot");
        assertEquals(x2.toString(), g.externalSpecs().get(0).path(), "and x is the LATER file");
        assertEquals(List.of("x: 3 rows", "y: 2 rows"), g.externalNotes(), "one note per label, the replaced one gone");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> echo = (List<Map<String, Object>>) ((Map<String, Object>) r.toMap().get("applied")).get("external");
        assertEquals(List.of("x", "y"), echo.stream().map(m -> m.get("label")).toList(), "the echo in the same order");
        assertEquals(3, echo.get(0).get("rows"));
        // the PLOT: after extraction lands, the external series follow the audit series in the legend's order
        long deadline = System.currentTimeMillis() + 10_000;
        while (g.chart().plottedSeries().size() < 3 && System.currentTimeMillis() < deadline) Thread.sleep(50);
        List<String> plotted = g.chart().plottedSeries().stream().map(s -> s.label()).toList();
        assertEquals(3, plotted.size(), "audit + two externals plotted: " + plotted);
        assertEquals(List.of("x", "y"), plotted.subList(1, 3), "plotted in the legend's order, so each swatch names its own line");
    }

    /** Re-review 2026-09-18 F5: a label that merely starts with another ("x: y") keeps its own note when "x" is replaced. */
    @Test
    void aPrefixRelatedLabel_keepsItsOwnNote_whenTheOtherIsReplaced(@TempDir Path tmp) throws Exception {
        Path x1 = tmp.resolve("x1.csv"), xy = tmp.resolve("xy.csv"), x2 = tmp.resolve("x2.csv");
        Files.writeString(x1, "time,value\n1750000000000,1\n1750000001000,2\n", StandardCharsets.UTF_8);
        Files.writeString(xy, "time,value\n1750000000000,10\n1750000001000,20\n", StandardCharsets.UTF_8);
        Files.writeString(x2, "time,value\n1750000000000,5\n1750000001000,6\n1750000002000,7\n", StandardCharsets.UTF_8);
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs, tmp);
        var r = ex.render("graph", Map.of("newTab", true, "name", "prefix",
                "series", List.of("bidMakerOrder.price"),
                "external", List.of(external(x1, "x"), external(xy, "x: y"), external(x2, "x"))));
        assertTrue(r.ok(), r::toString);
        GraphPanel g = tabs.graphNamed("prefix");
        assertEquals(List.of("x", "x: y"), g.externalSpecs().stream().map(s -> s.label()).toList());
        assertEquals(List.of("x: 3 rows", "x: y: 2 rows"), g.externalNotes(), "each label its own note, in first-seen order");
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
