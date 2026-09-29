package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import java.util.List;

/** The DEMO introduction uses the ordinary saved-walk model and playback path. */
final class DemoTour {
    static final String NAME = "DEMO product introduction";
    static final String TITLE = "A quick tour of the analyser";

    private DemoTour() { }

    static List<WalkSpec.Step> steps() {
        return List.of(
                new WalkSpec.Step("Start with the audit records: each row shows a processor cycle. Select one to inspect what happened.",
                        new WalkSpec.View("summary", null, null, null, null),
                        List.of(new WalkSpec.Target("records", "The run's recorded cycles", null))),
                new WalkSpec.Step("Topology connects those cycles to the processor design. The coverage verdict distinguishes nodes that logged from nodes that did not.",
                        new WalkSpec.View("topology", null, null, null, null),
                        List.of(new WalkSpec.Target("topology", "The processor graph", null),
                                new WalkSpec.Target("topology:verdict", "What this log can say about coverage", null))),
                new WalkSpec.Step("A selected record opens its detail. This DEMO log and graph are bundled together, so you can explore without a server or key.",
                        new WalkSpec.View("summary", null, 0, null, null),
                        List.of(new WalkSpec.Target("records:row:0", "The first recorded cycle", null),
                                new WalkSpec.Target("detail", "Its event and node details", null))),
                new WalkSpec.Step("This tour is a saved spotlight walk in the sample project. Replay it from Reports, or make your own walk from a spotlight.",
                        new WalkSpec.View("reports", null, null, null, null),
                        List.of(new WalkSpec.Target("tab:reports", "Saved reports and spotlight walks", null))));
    }
}
