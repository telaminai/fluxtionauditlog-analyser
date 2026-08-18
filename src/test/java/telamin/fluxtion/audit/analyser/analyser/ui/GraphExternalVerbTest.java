package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code graph {external}} (M29.3): the echo carries the loader's honesty report, refusals ride
 * warnings[] naming the rule, and the D-F4 confinement (exchange directory + chooser grants) is
 * enforced at the verb, not trusted to callers.
 */
class GraphExternalVerbTest {

    @TempDir
    Path exchangeDir;

    @TempDir
    Path elsewhere;

    private final HeapLogStore store = new HeapLogStore(Samples.sample());

    private ActionExecutor executor(GraphTabs tabs, java.util.Set<Path> grants) {
        tabs.bind(store, new FilterState());
        FilterState filter = new FilterState();
        ActionExecutor ex = new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(),
                (r, n, f) -> { });
        AppConfig cfg = new AppConfig();
        cfg.assistantExports = true;
        cfg.assistantExportDir = exchangeDir.toString();
        ex.bindExportPolicy(() -> cfg);
        ex.setReadGrants(() -> grants);
        return ex;
    }

    private Path csv(Path dir, String name) throws IOException {
        Path f = dir.resolve(name);
        Files.writeString(f, "ts,mid\n3000,3\n1000,1\nbad,9\n2000,2\n");
        return f;
    }

    private static Map<String, Object> external(Path f) {
        return Map.of("path", f.toString(), "label", "venue mid", "time", "ts",
                "timeFormat", "epochMillis", "value", "mid", "offsetMillis", 250);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theEchoCarriesTheLoadersHonestyReport() throws IOException {
        GraphTabs tabs = new GraphTabs();
        var r = executor(tabs, java.util.Set.of()).render("graph", Map.of(
                "newTab", true, "name", "g", "external", List.of(external(csv(exchangeDir, "v.csv")))));
        assertTrue(r.ok(), () -> "failed: " + r);
        List<Map<String, Object>> echo = (List<Map<String, Object>>) r.payload().get("external");
        Map<String, Object> e = echo.get(0);
        assertEquals(3, e.get("rows"));
        assertEquals(1, e.get("skipped"), "the 'bad' timestamp row");
        assertEquals(2, e.get("reordered"), "both 1000 and 2000 sit below the 3000 high-water mark");
        assertEquals(1250L, e.get("from"), "sorted, offset applied — the echo shows the resolved range");
        assertEquals(250L, e.get("offsetMillis"));
        assertNotNull(e.get("diagnostics"));
        // and the spec landed on the panel, ready for M29.4's persistence
        GraphPanel panel = walk(tabs);
        assertNotNull(panel);
        assertEquals(1, panel.externalSpecs().size());
    }

    private static GraphPanel walk(java.awt.Container c) {
        for (java.awt.Component child : c.getComponents()) {
            if (child instanceof GraphPanel g && "g".equals(g.graphName())) return g;
            if (child instanceof java.awt.Container inner) {
                GraphPanel g = walk(inner);
                if (g != null) return g;
            }
        }
        return null;
    }

    @Test
    @SuppressWarnings("unchecked")
    void aPathOutsideTheExchangeDirectoryIsRefusedNamingTheRule() throws IOException {
        GraphTabs tabs = new GraphTabs();
        var r = executor(tabs, java.util.Set.of()).render("graph", Map.of(
                "newTab", true, "name", "g",
                "series", List.of("bidMakerOrder.price"),
                "external", List.of(external(csv(elsewhere, "out.csv")))));
        assertTrue(r.ok(), "the rest of the call still applies");
        List<String> warnings = (List<String>) r.payload().get("warnings");
        assertNotNull(warnings);
        assertTrue(warnings.get(0).contains("outside the exchange directory"), warnings.toString());
        assertTrue(warnings.get(0).contains(exchangeDir.toAbsolutePath().normalize().toString()),
                "the refusal names the directory (F1): " + warnings);
        List<Map<String, Object>> echo = (List<Map<String, Object>>) r.payload().get("external");
        assertTrue(echo.isEmpty(), "nothing was loaded");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aChooserGrantedFileOutsideTheDirectoryLoads() throws IOException {
        GraphTabs tabs = new GraphTabs();
        Path granted = csv(elsewhere, "picked.csv").toAbsolutePath().normalize();
        var r = executor(tabs, java.util.Set.of(granted)).render("graph", Map.of(
                "newTab", true, "name", "g", "external", List.of(external(granted))));
        assertTrue(r.ok());
        List<Map<String, Object>> echo = (List<Map<String, Object>>) r.payload().get("external");
        assertEquals(1, echo.size(), "the chooser IS the grant");
    }
}
