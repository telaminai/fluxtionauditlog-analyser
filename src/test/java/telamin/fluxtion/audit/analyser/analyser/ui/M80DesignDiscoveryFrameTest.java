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
    @Test void anAcceptedJavaFollowReadRefreshesAndRebindsTheVisibleDeclaration(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "real display required");
        Path root=Files.createDirectories(tmp.resolve("definitions"));
        String original="import com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder;\n"
                +"class DEMOBuilder implements FluxtionGraphBuilder {\n void buildGraph(Config cfg) {\n"
                +" cfg.addNode(new DEMO(), \"a\");\n }\n}\n";
        Path builder=Files.writeString(root.resolve("DEMOBuilder.java"),original);
        try(var f=new Frame(tmp)) {
            onEdt(()->{
                ((telamin.fluxtion.audit.analyser.analyser.config.AppConfig)field(f.frame,"config")).sourceRoots.add(root.toString());
                f.frame.setSize(1300,850);f.frame.setVisible(true);f.frame.validate();
            });
            assertTrue(f.ex.render("open",Map.of("design",builder.toString())).ok());
            onEdt(()->{
                ((javax.swing.Timer)field(f.frame,"designFollowTimer")).stop();
                var result=f.ex.render("spotlight",Map.of("target","source:design:node:a"));
                assertTrue(result.ok(),result.toMap().toString());
            });
            String revised=original.replace(" cfg.addNode", "\n cfg.addNode");
            Files.writeString(builder,revised);
            onEdt(()->{
                try { var poll=MainFrame.class.getDeclaredMethod("pollDesign");poll.setAccessible(true);poll.invoke(f.frame); }
                catch(ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            var accepted=new java.util.concurrent.atomic.AtomicBoolean();
            long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
            do {
                onEdt(()->accepted.set(((telamin.fluxtion.audit.analyser.analyser.session.SessionDriver)field(f.frame,"session"))
                        .processor().designSession.builder().text().equals(revised)));
                if(accepted.get())break;
                Thread.sleep(20);
            } while(System.nanoTime()<until);
            assertTrue(accepted.get(),"control: the session accepted the changed Java revision");
            onEdt(()->{});
            onEdt(()->{
                var source=(SourcePanel)field(f.frame,"sourcePanel");
                assertEquals(revised,source.fileView().text(),"an accepted Java follow revision must reach the visible Design pane");
                assertEquals(5,source.fileView().line(),"the named declaration rebinds after a line is inserted");
                assertTrue(((SpotlightOverlay)field(f.frame,"spotlight")).isLit(),"a unique named target stays lit");
                assertTrue(((DesignSourcePanel)source.designComponent()).lineBounds(5).isPresent());
            });
        }
    }
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
    @Test void nodeAndBeanSynonymsLightTheSameJavaDeclaration(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "real display required");
        Path root=Files.createDirectories(tmp.resolve("definitions"));
        Path builder=Files.writeString(root.resolve("DEMOBuilder.java"),
                "import com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder;\n"
                +"class DEMOBuilder implements FluxtionGraphBuilder {\n void buildGraph(Config cfg) {\n"
                +" cfg.addNode(\n  new DEMO(),\n  \"a\");\n cfg.addNode(new Other(), computedName);\n }\n}\n");
        try(var f=new Frame(tmp)) {
            onEdt(()->{
                ((telamin.fluxtion.audit.analyser.analyser.config.AppConfig)field(f.frame,"config")).sourceRoots.add(root.toString());
                f.frame.setSize(1300,850);f.frame.setVisible(true);f.frame.validate();
            });
            assertTrue(f.ex.render("open",Map.of("design",builder.toString())).ok());
            var declaration=f.ex.render("source",Map.of("bean","a"));
            assertTrue(declaration.ok(),declaration.toMap().toString());
            assertEquals(4,find(declaration.payload(),"line"),"the topology declaration path lands at the call token");
            var nodes=(List<?>)find(f.ex.render("context",Map.of("sections",List.of("design"))).payload(),"nodes");
            assertEquals("a",((Map<?,?>)nodes.getFirst()).get("name"));
            assertEquals(Boolean.FALSE,((Map<?,?>)nodes.getLast()).get("anchored"));
            for(String spelling:List.of("node","bean")) onEdt(()->{
                String target="source:design:"+spelling+":a";
                var result=f.ex.render("spotlight",Map.of("target",target));
                assertTrue(result.ok(),"★ T7 both spellings must light the same DESIGN anchor: "+result.toMap());
                var panel=(SourcePanel)field(f.frame,"sourcePanel");
                assertEquals(SourcePanel.Mode.DESIGN,field(panel,"mode"));
                assertEquals(4,panel.fileView().line());
                assertTrue(((SpotlightOverlay)field(f.frame,"spotlight")).isLit());
                var design=(DesignSourcePanel)panel.designComponent();
                var index=(javax.swing.JList<?>)field(design,"beans");
                assertEquals(2,index.getModel().getSize());
                assertTrue(index.getModel().getElementAt(1).toString().contains("name not literal"));
                assertTrue(design.lineBounds(4).isPresent(),"the lit declaration is actually visible");
            });
        }
    }

    /**
     * M80 PR1 (line ranges) merged with M80 PR2 (the node synonym): both widen the SAME {@code DESIGN_BEAN}/
     * {@code DESIGN_LINE} families, and both must work on the SAME Java builder document — the node synonym
     * finding the call token's line, and a range spanning the whole multi-line call it lives in.
     */
    @Test void nodeSynonymAndLineRangeBothLightTheSameJavaBuilder(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "real display required");
        Path root=Files.createDirectories(tmp.resolve("definitions"));
        Path builder=Files.writeString(root.resolve("DEMOBuilder.java"),
                "import com.telamin.fluxtion.builder.compile.config.FluxtionGraphBuilder;\n"
                +"class DEMOBuilder implements FluxtionGraphBuilder {\n void buildGraph(Config cfg) {\n"
                +" cfg.addNode(\n  new DEMO(),\n  \"a\");\n cfg.addNode(new Other(), computedName);\n }\n}\n");
        try(var f=new Frame(tmp)) {
            onEdt(()->{
                ((telamin.fluxtion.audit.analyser.analyser.config.AppConfig)field(f.frame,"config")).sourceRoots.add(root.toString());
                f.frame.setSize(1300,850);f.frame.setVisible(true);f.frame.validate();
            });
            assertTrue(f.ex.render("open",Map.of("design",builder.toString())).ok());
            // the node synonym lights the declaration — the call token's line, same as bean:a already does
            var node=f.ex.render("spotlight",Map.of("target","source:design:node:a"));
            assertTrue(node.ok(),node.toMap().toString());
            onEdt(()->{
                var panel=(SourcePanel)field(f.frame,"sourcePanel");
                assertEquals(4,panel.fileView().line(),"node:a lands at the call token, same as bean:a");
                assertTrue(((DesignSourcePanel)panel.designComponent()).lineBounds(4).isPresent());
            });
            // the SAME Java builder document also accepts a line RANGE spanning the whole addNode(...) call (4-6)
            String RANGE="source:design:line:4-6";
            var range=f.ex.render("spotlight",Map.of("target",RANGE));
            assertTrue(range.ok(),range.toMap().toString());
            @SuppressWarnings("unchecked") var lit=((List<Map<String,Object>>)range.payload().get("lit")).getFirst();
            assertEquals(RANGE,lit.get("target"),"the range is echoed back exactly");
            assertEquals(4,lit.get("line"));assertEquals(6,lit.get("toLine"));
            assertEquals(false,lit.get("partial"),"three short lines fit the design panel");
            onEdt(()->{
                var panel=(SourcePanel)field(f.frame,"sourcePanel");
                var design=(DesignSourcePanel)panel.designComponent();
                try {
                    var docRoot=design.text.getDocument().getDefaultRootElement();
                    var first=design.text.modelToView2D(docRoot.getElement(3).getStartOffset());   // line 4 (0-based 3)
                    var last=design.text.modelToView2D(docRoot.getElement(5).getEndOffset()-1);    // line 6 (0-based 5)
                    int expectedHeight=(int)Math.ceil(last.getMaxY())-(int)Math.floor(first.getY());
                    assertEquals(expectedHeight,((SpotlightOverlay)field(f.frame,"spotlight")).lit().getFirst().bounds().height,
                            "the range's cut-out spans the whole addNode(...) call: first line's top to last line's bottom");
                } catch(Exception ex){throw new AssertionError(ex);}
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
