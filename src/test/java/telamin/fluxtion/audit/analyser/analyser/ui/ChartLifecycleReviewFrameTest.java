package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.*;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Constructed regression cases: the real frame's persistence callbacks, not a copied merge algorithm. */
class ChartLifecycleReviewFrameTest {
    @TempDir Path tmp;
    static GraphSpec chart(String name, boolean open) {
        return new GraphSpec(name, List.of("node\u0001value"), List.of(), 1000L, 2000L, "caption",
                "keep this explanation", List.of(new GraphSpec.NoteSpec(1000, "keep this note", null)),
                List.of(), List.of(), List.of(), List.of(), List.of(), "line", open);
    }
    static <T> T edt(Callable<T> task) throws Exception {
        AtomicReference<T> value = new AtomicReference<>(); AtomicReference<Throwable> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { try { value.set(task.call()); } catch (Throwable t) { error.set(t); } });
        if (error.get() instanceof AssertionError a) throw a;
        if (error.get() != null) throw new RuntimeException(error.get());
        return value.get();
    }
    static Object field(Object obj, String name) throws Exception {
        Field f = obj.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(obj);
    }
    static void invoke(Object obj, String name) throws Exception {
        Method m = obj.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(obj);
    }
    static List<Component> components(Component c) {
        var all = new java.util.ArrayList<Component>(); all.add(c);
        if (c instanceof Container parent) for (Component child : parent.getComponents()) all.addAll(components(child));
        return all;
    }
    static JButton button(Component root, String name) {
        return components(root).stream().filter(c -> c instanceof JButton b && name.equals(b.getText()))
                .map(c -> (JButton)c).findFirst().orElseThrow();
    }
    final class Fixture implements AutoCloseable {
        final String oldHome = System.getProperty("user.home");
        final MainFrame frame; final GraphTabs tabs; final AppConfig config; final ActionExecutor executor;
        final Path profile, log;
        Fixture(GraphSpec... specs) throws Exception {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a real display");
            System.setProperty("user.home", Files.createDirectories(tmp.resolve("home")).toString());
            profile = ProjectProfile.pathFor(Files.createDirectories(tmp.resolve("project")));
            AppConfig seed = new AppConfig(); seed.savedGraphs.addAll(List.of(specs));
            ProjectProfile.save(profile, seed, new SettingsShare());
            log = Files.writeString(tmp.resolve("input.yaml"), "---\neventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n    - node: { value: 1}\n---\n");
            frame = edt(MainFrame::new); tabs = (GraphTabs)field(frame,"graphTabs"); config = (AppConfig)field(frame,"config");
            executor = (ActionExecutor)field(frame,"actionExecutor");
            assertTrue(executor.render("open", Map.of("project",profile.toString())).ok());
            assertTrue(executor.render("open", Map.of("log",log.toString())).ok());
            for (int i=0;i<400 && edt(() -> field(frame,"store"))==null;i++) Thread.sleep(25);
            assertNotNull(edt(() -> field(frame,"store")), "log loaded");
            edt(() -> { frame.setSize(1200,850); frame.setVisible(true); flush(); return null; });
        }
        void flush() throws Exception { invoke(frame,"flushProject"); }
        GraphSpec saved(String name) { return config.savedGraphs.stream().filter(g -> g.name().equals(name)).findFirst().orElseThrow(); }
        void openRow(String name) throws Exception {
            invoke(frame,"refreshProjectPanel");
            ProjectPanel panel=(ProjectPanel)field(frame,"projectPanel");
            JLabel label=components(panel).stream().filter(c -> c instanceof JLabel l && name.equals(l.getText()))
                    .map(c -> (JLabel)c).findFirst().orElseThrow();
            button(label.getParent(),"Open").doClick();
        }
        void delete(boolean confirm) throws Exception { delete(confirm, () -> { }); }
        void delete(boolean confirm, Runnable duringQuestion) throws Exception {
            var handled = new java.util.concurrent.atomic.AtomicBoolean();
            Timer timer = new Timer(20, e -> {
                for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isShowing()) {
                    for(Component c: components(w)) if(c instanceof JOptionPane p) {
                        if (!handled.compareAndSet(false, true)) return;
                        if (System.getProperty("review.captureDir") != null) {
                            try {
                                Path out=Path.of(System.getProperty("review.captureDir"));Files.createDirectories(out);
                                javax.imageio.ImageIO.write(new Robot().createScreenCapture(w.getBounds()),"png",out.resolve("delete-confirmation.png").toFile());
                            } catch (Exception ex) { throw new RuntimeException(ex); }
                        }
                        duringQuestion.run();
                        assertTrue(p.getMessage().toString().contains("Delete the chart"), "real confirmation names the destructive act");
                        button(p, confirm ? "OK" : "Cancel").doClick(); return;
                    }
                }
            });
            timer.start();
            try { button(tabs,"Delete chart").doClick(); } finally { timer.stop(); }
            assertTrue(handled.get(), "the production Delete button required confirmation");
        }
        @Override public void close() throws Exception {
            edt(() -> { frame.dispose(); return null; }); System.setProperty("user.home", oldHome);
        }
    }
    @Test void dropdownChoiceIsSavedByTheProductionListener() throws Exception {
        try(var f=new Fixture(chart("One",true))) {
            edt(() -> {
                GraphPanel p=f.tabs.graphNamed("One");JComboBox<?> combo=(JComboBox<?>)field(p,"styleCombo");
                combo.setSelectedIndex(2);combo.setSelectedIndex(1);f.flush();
                AppConfig back=new AppConfig();assertTrue(ProjectProfile.load(f.profile,back,new SettingsShare()).loaded());
                assertEquals("line",back.savedGraphs.getFirst().style());return null;
            });
            assertTrue(f.executor.render("open",Map.of("close","project")).ok());
            assertTrue(f.executor.render("open",Map.of("project",f.profile.toString())).ok());
            assertTrue(f.executor.render("open",Map.of("log",f.log.toString())).ok());
            for(int i=0;i<400 && edt(()->field(f.frame,"store"))==null;i++)Thread.sleep(25);
            edt(()->{assertEquals("Line",((JComboBox<?>)field(f.tabs.graphNamed("One"),"styleCombo")).getSelectedItem(),
                    "the actual dropdown must return as Line after project close/reopen");return null;});
        }
    }
    @Test void rowReopenPersistsOpenStateWithoutAnotherEdit() throws Exception {
        try(var f=new Fixture(chart("One",true),chart("Two",false))) { edt(() -> {
            f.openRow("Two"); f.flush();
            assertTrue(f.saved("Two").open(),"row reveal must persist the open view state");
            GraphPanel same=f.tabs.graphNamed("Two"); same.setCaption("later edit"); f.openRow("One"); f.openRow("Two");
            assertSame(same,f.tabs.graphNamed("Two")); assertEquals("later edit",same.caption()); return null;
        }); }
    }
    @Test void renameMovesTheDefinitionInsteadOfLeavingAGhost() throws Exception {
        try(var f=new Fixture(chart("One",true),chart("Two",false))) { edt(() -> {
            assertTrue(f.tabs.renameNamed("One","Renamed")); f.flush();
            assertEquals(List.of("Renamed","Two"),f.config.savedGraphs.stream().map(GraphSpec::name).toList(),"rename must replace the old identity");
            assertEquals("keep this explanation",f.saved("Renamed").explanation()); return null;
        }); }
    }
    @Test void closedNamesAreReservedForCreationAndRename() throws Exception {
        try(var f=new Fixture(chart("One",true),chart("Graph 2",false))) { edt(() -> {
            assertNull(assertDoesNotThrow(()->f.tabs.graphForAction("One",true),"duplicate creation must refuse before any save"),"newTab must not duplicate an open name");
            assertNull(assertDoesNotThrow(()->f.tabs.graphForAction("Graph 2",true),"closed duplicate creation must refuse before any save"),"newTab must not overwrite a saved name");
            var modal=new java.util.concurrent.atomic.AtomicBoolean();
            Timer dismiss=new Timer(20,e->{for(Window w:Window.getWindows())if(w instanceof JDialog && w.isShowing()) {
                modal.set(true);w.dispose();
            }});
            dismiss.start();
            try {assertFalse(f.tabs.renameNamed("One", "Graph 2"),"rename must refuse a closed name");}
            finally {dismiss.stop();}
            assertFalse(modal.get(),"programmatic rename must refuse without a modal");
            GraphPanel added=f.tabs.addGraph(null); assertNotEquals("Graph 2",added.graphName(),"generated names reserve closed definitions"); return null;
        }); }
    }
    @Test void graphActionOnClosedChartPreservesItsDefinition() throws Exception {
        try(var f=new Fixture(chart("One",true),chart("Two",false))) { edt(() -> {
            GraphPanel p=f.tabs.graphForAction("Two",false);
            assertNotNull(p,"named action must reopen the closed definition");
            assertEquals("keep this explanation",p.notes().explanation(),"named action must load the closed definition before editing"); return null;
        }); }
    }
    @Test void importingOverAnOpenChartKeepsIncomingDefinitionAndOpenState() throws Exception {
        try(var f=new Fixture(chart("One",true))) { edt(() -> {
            AppConfig incoming=new AppConfig();
            incoming.savedGraphs.add(new GraphSpec("One",List.of("node\u0001other"),List.of(),null,null,"incoming",
                    "incoming explanation",List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),"points",false));
            incoming.savedGraphs.add(chart("New chart",true));
            SettingsShare share=new SettingsShare();
            var categories=java.util.Set.of(SettingsShare.Category.GRAPHS);
            share.apply(share.preview(share.export(incoming,categories),f.config,f.profile.getParent()),categories,f.config);
            invoke(f.frame,"applyImportedConfig");f.flush();
            assertEquals("points",f.saved("One").style(),"import must win over old tabs before a save");
            assertFalse(f.saved("One").open());assertEquals("incoming explanation",f.saved("One").explanation());
            assertNull(f.tabs.graphNamed("One"));assertNotNull(f.tabs.graphNamed("New chart"));
            return null;
        }); }
    }
    @Test void confirmationCannotDeleteAReplacementTab() throws Exception {
        try(var f=new Fixture(chart("One",true))) { edt(() -> {
            f.delete(true, () -> {
                f.config.savedGraphs.clear();f.config.savedGraphs.add(chart("Replacement",true));
                f.tabs.restore(List.copyOf(f.config.savedGraphs));
            });
            assertNotNull(f.tabs.graphNamed("Replacement"),"a stale confirmation must not delete the replacement tab");
            assertEquals(List.of("Replacement"),f.config.savedGraphs.stream().map(GraphSpec::name).toList());
            return null;
        }); }
    }


    @Test void cancelPreservesBytesAndConfirmDeletesOnlyTheNamedChart() throws Exception {
        try(var f=new Fixture(chart("Delete me",true),chart("Graph 2",false))) { edt(() -> {
            f.flush();byte[] before=Files.readAllBytes(f.profile);GraphPanel pane=f.tabs.graphNamed("Delete me");
            f.delete(false);f.flush();assertArrayEquals(before,Files.readAllBytes(f.profile),"Cancel changes no profile bytes");
            assertSame(pane,f.tabs.graphNamed("Delete me"));f.delete(true);f.flush();
            assertTrue(f.config.savedGraphs.stream().noneMatch(g->g.name().equals("Delete me")));
            assertEquals("keep this explanation",f.saved("Graph 2").explanation());assertFalse(f.saved("Graph 2").open());
            button(f.tabs,"New graph").doClick();f.flush();
            assertEquals("keep this explanation",f.saved("Graph 2").explanation(),"delete then create must preserve closed names");
            return null;
        }); }
    }
    @Test void alternatingReportRowsReachesTheRealReportsPanelByIdentity() throws Exception {
        try(var f=new Fixture(chart("One",true))) { edt(() -> {
            f.config.reports.add(new telamin.fluxtion.audit.analyser.analyser.report.ReportSpec("first-id","First title",null,null,null,null,List.of()));
            f.config.reports.add(new telamin.fluxtion.audit.analyser.analyser.report.ReportSpec("second-id","Second title",null,null,null,null,List.of()));
            ReportsPanel reports=(ReportsPanel)field(f.frame,"reportsPanel");reports.refresh();
            JList<?> list=(JList<?>)field(reports,"list");
            f.openRow("Second title");assertEquals("second-id",list.getSelectedValue(),"second row must select second identity");
            assertTrue(components((Component)field(reports,"detail")).stream().anyMatch(c->c instanceof JTextArea t && "Second title".equals(t.getText())),"second report detail must be rendered");
            f.openRow("First title");assertEquals("first-id",list.getSelectedValue(),"first row must select first identity");
            assertTrue(components((Component)field(reports,"detail")).stream().anyMatch(c->c instanceof JTextArea t && "First title".equals(t.getText())),"first report detail must be rendered");
            assertSame(reports,((JTabbedPane)field(f.frame,"sideTabs")).getSelectedComponent());return null;
        }); }
    }
    @Test void closeAndReloadKeepDefinitionAndOpenRestoresItsContent() throws Exception {
        try(var f=new Fixture(chart("One",true),chart("Two",true))) {
            edt(()->{
                f.openRow("Two");button(f.tabs,"Close graph").doClick();f.flush();
                assertNull(f.tabs.graphNamed("Two"));assertFalse(f.saved("Two").open());
                assertEquals("keep this explanation",f.saved("Two").explanation());
                return null;
            });
            assertTrue(f.executor.render("open",Map.of("log",f.log.toString())).ok());
            for(int i=0;i<400 && edt(()->field(f.frame,"store"))==null;i++)Thread.sleep(25);
            edt(()->{
                assertNull(f.tabs.graphNamed("Two"),"reload leaves the closed definition closed");
                f.openRow("Two");f.flush();GraphSpec back=f.saved("Two");
                assertEquals("line",back.style());assertEquals(1000L,back.from());assertEquals(2000L,back.to());
                assertEquals("keep this note",back.notes().getFirst().text());
                assertEquals("keep this explanation",back.explanation());
                GraphPanel same=f.tabs.graphNamed("Two");same.addSpecs(List.of("node\u0001later"));
                var series=same.seriesSpecs();f.openRow("One");f.openRow("Two");
                assertSame(same,f.tabs.graphNamed("Two"));assertEquals(series,same.seriesSpecs());
                return null;
            });
        }
    }
    @Test void projectSwitchDoesNotMergeOutgoingChartsIntoIncomingProfile() throws Exception {
        try(var f=new Fixture(chart("Outgoing",true),chart("Closed",false))) {
            Path next=ProjectProfile.pathFor(tmp.resolve("next"));AppConfig incoming=new AppConfig();incoming.savedGraphs.add(chart("Incoming",true));
            ProjectProfile.save(next,incoming,new SettingsShare());
            assertTrue(f.executor.render("open",Map.of("project",next.toString())).ok());
            assertTrue(f.executor.render("open",Map.of("log",f.log.toString())).ok());
            for(int i=0;i<400 && edt(()->field(f.frame,"store"))==null;i++)Thread.sleep(25);
            edt(()->{f.flush();assertEquals(List.of("Incoming"),f.config.savedGraphs.stream().map(GraphSpec::name).toList());
                AppConfig old=new AppConfig();assertTrue(ProjectProfile.load(f.profile,old,new SettingsShare()).loaded());
                assertEquals(List.of("Outgoing","Closed"),old.savedGraphs.stream().map(GraphSpec::name).toList());return null;});
        }
    }
}
