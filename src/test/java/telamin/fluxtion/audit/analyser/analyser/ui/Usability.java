package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.JTableHeader;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The usability scorecard's measuring instrument (owner, 2026-10-18: "usability improvement measured, not asserted").
 * It reads the screen the way the frame tests do — the showing Swing tree of every window — and it acts the way they
 * do: real Swing events dispatched to the component a person would press, found by what the person can SEE.
 *
 * <p>Every definition here is mechanical, so a re-run gives the same numbers (docs/usability/scorecard.md):
 * <ul>
 *   <li><b>chrome words</b>: the words of every visible label, button, tab title, section title, list row, combo value
 *       and read-only text, in the part of it that is on screen; tooltips, the transcript, table cells and the text a
 *       person typed are not chrome (the M78 review's rule);</li>
 *   <li><b>an act</b>: one click, one key, one typed string, one drag (a drop) or one scroll;</li>
 *   <li><b>a hesitation</b>: before an act, its control is not visible, is below the fold, sits behind a context menu or
 *       an overflow glyph, or its visible label holds none of the task's key words;</li>
 *   <li><b>a dead end</b>: the next control is not visible AND nothing visible names any of the task's key words;</li>
 *   <li><b>a clipped string</b>: what {@link TextFit} reports.</li>
 * </ul>
 * Test and tools code only: it adds no hook to the product (rule 9 is not involved).
 */
final class Usability {

    private Usability() {
    }

    /** A word: a run of letters or digits, joined by an apostrophe, hyphen or dot ("Shift+Enter" is two). */
    static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:['’.\\-][\\p{L}\\p{N}]+)*");

    static int words(String text) {
        if (text == null) return 0;
        Matcher m = WORD.matcher(plain(text));
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    /** HTML to the words it shows. */
    static String plain(String text) {
        if (text == null) return "";
        String t = text;
        if (t.regionMatches(true, 0, "<html", 0, 5)) {
            t = t.replaceAll("(?is)<head>.*?</head>", " ").replaceAll("<[^>]+>", " ")
                    .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&#8212;", "—").replace("&mdash;", "—");
        }
        return t.replaceAll("\\s+", " ").strip();
    }

    // ---- reading the screen -------------------------------------------------------------------------------------------

    /** One piece of visible chrome: who shows it and the words on screen. */
    record Shown(Component component, String text) {
        int words() {
            return Usability.words(text);
        }
    }

    /** The showing windows, topmost first: dialogs (newest first), then frames and the rest. */
    static List<Window> windows() {
        List<Window> dialogs = new ArrayList<>();
        List<Window> rest = new ArrayList<>();
        for (Window w : Window.getWindows()) {
            if (!w.isShowing()) continue;
            if (w instanceof Dialog) dialogs.add(0, w);
            else rest.add(0, w);
        }
        dialogs.addAll(rest);
        return dialogs;
    }

    /** Every piece of visible chrome under {@code root}. Call on the event thread. */
    static List<Shown> chrome(Component root) {
        List<Shown> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    /** Every piece of visible chrome in every showing window. Call on the event thread. */
    static List<Shown> chromeOnScreen() {
        List<Shown> out = new ArrayList<>();
        for (Window w : windows()) collect(w, out);
        return out;
    }

    static int chromeWords(List<Shown> shown) {
        int n = 0;
        for (Shown s : shown) n += s.words();
        return n;
    }

    /** Visible buttons (enabled or not) under {@code root}: the M78 "controls" count. */
    static int controls(Component root) {
        int[] n = new int[1];
        countControls(root, n);
        return n[0];
    }

    private static void countControls(Component c, int[] n) {
        if (!c.isShowing() || !onScreen(c)) return;
        if (c instanceof AbstractButton b && !(c instanceof javax.swing.JMenuItem) && !label(b).isBlank()) n[0]++;
        if (c instanceof Container k && !(c instanceof JTextComponent)) for (Component ch : k.getComponents()) countControls(ch, n);
    }

    /** Whether a component shows at least a 4×6 px patch (the M74 inventory rule for "visible"). */
    static boolean onScreen(Component c) {
        if (!(c instanceof JComponent j)) return c.isShowing() && c.getWidth() > 0 && c.getHeight() > 0;
        Rectangle v = j.getVisibleRect();
        return v.width >= 4 && v.height >= 6;
    }

    private static void collect(Component c, List<Shown> out) {
        if (!c.isShowing() || c.getWidth() <= 0 || c.getHeight() <= 0) return;
        if (c instanceof TranscriptView) return;                        // the transcript is content, not chrome
        if (c instanceof JComponent j) {
            String title = borderTitle(j.getBorder());
            if (!title.isBlank() && onScreen(c)) out.add(new Shown(c, title));
        }
        if (onScreen(c)) {
            String t = chromeText(c);
            if (t != null && !t.isBlank()) out.add(new Shown(c, t));
        }
        if (c instanceof JTable || c instanceof JList<?> || c instanceof JTextComponent || c instanceof JComboBox<?>) return;
        if (c instanceof Container k) for (Component ch : k.getComponents()) collect(ch, out);
    }

    static String borderTitle(Border b) {
        if (b instanceof TitledBorder t) return t.getTitle() == null ? "" : t.getTitle();
        if (b instanceof CompoundBorder cb) return (borderTitle(cb.getOutsideBorder()) + " " + borderTitle(cb.getInsideBorder())).strip();
        return "";
    }

    /** The chrome text a component shows, or null. */
    static String chromeText(Component c) {
        if (c instanceof AbstractButton b) return label(b);
        if (c instanceof JLabel l) return plain(l.getText());
        if (c instanceof JTabbedPane tp) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < tp.getTabCount(); i++) {
                Rectangle r = tp.getBoundsAt(i);
                if (r != null && tp.getVisibleRect().intersects(r) && tp.getTitleAt(i) != null) sb.append(tp.getTitleAt(i)).append(' ');
            }
            return sb.toString().strip();
        }
        if (c instanceof JTableHeader h) {
            StringBuilder sb = new StringBuilder();
            Rectangle vis = h.getVisibleRect();
            for (int i = 0; i < h.getColumnModel().getColumnCount(); i++) {
                if (vis.intersects(h.getHeaderRect(i))) sb.append(h.getColumnModel().getColumn(i).getHeaderValue()).append(' ');
            }
            return sb.toString().strip();
        }
        if (c instanceof JList<?> list) return listText(list);
        // M77-5: Set up AI's "Where data goes" picture paints its own words
        if (c instanceof telamin.fluxtion.audit.analyser.analyser.ui.setup.FlowDiagram d) {
            return d.model() == null ? null : String.join(" ", d.model().drawnWords());
        }
        if (c instanceof JComboBox<?> cb) return cb.getSelectedItem() == null ? null : plain(String.valueOf(cb.getSelectedItem()));
        if (c instanceof JTextComponent t) {
            if (t.isEditable()) {
                // the person's own text is not chrome; the placeholder of an empty box is, as far as it is drawn
                String ph = placeholder(t);
                if (t.getDocument().getLength() > 0 || ph == null) return null;
                java.awt.FontMetrics fm = t.getFontMetrics(t.getFont());
                int room = t.getWidth() - t.getInsets().left - t.getInsets().right - 2;
                int end = ph.length();
                while (end > 0 && fm.stringWidth(ph.substring(0, end)) > room) end--;
                return ph.substring(0, end);
            }
            return visibleText(t);
        }
        return null;
    }

    /** The placeholder an empty box paints: FlatLaf's client property, or the assistant composer's own. */
    static String placeholder(JTextComponent t) {
        Object ph = t.getClientProperty("JTextField.placeholderText");
        if (ph != null) return String.valueOf(ph);
        Class<?> outer = t.getClass().getEnclosingClass();
        return outer == AssistantSurface.class ? AssistantSurface.COMPOSER_PLACEHOLDER : null;
    }

    /** What a button shows: its text, or the words a custom-painted button draws. */
    static String label(AbstractButton b) {
        String t = plain(b.getText());
        if (!t.isBlank()) return t;
        if (b instanceof StartPanel.ActionChip chip) return chip.label() == null ? "" : chip.label();
        String card = cardText(b);
        if (card != null) return card;
        Icon icon = b.getIcon();
        if (icon != null && icon.getClass().getName().endsWith("NavRail$RotatedText")) {
            String an = b.getAccessibleContext().getAccessibleName();
            return an == null ? "" : an;
        }
        return "";
    }

    /**
     * A profile card painted its title and its line (P3). M77-4 retired the cards; no button of this build paints its
     * own words, so this is null for every button. Kept as the one place a painted card would be read.
     */
    static String cardText(AbstractButton b) {
        return null;
    }

    /** The painted title of a profile card alone (its label as a target): none in this build (see {@link #cardText}). */
    static String cardTitle(AbstractButton b) {
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String listText(JList list) {
        StringBuilder sb = new StringBuilder();
        Rectangle vis = list.getVisibleRect();
        for (int i = 0; i < list.getModel().getSize(); i++) {
            Rectangle cell = list.getCellBounds(i, i);
            if (cell == null || !vis.intersects(cell)) continue;
            sb.append(rowText(list, i)).append(' ');
        }
        return sb.toString().strip();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static String rowText(JList list, int i) {
        Object v = list.getModel().getElementAt(i);
        Component rc = list.getCellRenderer().getListCellRendererComponent(list, v, i, false, false);
        String s = rendered(rc);
        return s.isBlank() ? plain(String.valueOf(v)) : s;
    }

    private static String rendered(Component rc) {
        if (rc instanceof JLabel l) return plain(l.getText());
        if (rc instanceof AbstractButton b) return plain(b.getText());
        if (rc instanceof JTextComponent t) return plain(t.getText());
        if (rc instanceof Container k) {
            StringBuilder sb = new StringBuilder();
            for (Component ch : k.getComponents()) sb.append(rendered(ch)).append(' ');
            return sb.toString().strip();
        }
        return "";
    }

    /** The part of a read-only text component's text that is inside its visible rectangle. */
    static String visibleText(JTextComponent t) {
        Rectangle v = t.getVisibleRect();
        int len = t.getDocument().getLength();
        if (len == 0) return "";
        try {
            int a = t.viewToModel2D(new Point(v.x, v.y));
            int b = t.viewToModel2D(new Point(v.x + v.width - 1, v.y + v.height - 1));
            a = Math.max(0, Math.min(a, len));
            b = Math.max(a, Math.min(b + 1, len));
            if (v.width >= t.getWidth() - 1 && v.height >= t.getHeight() - 1) { a = 0; b = len; }
            return t.getDocument().getText(a, b - a);
        } catch (Exception e) {
            return "";
        }
    }

    /** Clipped strings on screen, in every showing window. */
    static List<String> clipped() {
        List<String> out = new ArrayList<>();
        for (Window w : windows()) {
            if (w instanceof RootPaneContainer rp) out.addAll(clipped(rp.getRootPane()));
        }
        return out;
    }

    /**
     * Clipped strings under {@code root}: {@link TextFit}'s verdict on every text that is at least partly on screen. A
     * text with no visible patch at all (in a collapsed column, under a closed card) is hidden, not clipped: it is not
     * counted here, and a control the task needs that is hidden is a hesitation instead.
     */
    static List<String> clipped(Component root) {
        List<String> out = new ArrayList<>();
        clippedUnder(root, out);
        return out;
    }

    private static void clippedUnder(Component c, List<String> out) {
        if (!c.isShowing() || c.getWidth() <= 0 || c.getHeight() <= 0) return;
        if (c instanceof JLabel || c instanceof AbstractButton || c instanceof JTextComponent) {
            Rectangle v = ((JComponent) c).getVisibleRect();
            if (!v.isEmpty()) out.addAll(TextFit.problems(c));
            if (c instanceof JTextComponent || c instanceof AbstractButton) return;
        }
        if (c instanceof Container k) for (Component ch : k.getComponents()) clippedUnder(ch, out);
    }

    /** With {@code -Dusability.shots=<dir>}: the window painted to a PNG, for reading a measure (never committed). */
    static void shot(String name) {
        String dir = System.getProperty("usability.shots");
        if (dir == null) return;
        try {
            onEdt(() -> {
                int i = 0;
                for (Window w : windows()) {
                    if (!(w instanceof RootPaneContainer rp)) continue;
                    JRootPane r = rp.getRootPane();
                    var img = new java.awt.image.BufferedImage(Math.max(1, r.getWidth()), Math.max(1, r.getHeight()),
                            java.awt.image.BufferedImage.TYPE_INT_RGB);
                    var g = img.createGraphics();
                    r.paint(g);
                    g.dispose();
                    try {
                        java.nio.file.Files.createDirectories(java.nio.file.Path.of(dir));
                        javax.imageio.ImageIO.write(img, "png", java.nio.file.Path.of(dir, name + "-" + (i++) + ".png").toFile());
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                }
            });
        } catch (Exception ignore) {
            // a picture is a reading aid only
        }
    }

    // ---- what a person looks for ----------------------------------------------------------------------------------------

    /** A control, as the person would find it: the component, the patch they press (its coordinates), its label. */
    record Located(Component component, Rectangle area, String label) {
        Located(Component component, String label) {
            this(component, new Rectangle(0, 0, component.getWidth(), component.getHeight()), label);
        }
    }

    /** Finds a control on the event thread; null when there is none on screen. */
    @FunctionalInterface
    interface Finder {
        Located find();
    }

    /** A showing button whose visible label contains {@code text} (topmost window first). */
    static Finder button(String text) {
        return () -> {
            for (Window w : windows()) {
                Located l = buttonIn(w, text);
                if (l != null) return l;
            }
            return null;
        };
    }

    /** A showing button whose label contains {@code text}, inside the window titled {@code title}. */
    static Finder buttonInWindow(String title, String text) {
        return () -> {
            for (Window w : windows()) {
                String t = w instanceof Frame f ? f.getTitle() : w instanceof Dialog d ? d.getTitle() : "";
                if (title.equals(t)) return buttonIn(w, text);
            }
            return null;
        };
    }

    private static Located buttonIn(Component c, String text) {
        if (!c.isShowing()) return null;
        if (c instanceof AbstractButton b) {
            String card = cardTitle(b);
            String shown = card != null ? card : label(b);
            if (shown.contains(text)) return new Located(b, shown);
        }
        if (c instanceof javax.swing.JMenu m && m.isPopupMenuVisible()) {
            Located l = buttonIn(m.getPopupMenu(), text);
            if (l != null) return l;
        }
        if (c instanceof Container k) {
            for (Component ch : k.getComponents()) {
                Located l = buttonIn(ch, text);
                if (l != null) return l;
            }
        }
        return null;
    }

    /** The first of several labels that a showing button carries: one task, the routes different builds offer. */
    static Finder anyButton(String... texts) {
        return () -> {
            for (String t : texts) {
                Located l = button(t).find();
                if (l != null) return l;
            }
            return null;
        };
    }

    /** A showing button whose visible label is exactly {@code text} (the toolbar's "Open", not "Open sample…"). */
    static Finder buttonExactly(String text) {
        return () -> {
            for (Window w : windows()) {
                Located l = exactIn(w, text);
                if (l != null) return l;
            }
            return null;
        };
    }

    private static Located exactIn(Component c, String text) {
        if (!c.isShowing()) return null;
        if (c instanceof AbstractButton b && text.equals(label(b))) return new Located(b, label(b));
        if (c instanceof Container k) {
            for (Component ch : k.getComponents()) {
                Located l = exactIn(ch, text);
                if (l != null) return l;
            }
        }
        return null;
    }

    /** A row of a showing list whose rendered text contains {@code text}, labelled by that text. */
    @SuppressWarnings({"rawtypes"})
    static Finder listRow(Supplier<JList> list, String text) {
        return () -> {
            JList l = list.get();
            if (l == null) return null;
            for (int i = 0; i < l.getModel().getSize(); i++) {
                String row = rowText(l, i);
                if (row.contains(text)) return new Located(l, l.getCellBounds(i, i), row);
            }
            return null;
        };
    }

    /** The editable text field inside the showing window titled {@code title} (a file chooser's name box). */
    static Finder textFieldInWindow(String title) {
        return () -> {
            for (Window w : windows()) {
                String t = w instanceof Frame f ? f.getTitle() : w instanceof Dialog d ? d.getTitle() : "";
                if (!title.equals(t)) continue;
                for (javax.swing.JTextField f : UnifiedAssistantFrameTest.all(w, javax.swing.JTextField.class)) {
                    if (f.isShowing() && f.isEditable()) return new Located(f, "File name");
                }
            }
            return null;
        };
    }

    /** The first showing component of {@code type} in the window titled {@code title}, labelled by its text. */
    static Finder firstInWindow(String title, Class<? extends AbstractButton> type) {
        return () -> {
            for (Window w : windows()) {
                String t = w instanceof Frame f ? f.getTitle() : w instanceof Dialog d ? d.getTitle() : "";
                if (!title.equals(t)) continue;
                for (AbstractButton b : UnifiedAssistantFrameTest.all(w, type)) {
                    if (b.isShowing()) return new Located(b, label(b));
                }
            }
            return null;
        };
    }

    // ---- the right record (for M79's flag-and-select tasks) -------------------------------------------------------------

    /**
     * The digest of the record the person has selected — the product's own record identity
     * ({@code WalkIdentity.recordDigest} of its raw text, as a walk step binds a record) — or null when none is.
     * Call on the event thread.
     */
    static String selectedRecordDigest(MainFrame f) {
        var store = (telamin.fluxtion.audit.analyser.analyser.parse.LogStore) AsyncOpenInterleavingFrameTest.field(f, "store");
        JTable t = ((LogTablePanel) AsyncOpenInterleavingFrameTest.field(f, "tablePanel")).table();
        int view = t.getSelectedRow();
        if (store == null || view < 0) return null;
        return telamin.fluxtion.audit.analyser.analyser.walk.WalkIdentity.recordDigest(store.rawText(t.convertRowIndexToModel(view)));
    }

    /** The digest of the first record whose raw text satisfies {@code which}: the EXPECTED record. Event thread. */
    static String expectedRecordDigest(MainFrame f, java.util.function.Predicate<String> which) {
        var store = (telamin.fluxtion.audit.analyser.analyser.parse.LogStore) AsyncOpenInterleavingFrameTest.field(f, "store");
        if (store == null) return null;
        for (int i = 0; i < store.size(); i++) {
            String raw = store.rawText(i);
            if (which.test(raw)) return telamin.fluxtion.audit.analyser.analyser.walk.WalkIdentity.recordDigest(raw);
        }
        return null;
    }

    /** Whether the person's selection IS the expected record (by digest, never by row number). Event thread. */
    static boolean rightRecordSelected(MainFrame f, String expectedDigest) {
        return expectedDigest != null && expectedDigest.equals(selectedRecordDigest(f));
    }

    /** A given component (an editable box, a drop zone), labelled by what the person reads on or beside it. */
    static Finder component(Supplier<? extends Component> c, Supplier<String> label) {
        return () -> {
            Component k = c.get();
            return k == null ? null : new Located(k, label.get());
        };
    }

    /** A row of a table, labelled by the text of its visible cells. */
    static Finder tableRow(Supplier<JTable> table, java.util.function.IntSupplier viewRow) {
        return () -> {
            JTable t = table.get();
            int row = viewRow.getAsInt();
            if (t == null || row < 0) return null;
            Rectangle r = t.getCellRect(row, 0, true).union(t.getCellRect(row, t.getColumnCount() - 1, true));
            StringBuilder sb = new StringBuilder();
            for (int col = 0; col < t.getColumnCount(); col++) {
                Component rc = t.prepareRenderer(t.getCellRenderer(row, col), row, col);
                sb.append(rendered(rc)).append(' ');
            }
            return new Located(t, r, sb.toString().strip());
        };
    }

    // ---- one task run --------------------------------------------------------------------------------------------------

    enum ActKind { CLICK, KEY, DRAG, SCROLL }

    /** Why the person would hesitate before an act. */
    enum Reason { NOT_VISIBLE, BELOW_FOLD, BEHIND_MENU, LABEL_LACKS_KEY_WORD, DISABLED }

    record StepRecord(int n, ActKind kind, String what, String label, List<Reason> reasons, boolean deadEnd) {
    }

    /**
     * One scripted task: acts, hesitations, dead ends, the words on screen before the first act, clipped strings.
     * The script's steps call {@link #click}, {@link #type}, {@link #key}, {@link #rightClick}, {@link #drop}.
     */
    static final class Run {
        final String id;
        final String name;
        final List<String> keyWords;
        final List<StepRecord> steps = new ArrayList<>();
        final Set<String> clippedSeen = new LinkedHashSet<>();
        final Map<ActKind, Integer> acts = new LinkedHashMap<>();
        /** Acts counted when a named milestone was reached (A2: "project", "evidence"). */
        final Map<String, Integer> milestones = new LinkedHashMap<>();
        /** Paths the person had to type (a file chooser's name box). */
        int typedPaths;
        /** Times the task needed a terminal outside the analyser (none can be scripted; a target's metric). */
        int terminalHandOffs;
        /** Why a correctness check failed, in words (empty when it passed or there is none). */
        final List<String> notes = new ArrayList<>();
        int wordsBeforeFirstAct = -1;
        boolean success;
        String failure;

        Run(String id, String name, List<String> keyWords) {
            this.id = id;
            this.name = name;
            this.keyWords = keyWords;
            for (ActKind k : ActKind.values()) acts.put(k, 0);
        }

        int totalActs() {
            int n = 0;
            for (int v : acts.values()) n += v;
            return n;
        }

        int hesitations() {
            int n = 0;
            for (StepRecord s : steps) if (!s.reasons().isEmpty()) n++;
            return n;
        }

        int deadEnds() {
            int n = 0;
            for (StepRecord s : steps) if (s.deadEnd()) n++;
            return n;
        }

        /** The screen before the first act: its words and its clipped strings. */
        void begin() throws Exception {
            settle();
            shot(id + "-0-begin");
            onEdt(() -> {
                wordsBeforeFirstAct = chromeWords(chromeOnScreen());
                clippedSeen.addAll(clipped());
            });
        }

        private boolean hasKeyWord(String label) {
            String l = label == null ? "" : label.toLowerCase(Locale.ROOT);
            for (String k : keyWords) if (l.contains(k.toLowerCase(Locale.ROOT))) return true;
            return false;
        }

        /**
         * Locates the control, judges it as the person meets it, scrolls to it if it is below the fold (one more act) and
         * returns it. A control that cannot be found is a failed task.
         */
        private Located approach(String what, Finder finder, boolean contextMenu) throws Exception {
            return approach(what, finder, contextMenu, false);
        }

        private Located approach(String what, Finder finder, boolean contextMenu, boolean look) throws Exception {
            settle();
            shot(id + "-" + (steps.size() + 1) + "-before");
            AtomicReference<Located> found = new AtomicReference<>();
            AtomicReference<List<Reason>> why = new AtomicReference<>();
            boolean[] dead = new boolean[1];
            boolean[] scroll = new boolean[1];
            onEdt(() -> {
                clippedSeen.addAll(clipped());
                Located l = finder.find();
                List<Reason> reasons = new ArrayList<>();
                if (l == null || !l.component().isShowing()) {
                    reasons.add(Reason.NOT_VISIBLE);
                    dead[0] = chromeOnScreen().stream().noneMatch(s -> hasKeyWord(s.text()));
                } else {
                    Component c = l.component();
                    Rectangle area = l.area();
                    Point centre = new Point(area.x + area.width / 2, area.y + area.height / 2);
                    Rectangle vis = c instanceof JComponent j ? j.getVisibleRect() : new Rectangle(c.getSize());
                    if (!vis.contains(centre)) {
                        if (scrollsTo(c, area)) { reasons.add(Reason.BELOW_FOLD); scroll[0] = true; }
                        else reasons.add(Reason.NOT_VISIBLE);           // no scrolling can show it
                    } else if (!hitBy(c, centre)) {
                        reasons.add(Reason.NOT_VISIBLE);                       // covered by something else
                    }
                    if (!look && !c.isEnabled()) reasons.add(Reason.DISABLED);
                    if (!look && (contextMenu || !l.label().matches("(?s).*[\\p{L}\\p{N}].*"))) reasons.add(Reason.BEHIND_MENU);
                    if (!look && !hasKeyWord(l.label())) reasons.add(Reason.LABEL_LACKS_KEY_WORD);
                }
                found.set(l);
                why.set(reasons);
            });
            Located l = found.get();
            if (scroll[0]) {
                onEdt(() -> {
                    if (l.component() instanceof JComponent j) j.scrollRectToVisible(l.area());
                });
                count(ActKind.SCROLL);
                settle();
            }
            // the act's kind is filled in by the act itself (a look step stays without one)
            steps.add(new StepRecord(steps.size() + 1, null, what,
                    l == null ? "" : abbrev(l.label()), List.copyOf(why.get()), dead[0]));
            if (l == null || !l.component().isShowing()) {
                throw new TaskFailed("step " + steps.size() + " (" + what + "): no such control on screen");
            }
            return l;
        }

        private void record(ActKind kind) {
            StepRecord last = steps.remove(steps.size() - 1);
            steps.add(new StepRecord(last.n(), kind, last.what(), last.label(), last.reasons(), last.deadEnd()));
            count(kind);
        }

        private void count(ActKind kind) {
            acts.merge(kind, 1, Integer::sum);
        }

        /** A left click on the control's centre: press, release and click, dispatched to it. */
        void click(String what, Finder finder) throws Exception {
            Located l = approach(what, finder, false);
            if (!l.component().isEnabled()) throw new TaskFailed("step " + steps.size() + " (" + what + "): disabled");
            record(ActKind.CLICK);
            mouse(l, MouseEvent.BUTTON1, false);
            settle();
        }

        /**
         * The person must READ something to go on (the unticked type that hides the records): judged on visibility only,
         * and one scroll act when it is below the fold. No click.
         */
        void look(String what, Finder finder) throws Exception {
            approach(what, finder, false, true);
            settle();
        }

        /** Waits until the control the person must press next is enabled (a person waits too; no act). */
        void awaitEnabled(String what, Finder finder) throws Exception {
            await(what + " is enabled", () -> {
                AtomicReference<Boolean> ok = new AtomicReference<>(false);
                try {
                    onEdt(() -> {
                        Located l = finder.find();
                        ok.set(l != null && l.component().isEnabled() && l.component().isShowing());
                    });
                } catch (Exception e) {
                    return false;
                }
                return ok.get();
            });
        }

        /** A right click on a table row: the record's context menu (no visible cue). */
        void rightClick(String what, Finder finder) throws Exception {
            Located l = approach(what, finder, true);
            record(ActKind.CLICK);
            mouse(l, MouseEvent.BUTTON3, true);
            settle();
        }

        /** A milestone reached: the acts so far are recorded under its name. */
        void mark(String milestone) {
            milestones.put(milestone, totalActs());
        }

        /** The person types a PATH into a box: one key act and one typed path. */
        void typePath(String what, Finder finder, String path) throws Exception {
            type(what, finder, path);
            typedPaths++;
        }

        /** The person types {@code text} into a box: one act, whatever its length. */
        void type(String what, Finder finder, String text) throws Exception {
            Located l = approach(what, finder, false);
            record(ActKind.KEY);
            onEdt(() -> {
                JTextComponent t = (JTextComponent) l.component();
                t.requestFocusInWindow();
                t.setText(text);
            });
            settle();
        }

        /** One key press on a component, through its own key bindings (posted key events need a focus owner). */
        void key(String what, Finder finder, int keyCode) throws Exception {
            Located l = approach(what, finder, false);
            record(ActKind.KEY);
            onEdt(() -> {
                JComponent c = (JComponent) l.component();
                c.requestFocusInWindow();
                KeyStroke ks = KeyStroke.getKeyStroke(keyCode, 0);
                KeyEvent e = new KeyEvent(c, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, keyCode, KeyEvent.CHAR_UNDEFINED);
                for (Component k = c; k != null; k = k.getParent()) {
                    if (!(k instanceof JComponent jk)) break;
                    int cond = k == c ? JComponent.WHEN_FOCUSED : JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT;
                    Object name = jk.getInputMap(cond).get(ks);
                    if (name != null && jk.getActionMap().get(name) != null) {
                        SwingUtilities.notifyAction(jk.getActionMap().get(name), ks, e, jk, 0);
                        return;
                    }
                }
                c.dispatchEvent(e);
            });
            settle();
        }

        /**
         * A drag of a split pane's divider by {@code dy} pixels: press, drag and release dispatched to the divider. Its
         * label is the divider's (none: a grip with no words).
         */
        void dragDivider(String what, java.util.function.Supplier<javax.swing.JSplitPane> split, int dy) throws Exception {
            Finder f = () -> {
                javax.swing.JSplitPane sp = split.get();
                if (sp == null || !(sp.getUI() instanceof javax.swing.plaf.basic.BasicSplitPaneUI ui)) return null;
                return new Located(ui.getDivider(), "");
            };
            Located l = approach(what, f, false);
            record(ActKind.DRAG);
            Component d = l.component();
            int x = d.getWidth() / 2;
            int y = d.getHeight() / 2;
            SwingUtilities.invokeLater(() -> {
                long now = System.currentTimeMillis();
                int b1 = java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
                d.dispatchEvent(new MouseEvent(d, MouseEvent.MOUSE_PRESSED, now, b1, x, y, 1, false, MouseEvent.BUTTON1));
                for (int step = 1; step <= 4; step++) {
                    d.dispatchEvent(new MouseEvent(d, MouseEvent.MOUSE_DRAGGED, now, b1, x, y + dy * step / 4, 1, false,
                            MouseEvent.NOBUTTON));
                }
                d.dispatchEvent(new MouseEvent(d, MouseEvent.MOUSE_RELEASED, now, 0, x, y + dy, 1, false, MouseEvent.BUTTON1));
            });
            settle();
        }

        /** A file dropped on a component: one drag act, delivered as a drop delivers it (its TransferHandler). */
        void drop(String what, Finder finder, java.nio.file.Path file) throws Exception {
            Located l = approach(what, finder, false);
            record(ActKind.DRAG);
            onEdt(() -> {
                JComponent c = (JComponent) l.component();
                java.awt.datatransfer.Transferable t = new java.awt.datatransfer.Transferable() {
                    @Override public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                        return new java.awt.datatransfer.DataFlavor[]{java.awt.datatransfer.DataFlavor.javaFileListFlavor};
                    }
                    @Override public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor f) {
                        return java.awt.datatransfer.DataFlavor.javaFileListFlavor.equals(f);
                    }
                    @Override public Object getTransferData(java.awt.datatransfer.DataFlavor f) {
                        return List.of(file.toFile());
                    }
                };
                c.getTransferHandler().importData(new javax.swing.TransferHandler.TransferSupport(c, t));
            });
            settle();
        }

        /** Runs one optional step when its control shows (a dialog that may or may not ask). */
        boolean clickIfShowing(String what, Finder finder, long waitMillis) throws Exception {
            long until = System.currentTimeMillis() + waitMillis;
            do {
                AtomicReference<Boolean> there = new AtomicReference<>(false);
                onEdt(() -> {
                    Located l = finder.find();
                    there.set(l != null && l.component().isShowing());
                });
                if (there.get()) {
                    click(what, finder);
                    return true;
                }
                Thread.sleep(50);
            } while (System.currentTimeMillis() < until);
            return false;
        }

        /** The screen the task ends on (still open): its clipped strings join the run's; returns {@code ok}. */
        boolean endScreen(boolean ok) throws Exception {
            settle();
            shot(id + "-end");
            onEdt(() -> clippedSeen.addAll(clipped()));
            return ok;
        }

        void end(boolean ok, String why) {
            success = ok;
            failure = why;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("task", name);
            m.put("keyWords", keyWords);
            m.put("success", success);
            if (failure != null) m.put("failure", failure);
            Map<String, Object> a = new LinkedHashMap<>();
            for (var e : acts.entrySet()) a.put(e.getKey().name().toLowerCase(Locale.ROOT) + "s", e.getValue());
            a.put("total", totalActs());
            m.put("acts", a);
            if (!milestones.isEmpty()) m.put("actsTo", new LinkedHashMap<>(milestones));
            m.put("typedPaths", typedPaths);
            m.put("terminalHandOffs", terminalHandOffs);
            if (!notes.isEmpty()) m.put("notes", new ArrayList<>(notes));
            m.put("hesitations", hesitations());
            m.put("deadEnds", deadEnds());
            m.put("wordsBeforeFirstAct", wordsBeforeFirstAct);
            m.put("clippedCount", clippedSeen.size());
            m.put("clipped", new ArrayList<>(clippedSeen));
            List<Object> s = new ArrayList<>();
            for (StepRecord r : steps) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("n", r.n());
                x.put("act", r.kind() == null ? "none" : r.kind().name().toLowerCase(Locale.ROOT));
                x.put("what", r.what());
                x.put("label", r.label());
                List<String> rs = new ArrayList<>();
                for (Reason reason : r.reasons()) rs.add(reason.name().toLowerCase(Locale.ROOT));
                x.put("hesitation", rs);
                x.put("deadEnd", r.deadEnd());
                s.add(x);
            }
            m.put("steps", s);
            return m;
        }
    }

    /** A task that could not go on: the control it needed was not there. */
    static final class TaskFailed extends Exception {
        TaskFailed(String message) {
            super(message);
        }
    }

    // ---- small helpers -------------------------------------------------------------------------------------------------

    /**
     * Whether scrolling can bring {@code area} of {@code c} into view: it sits in a scroll pane whose viewport is at least
     * as tall as the area's centre needs (a viewport squeezed to no rows has nothing to scroll into).
     */
    private static boolean scrollsTo(Component c, Rectangle area) {
        for (Container p = c.getParent(); p != null; p = p.getParent()) {
            if (p instanceof JViewport v && v.getParent() instanceof JScrollPane) {
                return v.getExtentSize().height >= Math.max(6, area.height / 2) && v.getExtentSize().width >= 4;
            }
        }
        return false;
    }

    /** Whether a press at {@code p} (in {@code c}'s coordinates) reaches {@code c} or a child of it. */
    private static boolean hitBy(Component c, Point p) {
        JRootPane root = SwingUtilities.getRootPane(c);
        if (root == null) return true;
        Point rp = SwingUtilities.convertPoint(c, p, root);
        Component deepest = SwingUtilities.getDeepestComponentAt(root, rp.x, rp.y);
        if (deepest == null) return false;
        if (deepest == c || SwingUtilities.isDescendingFrom(deepest, c)) return true;
        // a renderer pane or a glass pane that lets presses through counts as the target
        return deepest instanceof javax.swing.CellRendererPane || SwingUtilities.isDescendingFrom(c, deepest)
                && !(deepest instanceof JRootPane);
    }

    private static void mouse(Located l, int button, boolean popup) throws Exception {
        Component c = l.component();
        Rectangle a = l.area();
        int x = a.x + Math.min(a.width / 2, 40);
        int y = a.y + a.height / 2;
        if (!(c instanceof JTable)) x = a.x + a.width / 2;
        int mods = button == MouseEvent.BUTTON3 ? java.awt.event.InputEvent.BUTTON3_DOWN_MASK : java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
        int fx = x;
        // later, not and-wait: a click may open a modal dialog, whose loop must not hold this thread
        SwingUtilities.invokeLater(() -> {
            long now = System.currentTimeMillis();
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, now, mods, fx, y, 1, popup, button));
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, now, 0, fx, y, 1, false, button));
            c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_CLICKED, now, 0, fx, y, 1, false, button));
        });
    }

    static String abbrev(String s) {
        String one = s == null ? "" : s.replace('\n', ' ').strip();
        return one.length() > 90 ? one.substring(0, 90) + "…" : one;
    }

    static void onEdt(Runnable r) throws Exception {
        AsyncOpenInterleavingFrameTest.onEdt(r);
    }

    /** A few passes of the event queue with short pauses: what was queued by what was queued has run. */
    static void settle() throws Exception {
        for (int i = 0; i < 4; i++) {
            SwingUtilities.invokeAndWait(() -> { });
            Thread.sleep(60);
        }
    }

    static void await(String what, BooleanSupplier done) throws Exception {
        long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < until) {
            if (done.getAsBoolean()) return;
            Thread.sleep(50);
        }
        throw new TaskFailed("timed out waiting: " + what);
    }
}
