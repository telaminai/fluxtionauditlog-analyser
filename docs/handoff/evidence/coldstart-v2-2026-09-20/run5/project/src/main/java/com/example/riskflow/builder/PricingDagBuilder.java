package com.example.riskflow.builder;

import com.example.riskflow.node.RootNode;
import com.telamin.fluxtion.builder.compile.config.FluxtionCompilerConfig;
import com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;
import com.example.riskflow.node.Enrich;
import com.example.riskflow.node.Risk;
import com.example.riskflow.node.Audit;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel;

/**
 * Authors the dispatch graph. Implements FluxtionGraphBuilder so the same recipe
 * works in-process (Fluxtion.compile, this project) and AOT (fluxtion-maven-plugin)
 * without change — see the project README.
 */
public class PricingDagBuilder implements FluxtionGraphBuilder {

    @Override
    public void buildGraph(EventProcessorConfig cfg) {
        RootNode rootNode = new RootNode();
        Enrich enrich = new Enrich(rootNode);
        Risk risk = new Risk(rootNode, enrich);
        Audit audit = new Audit(risk);
        cfg.addNode(audit); // terminal pulls in its ancestors via ctor refs
        cfg.addEventAudit(LogLevel.INFO);
    }

    @Override
    public void configureGeneration(FluxtionCompilerConfig cfg) {
        // Used only by AOT generation (./mvnw -Pgenerate-fluxtion); ignored in-process.
        cfg.setClassName("PricingDag");
        cfg.setPackageName("com.example.riskflow.generated");
        // google-java-format is excluded from the browser build (Open in Playground), so skip
        // Java-side formatting — the generated source is still valid; local maven AOT formats anyway.
        cfg.setFormatSource(false);
        // Emit GraalVM native-image reachability metadata (META-INF/native-image/...): makes
        // @ServiceRegistered service wiring work in a native image. Needs fluxtion >= 1.0.6.
        cfg.setGenerateReachabilityMetadata(true);
    }
}
