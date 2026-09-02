package com.acme.app;

// The three builder imports. Note they sit under TWO different roots.
// They come from fluxtion-builder-api, pulled in transitively — not from the artifact you declared.
import com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder;
import com.telamin.fluxtion.builder.compile.config.FluxtionCompilerConfig;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;

/**
 * Maven's {@code scan} goal finds this at {@code process-classes} and calls it.
 *
 * <p><b>This class is what makes generation happen.</b> The goal scans for implementations of
 * {@link FluxtionGraphBuilder}; if there is none, it silently generates nothing and the build then
 * fails as though the plugin were broken. Rename it or rewrite it freely, but keep a class that
 * implements this interface. One author deleted it while replacing the example nodes, concluded the
 * plugin did not work, and hand-wrote a processor into the generated package — which is never the fix.
 */
public class AppGraphBuilder implements FluxtionGraphBuilder {

    @Override
    public void buildGraph(EventProcessorConfig cfg) {
        SensorState state = new SensorState();
        LimitStore limits = new LimitStore();
        // Name every node you care about. An unnamed node still works but appears in the audit log
        // with a generated suffix (sensorState_1), which makes the log harder to read and to assert on.
        cfg.addNode(state, "sensorState");
        cfg.addNode(limits, "limitStore");
        cfg.addNode(new ThresholdAlert(state, limits), "thresholdAlert");     // the tree is reachable from what you add

        // WITHOUT THIS LINE THE AUDIT LOG IS SILENTLY EMPTY. Nothing warns you.
        cfg.addEventAudit(EventLogControlEvent.LogLevel.INFO);
    }

    @Override
    public void configureGeneration(FluxtionCompilerConfig cfg) {
        cfg.setClassName("AppProcessor");
        cfg.setPackageName("com.acme.app.generated");
    }
}
