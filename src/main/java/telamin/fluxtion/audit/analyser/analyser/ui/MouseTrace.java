package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A field diagnostic for ONE open question: when the records table is left scrolling and extending its selection
 * with nothing touching it, where did the mouse release go?
 *
 * <p><b>Why this exists.</b> During a 1.26.0 demo the table began auto-scrolling and extending its selection on
 * its own, and only restarting the app stopped it. That symptom is {@code javax.swing.Autoscroller} still
 * running, which stops only when the TABLE itself processes a {@code MOUSE_RELEASED}. So a release went
 * somewhere else. A first theory — that the spotlight overlay, being the frame's glass pane, swallowed it — was
 * <b>disproved</b>: {@code LightweightDispatcher} keeps a mouse grab, so an in-progress drag's release is
 * retargeted to the component that took the press whatever is on top. Measured on a real frame: the table gets
 * its release even with the overlay raised mid-gesture. The remaining candidates are another <i>window</i>
 * taking the release or the grab — a dialog, a heavyweight popup, a tooltip — or the OS delivering it outside
 * the frame. A posted event cannot model which window the OS picks, so this needs a physical mouse, and
 * therefore needs instrumentation rather than a test.
 *
 * <p><b>What it records.</b> Every mouse press, release and click that reaches the AWT event queue, with its
 * source component AND its source <i>window</i>, the component under the pointer, the buttons it believes are
 * down, and the table's selection state. Drags are counted rather than logged, so the trace stays readable.
 * A line beginning {@code SUSPECT} marks the thing we are hunting: the table's selection changing while no
 * button is believed down.
 *
 * <p><b>Off unless asked.</b> Enabled only by {@code -Danalyser.mouseTrace=<path>} (or {@code =stderr}). It adds
 * a passive {@link java.awt.event.AWTEventListener}; it consumes nothing and changes no behaviour.
 */
public final class MouseTrace {

    /** The system property that turns it on: a file path, or "stderr". */
    public static final String PROPERTY = "analyser.mouseTrace";

    private final Writer out;
    private final Set<Integer> buttonsDown = new LinkedHashSet<>();
    private int dragsSincePress;
    /** Whether any real press has been seen: a runaway follows a gesture, model churn does not. */
    private boolean everSawPress;
    private JTable watched;
    private int lastSelectionSize = -1;

    private MouseTrace(Writer out) {
        this.out = out;
    }

    /**
     * Install the trace if {@code -Danalyser.mouseTrace} is set; otherwise do nothing and return null.
     * Never throws into startup: a diagnostic that stops the app starting is worse than the bug.
     */
    public static MouseTrace installIfRequested() {
        String where = System.getProperty(PROPERTY);
        if (where == null || where.isBlank()) return null;
        try {
            Writer w = "stderr".equalsIgnoreCase(where)
                    ? new java.io.OutputStreamWriter(System.err, StandardCharsets.UTF_8)
                    : Files.newBufferedWriter(Path.of(where), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            MouseTrace trace = new MouseTrace(w);
            trace.line("START", "trace=" + where + " note=\"press/release/click with source window; drags counted\"");
            Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
                if (e instanceof MouseEvent me) trace.onMouse(me);
            }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
            return trace;
        } catch (IOException | RuntimeException e) {
            System.err.println("[mouseTrace] not installed: " + e);
            return null;
        }
    }

    /** Watch this table's selection, so the trace says what the table was doing at each event. */
    public void watch(JTable table) {
        this.watched = table;
        if (table == null) return;
        table.getSelectionModel().addListSelectionListener(e -> {
            int size = selectionSize();
            boolean grew = lastSelectionSize >= 0 && size > lastSelectionSize;
            lastSelectionSize = size;
            boolean adjusting = table.getSelectionModel().getValueIsAdjusting();
            // THE HUNT, stated precisely: the table believes a drag GESTURE is in progress (valueIsAdjusting)
            // while no button is down. That is the stuck state itself, and it is what a runaway selection is
            // made of. Deliberately NOT "the selection grew with no button down" — every programmatic
            // selection does that (a walk step, goto, a spotlight), and a diagnostic that cries at normal
            // behaviour is one nobody reads.
            // size > 0 and everSawPress, because a real capture proved the bare rule fires on nothing: opening a
            // log churns the selection model, so valueIsAdjusting goes true with no button down and an EMPTY
            // selection, three times in a row. A runaway always has rows selected and always follows a real
            // gesture, so both are required.
            if (adjusting && buttonsDown.isEmpty() && size > 0 && everSawPress) {
                line("SUSPECT", "tableIsMidDragButNoButtonIsDown size=" + size + " grew=" + grew
                        + " dragsSinceLastPress=" + dragsSincePress
                        + " note=\"the release that should have ended this gesture went elsewhere - the"
                        + " RELEASED line above says where\"");
            }
        });
    }

    private void onMouse(MouseEvent e) {
        switch (e.getID()) {
            case MouseEvent.MOUSE_DRAGGED -> dragsSincePress++;
            case MouseEvent.MOUSE_PRESSED -> {
                buttonsDown.add(e.getButton());
                everSawPress = true;
                dragsSincePress = 0;
                line("PRESSED", describe(e));
            }
            case MouseEvent.MOUSE_RELEASED -> {
                boolean wasDown = buttonsDown.remove(e.getButton());
                line("RELEASED", describe(e) + " hadPress=" + wasDown + " drags=" + dragsSincePress);
            }
            case MouseEvent.MOUSE_CLICKED -> line("CLICKED", describe(e));
            default -> { }
        }
    }

    /** The fields that answer "where did it go": the source, its window, and what is under the pointer. */
    private String describe(MouseEvent e) {
        Component src = e.getComponent();
        Window win = src == null ? null : SwingUtilities.getWindowAncestor(src);
        Component under = src == null ? null : SwingUtilities.getDeepestComponentAt(src, e.getX(), e.getY());
        return "btn=" + e.getButton()
                + " down=" + buttonsDown
                + " src=" + name(src)
                + " srcWindow=" + windowName(win)
                + " under=" + name(under)
                + " onTable=" + onWatchedTable(src)
                + " sel=" + selectionSize()
                + " adjusting=" + (watched == null ? "-" : watched.getSelectionModel().getValueIsAdjusting())
                + " popupShowing=" + popupShowing();
    }

    private boolean onWatchedTable(Component c) {
        for (Component p = c; p != null; p = p.getParent()) if (p == watched) return true;
        return false;
    }

    private int selectionSize() {
        return watched == null ? -1 : watched.getSelectedRowCount();
    }

    /** Whether any window other than the main frame is showing — a dialog, a heavyweight popup, a tooltip. */
    private static String popupShowing() {
        StringBuilder others = new StringBuilder();
        for (Window w : Window.getWindows()) {
            if (!w.isShowing()) continue;
            String n = windowName(w);
            if (n.startsWith("MainFrame")) continue;
            if (others.length() > 0) others.append(',');
            others.append(n);
        }
        return others.length() == 0 ? "none" : others.toString();
    }

    private static String name(Component c) {
        if (c == null) return "null";
        String n = c.getName();
        return c.getClass().getSimpleName() + (n == null || n.isBlank() ? "" : "#" + n);
    }

    private static String windowName(Window w) {
        if (w == null) return "null";
        String title = w instanceof java.awt.Frame f ? f.getTitle()
                : w instanceof java.awt.Dialog d ? d.getTitle() : null;
        return w.getClass().getSimpleName() + (title == null || title.isBlank() ? "" : "(" + title + ")");
    }

    private void line(String kind, String rest) {
        try {
            out.write(Instant.now() + " " + kind + " " + rest + System.lineSeparator());
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
