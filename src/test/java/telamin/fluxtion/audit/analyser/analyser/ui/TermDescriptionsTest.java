package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M79-3 A3.4 (spec-first-minute §8.2; the tooltips are cut): where Mongoose, GraphML, {@code .fexp} and the Fluxtion key
 * first appear — the start page's openers, the Context column, Settings — the control carries an ACCESSIBLE DESCRIPTION
 * in the app's existing words, and no product-term tooltip is added.
 */
class TermDescriptionsTest {

    static final Path UI = Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui");
    static final Pattern TERM = Pattern.compile("Mongoose|GraphML|\\.fexp|Fluxtion key");

    @Test
    @DisplayName("A3.4: the Context column's rows naming a term carry its existing words as the accessible description")
    void theContextColumnDescribesEachTerm() throws Exception {
        String start = Files.readString(UI.resolve("StartPanel.java"));
        String bundles = Files.readString(Path.of("docs/site/evidence-bundles/index.md"));
        // the existing words: the start page's key card and GraphML opener, and the Evidence bundles page
        assertTrue(start.contains("\"" + ProjectModel.KEY_PRESENT_WORDS + "\""), "the key card's words (present)");
        assertTrue(start.contains("\"" + ProjectModel.KEY_ABSENT_WORDS + "\""), "the key card's words (absent)");
        assertTrue(start.contains("\"" + ProjectModel.GRAPHML_WORDS + "\""), "the GraphML opener's words");
        assertTrue(bundles.contains("An **evidence bundle** is one `.fexp` file that carries an investigation to someone else"));
        String flat = bundles.replaceAll("\\s+", " ");
        assertTrue(flat.contains("**Unsigned.** Verification shows that nothing in the bundle has changed since it was packed. "
                + "It does not show who packed it."), "the page's unsigned limit");
        assertTrue(ProjectModel.FEXP_WORDS.contains("Unsigned"), "the .fexp description says it is unsigned");

        Map<String, Object> ctx = Map.of(
                "project", Map.of("active", true, "name", "demo", "root", "/demo", "bundle",
                        Map.of("identity", "sha256:abc", "source", "/in/case.fexp", "workingCopy", "/tmp/wc")),
                "fluxtionKey", Map.of("canonicalFilePresent", true, "canonicalFile", "/k", "precedenceNote", "n"),
                "log", Map.of("path", "/demo/audit.yaml", "records", 3),
                "graphPairing", Map.of("graph", "Demo.graphml", "graphPath", "/demo/Demo.graphml", "graphSource", "OPENED"));
        var rows = ProjectModel.from(ctx).sections().stream().flatMap(s -> s.rows().stream()).toList();
        assertEquals(ProjectModel.KEY_PRESENT_WORDS, row(rows, "Fluxtion key file").description());
        assertEquals(ProjectModel.FEXP_WORDS, row(rows, "Evidence bundle").description());
        assertEquals(ProjectModel.GRAPHML_WORDS, row(rows, "Demo.graphml").description());

        // the panel puts it on the row's label, and adds no product-term tooltip
        ProjectPanel panel = new ProjectPanel(new ProjectPanel.Navigator() {
            @Override public void showTab(String title) { }
            @Override public void openSettings(String page) { }
            @Override public void showReport(String name) { }
            @Override public void showGraph(String name) { }
        });
        panel.render(ProjectModel.from(ctx));
        List<String> described = new ArrayList<>();
        for (JLabel l : labels(panel)) {
            String d = l.getAccessibleContext().getAccessibleDescription();
            if (d != null && !d.equals(l.getToolTipText())) described.add(l.getText() + " = " + d);
            String tip = l.getToolTipText();
            if (tip != null) assertFalse(tip.contains(ProjectModel.FEXP_WORDS) || tip.contains(ProjectModel.GRAPHML_WORDS),
                    "a product-term tooltip: " + tip);
        }
        assertTrue(described.stream().anyMatch(s -> s.endsWith(ProjectModel.KEY_PRESENT_WORDS)), described.toString());
        assertTrue(described.stream().anyMatch(s -> s.endsWith(ProjectModel.FEXP_WORDS)), described.toString());
        assertTrue(described.stream().anyMatch(s -> s.endsWith(ProjectModel.GRAPHML_WORDS)), described.toString());
    }

    @Test
    @DisplayName("A3.4: every start-page opener that names a term is an ActionChip with words for its description")
    void theStartPageOpenersDescribeEachTerm() throws Exception {
        String start = Files.readString(UI.resolve("StartPanel.java"));
        // chip(title, label, why, go): ActionChip sets the accessible description from `why`
        Matcher m = Pattern.compile("chip\\(\"([^\"]*)\",\\s*\"([^\"]*)\",\\s*\"([^\"]*)\"").matcher(start);
        int named = 0;
        while (m.find()) {
            if (!TERM.matcher(m.group(1) + " " + m.group(2)).find()) continue;
            named++;
            assertFalse(m.group(3).isBlank(), "a start-page opener naming a term has no description: " + m.group(1));
        }
        assertTrue(named >= 1, "the start page's GraphML opener is an ActionChip");
        assertTrue(start.contains("getAccessibleContext().setAccessibleDescription(why)"),
                "ActionChip puts its words on the accessible description");
    }

    @Test
    @DisplayName("A3.4: Settings names none of the four terms in its own words, so it has none to describe")
    void settingsNamesNoTerm() throws Exception {
        String settings = Files.readString(UI.resolve("ConfigPanel.java"));
        Matcher literal = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(settings);
        List<String> found = new ArrayList<>();
        while (literal.find()) if (TERM.matcher(literal.group(1)).find()) found.add(literal.group(1));
        assertEquals(List.of(), found, "Settings now names a product term: give its control the existing words (§8.2)");
    }

    static ProjectModel.Row row(List<ProjectModel.Row> rows, String prefix) {
        return rows.stream().filter(r -> r.primary() != null && r.primary().startsWith(prefix)).findFirst()
                .orElseThrow(() -> new AssertionError("no row " + prefix + " in " + rows.stream().map(ProjectModel.Row::primary).toList()));
    }

    static List<JLabel> labels(Container c) {
        List<JLabel> out = new ArrayList<>();
        for (Component k : c.getComponents()) {
            if (k instanceof JLabel l) out.add(l);
            if (k instanceof Container inner) out.addAll(labels(inner));
        }
        return out;
    }
}
