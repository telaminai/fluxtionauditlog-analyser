package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.llm.VerbSchemas;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M38.4 (spec-portable-context D-C5) — a repeatable analysis is a named sequence of ANALYSER verbs with its
 * rationale and declared parameters. Tier 2 by construction: the gate admits only verbs on the shipped
 * surface and refuses the two lifecycle acts that belong to a person. Recall is an offer; the runner stops
 * at the first failure.
 */
class AnalysisSpecTest {

    private static final Set<String> VERBS = VerbSchemas.all().keySet();

    private static AnalysisSpec breach() {
        return new AnalysisSpec("spread breach", "every breach incident starts the same way",
                List.of("log", "node"), Map.of("node", "quotePublisher"),
                List.of(new AnalysisSpec.Step("open", Map.of("log", "{log}")),
                        new AnalysisSpec.Step("filter", Map.of("text", "{node}")),
                        new AnalysisSpec.Step("graph", Map.of("name", "Spread — {node}", "series", List.of("{node}.spread")))));
    }

    @Test
    void theGateAdmitsOnlyAnalyserVerbs_andRefusesTheTwoHumanActs() {
        assertTrue(AnalysisSpec.refuse(breach(), VERBS).isEmpty());
        var unknown = new AnalysisSpec("x", "", List.of(), Map.of(), List.of(new AnalysisSpec.Step("deploy", Map.of())));
        assertTrue(AnalysisSpec.refuse(unknown, VERBS).get().contains("not an analyser verb"), "tier 2: only this viewer's verbs");
        var project = new AnalysisSpec("x", "", List.of(), Map.of(), List.of(new AnalysisSpec.Step("open", Map.of("project", "/p/.analyser/project.fluxtion-settings"))));
        assertTrue(AnalysisSpec.refuse(project, VERBS).get().contains("session boundary"), "a project switch is a person's act");
        var closeProject = new AnalysisSpec("x", "", List.of(), Map.of(), List.of(new AnalysisSpec.Step("open", Map.of("close", "project"))));
        assertTrue(AnalysisSpec.refuse(closeProject, VERBS).isPresent());
        var closeLog = new AnalysisSpec("x", "", List.of(), Map.of(), List.of(new AnalysisSpec.Step("open", Map.of("close", "log"))));
        assertTrue(AnalysisSpec.refuse(closeLog, VERBS).isEmpty(), "closing the LOG is an analysis step; only the project is off limits");
        var undeclared = new AnalysisSpec("x", "", List.of("log"), Map.of(), List.of(new AnalysisSpec.Step("filter", Map.of("text", "{node}"))));
        assertTrue(AnalysisSpec.refuse(undeclared, VERBS).get().contains("{node} is not a declared parameter"));
        assertTrue(AnalysisSpec.refuse(new AnalysisSpec("x", "", List.of(), Map.of(), List.of()), VERBS).get().contains("no steps"));
        assertTrue(AnalysisSpec.refuse(new AnalysisSpec("x", "", List.of("bad name"), Map.of(), breach().steps()), VERBS).get().contains("identifier"));
    }

    @Test
    void everyPathAStepNamesMustBeInsideTheProject_orAParameterBoundAtRunTime() {
        // review F1: the four shapes the reviewer got past the first gate
        Map<AnalysisSpec.Step, String> outside = new LinkedHashMap<>();
        outside.put(new AnalysisSpec.Step("report", Map.of("path", "/tmp/anything.pdf")), "report.path");
        outside.put(new AnalysisSpec.Step("screenshot", Map.of("path", "/Users/someone/Desktop/x.png")), "screenshot.path");
        outside.put(new AnalysisSpec.Step("source_root", Map.of("add", List.of("/etc"))), "source_root.add");
        outside.put(new AnalysisSpec.Step("open", Map.of("log", "/var/log/anything.yaml")), "open.log");
        outside.put(new AnalysisSpec.Step("open", Map.of("logs", List.of("logs/a.yaml", "../../other/b.yaml"))), "open.logs");
        outside.forEach((step, where) -> {
            var r = AnalysisSpec.refuse(new AnalysisSpec("x", "", List.of(), Map.of(), List.of(step)), VERBS);
            assertTrue(r.isPresent(), "must refuse: " + where);
            assertTrue(r.get().contains(where) && r.get().contains("inside the project"), where + " -> " + r.get());
        });
        // inside the project, or bound at run time: accepted
        var ok = new AnalysisSpec("x", "", List.of("log"), Map.of(), List.of(
                new AnalysisSpec.Step("open", Map.of("log", "{log}", "graphml", "build/processor.graphml")),
                new AnalysisSpec.Step("source_root", Map.of("add", List.of("src/main/java"))),
                new AnalysisSpec.Step("report", Map.of("path", "incident.pdf")),
                new AnalysisSpec.Step("screenshot", Map.of("path", "shots/after.png"))));
        assertTrue(AnalysisSpec.refuse(ok, VERBS).isEmpty(), AnalysisSpec.refuse(ok, VERBS).orElse(""));

        // run time: open/source_root resolve against the project root; report/screenshot stay exchange-relative;
        // a value bound absolute by the recaller is left alone (they chose it, and see it)
        var bound = AnalysisSpec.resolvePaths(ok.bind(Map.of("log", "/data/prod/audit.yaml")), java.nio.file.Path.of("/work/proj"));
        assertEquals("/data/prod/audit.yaml", bound.get(0).params().get("log"));
        assertEquals("/work/proj/build/processor.graphml", bound.get(0).params().get("graphml"));
        assertEquals(List.of("/work/proj/src/main/java"), bound.get(1).params().get("add"));
        assertEquals("incident.pdf", bound.get(2).params().get("path"), "exchange-relative, untouched");
        assertEquals("shots/after.png", bound.get(3).params().get("path"));
        assertEquals(bound.get(0).params().get("graphml"), AnalysisSpec.resolvePaths(bound, java.nio.file.Path.of("/elsewhere")).get(0).params().get("graphml"),
                "already absolute stays put");
        assertSame(ok.steps(), AnalysisSpec.resolvePaths(ok.steps(), null), "no project root: nothing to resolve against");
    }

    @Test
    void bindingSubstitutesEverywhere_andNamesWhatIsStillMissing() {
        AnalysisSpec a = breach();
        assertEquals(List.of("log"), a.unbound(Map.of()), "node has a default; log does not");
        assertEquals(List.of(), a.unbound(Map.of("log", "/x/a.yaml")));
        var bound = a.bind(Map.of("log", "/x/a.yaml"));
        assertEquals("/x/a.yaml", bound.get(0).params().get("log"));
        assertEquals("quotePublisher", bound.get(1).params().get("text"), "the default applied");
        assertEquals("Spread — quotePublisher", bound.get(2).params().get("name"), "inside a longer string");
        assertEquals(List.of("quotePublisher.spread"), bound.get(2).params().get("series"), "inside a list");
        assertEquals("orderTracker", a.bind(Map.of("log", "/x", "node", "orderTracker")).get(1).params().get("text"), "a supplied value beats the default");
    }

    @Test
    void theRunnerStopsAtTheFirstFailure_andReportsEveryStep() {
        AnalysisSpec a = breach();
        List<String> seen = new ArrayList<>();
        var run = AnalysisSpec.run(a.bind(Map.of("log", "/x/a.yaml")), step -> {
            seen.add(step.action());
            return step.action().equals("filter") ? ActionResult.error("no log is loaded") : ActionResult.ok(step.action(), "x", Map.of());
        });
        assertFalse(run.completed());
        assertEquals(2, run.stoppedAt());
        assertEquals(List.of("open", "filter"), seen, "graph never ran — step 3 on a failed step 2 answers nothing");
        assertTrue(run.steps().get(0).ok() && !run.steps().get(1).ok());
        assertEquals("no log is loaded", run.steps().get(1).error());
        var all = AnalysisSpec.run(a.bind(Map.of("log", "/x")), step -> ActionResult.ok(step.action(), "x", Map.of()));
        assertTrue(all.completed() && all.steps().size() == 3);
        var thrown = AnalysisSpec.run(a.bind(Map.of("log", "/x")), step -> { throw new IllegalStateException("boom"); });
        assertEquals("boom", thrown.steps().get(0).error(), "an exception is a failed step, not a crashed run");
    }

    @Test
    void persistenceRoundTrips_theLoaderDropsAndExplains_andTheShareCategoryTravelsByDefault(@TempDir Path dir) throws Exception {
        Properties p = new Properties();
        ConfigStore.writeAnalyses(p, List.of(breach()));
        List<AnalysisSpec> back = new ArrayList<>();
        assertTrue(ConfigStore.readAnalyses(p, back).isEmpty());
        assertEquals(List.of(breach()), back, "record equality: name, rationale, params, defaults, steps with their JSON params");

        p.setProperty("analysis.count", "2");
        p.setProperty("analysis.1.name", "evil");
        p.setProperty("analysis.1.step.count", "1");
        p.setProperty("analysis.1.step.0.action", "open");
        p.setProperty("analysis.1.step.0.params", "{\"project\": \"/tmp/x/.analyser/project.fluxtion-settings\"}");
        var refused = ConfigStore.readAnalyses(p, back);
        assertEquals(1, back.size());
        assertEquals(1, refused.size());
        assertTrue(refused.get(0).contains("session boundary"), refused.get(0));

        assertTrue(SettingsShare.Category.ANALYSES.defaultOn, "tier 2 travels: it can only drive this viewer");
        String label = SettingsShare.Category.ANALYSES.label.toLowerCase();
        assertTrue(label.contains("never a server") && label.contains("rationale"), label);
        assertTrue(ProjectProfile.PROJECT_SCOPED.contains(SettingsShare.Category.ANALYSES));

        SettingsShare share = new SettingsShare();
        AppConfig sender = new AppConfig();
        sender.analyses.add(breach());
        String text = share.export(sender, Set.of(SettingsShare.Category.ANALYSES), null);
        assertTrue(text.contains("analysis.0.name=spread breach"), text);
        assertFalse(share.export(sender, Set.of(SettingsShare.Category.SOURCE_ROOTS), null).contains("analysis."));
        AppConfig receiver = new AppConfig();
        var plan = share.preview(text + "analysis.count=2\nanalysis.1.name=evil\nanalysis.1.step.count=1\nanalysis.1.step.0.action=deploy\nanalysis.1.step.0.params={}\n", receiver);
        assertTrue(plan.summary().get(SettingsShare.Category.ANALYSES).contains("1 REFUSED"), plan.summary().toString());
        share.apply(plan, Set.of(SettingsShare.Category.ANALYSES), receiver);
        assertEquals(List.of("spread breach"), receiver.analyses.stream().map(AnalysisSpec::name).toList());

        Path file = dir.resolve(ProjectProfile.CANONICAL_RELATIVE);
        Files.createDirectories(file.getParent());
        assertTrue(ProjectProfile.save(file, sender, share));
        AppConfig loaded = new AppConfig();
        assertTrue(ProjectProfile.load(file, loaded, share).loaded());
        assertEquals(List.of(breach()), loaded.analyses);
        Files.writeString(file, Files.readString(file) + "analysis.count=2\nanalysis.1.name=evil\nanalysis.1.step.count=1\nanalysis.1.step.0.action=deploy\nanalysis.1.step.0.params={}\n");
        var res = ProjectProfile.load(file, loaded, share);
        assertTrue(res.message().contains("⚠ analyses:"), res.message());
        ProjectProfile.clearProjectScoped(loaded);
        assertTrue(loaded.analyses.isEmpty());
    }
}
