package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.plaf.basic.BasicHTML;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * SF-2 on a REAL, shown analyser: a report title an agent authored and a recent project's configured path are drawn
 * as their characters, and an {@code <img src=…>} in either is never fetched — not when the window paints, and not
 * when the person hovers the row and its tooltip opens.
 *
 * <p>The witness is on the same screen and the same probe: a component built the way the shipped code built it (a
 * plain {@code JLabel} given the text, a button whose tooltip is the raw path) is painted and hovered too, and MUST
 * reach the server. Without it, "no request" could mean only that this display never asked. Hover is the pointer's
 * enter and move delivered to the component, with the tooltip delay at zero, so the real tooltip window opens; no key
 * events are used (they are dropped without focus on macOS).
 */
class ForeignTextFrameTest {

    @Test
    @DisplayName("SF-2: a report title and a recent project render as their characters; nothing is fetched on paint or hover")
    void foreignTextIsItsCharactersOnAShownWindow(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display");
        ToolTipManager tips = ToolTipManager.sharedInstance();
        int initialDelay = tips.getInitialDelay();
        AtomicReference<JFrame> witnessFrame = new AtomicReference<>();
        try (LoopbackProbe probe = new LoopbackProbe();
             var f = EvidenceCaptureFrameTest.shown(tmp)) {
            onEdt(() -> tips.setInitialDelay(0));
            EvidenceCaptureFrameTest.openLog(f,
                    Path.of("src/main/resources/demo/demo-quote-audit.yaml").toAbsolutePath());
            // an agent writes the report; its title is the payload — short, so the Project panel draws it whole rather
            // than abbreviating it in the middle
            onEdt(() -> render(f.ex, "report", Map.of("name", "sf2-demo", "title", probe.payload("/r"),
                    "sections", List.of(Map.of("kind", "narrative", "text", "DEMO")))));

            // the Reports tab's list, painted from the real frame's panel
            onEdt(() -> {
                ReportsPanel reports = (ReportsPanel) field(f.frame, "reportsPanel");
                reports.refresh();
                @SuppressWarnings("unchecked")
                JList<String> list = (JList<String>) UiTextTest.find(reports, JList.class);
                assertNotNull(list, "the reports list");
                assertTrue(list.getModel().getSize() > 0, "the report is listed");
                list.setSize(400, 200);
                paint(list);
                JLabel cell = (JLabel) list.getCellRenderer().getListCellRendererComponent(list, list.getModel()
                        .getElementAt(0), 0, false, false);
                assertTrue(cell.getText().contains("&lt;img src="), "the title is drawn as characters: " + cell.getText());
                UiTextTest.tip(cell);
                // the Project panel lists the report by its title
                paint((JComponent) field(f.frame, "projectPanel"));
            });

            // the start page, with a recent project whose configured path is the payload
            String recent = probe.payload("/recent-project");
            AtomicReference<JButton> row = new AtomicReference<>();
            onEdt(() -> {
                try {
                    var show = MainFrame.class.getDeclaredMethod("showWorkspace", boolean.class);
                    show.setAccessible(true);
                    show.invoke(f.frame, true);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
                StartPanel start = (StartPanel) field(f.frame, "startPanel");
                start.setRecentProjects(List.of(recent));
                f.frame.validate();
                JButton b = UiTextTest.buttons(start).stream()
                        .filter(x -> x instanceof JButton && x.getText() != null && x.getText().contains(recent))
                        .map(x -> (JButton) x).findFirst().orElse(null);
                assertNotNull(b, "the recent project's row");
                assertNull(b.getClientProperty(BasicHTML.propertyKey), "the row's text is not an HTML view");
                b.scrollRectToVisible(new Rectangle(b.getSize()));
                row.set(b);
            });

            // the witness, built as the shipped code built them, on screen beside the analyser
            AtomicReference<JButton> witnessButton = new AtomicReference<>();
            onEdt(() -> {
                JFrame w = new JFrame("SF-2 witness");
                JPanel p = new JPanel(new FlowLayout());
                p.add(new JLabel(probe.payload("/witness-paint")));
                JButton b = new JButton("witness");
                b.setToolTipText(probe.payload("/witness-hover"));
                p.add(b);
                w.setContentPane(p);
                w.setBounds(f.frame.getX() + f.frame.getWidth() + 10, f.frame.getY(), 260, 120);
                w.setVisible(true);
                witnessFrame.set(w);
                witnessButton.set(b);
            });
            Robot robot = new Robot();
            robot.waitForIdle();
            onEdt(() -> { paint(f.frame.getRootPane()); paint(witnessFrame.get().getRootPane()); });

            hover(robot, row.get());
            hover(robot, witnessButton.get());
            assertTrue(probe.awaitHit("/witness-paint", 5000), "witness: a label given the text the way it shipped fetches");
            assertTrue(probe.awaitHit("/witness-hover", 5000),
                    "witness: hovering a raw tooltip on this display fetches — so the hover below was a real one");
            // and once more on the row, after the witness proved hover works here
            hover(robot, row.get());
            onEdt(() -> UiTextTest.tip(row.get()));          // the direct call ToolTipManager makes
            Thread.sleep(400);
            assertEquals(0, probe.hits("/r"), "a report title was fetched");
            assertEquals(0, probe.hits("/recent-project"), "a recent project's path was fetched");
        } finally {
            onEdt(() -> {
                tips.setInitialDelay(initialDelay);
                if (witnessFrame.get() != null) witnessFrame.get().dispose();
            });
        }
    }

    /**
     * The pointer arriving on {@code c}: MOUSE_ENTERED then MOUSE_MOVED delivered to the component, which is what
     * {@link ToolTipManager} listens for. Delivered directly rather than through {@code Robot.mouseMove}, which macOS
     * drops for a process without the accessibility grant — the witness below proves the tooltip really opens.
     */
    private static void hover(Robot robot, JComponent c) throws Exception {
        onEdt(() -> {
            int x = Math.min(20, c.getWidth() / 2), y = c.getHeight() / 2;
            long now = System.currentTimeMillis();
            c.dispatchEvent(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_ENTERED, now, 0, x, y, 0, false));
            c.dispatchEvent(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_MOVED, now + 1, 0, x + 2, y, 0, false));
        });
        robot.waitForIdle();
        Thread.sleep(500);
        onEdt(() -> c.dispatchEvent(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_EXITED,
                System.currentTimeMillis(), 0, -1, -1, 0, false)));
    }

    private static void paint(JComponent c) {
        int w = Math.max(1, c.getWidth()), h = Math.max(1, c.getHeight());
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            c.paint(g);
        } finally {
            g.dispose();
        }
    }
}
