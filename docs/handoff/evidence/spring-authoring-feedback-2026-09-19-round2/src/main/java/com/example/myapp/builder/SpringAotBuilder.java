package com.example.myapp.builder;

import com.telamin.fluxtion.builder.compile.config.FluxtionCompilerConfig;
import com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder;
import com.telamin.fluxtion.builder.extern.spring.FluxtionSpring;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;

/**
 * AOT generation entry for the Spring topology — discovered by the fluxtion-maven-plugin 'scan'
 * goal (and the playground BuilderRunner). buildGraph applies application-context.xml; the scan
 * then emits MyProcessor (package com.example.myapp.generated), which FluxtionMain loads.
 */
public class SpringAotBuilder implements FluxtionGraphBuilder {

    @Override
    public void buildGraph(EventProcessorConfig cfg) {
        GenericApplicationContext ctx = new GenericApplicationContext();
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(ctx);
        reader.setValidating(false);
        reader.setNamespaceAware(false);
        reader.loadBeanDefinitions(new ClassPathResource("application-context.xml"));
        ctx.refresh();
        FluxtionSpring.applyContext(cfg, ctx);
    }

    @Override
    public void configureGeneration(FluxtionCompilerConfig cfg) {
        cfg.setClassName("MyProcessor");
        cfg.setPackageName("com.example.myapp.generated");
        // Emit GraalVM native-image reachability metadata (META-INF/native-image/...): makes
        // @ServiceRegistered service wiring work in a native image. Needs fluxtion >= 1.0.6.
        cfg.setGenerateReachabilityMetadata(true);
    }
}
