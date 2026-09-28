package telamin.fluxtion.audit.analyser.analyser.ui;

import java.util.*;
import javax.swing.SwingUtilities;
import telamin.fluxtion.audit.analyser.analyser.config.*;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.report.LogFingerprint;
import telamin.fluxtion.audit.analyser.analyser.walk.*;

/** Reviewer probe: exercises production authoring/resolution through the existing recording fixtures. */
public class ReviewProbe {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(ReviewProbe::run);
        System.exit(0);
    }
    static void run() {
        var rig = new WalkAuthoringTest.Rig();
        var run = new ArrayList<>(List.of("sha256:run-A"));
        var author = new WalkAuthoring(new WalkAuthoring.Frame() {
            public AppConfig config() { return rig.config; }
            public LogStore store() { return rig.store; }
            public FilterState filter() { return rig.filter; }
            public GraphTabs graphs() { return rig.graphs; }
            public TopologyPanel topology() { return rig.topology; }
            public String selectedTabWord() { return "graph"; }
            public int selectedRecord() { return 0; }
            public List<SpotlightOverlay.Lit> lit() { return rig.lit; }
            public List<String> runBasisNow() { return List.copyOf(run); }
            public LogFingerprint fingerprint() { return null; }
            public long generation() { return rig.generation; }
            public void persist() { rig.persisted++; }
        });
        rig.graphs.addGraph("DEMO");
        var chart = author.bind(new WalkSpec.Step("run A claim",
                new WalkSpec.View("graph", WalkSpec.Filter.ALL, null, "DEMO", null),
                List.of(new WalkSpec.Target("graph:DEMO", "run A value", null))));
        require(author.save("DEMO", "", List.of(chart), "person", rig.generation) == null, "initial save");
        var old = rig.config.walks.get(0);
        var facts = new WalkResolver.Facts() {
            public String recordRepresentation() { return "HeapLogStore"; }
            public int recordCount() { return rig.store.size(); }
            public String recordDigest(int i) { return WalkIdentity.recordDigest(rig.store.rawText(i)); }
            public List<String> runBasis() { return List.copyOf(run); }
            public String chartDefinitionDigest(String name) { return ConfigStore.chartDefinitionDigest(rig.graphs.specs().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow()); }
            public boolean chartHasExternalSeries(String name) { return false; }
            public WalkResolver.Drawn chartDrawn(String name) { return new WalkResolver.Drawn(true, "probe supplied drawn fact"); }
            public String graphDigest() { return null; }
            public boolean nodeExists(String id) { return false; }
        };
        run.set(0, "sha256:run-B"); rig.generation++;
        var before = WalkResolver.verdict(chart.targets().get(0), chart.view(), old.runBasis(), facts);
        var added = author.bind(new WalkSpec.Step("new comment", WalkSpec.View.NONE,
                List.of(new WalkSpec.Target("status", "new", null))));
        require(author.append("DEMO", new WalkAuthoring.Capture(added, List.of(), rig.generation)) == null, "append");
        var newer = rig.config.walks.get(0);
        var after = WalkResolver.verdict(chart.targets().get(0), chart.view(), newer.runBasis(), facts);
        System.out.println("append: old chart " + before.state() + " -> " + after.state()
                + "; old step unchanged=" + chart.equals(newer.steps().get(0)));
        var record = new WalkSpec.Target("records:row:0", "text", new WalkSpec.Basis("record",
                WalkIdentity.recordDigest(rig.store.rawText(0)), "DEMO-other-representation"));
        var repr = WalkResolver.verdict(record, WalkSpec.View.NONE, List.of(), facts);
        System.out.println("different representation: state=" + repr.state() + ", available=" + repr.available());
        var viewRig = new WalkPresenterTest.Rig();
        viewRig.filter.setAll(1L, 2L, FilterState.GroupMode.RAW_EVENT, Set.of("DEMO"), "dirty");
        var parsed = WalkSteps.parse(List.of(Map.of("view", Map.of("tab", "topology"), "targets", List.of("status"))));
        require(parsed.ok(), "valid tab-only view");
        new WalkPresenter(viewRig).apply(parsed.steps().get(0).view());
        System.out.println("omitted filter: group=" + viewRig.filter.groupMode() + ", text=" + viewRig.filter.text());
        var overflow = WalkSteps.parse(List.of(Map.of("view", Map.of("record", 4294967296L), "targets", List.of("records:row:0"))));
        System.out.println("record 4294967296: accepted=" + overflow.ok() + ", resolved=" + (overflow.ok() ? overflow.steps().get(0).view().record() : overflow.error()));
    }
    static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
