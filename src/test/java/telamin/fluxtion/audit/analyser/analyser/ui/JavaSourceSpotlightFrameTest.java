package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.source.*;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Point;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.*;

/** Constructed regression cases, not a replay of participant evidence. */
class JavaSourceSpotlightFrameTest {
    static final String TARGET="source:java:com.acme.Node:line:3";
    static SourceService service(Frame f) { return (SourceService)field(f.frame,"sourceService"); }
    static SpotlightOverlay overlay(Frame f) { return (SpotlightOverlay)field(f.frame,"spotlight"); }
    static Path source(Path tmp) throws Exception {
        Path p=tmp.resolve("src/com/acme/Node.java"); Files.createDirectories(p.getParent());
        Files.writeString(p,"package com.acme;\npublic class Node {\n    public int value = 42;\n"+"    // context\n".repeat(180)+"}\n"); return p;
    }
    static void show(Frame f,Path root) throws Exception {
        onEdt(()->{service(f).configure(List.of(root.toString()),null);((AppConfig)field(f.frame,"config")).sourceRoots.add(root.toString());f.frame.setSize(1500,950);f.frame.setVisible(true);f.frame.validate();});
        onEdt(()->{});onEdt(()->{});
    }
    @Test void graphAndJavaUseOneVisibleDestinationInEitherOrder(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless()); Path src=source(tmp);
        Path graph=Files.writeString(tmp.resolve("demo.graphml"),graph("node"));
        try(var f=new Frame(tmp)) {
            show(f,tmp.resolve("src"));
            {
                Path log=Files.writeString(tmp.resolve("example.yml"),log("node"));
                assertTrue(f.ex.render("open",Map.of("log",log.toString())).ok());awaitLoaded(f.ex);
                onEdt(()->{var tabs=(JTabbedPane)field(f.frame,"sideTabs");var parent=tabs.getParent();
                    if(parent instanceof JSplitPane split) split.setDividerLocation(300);
                    ((TopologyPanel)field(f.frame,"topologyPanel")).sourceViewer().setMode(SourcePanel.Mode.NODE);
                });
            }
            assertTrue(f.ex.render("open",Map.of("graphml",graph.toString())).ok());
            for(boolean initiallyOpen:List.of(false,true)) for(var targets:List.of(List.of("topology:node:node",TARGET),List.of(TARGET,"topology:node:node"))) {
                onEdt(()->{var topology=(TopologyPanel)field(f.frame,"topologyPanel");
                    if(initiallyOpen)topology.ensureSourcePaneVisible();else topology.setSourcePaneVisible(false);f.frame.validate();});
                onEdt(()->{});onEdt(()->{});
                var result=f.ex.render("spotlight",Map.of("targets",targets.stream().map(t -> Map.of("target",t,"caption",t.equals(TARGET) ? "Inspect this declaration" : "The node being discussed")).toList()));
                assertTrue(result.ok(),result.toMap().toString());
                onEdt(()->{
                    assertEquals(2,overlay(f).lit().size());
                    var topo=(TopologyPanel)field(f.frame,"topologyPanel");assertTrue(topo.isShowing());assertNotNull(topo.openSourcePane());
                    var lit=javaEcho(result);assertEquals("topology-source",lit.get("destination"));
                    assertEquals(src.toString(),lit.get("file"));assertEquals("first-match",lit.get("selectionPolicy"));
                    assertEquals("unverified",lit.get("relationship"));assertEquals(false,lit.get("partial"));
                    assertTrue(overlay(f).lit().stream().filter(l->l.target().equals(TARGET)).findFirst().orElseThrow().bounds().width>30,"line band is wider than a caret");
                });
                var context=f.ex.render("context",Map.of());
                assertTrue(context.toMap().toString().contains(javaEcho(result).get("revision").toString()),"context retains the same revision as the echo");
                if(System.getProperty("sourceSpotlight.capture")!=null && initiallyOpen) {
                    Path image=Path.of(System.getProperty("sourceSpotlight.capture")).toAbsolutePath();Files.createDirectories(image.getParent());Files.deleteIfExists(image);
                    onEdt(()->{var cfg=(AppConfig)field(f.frame,"config");cfg.assistantExports=true;cfg.assistantExportDir=image.getParent().toString();});
                    var shot=f.ex.render("screenshot",Map.of("path",image.toString()));assertTrue(shot.ok(),shot.toMap().toString());
                }
            }
        }
    }
    @Test void changedSelectedProcessorUpdatesModelAndBadBatchDoesNotReveal(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());Path file=source(tmp);
        Files.writeString(file,"package com.acme;\npublic class Node {\n public OldType child;\n}\n");
        try(var f=new Frame(tmp)) {
            show(f,tmp.resolve("src"));onEdt(()->{service(f).select("com.acme.Node");assertEquals("com.acme.OldType",service(f).fqnForInstance("child"));});
            Files.writeString(file,"package com.acme;\npublic class Node {\n public NewType child;\n}\n");
            var r=f.ex.render("spotlight",Map.of("target",TARGET));assertTrue(r.ok(),r.toMap().toString());
            onEdt(()->assertEquals("com.acme.NewType",service(f).fqnForInstance("child")));
            Files.writeString(file.resolveSibling("Other.java"),"package com.acme; class Other {}\n");
            assertTrue(f.ex.render("spotlight",Map.of("target","source:java:com.acme.Other")).ok());
            onEdt(()->assertEquals("com.acme.NewType",service(f).fqnForInstance("child"),"non-selected reread leaves model unchanged"));
            assertTrue(f.ex.render("spotlight",Map.of("target",TARGET)).ok());
            var bad=f.ex.render("spotlight",Map.of("targets",List.of("source:java:com.acme.Node","source:java:com.acme.Missing")));
            assertFalse(bad.ok());onEdt(()->assertEquals(List.of(TARGET),overlay(f).lit().stream().map(SpotlightOverlay.Lit::target).toList()));
        }
    }
    @Test void realEntranceRereadsJarMissAndHitButNewJarNeedsReconfiguration(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path repo=Files.createDirectories(tmp.resolve("repo")), jar=repo.resolve("demo-sources.jar");
        jar(jar,Map.of("com/acme/Other.java","package com.acme; class Other {}"));
        try(var f=new Frame(tmp)) {
            show(f,tmp.resolve("src"));onEdt(()->service(f).configure(List.of(),null,List.of(repo.toString()),true));
            var miss=f.ex.render("spotlight",Map.of("target",TARGET));assertFalse(miss.ok());
            jar(jar,Map.of("com/acme/Node.java","package com.acme;\nclass Node {\n int value=1;\n}\n"));
            var first=f.ex.render("spotlight",Map.of("target",TARGET));assertTrue(first.ok(),first.toMap().toString());
            assertEquals(jar.toString(),javaEcho(first).get("archive"));
            assertFalse(f.ex.render("source",Map.of("fqn","com.acme.Node")).ok(),"root-only glance never reads a jar");
            assertTrue(f.ex.render("spotlight",Map.of("target",TARGET)).ok());
            jar(jar,Map.of("com/acme/Node.java","package com.acme;\nclass Node {\n int value=2;\n}\n"));
            var replaced=f.ex.render("spotlight",Map.of("target","source:java:com.acme.Node","add",true));
            assertTrue(replaced.ok(),replaced.toMap().toString());
            assertNotEquals(javaEcho(first).get("revision"),javaEcho(replaced).get("revision"));
            assertEquals(List.of(TARGET),replaced.payload().get("wentOut"),"retained line cannot move to new bytes");
            jar(repo.resolve("new-sources.jar"),Map.of("com/acme/Later.java","package com.acme; class Later {}"));
            assertFalse(f.ex.render("spotlight",Map.of("target","source:java:com.acme.Later")).ok());
            onEdt(()->service(f).configure(List.of(),null,List.of(repo.toString()),true));
            assertTrue(f.ex.render("spotlight",Map.of("target","source:java:com.acme.Later")).ok());
        }
    }

    @Test void duplicateRootPolicyIsDisclosedAndGlanceStillRefuses(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());Path first=source(tmp.resolve("first")), second=source(tmp.resolve("second"));
        Files.writeString(second,"package com.acme;\nclass Node {\n int different=7;\n}\n");
        try(var f=new Frame(tmp)) {
            show(f,tmp.resolve("first/src"));
            onEdt(()->{((AppConfig)field(f.frame,"config")).sourceRoots.add(tmp.resolve("second/src").toString());
                service(f).configure(List.of(tmp.resolve("first/src").toString(),tmp.resolve("second/src").toString()),null);});
            var a=f.ex.render("spotlight",Map.of("target",TARGET));assertTrue(a.ok(),a.toMap().toString());assertEquals(first.toString(),javaEcho(a).get("file"));
            assertFalse(f.ex.render("source",Map.of("fqn","com.acme.Node")).ok());
            onEdt(()->service(f).configure(List.of(tmp.resolve("second/src").toString(),tmp.resolve("first/src").toString()),null));
            var b=f.ex.render("spotlight",Map.of("target",TARGET));assertTrue(b.ok(),b.toMap().toString());assertEquals(second.toString(),javaEcho(b).get("file"));
        }
    }

    @Test void blockedArchiveReadLeavesEdtFreeAndClearConfigurationAndNewRequestSupersedeIt(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());Path root=Files.createDirectories(tmp.resolve("src/com/acme"));
        Files.writeString(root.resolve("Ready.java"),"package com.acme; class Ready {}\n");
        Path repo=Files.createDirectories(tmp.resolve("repo"));
        try(var f=new Frame(tmp)) {
            show(f,tmp.resolve("src"));
            for(String change:List.of("clear","configuration","new-request","view")) {
                onEdt(()->{
                    service(f).configure(List.of(tmp.resolve("src").toString()),"com.acme.Node",List.of(repo.toString()),true);
                    service(f).acceptSpotlightModel(service(f).captureLookup(),"com.acme.Node",EventProcessorModel.parse("com.acme.Node",
                            "package com.acme;\nclass Node {\n public OldType child;\n}\n"));
                });
                Object archives=field(service(f),"maven");
                CompletableFuture<ActionResult> pending;
                synchronized(archives) {
                    pending=CompletableFuture.supplyAsync(()->f.ex.render("spotlight",Map.of("target",TARGET)));
                    // This is the actual resolver's discovery lock, not a substitute reader/helper entrance.
                    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);Thread reader=null;
                    while(System.nanoTime()<deadline && reader==null) {
                        for(var e:Thread.getAllStackTraces().entrySet())
                            if(e.getKey().getState()==Thread.State.BLOCKED && java.util.Arrays.stream(e.getValue()).anyMatch(st->st.getMethodName().equals("jarList"))) reader=e.getKey();
                        if(reader==null) Thread.sleep(10);
                    }
                    assertNotNull(reader,"archive read must be reached");
                    assertTrue(reader.getName().startsWith("analyser-bg-"),"archive read must be off EDT");
                    var sentinel=new CountDownLatch(1);SwingUtilities.invokeLater(sentinel::countDown);
                    assertTrue(sentinel.await(3,TimeUnit.SECONDS),"EDT sentinel must run while archive read blocks");
                    if(change.equals("clear")) assertTrue(f.ex.render("spotlight",Map.of("clear",true)).ok());
                    else if(change.equals("configuration")) onEdt(()->service(f).configure(List.of(tmp.resolve("src").toString()),null));
                    else if(change.equals("new-request")) assertTrue(f.ex.render("spotlight",Map.of("target","source:java:com.acme.Ready")).ok());
                    else onEdt(()->{var tabs=(JTabbedPane)field(f.frame,"sideTabs");tabs.setSelectedIndex((tabs.getSelectedIndex()+1)%tabs.getTabCount());});
                    jar(repo.resolve("demo-sources.jar"),Map.of("com/acme/Node.java","package com.acme;\nclass Node {\n public NewType child;\n}\n"));
                }
                var result=pending.get(10,TimeUnit.SECONDS);assertFalse(result.ok(),change+": superseded read must refuse");
                assertTrue(result.toMap().toString().contains("superseded"),result.toMap().toString());
                onEdt(()->{
                    assertTrue(overlay(f).lit().stream().noneMatch(l->l.target().equals(TARGET)),"no late resurrection");
                    if(change.equals("configuration"))assertNull(service(f).fqnForInstance("child"));
                    else assertEquals("com.acme.OldType",service(f).fqnForInstance("child"),"superseded reread must not replace selected model");
                });
                f.ex.render("spotlight",Map.of("clear",true));
            }
        }
    }

    @Test void preparationDeadlineRefusesBeforeReadReturnsAndLateCompletionCannotLight(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path repo=Files.createDirectories(tmp.resolve("repo"));
        jar(repo.resolve("demo-sources.jar"),Map.of("com/acme/Node.java","package com.acme;\nclass Node {\n int value=1;\n}\n"));
        try(var f=new Frame(tmp)) {
            show(f,tmp.resolve("src"));
            onEdt(()->service(f).configure(List.of(),null,List.of(repo.toString()),true));
            var timeout=MainFrame.class.getDeclaredField("javaSourcePreparationTimeout");timeout.setAccessible(true);
            onEdt(()->{try{timeout.set(f.frame,java.time.Duration.ofMillis(200));}catch(Exception e){throw new AssertionError(e);}});
            var finished=new CountDownLatch(1);Object archives=field(service(f),"maven");
            synchronized(archives) {
                var pending=CompletableFuture.supplyAsync(()->f.ex.render("spotlight",Map.of("target",TARGET)));
                long startDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                boolean entered=false;
                while(System.nanoTime()<startDeadline && !entered) {
                    entered=Thread.getAllStackTraces().values().stream().anyMatch(st->java.util.Arrays.stream(st).anyMatch(e->e.getMethodName().equals("jarList")));
                    if(!entered)Thread.sleep(5);
                }
                assertTrue(entered,"deadline witness reaches actual blocked discovery");
                var refusal=pending.get(5,TimeUnit.SECONDS);
                assertFalse(refusal.ok());assertTrue(refusal.toMap().toString().contains("deadline expired"),refusal.toMap().toString());
                onEdt(()->assertFalse(overlay(f).isLit()));
            }
            // A sentinel enqueued by a second lookup on the same discovery monitor is after the late read.
            telamin.fluxtion.audit.analyser.analyser.core.Background.run(()->{synchronized(archives){return true;}},v->finished.countDown(),e->finished.countDown());
            assertTrue(finished.await(5,TimeUnit.SECONDS));
            long settleDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(Thread.getAllStackTraces().values().stream().anyMatch(st->java.util.Arrays.stream(st)
                    .anyMatch(e->e.getClassName().endsWith("Background") || e.getClassName().endsWith("JavaSpotlightPlan")))) {
                assertTrue(System.nanoTime()<settleDeadline,"late worker must finish after release");Thread.sleep(5);
            }
            onEdt(()->{});onEdt(()->{});
            onEdt(()->{assertFalse(overlay(f).isLit(),"late read after deadline must never light");
                assertTrue(((Map<?,?>)field(f.frame,"javaSpotlightBindings")).isEmpty());});
        }
    }

    @Test void interruptedCallerCancelsOnlyItsOwnPreparation(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());Path repo=Files.createDirectories(tmp.resolve("repo"));
        jar(repo.resolve("demo-sources.jar"),Map.of("com/acme/Node.java","package com.acme;\nclass Node {\n int value=1;\n}\n"));
        try(var f=new Frame(tmp)) {
            show(f,tmp.resolve("src"));onEdt(()->service(f).configure(List.of(),null,List.of(repo.toString()),true));
            var result=new AtomicReference<ActionResult>();var interrupted=new AtomicBoolean();
            Thread caller=new Thread(()->{result.set(f.ex.render("spotlight",Map.of("target",TARGET)));interrupted.set(Thread.currentThread().isInterrupted());});
            Object archives=field(service(f),"maven");
            synchronized(archives) {
                caller.start();
                long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean entered=false;
                while(System.nanoTime()<end && !entered) {
                    entered=Thread.getAllStackTraces().values().stream().anyMatch(st->java.util.Arrays.stream(st).anyMatch(e->e.getMethodName().equals("jarList")));
                    if(!entered)Thread.sleep(5);
                }
                assertTrue(entered);caller.interrupt();caller.join(2000);
                assertFalse(caller.isAlive());assertFalse(result.get().ok());assertTrue(interrupted.get());
                assertTrue(f.ex.render("spotlight",Map.of("target","status")).ok());
            }
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(Thread.getAllStackTraces().values().stream().anyMatch(st->java.util.Arrays.stream(st).anyMatch(e->e.getClassName().endsWith("JavaSpotlightPlan")))) {
                assertTrue(System.nanoTime()<end);Thread.sleep(5);
            }
            onEdt(()->{});onEdt(()->assertEquals(List.of("status"),overlay(f).lit().stream().map(SpotlightOverlay.Lit::target).toList(),"abandoned request neither lights late nor clears a newer spotlight"));
        }
    }

    @Test void invalidJavaBeforeRecordRevealKeepsSelectionAndFilter(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());source(tmp);Path log=Files.writeString(tmp.resolve("demo.yml"),log("node"));
        try(var f=new Frame(tmp)) {
            show(f,tmp.resolve("src"));assertTrue(f.ex.render("open",Map.of("log",log.toString())).ok());awaitLoaded(f.ex);
            onEdt(()->assertTrue(f.ex.render("filter",Map.of("text","absent")).ok()));
            var table=(LogTablePanel)field(f.frame,"tablePanel");AtomicInteger rows=new AtomicInteger(),selection=new AtomicInteger();
            onEdt(()->{rows.set(table.table().getRowCount());selection.set(table.table().getSelectedRow());assertEquals(0,rows.get(),"negative control starts with a filtered-out record");});
            var invalid=f.ex.render("spotlight",Map.of("targets",List.of("records:row:0","source:java:com.acme.Missing")));
            assertFalse(invalid.ok());onEdt(()->{assertEquals(rows.get(),table.table().getRowCount());assertEquals(selection.get(),table.table().getSelectedRow());});
        }
    }

    @Test void viewportHooksRemeasureWithoutAnotherRequestAndBindingsNeverRetarget(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());source(tmp);
        try(var f=new Frame(tmp)) {
            show(f,tmp.resolve("src"));
            for(boolean embedded:List.of(false,true)) {
                if(embedded) assertTrue(f.ex.render("spotlight",Map.of("target","tab:topology")).ok());
                var first=f.ex.render("spotlight",Map.of("target",TARGET));assertTrue(first.ok(),first.toMap().toString());
                onEdt(()->{
                    var anchor=anchor(f,TARGET);var scroll=(JScrollPane)field(anchor.component(),"scroll");
                    scroll.getViewport().setViewPosition(new Point(0,1000));
                });
                onEdt(()->{});onEdt(()->assertFalse(overlay(f).isLit(),"viewport callback must extinguish scrolled-away line"));
                assertTrue(f.ex.render("spotlight",Map.of("target",TARGET)).ok());
                onEdt(()->{
                    var binding=((Map<?,?>)field(f.frame,"javaSpotlightBindings")).get(TARGET);
                    var viewer=(SourcePanel)field(binding,"viewer");
                    viewer.openFqn("com.acme.NoSuch");
                });
                onEdt(()->{});onEdt(()->{
                    assertFalse(overlay(f).isLit(),"same line in a replacement document is not the old anchor");
                    assertTrue(((Map<?,?>)field(f.frame,"javaSpotlightBindings")).isEmpty());
                });
            }
        }
    }

    @Test void wrappedLogicalLineMeasuresAllRowsClipsAndReportsPartial(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());Path file=source(tmp);
        // One moderately wrapped line and one deliberately taller than the visible viewport.
        Files.writeString(file,"package com.acme;\nclass Node {\n // "+"word ".repeat(100)+"\n // "+"long ".repeat(4000)+"\n}\n");
        try(var f=new Frame(tmp)) {
            show(f,tmp.resolve("src"));var panel=(SourcePanel)field(f.frame,"sourcePanel");
            onEdt(()->{for(var c:((JPanel)panel.getComponent(0)).getComponents())if(c instanceof JCheckBox box && box.getText().equals("Wrap"))box.doClick();});
            var r=f.ex.render("spotlight",Map.of("target",TARGET));assertTrue(r.ok(),r.toMap().toString());
            onEdt(()->{
                var a=anchor(f,TARGET);var text=(JTextPane)field(a.component(),"text");
                var element=text.getDocument().getDefaultRootElement().getElement(2);
                try {
                    var first=text.modelToView2D(element.getStartOffset());var last=text.modelToView2D(element.getEndOffset()-1);
                    int expected=(int)Math.ceil(last.getMaxY())-(int)Math.floor(first.getY());
                    assertTrue(expected>first.getHeight(),"fixture must wrap across multiple rows");
                    assertEquals(expected,overlay(f).lit().getFirst().bounds().height,"all visual rows form the logical line band");
                }catch(Exception ex){throw new AssertionError(ex);}
            });
            var tall=f.ex.render("spotlight",Map.of("target","source:java:com.acme.Node:line:4"));assertTrue(tall.ok(),tall.toMap().toString());
            assertEquals(true,javaEcho(tall).get("partial"));
            onEdt(()->{
                var a=anchor(f,"source:java:com.acme.Node:line:4");var text=(JTextPane)field(a.component(),"text");
                Rectangle viewport=SwingUtilities.convertRectangle(text,text.getVisibleRect(),overlay(f));
                Rectangle lit=overlay(f).lit().getFirst().bounds();
                assertTrue(viewport.contains(lit),"clipped band must stay inside the viewport");
                assertTrue(lit.height>100,"tall line lights all visible rows, not its first row");
                var scroll=(JScrollPane)field(a.component(),"scroll");scroll.getViewport().setViewPosition(new Point(0,scroll.getViewport().getViewPosition().y+7));
            });
            onEdt(()->{});onEdt(()->assertEquals(1,overlay(f).lit().size(),"partly visible Java line stays lit"));
            var whole=f.ex.render("spotlight",Map.of("target","source:java:com.acme.Node"));assertTrue(whole.ok());
            assertFalse(javaEcho(whole).containsKey("partial"));
            onEdt(()->{
                var a=anchor(f,"source:java:com.acme.Node");var text=(JTextPane)field(a.component(),"text");
                var viewport=SwingUtilities.convertRectangle(text,text.getVisibleRect(),overlay(f));
                assertEquals(viewport,overlay(f).lit().getFirst().bounds(),"whole Java means viewport, not its label");
            });
        }
    }

    @Test void clickEscapeSelectiveClearAndReplaceReleaseBindings(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());source(tmp);
        try(var f=new Frame(tmp)) {
            show(f,tmp.resolve("src"));
            for(String operation:List.of("click","escape","clear","replace")) {
                assertTrue(f.ex.render("spotlight",Map.of("target",TARGET)).ok());
                onEdt(()->{
                    var overlay=overlay(f);
                    if(operation.equals("click"))overlay.dispatchEvent(new java.awt.event.MouseEvent(overlay,java.awt.event.MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),0,5,5,1,false));
                    else if(operation.equals("escape")) {
                        var key=overlay.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE,0));
                        overlay.getActionMap().get(key).actionPerformed(new java.awt.event.ActionEvent(overlay,0,"escape"));
                    } else if(operation.equals("clear")) f.ex.render("spotlight",Map.of("clear",true,"target",TARGET));
                    else f.ex.render("spotlight",Map.of("target","status"));
                    assertTrue(((Map<?,?>)field(f.frame,"javaSpotlightBindings")).isEmpty(),operation+" releases document references");
                });
                onEdt(()->{});onEdt(()->assertTrue(overlay(f).lit().stream().noneMatch(l->l.target().equals(TARGET)),"no resurrection"));
            }
        }
    }

    static SourcePanel.JavaAnchor anchor(Frame f,String name) {
        Object binding=((Map<?,?>)field(f.frame,"javaSpotlightBindings")).get(name);
        return (SourcePanel.JavaAnchor)field(binding,"anchor");
    }
    static void jar(Path path,Map<String,String> entries) throws Exception {
        Files.createDirectories(path.getParent());
        try(var zip=new java.util.zip.ZipOutputStream(Files.newOutputStream(path))) {
            for(var e:entries.entrySet()) {zip.putNextEntry(new java.util.zip.ZipEntry(e.getKey()));zip.write(e.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}
        }
    }

    @SuppressWarnings("unchecked") static Map<String,Object> javaEcho(ActionResult r) {
        return ((List<Map<String,Object>>)r.payload().get("lit")).stream().filter(e->e.get("target").toString().startsWith("source:java:")).findFirst().orElseThrow();
    }
}
