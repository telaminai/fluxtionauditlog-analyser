package com.acme.app;

// The three builder imports. Note they sit under TWO different roots.
// They come from fluxtion-builder-api, pulled in transitively — not from the artifact you declared.
import com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder;
import com.telamin.fluxtion.builder.compile.config.FluxtionCompilerConfig;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;

/** Maven's {@code scan} goal finds this at {@code process-classes} and calls it. */
public class AppGraphBuilder implements FluxtionGraphBuilder {

    @Override
    public void buildGraph(EventProcessorConfig cfg) {
        SensorState state = new SensorState();
        // Name every node you care about. An unnamed node still works but appears in the audit log
        // with a generated suffix (priceBook_1), which makes the log harder to read and to assert on.
        cfg.addNode(state, "sensorState");
        cfg.addNode(new ThresholdAlert(state), "thresholdAlert");     // the tree is reachable from what you add

        // WITHOUT THIS LINE THE AUDIT LOG IS SILENTLY EMPTY. Nothing warns you.
        cfg.addEventAudit(EventLogControlEvent.LogLevel.INFO);
    }

    @Override
    public void configureGeneration(FluxtionCompilerConfig cfg) {
        cfg.setClassName("AppProcessor");
        cfg.setPackageName("com.acme.app.generated");
    }
}
