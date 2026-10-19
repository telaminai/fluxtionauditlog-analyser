package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import java.awt.GraphicsEnvironment;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.*;

/** Stub-assistant calls only: no CLI, model or credentials. Run unchanged-main witness before implementation. */
class M80DesignDiscoveryFrameTest {
    @Test void readableCandidateAndReadWarningsReachTheDesignPaneAndContext(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(),"real display required");
        Path project=Files.createDirectories(tmp.resolve("DEMO"));
        Path root=Files.createDirectories(project.resolve("src"));
        Path builder=Files.writeString(root.resolve("DEMOBuilder.java"),
                "import com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder;\n"
                +"class DEMOBuilder implements FluxtionGraphBuilder { void buildGraph(Config cfg) {} }");
        String expected=builder.toRealPath().toString();
        Files.write(root.resolve("bad.java"),new byte[]{(byte)0xff});
        Files.writeString(root.resolve("large.xml")," ".repeat(telamin.fluxtion.audit.analyser.analyser.design.DesignFiles.MAX_BYTES+1));
        Files.writeString(Files.createDirectories(project.resolve(".analyser")).resolve("project.fluxtion-settings"),
                "share.version=1\nsourceRoot.count=1\nsourceRoot.0=src\n");
        try(var f=new Frame(tmp)) {
            onEdt(()->{f.frame.setSize(1300,850);f.frame.setVisible(true);f.frame.validate();});
            assertTrue(f.ex.render("open",Map.of("project",project.toString())).ok());
            var shown=new java.util.concurrent.atomic.AtomicReference<String>("");
            long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
            do {
                onEdt(()->{
                    SourcePanel source=(SourcePanel)field(f.frame,"sourcePanel");
                    if(source.fileView()!=null && source.fileView().file().equals(expected))
                        shown.set(((javax.swing.JTextArea)field(source.designComponent(),"status")).getText());
                });
                if(shown.get().contains("2 files could not be read:"))break;
                Thread.sleep(20);
            } while(System.nanoTime()<until);
            assertTrue(shown.get().contains("2 files could not be read:"),"candidate and warning must reach the actual Design pane: "+shown.get());
            assertTrue(shown.get().contains("bad.java") && shown.get().contains("large.xml"));
            var context=f.ex.render("context",Map.of("sections",List.of("design")));
            assertEquals(1,((List<?>)find(context.payload(),"files")).size());
            assertTrue(String.valueOf(find(context.payload(),"note")).contains("2 files could not be read:"),context.toMap().toString());
            assertTrue(f.ex.render("source",Map.of("file",expected)).ok());
            onEdt(()->{
                var source=(SourcePanel)field(f.frame,"sourcePanel");
                var status=(javax.swing.JTextArea)field(source.designComponent(),"status");
                assertTrue(status.isShowing(),"the warning is on the visible Design pane");
                assertTrue(status.getText().contains("2 files could not be read:"),"explicit glances retain the warning");
            });
        }
    }
    @Test void aStubFindsTheBuilderInTwoCallsWithoutRefusals(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "real display required");
        Path project=Files.createDirectories(tmp.resolve("DEMO"));
        Path root=Files.createDirectories(project.resolve("src/main/java"));
        Path builder=Files.createDirectories(root.resolve("com/acme")).resolve("DEMOBuilder.java");
        Files.writeString(builder, "package com.acme;\n"
                + "import com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder;\n"
                + "public class DEMOBuilder implements FluxtionGraphBuilder {\n"
                + " public void buildGraph(EventProcessorConfig cfg) { cfg.addNode(new DEMO(), \"demo\"); }\n}\n");
        Path profile=Files.createDirectories(project.resolve(".analyser")).resolve("project.fluxtion-settings");
        Files.writeString(profile,"share.version=1\nsourceRoot.count=1\nsourceRoot.0=src/main/java\n");
        try (var f=new Frame(tmp)) {
            assertTrue(f.ex.render("open",Map.of("project",project.toString())).ok());
            // Wait for background preparation outside the measured two-call stub exchange. Main lacks this key,
            // so the identical stub runs immediately there and records its actual missing-selector refusal.
            long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
            var observed=new java.util.concurrent.atomic.AtomicReference<Map<String,Object>>();
            Runnable observe=()->observed.set(((telamin.fluxtion.audit.analyser.analyser.session.SessionDriver)
                    field(f.frame,"session")).processor().designSession.echo());
            onEdt(observe);
            while (observed.get().containsKey("kind") && !observed.get().containsKey("files") && System.nanoTime()<until) {
                Thread.sleep(20);
                onEdt(observe);
            }
            var first=f.ex.render("context",Map.of("sections",List.of("design")));
            Object found=find(first.payload(),"files");
            String path=found instanceof List<?> entries && !entries.isEmpty()
                    ? String.valueOf(((Map<?,?>)entries.getFirst()).get("path")) : "";
            ActionResult second=f.ex.render("source",Map.of("file",path));
            System.out.println("M80 stub: calls=2; context="+first.ok()+"; discovered="+!path.isEmpty()
                    +"; source="+second.ok()+"; refusal="+second.error());
            assertTrue(second.ok(), "★ T10 the discovered definition must open with no selector/root repair: "+second.error());
            assertEquals(builder.toRealPath().toString(), find(second.payload(),"file"));
            assertEquals("DESIGN", find(second.payload(),"mode"), "a builder is its wiring, not NODE logic");
        }
    }
}
