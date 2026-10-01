package telamin.fluxtion.audit.analyser.analyser.ui;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import telamin.fluxtion.audit.analyser.analyser.config.*;
import telamin.fluxtion.audit.analyser.analyser.session.*;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.*;
import static telamin.fluxtion.audit.analyser.analyser.ui.EvidenceCaptureFrameTest.*;

@org.junit.jupiter.api.condition.DisabledIfSystemProperty(named = "java.awt.headless", matches = "true")
public class Issue84BundleFrameTest {
    static Path tmp(String name) throws Exception {
        return Files.createTempDirectory( "DEMO-" + name + "-");
    }

    static Path profile(Path dir) throws Exception {
        Path p = ProjectProfile.pathFor(dir);
        ProjectProfile.save(p, new AppConfig(), new SettingsShare());
        return p;
    }

    static String note(List<GraphSpec> specs) {
        return specs.stream().filter(s -> s.name().equals("DEMO-chart")).findFirst().orElseThrow()
                .notes().stream().map(GraphSpec.NoteSpec::text).reduce((a,b)->a+","+b).orElse("");
    }

    @Test void aBackgroundChartActionReportsItsViewChangeOnTheEdt() throws Exception {
        try (var f = shown(tmp("view-fact-thread"))) {
            openLog(f, DEMO_LOG);
            assertFalse(SwingUtilities.isEventDispatchThread(), "the action really starts outside the EDT");
            var reply = assertDoesNotThrow(() -> f.ex.render("graph", Map.of("name", "DEMO-background",
                    "series", List.of("priceListener.mid"))), "viewObservationMustReturnToTheEdt");
            assertTrue(reply.ok(), reply.toMap().toString());
            onEdt(() -> assertNotNull(((GraphTabs)field(f.frame, "graphTabs")).graphNamed("DEMO-background"),
                    "viewObservationMustNotBreakTheChartAction"));
        }
    }

    @Test void importingGraphsKeepsIncomingNotes() throws Exception {
        Path tmp = tmp("import");
        try (var f = shown(tmp)) {
            Path exchange = exchange(f, tmp);
            Path projectFile = profile(tmp.resolve("project"));
            onEdt(() -> render(f.ex, "open", Map.of("project", projectFile.toString())));
            openLog(f, DEMO_LOG);
            onEdt(() -> {
                var result = render(f.ex, "graph", Map.of("newTab", true, "name", "DEMO-chart", "series", List.of("priceListener.mid"), "notes", List.of(Map.of("at",1767258000090L,"text","DEMO sender"))));
                if(!Boolean.TRUE.equals(result.get("ok"))) throw new IllegalStateException("graph refused: "+result);
                if(!"DEMO sender".equals(note(((GraphTabs)field(f.frame,"graphTabs")).specs()))) throw new IllegalStateException("sender chart precondition");
            });
            onEdt(() -> render(f.ex, "report", Map.of("bundle", Map.of("path", "import.fexp"))));
            System.out.println("capture for import phase=" + awaitDecided(f).get("phase"));
            onEdt(() -> {
                render(f.ex, "graph", Map.of("name", "DEMO-chart", "series", List.of("priceListener.bid"), "notes", List.of(Map.of("at",1767258000090L,"text","DEMO recipient"))));
                if(!"DEMO recipient".equals(note(((GraphTabs)field(f.frame,"graphTabs")).specs()))) throw new IllegalStateException("recipient chart precondition");
            });
            AtomicReference<Map<String,Object>> result = new AtomicReference<>();
            onEdt(() -> result.set(render(f.ex, "import", Map.of("bundle", exchange.resolve("import.fexp").toString(), "categories", List.of("GRAPHS")))));
            System.out.println("import reply=" + result.get());
            onEdt(() -> {
                var config = (AppConfig) field(f.frame, "config");
                var tabs = (GraphTabs) field(f.frame, "graphTabs");
                assertEquals("DEMO sender", note(tabs.specs()), "incomingNotesAreOnScreen");
                assertEquals("DEMO sender", note(config.savedGraphs), "incomingNotesAreInConfig");
                assertEquals(List.of("priceListener\u0001mid"), chart(tabs.specs()).series(), "incomingSeriesAreOnScreen");
                assertEquals(List.of("priceListener\u0001mid"), chart(config.savedGraphs).series(), "incomingSeriesAreInConfig");
                ((ProjectSession)field(f.frame, "project")).flush();
            });
            AppConfig back = new AppConfig();
            ProjectProfile.load(projectFile, back, new SettingsShare());
            System.out.println("import on disk='" + note(back.savedGraphs) + "'");
            assertEquals("DEMO sender",note(back.savedGraphs),"incomingGraphSurvivesTheSaveFunnel");
            assertEquals(List.of("priceListener\u0001mid"), chart(back.savedGraphs).series(), "incomingSeriesArePersisted");
        }
    }

    static GraphSpec chart(List<GraphSpec> specs) {
        return specs.stream().filter(s -> s.name().equals("DEMO-chart")).findFirst().orElseThrow();
    }

    @Test void startupDoesNotRewriteACommittedProfile() throws Exception {
        Path tmp = tmp("startup");
        Path p = ProjectProfile.pathFor(tmp.resolve("project"));
        Files.createDirectories(p.getParent());
        String original = "# DEMO committed profile\nsourceRoot.count=0\n";
        Files.writeString(p, original);
        Path cfg = tmp.resolve("home/.fluxtion-analyser/config");
        Files.createDirectories(cfg.getParent());
        Files.writeString(cfg, "activeProjectPath=" + p + "\n");
        MainFrame.reopenChooser = (label, candidates) -> null;
        try (var f = shown(tmp)) {
            Thread.sleep(1300);
            System.out.println("startup with no edits: profileChanged=" + !original.equals(Files.readString(p)));
            assertEquals(original,Files.readString(p),"startupLeavesCommittedProfileByteIdentical");
        }
    }

    @Test void aPersonsReopenOfferRunsAfterTheCycle() throws Exception {
        Path tmp = tmp("reopen");
        try (var f = shown(tmp)) {
            Path p = profile(tmp.resolve("project"));
            Path log = Files.copy(DEMO_LOG, p.getParent().getParent().resolve("DEMO-log.yaml"));
            AtomicBoolean asked = new AtomicBoolean();
            AtomicBoolean busy = new AtomicBoolean();
            onEdt(() -> ((AppConfig)field(f.frame,"config")).addRecent(log.toString()));
            MainFrame.reopenChooser = (label,candidates) -> {
                var driver = (SessionDriver) field(f.frame,"session");
                busy.set(driver.isDispatching());
                asked.set(true);
                return new ProjectReopenDialog.Choice(log.toString(), null);
            };
            var request = MainFrame.class.getDeclaredMethod("requestProject", Path.class, TransitionKind.class, String.class, boolean.class);
            request.setAccessible(true);
            onEdt(() -> { try { request.invoke(f.frame,p,TransitionKind.EXPLICIT_SWITCH,"DEMO-person",true); } catch(Exception e){throw new RuntimeException(e);} });
            for (int i=0;i<100&&!asked.get();i++) Thread.sleep(50);
            awaitLoaded(f.ex);
            assertTrue(asked.get());assertFalse(busy.get(),"noModalInsideTheDriverCycle");assertEquals(log.toString(),f.processorLog());
            System.out.println("reopen selection: asked=" + asked.get() + " midCycle=" + busy.get() + " logOpened=" + log.toString().equals(f.processorLog()));
        }
        MainFrame.reopenChooser = (label,candidates) -> null;
    }

    @Test void aReplacementProfileIsNotTheSendersEvidence() throws Exception {
        Path tmp = tmp("stale");
        try (var f = shown(tmp)) {
            Path exchange = exchange(f,tmp);
            Path own = profile(tmp.resolve("own"));
            onEdt(() -> render(f.ex,"open",Map.of("project",own.toString())));
            openLog(f,DEMO_LOG);
            onEdt(() -> render(f.ex,"report",Map.of("bundle",Map.of("path","stale.fexp"))));
            if (!"WRITTEN".equals(awaitDecided(f).get("phase"))) throw new IllegalStateException("capture failed");
            onEdt(() -> {
                var project = (ProjectSession)field(f.frame,"project");
                project.requestSave();
                project.setPreSave(() -> {throw new IllegalStateException("DEMO pre-save failed");});
                render(f.ex,"open",Map.of("bundle",exchange.resolve("stale.fexp").toString()));
            });
            Path[] replacement = new Path[1];
            for(int i=0;i<100&&replacement[0]==null;i++) {
                onEdt(() -> {
                    var driver=(SessionDriver)field(f.frame,"session");
                    if (driver.auditSink().matching("DEMO pre-save failed").isEmpty()) return;
                    try (var paths = Files.walk(tmp.resolve("home/.fluxtion-analyser/bundles"))) {
                        replacement[0] = paths.filter(p -> p.getFileName().toString().equals("project.fluxtion-settings"))
                                .findFirst().orElseThrow();
                    } catch(Exception ex){throw new RuntimeException(ex);}
                });
                Thread.sleep(50);
            }
            assertNotNull(replacement[0], "the verified working copy was prepared and application failed");
            AppConfig independent = new AppConfig();
            independent.selectedEventProcessor = "DEMO.recipient.OtherProcessor";
            independent.eventProcessorFqns.add(independent.selectedEventProcessor);
            ProjectProfile.save(replacement[0], independent, new SettingsShare());
            onEdt(() -> {
                var project=(ProjectSession)field(f.frame,"project");
                project.setPreSave(null);
                System.out.println("failed bundle real path: ownProjectRemains="+own.equals(project.activeFile()));
                render(f.ex,"open",Map.of("project",replacement[0].toString()));
                var driver=(SessionDriver)field(f.frame,"session");
                System.out.println("ordinary profile real path: fromBundle="+driver.snapshot().bundle().fromBundle()+" titleClaimsBundle="+f.frame.getTitle().contains("evidence bundle"));
                System.out.println("replacement profile processor="+((AppConfig)field(f.frame,"config")).selectedEventProcessor);
            });
            assertFalse(((SessionDriver)field(f.frame,"session")).snapshot().bundle().fromBundle(),"replacementProfileMustNotCarryTheAbortedBundlesIdentity");
        }
    }
    @Test void anUnrelatedEditDoesNotDeleteAnUnavailableAnchor() throws Exception {
        Path tmp=Issue84BundleFrameTest.tmp("offline-anchor");
        try(var f=shown(tmp)) {
            Path exchange=exchange(f,tmp);
            Path code=Files.createDirectories(tmp.resolve("DEMO-code"));
            String root=code.toString();
            openLog(f,DEMO_LOG);
            onEdt(()->render(f.ex,"report",Map.of("bundle",Map.of("path","offline.fexp"))));
            if(!"WRITTEN".equals(awaitDecided(f).get("phase")))throw new IllegalStateException("capture precondition");
            Path bundle=exchange.resolve("offline.fexp");
            openBundle(f,bundle);
            onEdt(()->render(f.ex,"source_root",Map.of("add",List.of(root))));
            var config=(AppConfig)field(f.frame,"config");
            if(!List.of(root).equals(config.bundleSourceRoots(bundle.toString())))throw new IllegalStateException("initial anchor precondition");
            onEdt(()->render(f.ex,"open",Map.of("close","project")));
            Path offline=code.resolveSibling("DEMO-code-offline");Files.move(code,offline);
            openBundle(f,bundle);
            onEdt(()->{
                if(!config.sourceRoots.isEmpty())throw new IllegalStateException("offline source precondition");
                if(!List.of(root).equals(config.bundleSourceRoots(bundle.toString())))throw new IllegalStateException("remembered anchor precondition");
                System.out.println("offline anchor before unrelated REPORTS import="+config.bundleSourceRoots(bundle.toString()));
                var reply=render(f.ex,"import",Map.of("bundle",bundle.toString(),"categories",List.of("REPORTS")));
                System.out.println("REPORTS-only import reply="+reply);
                System.out.println("offline anchor after unrelated REPORTS import="+config.bundleSourceRoots(bundle.toString()));
            });
            Files.move(offline,code);
            onEdt(()->render(f.ex,"open",Map.of("close","project")));
            openBundle(f,bundle);
            onEdt(()->System.out.println("source after directory returns and bundle reopens="+config.sourceRoots));
            assertEquals(List.of(root),config.sourceRoots,"unrelatedReportsImportMustNotDeleteThePersonsAnchor");
        }
    }

    @Test void aDisabledOfferIsRecordedAsSkipped() throws Exception {
        MainFrame.reopenChooser = null;
        try (var f = shown(Issue84BundleFrameTest.tmp("offers-off"))) {
            Path root = Issue84BundleFrameTest.tmp("offer-project");
            Path p = Issue84BundleFrameTest.profile(root);
            Path log = Files.copy(DEMO_LOG, root.resolve("DEMO-log.yaml"));
            onEdt(() -> {
                ((AppConfig)field(f.frame,"config")).addRecent(log.toString());
                try {
                    var request=MainFrame.class.getDeclaredMethod("requestProject",Path.class,TransitionKind.class,String.class,boolean.class);
                    request.setAccessible(true);
                    request.invoke(f.frame,p,TransitionKind.EXPLICIT_SWITCH,"DEMO-person",true);
                } catch(Exception e) { throw new RuntimeException(e); }
            });
            onEdt(() -> {});
            Thread.sleep(200);
            onEdt(() -> {
                var driver=(SessionDriver)field(f.frame,"session");
                var records=driver.auditSink().matching("offerProjectReopen");
                System.out.println("disabled offers: chooser=null dialogs="+f.dialogs.seen());
                for(String record:records) {
                    for(String line:record.split("\\n")) if(line.contains("offerProjectReopen")) System.out.println(" offer record: "+line);
                }
            });
            var records=((SessionDriver)field(f.frame,"session")).auditSink().matching("offerProjectReopen");
            assertTrue(records.stream().anyMatch(s->s.contains("offerProjectReopenSkipped")),"disabledOfferMustBeRecordedAsSkipped");
        }
    }

    static void openBundle(AsyncOpenInterleavingFrameTest.Frame f,Path bundle)throws Exception {
        onEdt(()->render(f.ex,"open",Map.of("bundle",bundle.toString())));
        boolean[] applied={false};
        for(int i=0;i<100&&!applied[0];i++) {
            onEdt(()->{
                var driver=(SessionDriver)field(f.frame,"session");
                applied[0]=driver.snapshot().bundle().fromBundle();
            });
            if(!applied[0])Thread.sleep(50);
        }
        if(!applied[0])throw new IllegalStateException("bundle apply precondition");
        awaitLoaded(f.ex);
    }
    @Test void previewLeavesNoWorkingCopy() throws Exception {
        Path tmp=tmp("borrow");
        try(var f=shown(tmp)) {
            Path exchange=exchange(f,tmp), own=profile(tmp.resolve("project"));
            onEdt(()->render(f.ex,"open",Map.of("project",own.toString())));
            openLog(f,DEMO_LOG);
            onEdt(()->render(f.ex,"report",Map.of("bundle",Map.of("path","DEMO-borrow.fexp"))));
            assertEquals("WRITTEN",awaitDecided(f).get("phase"));
            Path copies=tmp.resolve("home/.fluxtion-analyser/bundles");
            Set<Path> before=children(copies);
            Map<String,Object>[] reply=new Map[1];
            onEdt(()->reply[0]=render(f.ex,"import",Map.of("bundle",exchange.resolve("DEMO-borrow.fexp").toString())));
            System.out.println("preview reply="+reply[0]+" workingCopiesBefore="+before.size()+" after="+children(copies).size());
            assertEquals(before,children(copies),"previewMustNotCreateAWorkingCopy");
            assertTrue(Boolean.TRUE.equals(reply[0].get("ok")));
        }
    }
    static Set<Path> children(Path p)throws Exception { if(!Files.isDirectory(p))return Set.of(); try(var s=Files.list(p)){return new HashSet<>(s.toList());} }

    @org.junit.jupiter.api.BeforeEach @org.junit.jupiter.api.AfterEach
    void resetOfferSeams()throws Exception { MainFrame.reopenChooser=null; var enabled=MainFrame.class.getDeclaredField("offersEnabled");enabled.setAccessible(true);enabled.set(null,false); }
    @Test void machineValuesDoNotRideProjectShareOrBundle()throws Exception {
        Path tmp=tmp("tiers");AppConfig c=new AppConfig();
        c.defaultFocus="DEMO-project-focus";c.namedFocuses.add(new FocusSpec(c.defaultFocus,"DEMO advice",List.of("priceListener")));
        c.lastFocusByProject.put("DEMO-project","DEMO-machine-focus");
        c.addRecentBundle("DEMO-machine.fexp","sha256:DEMO","DEMO-machine-note");c.eventTypesDivider=137;
        Path profile=ProjectProfile.pathFor(tmp.resolve("project"));ProjectProfile.save(profile,c,new SettingsShare());
        String share=new SettingsShare().export(c,EnumSet.allOf(SettingsShare.Category.class));
        String committed=Files.readString(profile);
        Path bundled=tmp.resolve("bundled.fluxtion-settings");telamin.fluxtion.audit.analyser.bundle.BundleProfile.export(profile,bundled);
        for(String text:List.of(share,committed,Files.readString(bundled))) {
            assertTrue(text.contains("DEMO-project-focus"),"theProjectAdviceTravels");
            assertFalse(text.contains("DEMO-machine-focus")||text.contains("DEMO-machine-note")||text.contains("lastFocus")||text.contains("eventTypesDivider"),"machineTierMustNotTravel");
        }
        ProjectProfile.clearProjectScoped(c);assertEquals("",c.defaultFocus);assertEquals("DEMO-machine-focus",c.lastFocusByProject.get("DEMO-project"));
        ProjectProfile.save(profile,c,new SettingsShare());assertFalse(Files.readString(profile).contains("defaultFocus"),"NoDefaultMustNotBeResurrectedByKnownKeys");
        ConfigStore store=new ConfigStore(tmp.resolve("DEMO-machine-config"));store.save(c);assertEquals(c.lastFocusByProject,store.load().lastFocusByProject);
        c.lastFocusByProject.clear();store.save(c);assertTrue(store.load().lastFocusByProject.isEmpty(),"machineHistoryCanBeCleared");
        System.out.println("tiers: project default travels, machine history stays local, both can be cleared");
    }
    @Test void processorInContextIsExplicitlyTheSendersClaim()throws Exception {
        Path tmp=tmp("processor-claim");try(var f=shown(tmp)) {
            Path dir=exchange(f,tmp);openLog(f,DEMO_LOG);
            onEdt(()->((AppConfig)field(f.frame,"config")).selectedEventProcessor="com.acme.DEMOProcessor");
            onEdt(()->render(f.ex,"report",Map.of("bundle",Map.of("path","DEMO-claim.fexp"))));assertEquals("WRITTEN",awaitDecided(f).get("phase"));
            openBundle(f,dir.resolve("DEMO-claim.fexp"));
            Map<String,Object>[] result=new Map[1];onEdt(()->result[0]=render(f.ex,"context",Map.of("sections",List.of("project"))));
            Map ctx=(Map)result[0].get("context"), project=(Map)ctx.get("project"), bundle=(Map)project.get("bundle");
            System.out.println("bundle processor claim="+bundle.get("processorClaimed")+" relationship="+bundle.get("processorClaimedRelationship"));
            assertEquals("com.acme.DEMOProcessor",bundle.get("processorClaimed"));assertTrue(bundle.get("processorClaimedRelationship").toString().contains("not paired against this log"));assertFalse(bundle.containsKey("processor"));
        }
    }
    @Test void clearingOwnRootsOutsideABundleKeepsItsAnchor()throws Exception {
        Path tmp=tmp("own-roots");Path own=Files.createDirectories(tmp.resolve("DEMO-own-src")), code=Files.createDirectories(tmp.resolve("DEMO-bundle-src"));
        try(var f=shown(tmp)) {
            Path dir=exchange(f,tmp);openLog(f,DEMO_LOG);onEdt(()->render(f.ex,"source_root",Map.of("add",List.of(own.toString()))));
            onEdt(()->render(f.ex,"report",Map.of("bundle",Map.of("path","DEMO-roots.fexp"))));assertEquals("WRITTEN",awaitDecided(f).get("phase"));Path bundle=dir.resolve("DEMO-roots.fexp");
            openBundle(f,bundle);onEdt(()->render(f.ex,"source_root",Map.of("add",List.of(code.toString()))));
            onEdt(()->render(f.ex,"open",Map.of("close","project")));
            assertFalse(((SessionDriver)field(f.frame,"session")).snapshot().bundle().fromBundle());
            onEdt(()->render(f.ex,"source_root",Map.of("remove",List.of(own.toString()))));
            var c=(AppConfig)field(f.frame,"config");assertEquals(List.of(code.toString()),c.bundleSourceRoots(bundle.toString()));
            openBundle(f,bundle);assertEquals(List.of(code.toString()),c.sourceRoots);System.out.println("clearing own roots after closing the bundle leaves its anchor intact");
        }
    }
    @Test void recentsSurviveRestartWithoutWorkingCopiesOrFilenameCollisions()throws Exception {
        Path tmp=tmp("recents");Path a,b;String first,second;
        try(var f=shown(tmp)) {
            Path dir=exchange(f,tmp);Files.createDirectories(dir.resolve("DEMO-first"));Files.createDirectories(dir.resolve("DEMO-second"));openLog(f,DEMO_LOG);
            onEdt(()->render(f.ex,"report",Map.of("bundle",Map.of("path","DEMO-first/same.fexp","notes","DEMO first"))));assertEquals("WRITTEN",awaitDecided(f).get("phase"));a=dir.resolve("DEMO-first/same.fexp");
            onEdt(()->render(f.ex,"report",Map.of("bundle",Map.of("path","DEMO-second/same.fexp","notes","DEMO second"))));assertEquals("WRITTEN",awaitDecided(f).get("phase"));b=dir.resolve("DEMO-second/same.fexp");
            openBundle(f,a);onEdt(()->render(f.ex,"open",Map.of("close","project")));openBundle(f,a);onEdt(()->render(f.ex,"open",Map.of("close","project")));openBundle(f,b);
            var c=(AppConfig)field(f.frame,"config");assertEquals(2,c.recentBundles.size());first=c.recentBundles.get(0).identity();second=c.recentBundles.get(1).identity();assertNotEquals(first,second);
            assertTrue(c.recentProjects.stream().noneMatch(x->telamin.fluxtion.audit.analyser.bundle.EvidenceBundle.isWorkingCopy(Path.of(x))));
        }
        try(var f=shown(tmp)) {
            var c=(AppConfig)field(f.frame,"config");assertEquals(2,c.recentBundles.size());assertEquals(first,c.recentBundles.get(0).identity());assertEquals(second,c.recentBundles.get(1).identity());
            assertFalse(((ProjectSession)field(f.frame,"project")).hasProject(),"restartMustNotRestoreABundleWorkingCopyAsAnOrdinaryProject");
            var candidates=MainFrame.class.getDeclaredMethod("reopenCandidates");candidates.setAccessible(true);ProjectReopen r=(ProjectReopen)candidates.invoke(f.frame);
            assertTrue(java.util.stream.Stream.concat(r.logs().stream(),r.topologies().stream()).noneMatch(x->telamin.fluxtion.audit.analyser.bundle.EvidenceBundle.isWorkingCopy(Path.of(x))));
            System.out.println("recents: two same-name bundles remain distinct after reopen and restart; no copy is a project or reopen candidate");
        }
    }
    @Test void fallbackOfferMustNotDescribeAnUnrelatedLogAsInsideTheProject()throws Exception {
        Path tmp=tmp("fallback");Path projectRoot=tmp.resolve("DEMO-project"), ownProfile=profile(projectRoot);
        Path foreign=Files.createDirectories(tmp.resolve("DEMO-other-project"));Path log=Files.copy(DEMO_LOG,foreign.resolve("DEMO-other.yaml"));
        var text=new java.util.concurrent.atomic.AtomicReference<String>("");
        java.awt.event.AWTEventListener listener=e->{if(e instanceof java.awt.event.WindowEvent w&&w.getID()==java.awt.event.WindowEvent.WINDOW_OPENED&&w.getWindow() instanceof JDialog d&&d.getTitle().equals("Open a log or topology"))text.set(dialogText(d));};
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(listener,java.awt.AWTEvent.WINDOW_EVENT_MASK);
        try(var f=shown(tmp)) {
            MainFrame.enableReopenOffers();onEdt(()->((AppConfig)field(f.frame,"config")).addRecent(log.toString()));
            var request=MainFrame.class.getDeclaredMethod("requestProject",Path.class,TransitionKind.class,String.class,boolean.class);request.setAccessible(true);
            onEdt(()->{try{request.invoke(f.frame,ownProfile,TransitionKind.EXPLICIT_SWITCH,"DEMO-person",true);}catch(Exception e){throw new AssertionError(e);}});
            for(int i=0;i<100&&text.get().isEmpty();i++)Thread.sleep(20);
            assertFalse(text.get().isEmpty(),"the actual modal was observed");System.out.println("fallback modal text="+text.get());
            assertFalse(text.get().contains("opened inside this project"),"anUnrelatedRecentLogMustNotBeCalledInsideThisProject");
            assertTrue(text.get().contains("Audit logs — machine recent files"), "fallbackLogListMustNameItsMachineOrigin");
        }finally{java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(listener);}
    }
    static String dialogText(java.awt.Component c){StringBuilder s=new StringBuilder();if(c instanceof JLabel l)s.append(l.getText()).append("\n");if(c instanceof javax.swing.JTextArea a)s.append(a.getText()).append("\n");if(c instanceof java.awt.Container n)for(var child:n.getComponents())s.append(dialogText(child));return s.toString();}

    @Test void topologyFallbackHasItsOwnOriginBesideAProjectLog() throws Exception {
        Path tmp = tmp("mixed-origin"), root = tmp.resolve("DEMO-project"), own = profile(root);
        Path log = Files.copy(DEMO_LOG, root.resolve("DEMO-log.yaml"));
        Path graph = Files.writeString(tmp.resolve("DEMO-elsewhere.graphml"), "<graphml/>");
        AtomicReference<String> text = new AtomicReference<>("");
        java.awt.event.AWTEventListener listener = event -> {
            if (event instanceof java.awt.event.WindowEvent w && w.getID() == java.awt.event.WindowEvent.WINDOW_OPENED
                    && w.getWindow() instanceof JDialog d && d.getTitle().equals("Open a log or topology")) text.set(dialogText(d));
        };
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(listener, java.awt.AWTEvent.WINDOW_EVENT_MASK);
        try (var f = shown(tmp)) {
            MainFrame.enableReopenOffers();
            onEdt(() -> {
                var config = (AppConfig)field(f.frame, "config");
                config.addRecent(log.toString()); config.recentGraphml.add(graph.toString());
                try {
                    var request = MainFrame.class.getDeclaredMethod("requestProject", Path.class, TransitionKind.class, String.class, boolean.class);
                    request.setAccessible(true); request.invoke(f.frame, own, TransitionKind.EXPLICIT_SWITCH, "DEMO-person", true);
                } catch (Exception ex) { throw new AssertionError(ex); }
            });
            WalkPlaybackFrameTest.await("mixed-origin modal", () -> !text.get().isEmpty());
            assertTrue(text.get().contains("Audit logs — project locations"), "projectLogOriginIsPreserved");
            assertTrue(text.get().contains("Topologies — machine recent files"), "topologyFallbackNamesItsOwnOrigin");
        } finally { java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(listener); }
    }

    @Test void aDeferredHumanOfferDoesNotBelongToTheNextSocketProject() throws Exception {
        Path tmp = tmp("offer-race"), first = profile(tmp.resolve("DEMO-first")), second = profile(tmp.resolve("DEMO-second"));
        Path log = Files.copy(DEMO_LOG, first.getParent().getParent().resolve("DEMO-log.yaml"));
        AtomicInteger offers = new AtomicInteger();
        try (var f = shown(tmp)) {
            MainFrame.reopenChooser = (label, candidates) -> { offers.incrementAndGet(); return null; };
            onEdt(() -> {
                ((AppConfig)field(f.frame, "config")).addRecent(log.toString());
                try {
                    var request = MainFrame.class.getDeclaredMethod("requestProject", Path.class, TransitionKind.class, String.class, boolean.class);
                    request.setAccessible(true); request.invoke(f.frame, first, TransitionKind.EXPLICIT_SWITCH, "DEMO-person", true);
                } catch (Exception ex) { throw new AssertionError(ex); }
                render(f.ex, "open", Map.of("project", second.toString()));
            });
            // Drain both deferred rounds: candidate gathering, then presentation. This also
            // reaches the old implementation's dialog rather than waiting for a new audit word.
            onEdt(() -> {});
            onEdt(() -> {});
            onEdt(() -> assertEquals(0, offers.get(), "aLaterSocketOperationCannotInheritAHumanOffer"));
        }
    }

}
