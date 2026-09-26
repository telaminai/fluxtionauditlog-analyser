package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;

import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * M68.7 (owner, Q4 2026-09-26), in a real frame on a MAPPED log — the store whose charts and detail pane read the file
 * as it is now, while the table's rows are the log as it was indexed. After an in-place rewrite, the session's verdict
 * must reach every surface that can show a value: the table (M68.5), the charts (M68.7, gating G14) and the detail
 * pane (M68.7). Reopening the log clears all three.
 */
class M687ReviewProbeTest {

    @TempDir Path tmp;

    private static final String REC = "eventLogRecord:\n  logTime: %d\n  event: Tick\n  nodeLogs:\n    - node: { value: %d}\n---\n";

    private Path writeLog() throws Exception {
        return Files.writeString(tmp.resolve("mapped.yml"), "---\n" + REC.formatted(1000, 1) + REC.formatted(2000, 2));
    }

    @Test
    void anInPlaceRewriteIsStatedOnTheTableTheChartsAndTheDetailPane() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path log = writeLog();
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> { System.out.println("REVIEW originalHarnessShowing="+f.frame.isShowing()); ThemeManager.apply("Light"); javax.swing.SwingUtilities.updateComponentTreeUI(f.frame); f.frame.setSize(1200,800); f.frame.setVisible(true); });
            onEdt(() -> ((AppConfig) field(f.frame, "config")).memoryThresholdMb = 0);   // 0 → always memory-mapped
            assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok(), "the fixture opens");
            AsyncOpenInterleavingFrameTest.awaitLoaded(f.ex);
            onEdt(() -> assertEquals("MappedLogStore", field(f.frame, "store").getClass().getSimpleName(),
                    "control: the log is memory-mapped, the store that reads through"));
            assertTrue(f.ex.render("graph", Map.of("name", "Before", "series", List.of("node.value"))).ok(), "a chart opens");
            assertTrue(f.ex.render("goto", Map.of("recordIndex", 1)).ok(), "a record is selected");
            GraphTabs charts = (GraphTabs) field(f.frame, "graphTabs");
            DetailPanel detail = (DetailPanel) field(f.frame, "detailPanel");
            LogTablePanel table = (LogTablePanel) field(f.frame, "tablePanel");
            onEdt(() -> {
                assertNull(table.identityNote(), "control: nothing is stated before the file changes");
                assertNull(charts.identityNote(), "control: the charts say nothing before the file changes");
                assertNull(detail.identityNote(), "control: the detail pane says nothing before the file changes");
            });

            // same inode, same length, later modification time: the mapped channel now reads different bytes
            Files.writeString(log, Files.readString(log).replace("value: 2", "value: 7"));
            Files.setLastModifiedTime(log, FileTime.fromMillis(System.currentTimeMillis() + 60_000));
            var dispatcher = new telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher(false,null,
                    () -> ((telamin.fluxtion.audit.analyser.analyser.parse.LogStore)field(f.frame,"store")).index().snapshot(),
                    row -> ((telamin.fluxtion.audit.analyser.analyser.parse.LogStore)field(f.frame,"store")).rawText(row),f.ex);
            var read = dispatcher.dispatch(Map.of("action","read","params",Map.of("limit",1)));
            assertFalse(read.ok(),"a real record-reading request refuses the rewritten mapped file");
            var session = (telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(f.frame, "session");
            AtomicReference<String> verdict = new AtomicReference<>();
            for (int i = 0; i < 200; i++) {                       // the observation is posted with invokeLater
                onEdt(() -> verdict.set(session.snapshot().logIdentity()));
                if ("UNVERIFIED".equals(verdict.get()) || "REPLACEMENT".equals(verdict.get())) break;
                Thread.sleep(25);
            }
            assertTrue("UNVERIFIED".equals(verdict.get()) || "REPLACEMENT".equals(verdict.get()),
                    "control: the session observed the rewrite, got " + verdict.get());
            String reason = session.snapshot().logIdentityReason();

            onEdt(() -> assertAll("every surface that can show a value states the verdict",
                    () -> assertNotNull(table.identityNote(), "the table states the verdict (M68.5)"),
                    () -> assertNotNull(charts.identityNote(), "the charts must state the verdict before G14 can pass on them"),
                    () -> assertNotNull(detail.identityNote(), "the detail pane must state the verdict"),
                    () -> assertTrue(String.valueOf(charts.identityNote()).contains(reason), "the charts carry the session's reason"),
                    () -> assertTrue(String.valueOf(detail.identityNote()).contains(reason), "the detail pane carries the session's reason")));

            onEdt(() -> {
                f.frame.validate();
                for (var panel : List.of(charts,detail)) {
                    var label=(javax.swing.JLabel)field(panel,"identityBanner");
                    var vr=label.getVisibleRect();var tr=new java.awt.Rectangle();var ir=new java.awt.Rectangle();
                    String drawn=javax.swing.SwingUtilities.layoutCompoundLabel(label,label.getFontMetrics(label.getFont()),label.getText(),null,label.getVerticalAlignment(),label.getHorizontalAlignment(),label.getVerticalTextPosition(),label.getHorizontalTextPosition(),vr,ir,tr,0);
                    System.out.println("REVIEW "+panel.getClass().getSimpleName()+" showing="+label.isShowing()+" width="+label.getWidth()+" preferred="+label.getPreferredSize().width+" drawn="+drawn);
                }
                var image=new java.awt.image.BufferedImage(1200,800,java.awt.image.BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();f.frame.paint(g);g.dispose();
                try { javax.imageio.ImageIO.write(image,"png",Path.of(System.getProperty("review.output", "target"), "m687-live.png").toFile()); } catch(Exception e){throw new RuntimeException(e);}
                ThemeManager.apply("Dark");javax.swing.SwingUtilities.updateComponentTreeUI(f.frame);
                var label=(javax.swing.JLabel)field(charts,"identityBanner");
                charts.setIdentityNote(charts.identityNote());
                System.out.println("REVIEW darkActual="+Integer.toHexString(label.getForeground().getRGB())+" darkExpected="+Integer.toHexString(UiTheme.warnForeground().getRGB()));
            });
            var refusedReport=dispatcher.dispatch(Map.of("action","report","params",Map.of("name","Blocked","sections",List.of(Map.of("kind","chart","graph","Before")))));
            assertFalse(refusedReport.ok(),"mapped in-place rewrite also refuses the report request");
            System.out.println("REVIEW verdict="+verdict.get()+" mappedReportRefused="+!refusedReport.ok());
            // a chart opened after the verdict is under the same banner
            assertTrue(f.ex.render("graph", Map.of("name", "After", "series", List.of("node.value"))).ok(), "a second chart opens");
            onEdt(() -> assertNotNull(charts.identityNote(), "a chart opened after the verdict is still marked"));

            Path replacement=Files.writeString(tmp.resolve("replacement.yml"),Files.readString(log));
            Files.move(replacement,log,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            dispatcher.dispatch(Map.of("action","read","params",Map.of("limit",1)));
            onEdt(() -> {});
            onEdt(() -> assertNotNull(charts.identityNote(),"atomic replacement remains marked"));
            var cfg=(AppConfig)field(f.frame,"config");
            onEdt(() -> {cfg.assistantExports=true;cfg.assistantExportDir=tmp.toString();});
            var exported=dispatcher.dispatch(Map.of("action","report","params",Map.of("name","Retained chart","sections",List.of(Map.of("kind","chart","graph","Before")),"path",tmp.resolve("retained.pdf").toString())));
            assertTrue(exported.ok(),"atomic replacement keeps readable old channel and allows a qualified reply");
            System.out.println("REVIEW atomicReportAllowed="+exported.ok()+" replyHasIdentityNote="+exported.payload().containsKey("identityNote"));
            Files.copy(tmp.resolve("retained.pdf"),Path.of(System.getProperty("review.output", "target"), "m687-retained.pdf"),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            // reopening reads the file again, so nothing is superseded and nothing is said
            assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok(), "the log reopens");
            AsyncOpenInterleavingFrameTest.awaitLoaded(f.ex);
            for (int i = 0; i < 200 && charts.identityNote() != null; i++) Thread.sleep(25);
            onEdt(() -> assertAll("a reopened log is current on every surface",
                    () -> assertNull(table.identityNote(), "the table is current again"),
                    () -> assertNull(charts.identityNote(), "the charts are current again"),
                    () -> assertNull(detail.identityNote(), "the detail pane is current again")));
            Files.setLastModifiedTime(log,FileTime.fromMillis(System.currentTimeMillis()+120000));
            dispatcher.dispatch(Map.of("action","read","params",Map.of("limit",1)));
            onEdt(() -> {});
            assertTrue(f.ex.render("open",Map.of("close","log")).ok(),"close log succeeds");
            onEdt(() -> assertAll("close clears all new surfaces",()->assertNull(charts.identityNote()),()->assertNull(detail.identityNote())));
            System.out.println("REVIEW closeClears=true");

        }
    }
}
