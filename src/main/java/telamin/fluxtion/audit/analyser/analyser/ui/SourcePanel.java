package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.source.EventProcessorModel;
import telamin.fluxtion.audit.analyser.analyser.source.SourceNavigation;
import telamin.fluxtion.audit.analyser.analyser.source.SourceNavigation.Ref;
import telamin.fluxtion.audit.analyser.analyser.source.SourceService;
import telamin.fluxtion.audit.analyser.analyser.source.SourceDocument;
import telamin.fluxtion.audit.analyser.analyser.design.DesignWorkspace;
import telamin.fluxtion.audit.analyser.analyser.design.DesignDocument;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only, colourised source viewer with click-to-source navigation (spec §9), in three modes
 * (M22.13): <b>Processor</b>, <b>Node</b>, and <b>Split</b> showing both.
 *
 * <p>Split exists because of how a dispatch is actually read. The generated processor holds the call
 * site — the {@code auditInvocation(node, "name", "method", event)} and the guard above it that decides
 * whether the node runs at all — and the node class holds what that method then computed. With one pane,
 * following the call loses the guard, and going back to the guard loses the method; the two halves of the
 * answer will not sit still at the same time. So navigating to a node from the processor promotes the
 * view to Split rather than replacing what you navigated from.
 *
 * <ul>
 *   <li>{@link #showDispatchFor} scrolls the EventProcessor to the method that dispatches the
 *       selected record (its callback);</li>
 *   <li><b>Ctrl-click</b> an identifier navigates — a node field's {@code receiver.method()} opens
 *       that node's class at the method; a field opens its type; a Type opens its source;</li>
 *   <li>{@link #openInstance} opens a node's class (and scrolls to a method).</li>
 * </ul>
 * Each pane parses whatever it is showing into its own {@link EventProcessorModel}, so Ctrl-click
 * navigation works from either half of the split.
 */
public final class SourcePanel extends JPanel {

    /** Which of the two panes are on screen. */
    public enum Mode { PROCESSOR, NODE, SPLIT, DESIGN }

    private Runnable sourceViewChanged = () -> { };
    public void onSourceViewChanged(Runnable listener) { sourceViewChanged = listener == null ? () -> { } : listener; }
    private void sourceViewChanged() { sourceViewChanged.run(); }
    /** View identity for a pending preparation; layout extent alone is not a user navigation. */
    public Object spotlightViewState() {
        return List.of(mode, wrap, processorPane.navigation, nodePane.navigation,
                processorPane.scroll.getViewport().getViewPosition(), nodePane.scroll.getViewport().getViewPosition(),
                designPane.text.getVisibleRect().getLocation());
    }

    private final DesignSourcePanel designPane = new DesignSourcePanel();
    private final java.util.Map<Mode, JToggleButton> modeButtons = new java.util.EnumMap<>(Mode.class);
    private DesignWorkspace.View fileView;
    private String historyDestination;
    private java.util.function.Consumer<java.util.Map<String, Object>> fileNavigator = p -> { };

    private final JComboBox<String> processorCombo = new JComboBox<>();
    private final JavaHighlighter highlighter = new JavaHighlighter();

    private final Pane processorPane = new Pane("EventProcessor");
    private final Pane nodePane = new Pane("Node");
    private final JSplitPane split =
            new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, processorPane, nodePane);
    private final JPanel host = new JPanel(new BorderLayout());
    private Mode mode = Mode.SPLIT;

    private SourceService service;
    private java.util.function.Supplier<String> lookupHint = () -> "";
    private boolean syncing;
    private boolean wrap;

    private record History(String fqn, DesignWorkspace.View file) { }
    private final Deque<History> backStack = new ArrayDeque<>();
    private final JButton backButton = new JButton("◀ Back");
    private LogRecord dispatchRecord;   // the record whose dispatch method we scroll to on the EP

    public SourcePanel() {
        super(new BorderLayout());
        setBorder(UiTheme.pad());
        // showing a Java pane rechecks what it shows (edit-loop spec §C): a tab switch back to stale text
        // must not present it as current
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) revalidateShown();
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        backButton.setEnabled(false);
        backButton.setToolTipText("Back to previous source (Alt+Left, or " + menuKeyName() + "+[)");
        backButton.addActionListener(e -> back());
        top.add(backButton);
        top.add(new JLabel("EventProcessor:"));
        top.add(processorCombo);
        JButton openSel = new JButton("Show");
        top.add(openSel);
        top.add(buildModeToggle());
        JCheckBox wrapBox = new JCheckBox("Wrap", false);
        wrapBox.addActionListener(e -> setSourceWrap(wrapBox.isSelected()));
        top.add(wrapBox);
        top.add(new JLabel("  (Ctrl-click a node/method/type to navigate)"));
        add(top, BorderLayout.NORTH);

        designPane.onViewportChanged(this::sourceViewChanged);
        installBackKeyBindings();

        split.setResizeWeight(0.5);
        split.setBorder(null);
        add(host, BorderLayout.CENTER);
        applyMode();

        openSel.addActionListener(e -> showSelectedProcessor());
        processorCombo.addActionListener(e -> {
            if (syncing || service == null) return;
            Object sel = processorCombo.getSelectedItem();
            if (sel != null) {
                service.select(sel.toString());
                showSelectedProcessor();
            }
        });
    }

    // ---- modes ------------------------------------------------------------------------------------

    private JPanel buildModeToggle() {
        JPanel group = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        ButtonGroup buttons = new ButtonGroup();
        JToggleButton processor = new JToggleButton("Processor");
        JToggleButton node = new JToggleButton("Node");
        JToggleButton both = new JToggleButton("Split", true);
        JToggleButton design = new JToggleButton("Design");
        design.setToolTipText("The working XML design; relationship to a loaded run is unverified");
        design.addActionListener(e -> setMode(Mode.DESIGN));
        modeButtons.put(Mode.PROCESSOR, processor); modeButtons.put(Mode.NODE, node);
        modeButtons.put(Mode.SPLIT, both); modeButtons.put(Mode.DESIGN, design);
        processor.setToolTipText("Only the generated EventProcessor — the dispatch and its guards");
        node.setToolTipText("Only the node class you navigated to");
        both.setToolTipText("Both: the call site above, the method it calls below");
        processor.addActionListener(e -> setMode(Mode.PROCESSOR));
        node.addActionListener(e -> setMode(Mode.NODE));
        both.addActionListener(e -> setMode(Mode.SPLIT));
        for (JToggleButton b : List.of(processor, node, both, design)) {
            b.setFocusable(false);
            buttons.add(b);
            group.add(b);
        }
        return group;
    }

    public void setMode(Mode newMode) {
        if (newMode == null || mode == newMode) return;
        mode = newMode;
        if (modeButtons.containsKey(mode)) modeButtons.get(mode).setSelected(true);
        applyMode();
    }

    /** Rebuild the centre for the current mode, keeping the split's divider position across switches. */
    private void applyMode() {
        int divider = split.getDividerLocation();
        host.removeAll();
        switch (mode) {
            case PROCESSOR -> host.add(processorPane, BorderLayout.CENTER);
            case NODE -> host.add(nodePane, BorderLayout.CENTER);
            case DESIGN -> host.add(designPane, BorderLayout.CENTER);
            case SPLIT -> {
                split.setTopComponent(processorPane);
                split.setBottomComponent(nodePane);
                host.add(split, BorderLayout.CENTER);
                if (divider > 0) split.setDividerLocation(divider);
            }
        }
        host.revalidate();
        host.repaint();
        sourceViewChanged();
    }

    /**
     * Promote to Split when a navigation would otherwise land in a hidden pane. Following a call into a
     * node while showing only the processor would look like nothing happened, which is worse than the
     * mode changing under the user.
     */
    private void revealPaneFor(Pane pane) {
        if (mode == Mode.DESIGN) setMode(pane == nodePane ? Mode.NODE : Mode.PROCESSOR);
        if (mode == Mode.SPLIT) return;
        if ((mode == Mode.PROCESSOR && pane == nodePane) || (mode == Mode.NODE && pane == processorPane)) {
            setMode(Mode.SPLIT);
        }
    }

    // ---- wiring -----------------------------------------------------------------------------------

    public void bind(SourceService service) {
        this.service = service;
    }

    // ---- reading a pane's file ----------------------------------------------------------------------

    /**
     * How a pane's file is read. Always called off the EDT against a {@link SourceService.Lookup} captured on
     * it; a test may substitute a read that blocks. Edit-loop spec §C: a pane that shows a file must be able
     * to recheck it on disk without the EDT waiting on the filesystem.
     */
    java.util.function.BiFunction<SourceService.Lookup, String, Optional<SourceDocument>> reader =
            SourceService.Lookup::freshDocument;
    /** How long a read may take before the pane says it gave up and ignores the late answer. */
    java.time.Duration readDeadline = java.time.Duration.ofSeconds(5);

    private record Read(SourceDocument document, EventProcessorModel model) { }

    /**
     * Read {@code fqn} for {@code pane} off the EDT, then install its text, origin and — for the selected
     * processor — the service's model, all from that one read (edit-loop spec §C). A newer request, a
     * configuration change or the deadline supersedes this one; a superseded answer is dropped, never
     * installed. While the read runs, a pane already showing that file says its text is not yet rechecked;
     * a pane switching to a new name shows no stale body under that name.
     */
    private void load(Pane pane, String fqn, Runnable then) {
        if (service == null || fqn == null) return;
        long ticket = ++pane.readTicket;
        pane.reading = true;
        SourceService.Lookup lookup = service.captureLookup();
        if (Objects.equals(fqn, pane.fqn) && !pane.source.isEmpty()) {
            pane.label.setRows(1);
            pane.label.setText(fqn + "  —  checking the file on disk; the text below is not yet rechecked");
        } else {
            pane.fqn = fqn; pane.source = ""; pane.model = null;
            pane.label.setRows(1);
            pane.label.setText(fqn + "  —  reading");
            pane.renderPlain("Reading " + fqn + " …");
        }
        javax.swing.Timer deadline = new javax.swing.Timer((int) Math.max(1, readDeadline.toMillis()), e -> {
            if (pane.readTicket != ticket) return;
            pane.readTicket++;                                   // the late answer is never installed
            pane.reading = false;
            pane.label.setText(fqn + "  —  reading the file on disk timed out; "
                    + (pane.source.isEmpty() ? "nothing was read" : "the text below is unchecked"));
        });
        deadline.setRepeats(false);
        deadline.start();
        var reads = reader;
        offEdt(() -> {
            Optional<SourceDocument> document = reads.apply(lookup, fqn);
            return new Read(document.orElse(null),
                    document.map(d -> EventProcessorModel.parse(fqn, d.text())).orElse(null));
        }, read -> {
            deadline.stop();
            if (pane.readTicket != ticket) return;
            pane.reading = false;
            if (service == null || !service.isCurrent(lookup)) return;   // the switch that caused this reads anew
            install(pane, fqn, read, lookup);
            then.run();
        }, failure -> {
            deadline.stop();
            if (pane.readTicket != ticket) return;
            pane.reading = false;
            pane.label.setText(fqn + "  —  could not read the file on disk: " + failure.getMessage()
                    + (pane.source.isEmpty() ? "" : "; the text below is unchecked"));
        });
    }

    /** The pane's read runs on the background pool and answers on the EDT — never on the EDT itself. */
    private static <T> void offEdt(java.util.function.Supplier<T> work, java.util.function.Consumer<T> done,
                                   java.util.function.Consumer<Throwable> failed) {
        telamin.fluxtion.audit.analyser.analyser.core.Background.run(work, done, failed);
    }

    private void install(Pane pane, String fqn, Read read, SourceService.Lookup lookup) {
        SourceDocument document = read.document();
        pane.label.setRows(1);
        if (document == null) {
            boolean wasShown = Objects.equals(fqn, pane.fqn) && !pane.source.isEmpty();
            pane.fqn = fqn; pane.source = ""; pane.model = null;
            pane.label.setText(fqn + "  —  not found under the configured source roots"
                    + (wasShown ? " (it was shown before: deleted, renamed or no longer under these roots;"
                    + " its former text is not shown)" : ""));
            pane.label.setToolTipText(null);
            pane.showNothingToShow(fqn);
        } else if (Objects.equals(fqn, pane.fqn) && document.equals(pane.snapshot)) {
            pane.label.setText(fqn);                             // unchanged on disk
        } else {
            boolean sameFile = Objects.equals(fqn, pane.fqn) && !pane.source.isEmpty();
            int caret = sameFile ? pane.text.getCaretPosition() : 0;
            pane.navigation++;
            pane.fqn = fqn; pane.source = document.text(); pane.model = read.model(); pane.snapshot = document;
            highlighter.render(pane.text.getStyledDocument(), pane.source);
            pane.text.setWrap(wrap);
            pane.text.setCaretPosition(Math.min(caret, pane.text.getDocument().getLength()));
            pane.label.setText(fqn);
            sourceViewChanged();
        }
        if (document != null) {
            // disk freshness is not source/run identity: a fresh read still says so
            pane.label.setToolTipText("Source/run: unverified · " + document.identity()
                    + " · rendered-text-utf8 SHA-256 " + document.revision());
        }
        if (Objects.equals(fqn, service.selectedFqn())) service.acceptModel(lookup, fqn, read.model());
    }

    /** Recheck every file a pane shows — a pane coming into view, or the log/graph replaced. */
    public void revalidateShown() {
        if (service == null) return;
        for (Pane pane : List.of(processorPane, nodePane)) {
            if (pane.fqn != null && fileView == null) load(pane, pane.fqn, () -> { });
        }
    }

    /** True while a pane's read is outstanding — for tests. */
    boolean reading() {
        return processorPane.reading || nodePane.reading;
    }

    /** A pane's header line — for tests of what it discloses while and after reading. */
    String processorLabel() { return processorPane.label.getText(); }
    String nodeLabel() { return nodePane.label.getText(); }
    String nodePaneFqn() { return nodePane.fqn; }

    public void setProcessors(List<String> fqns, String selected) {
        syncing = true;
        try {
            processorCombo.setModel(new DefaultComboBoxModel<>(fqns.toArray(new String[0])));
            if (selected != null) processorCombo.setSelectedItem(selected);
        } finally {
            syncing = false;
        }
    }

    /**
     * Show the selected processor after the configuration changed — a project switch, a root added.
     *
     * <p>An unchanged class name is not a reason to keep what is on screen: the file behind that name may
     * now exist where it did not, or belong to a different project. Both panes re-read their file when
     * its content changed with the roots, so a stale "not found" (or a stale file) never survives a
     * switch. Found 2026-09-16: a log opened over the socket while an older checkout's project was in
     * force; the human then loaded the log's own project, the resolver found the processor, and the panel
     * still said "No source to show … root searched: <the OLD root>" because {@link #navigate} skips an
     * unchanged name.
     */
    public void showSelectedProcessor() {
        if (service == null) return;
        // Both panes re-read their file (off the EDT): the roots may now resolve the name to something else
        // or to nothing, and a MISS placeholder names roots that changed even when the miss did not (review
        // F4). review R2-F5: a configuration refresh is not a request to navigate — an unchanged hit keeps
        // its viewport and caret; only a new name is scrolled to its declaration.
        if (nodePane.fqn != null) load(nodePane, nodePane.fqn, () -> { });
        String fqn = service.selectedFqn();
        if (fqn != null && !Objects.equals(fqn, processorPane.fqn)) { openFqn(fqn); return; }
        if (processorPane.fqn != null) load(processorPane, processorPane.fqn, () -> { });
    }

    /** The processor pane's caret position — for tests of what a refresh must NOT move. */
    int processorCaretPosition() {
        return processorPane.text.getCaretPosition();
    }

    /** Place the processor pane's caret — for tests. */
    void setProcessorCaretPosition(int offset) {
        processorPane.text.setCaretPosition(offset);
    }

    /** The processor pane's visible text (source or placeholder) — for tests. */
    String processorPaneText() {
        return processorPane.text.getText();
    }

    /** The node pane's visible text (source or placeholder) — for tests. */
    String nodePaneText() {
        return nodePane.text.getText();
    }

    /**
     * Where the roots on screen came from and how to change that, appended to the "No source to show"
     * placeholder. Supplied by the frame, which knows the active project and any project the open log
     * belongs to that is not in force; the panel only knows the roots.
     */
    public void setLookupHint(java.util.function.Supplier<String> hint) {
        this.lookupHint = hint == null ? () -> "" : hint;
    }

    /**
     * The "No source to show" placeholder, as text. Pure so a test can read it. {@code hint} is the
     * frame's account of where the roots came from and what to load instead (see {@link #setLookupHint});
     * without it a reader saw the right root listed under the wrong project and nothing to say so
     * (2026-09-16: the offer to load the log's own project had gone by as a status-line note).
     */
    static String nothingToShowText(String missingFqn, List<java.nio.file.Path> roots, String hint) {
        StringBuilder sb = new StringBuilder();
        sb.append("No source to show\n\n")
          .append(missingFqn).append('\n')
          .append("was not found under the source roots below.\n\n");
        if (roots.isEmpty()) {
            sb.append("No source roots are configured yet.\n\n");
        } else {
            sb.append(roots.size() == 1 ? "Source root searched:\n" : "Source roots searched:\n");
            for (java.nio.file.Path root : roots) sb.append("    ").append(root).append('\n');
            sb.append('\n');
        }
        if (hint != null && !hint.isBlank()) sb.append(hint.strip()).append("\n\n");
        sb.append("Add one:  Sources ▸ Source roots… ▸ Add…\n")
          .append("or drag a project folder onto that tab — a project expands to its src/main/java,\n")
          .append("sub-modules included.\n\n")
          .append("A source root is the folder that directly contains your top-level package directory.");
        return sb.toString();
    }

    /** True once the processor half has a file in it — used to avoid re-navigating (and re-scrolling) it. */
    public boolean hasProcessorOpen() {
        return processorPane.fqn != null && !processorPane.source.isEmpty();
    }

    /** Re-colour both panes (e.g. after a theme change). */
    public void refresh() {
        processorPane.applyTheme();
        nodePane.applyTheme();
        designPane.refresh();
    }

    /** Toggle source line-wrap in both panes: swap the view behaviour, sync the scrollbars, re-render. */
    private void setSourceWrap(boolean on) {
        this.wrap = on;
        processorPane.setWrap(on);
        nodePane.setWrap(on);
        designPane.setWrap(on);
    }

    /**
     * Scroll the EventProcessor to where <b>this record's</b> cycle entered it.
     *
     * <p>Two shapes, and they need different lookups. An exported-service call names its method in
     * {@code callback()}, so that is the target. An ordinary event names no method at all — the entry is
     * the processor's {@code handleEvent} overload for that event type, and there is one per type, so
     * searching by method name alone would land on whichever came first.
     */
    public void showDispatchFor(LogRecord record) {
        if (service == null || record == null) return;
        this.dispatchRecord = record;
        if (mode == Mode.DESIGN) return;
        String fqn = service.selectedFqn();
        if (fqn == null) return;
        if (record.callback() != null) {
            openFqnAtMethod(fqn, record.callback());
            return;
        }
        openEventHandler(record.event());
    }

    /** Open a node's declaring class (via the selected processor) and scroll to {@code method}. */
    public void openInstance(String instanceId, String method) {
        if (service == null) return;
        String processor = service.selectedFqn();
        if (processor == null) { resolveInstance(instanceId, method); return; }
        // The node's class comes from the processor as it is on disk NOW: after a class rename, a model
        // cached from the old file would send this to the removed class (edit-loop spec §C).
        load(processorPane, processor, () -> resolveInstance(instanceId, method));
    }

    private void resolveInstance(String instanceId, String method) {
        String fqn = service.fqnForInstance(instanceId);
        if (fqn != null) {
            openFqnAtMethod(fqn, method);
        } else {
            nodePane.label.setText("no source mapping for node '" + instanceId + "'");
        }
    }

    public void openFqn(String fqn) {
        navigate(fqn, null);
    }

    public void openFqnAtMethod(String fqn, String method) {
        navigate(fqn, method);
    }

    /**
     * Open the EventProcessor at the {@code handleEvent} overload for this event type — where dispatch
     * for that event actually begins. Falls back to opening the processor when the overload is absent
     * (an event the processor does not handle, or a hand-written processor).
     */
    public void openEventHandler(String eventSimpleName) {
        if (service == null || eventSimpleName == null) return;
        String fqn = service.selectedFqn();
        if (fqn == null) return;
        // an event class may be nested (Events.MarketDataEvent); the overload names the simple type
        String simple = simpleName(eventSimpleName.strip());
        navigate(fqn, null, () -> {
            int off = SourceNavigation.eventHandlerOffset(processorPane.source, simple);
            if (off < 0) return;
            // the read may have just replaced the document, and a scroll issued before the new view has
            // been laid out lands roughly a screen out — the target ends up at the bottom instead of the top.
            // Deferring puts it after layout.
            SwingUtilities.invokeLater(() -> processorPane.scrollToOffset(off));
        });
    }

    private static String simpleName(String fqn) {
        if (fqn == null) return null;
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? fqn : fqn.substring(dot + 1);
    }

    /** The pane a file belongs in: the selected EventProcessor has its own, everything else is a node. */
    private Pane paneFor(String fqn) {
        return service != null && Objects.equals(fqn, service.selectedFqn()) ? processorPane : nodePane;
    }

    /** Navigate to a source file (recording history when the file changes) and scroll to a method. */
    private void navigate(String fqn, String method) {
        navigate(fqn, method, null);
    }

    private void navigate(String fqn, String method, Runnable instead) {
        if (service == null || fqn == null) return;
        boolean leavingFile = fileView != null;
        if (fileView != null) {
            backStack.push(new History(null, fileView)); fileView = null; backButton.setEnabled(true);
        }
        Pane pane = paneFor(fqn);
        boolean newName = !Objects.equals(fqn, pane.fqn);
        // a miss is retried on every navigation — the roots may have changed since it was rendered — but
        // only a NEW name is history worth going back to
        if (newName && pane.fqn != null && !leavingFile && !pane.source.isEmpty()) {
            backStack.push(new History(pane.fqn, null));
            backButton.setEnabled(true);
        }
        revealPaneFor(pane);
        // Every navigation rechecks the file — a same-name hit included (edit-loop spec §C: an unchanged
        // name is not an unchanged file). With no method to aim at, land on the TYPE rather than at line 1.
        // A Fluxtion graph's node classes are commonly nested in one holder, so opening the file is only
        // half the answer — the reader still has to find the class among its siblings.
        load(pane, fqn, instead != null ? instead : () -> {
            int off = method != null
                    ? SourceNavigation.methodDeclOffset(pane.source, method)
                    : SourceNavigation.typeDeclOffset(pane.source, simpleName(fqn));
            pane.scrollToOffset(off >= 0 ? off : 0);
        });
    }

    /** Navigate back to the previously shown source file (Alt+Left / Cmd|Ctrl+[). */
    private void back() {
        if (backStack.isEmpty()) return;
        History previous = backStack.pop();
        if (previous.file() != null) {
            historyDestination = previous.file().file();
            fileNavigator.accept(java.util.Map.of("file", previous.file().file(), "line", previous.file().line()));
            backButton.setEnabled(!backStack.isEmpty());
            return;
        }
        fileView = null;
        String prev = previous.fqn();
        Pane pane = paneFor(prev);
        revealPaneFor(pane);
        backButton.setEnabled(!backStack.isEmpty());
        load(pane, prev, () -> {
            // if we've returned to the EventProcessor, scroll to the triggering handler for the record
            if (service != null && prev.equals(service.selectedFqn()) && dispatchRecord != null) {
                int off = SourceNavigation.methodDeclOffset(pane.source, dispatchRecord.callback());
                pane.scrollToOffset(off >= 0 ? off : 0);
            }
        });
    }

    private void installBackKeyBindings() {
        var im = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        var am = getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), "nav-back");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, menuShortcutMask()), "nav-back");
        am.put("nav-back", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { back(); }
        });
    }

    public void setDesignNavigation(java.util.function.Consumer<java.util.Map<String, Object>> navigate,
                                    java.util.function.Consumer<String> node, java.util.function.Consumer<String> records) {
        fileNavigator = navigate;
        designPane.callbacks(navigate, node, records);
    }

    public void showFile(DesignWorkspace.View view, String note, boolean history) {
        boolean fromHistory = Objects.equals(historyDestination, view.file());
        historyDestination = null;
        if (history && !fromHistory) {
            if (fileView != null && !fileView.file().equals(view.file())) backStack.push(new History(null, fileView));
            else if (fileView == null) {
                String fqn = mode == Mode.PROCESSOR ? processorPane.fqn : nodePane.fqn;
                if (fqn != null) backStack.push(new History(fqn, null));
            }
        }
        fileView = view;
        if (view.mode().equals("DESIGN")) {
            designPane.render(view, note); setMode(Mode.DESIGN);
        } else {
            nodePane.snapshot = null; nodePane.navigation++; sourceViewChanged();
            nodePane.fqn = view.file(); nodePane.source = view.text();
            nodePane.model = EventProcessorModel.parse(view.file(), view.text());
            nodePane.label.setText(view.file());
            highlighter.render(nodePane.text.getStyledDocument(), view.text());
            setMode(Mode.NODE);
            nodePane.scrollToOffset(DesignDocument.offset(view.text(), view.line(), 1));
        }
        backButton.setEnabled(!backStack.isEmpty());
    }
    public boolean followsDesign() { return designPane.following(); }
    public DesignWorkspace.View fileView() { return fileView; }
    public void navigationFailed() { historyDestination = null; }
    public void designNote(String note) { designPane.note(note); }
    public void clearDesign() {
        designPane.clear();
        historyDestination = null;
        if (fileView != null) { fileView = null; nodePane.renderPlain("Source closed with the design session."); }
        backStack.clear(); backButton.setEnabled(false);
    }
    /** Binding measures only this rendered snapshot, never a new FQN lookup. */
    public record JavaAnchor(JComponent component, SourceDocument document, String fqn) { }
    public record JavaBand(Rectangle bounds, boolean partial) { }

    public JavaAnchor showJavaSnapshot(String fqn, SourceDocument document, EventProcessorModel model) {
        Pane pane = Objects.equals(fqn, service.selectedFqn()) ? processorPane : nodePane;
        fileView = null;
        if (pane.snapshot == null || !pane.snapshot.equals(document)) {
            pane.navigation++;
            pane.fqn = fqn; pane.source = document.text(); pane.model = model; pane.snapshot = document;
            highlighter.render(pane.text.getStyledDocument(), pane.source);
            pane.text.setWrap(wrap);
            pane.text.setCaretPosition(0);
        }
        String disclosure = "Source/run: unverified\n" + fqn + "\nsource-viewer · first-match\n" + document.identity();
        pane.label.setRows(5);
        pane.label.setText(disclosure);
        pane.label.setToolTipText(disclosure + " · rendered-text-utf8 SHA-256 " + document.revision());
        revealPaneFor(pane);
        sourceViewChanged();
        return new JavaAnchor(pane, document, fqn);
    }

    public void revealJava(JavaAnchor anchor, Integer line) {
        Pane pane = (Pane) anchor.component();
        pane.navigation++;
        java.awt.Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null) window.validate();
        pane.scroll.validate(); pane.text.validate();
        int offset = line == null ? 0 : pane.text.getDocument().getDefaultRootElement().getElement(line - 1).getStartOffset();
        pane.text.setCaretPosition(Math.min(offset, pane.text.getDocument().getLength()));
        try {
            Rectangle band = pane.logicalBand(line == null ? 1 : line);
            Rectangle view = pane.scroll.getViewport().getViewRect();
            if (!view.contains(band)) {
                int y = band.height > view.height ? band.y : Math.max(0, band.y - (view.height - band.height) / 2);
                pane.text.scrollRectToVisible(new Rectangle(view.x, y, view.width, view.height));
            }
        } catch (BadLocationException ignored) { /* measurement refuses an unavailable band */ }
    }

    public Optional<JavaBand> javaBounds(JavaAnchor anchor, Integer line) {
        Pane pane = (Pane) anchor.component();
        if (!pane.isShowing() || pane.snapshot == null || !pane.snapshot.equals(anchor.document())) return Optional.empty();
        Rectangle viewport = pane.scroll.getViewport().getViewRect().intersection(pane.text.getVisibleRect());
        if (viewport.isEmpty()) return Optional.empty();
        try {
            Rectangle band = line == null ? viewport : pane.logicalBand(line);
            return JavaLineGeometry.visible(band, viewport).map(visible -> new JavaBand(
                    SwingUtilities.convertRectangle(pane.text, visible.bounds(), this), visible.partial()));
        } catch (BadLocationException | IllegalArgumentException ex) { return Optional.empty(); }
    }

    public JComponent designComponent() { return designPane; }
    public void revealDesignLine(int line) { designPane.revealLine(line); }
    public Optional<Rectangle> designBounds(String file, Integer line) {
        if (mode != Mode.DESIGN || !Objects.equals(designPane.file(), file)) return Optional.empty();
        return line == null ? Optional.of(designPane.getVisibleRect()) : designPane.lineBounds(line);
    }

    /**
     * The platform menu-shortcut mask, headless-safe: {@code HeadlessToolkit} throws on
     * {@code getMenuShortcutKeyMaskEx()}, and this panel must stay constructible in headless tests/CI
     * (the repo convention — panels are built but never shown). Ctrl is the honest fallback: with no
     * display there is no platform to be right about.
     */
    private static int menuShortcutMask() {
        return java.awt.GraphicsEnvironment.isHeadless()
                ? InputEvent.CTRL_DOWN_MASK
                : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    }

    private static String menuKeyName() {
        return menuShortcutMask() == InputEvent.META_DOWN_MASK ? "Cmd" : "Ctrl";
    }

    // ---- one source pane --------------------------------------------------------------------------

    /**
     * One file on screen: its own text, model and history-free state. Two of these make the split, and
     * each parses what it shows so Ctrl-click navigation works from either.
     */
    private final class Pane extends JPanel {
        private final WrapTextPane text = new WrapTextPane(false);
        private final JScrollPane scroll = new JScrollPane(text);
        private final javax.swing.JTextArea label = new javax.swing.JTextArea(" ", 1, 0) {
            @Override public java.awt.Dimension getPreferredSize() {
                // A wrapping area measured before its first width otherwise asks for thousands of
                // pixels of height and gives the source viewport a negative extent on first reveal.
                var insets = getInsets();
                return new java.awt.Dimension(0, getRows() * getFontMetrics(getFont()).getHeight() + insets.top + insets.bottom);
            }
        };
        private String fqn;
        private String source = "";
        private EventProcessorModel model;
        private SourceDocument snapshot;
        private long navigation;
        private long readTicket;
        private boolean reading;

        Pane(String role) {
            super(new BorderLayout());
            text.setEditable(false);
            text.setFont(UiTheme.mono(12));
            scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            scroll.getViewport().addChangeListener(e -> sourceViewChanged());
            addHierarchyListener(e -> sourceViewChanged());
            label.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            label.setEditable(false); label.setLineWrap(true); label.setWrapStyleWord(true);
            label.setFont(UiTheme.mono(11));
            label.setForeground(UiTheme.mutedForeground());
            label.setBackground(javax.swing.UIManager.getColor("Panel.background"));
            label.setText(role);
            add(label, BorderLayout.NORTH);
            add(scroll, BorderLayout.CENTER);
            applyTheme();
            // a blank pane reads as broken; say what it is waiting for
            renderPlain(role.equals("Node")
                    ? "No node open.\n\nCtrl-click a type here, or open a node from the topology "
                      + "(Enter on a selected node, or its right-click menu)."
                    : "No EventProcessor open.\n\nPick one above and press Show, or select a record — "
                      + "the processor is inferred from the log's instance ids.");

            MouseAdapter nav = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (e.isControlDown() || e.isMetaDown()) navigateAt(text.viewToModel2D(e.getPoint()));
                }
                @Override public void mouseMoved(MouseEvent e) {
                    text.setCursor(Cursor.getPredefinedCursor(
                            (e.isControlDown() || e.isMetaDown()) ? Cursor.HAND_CURSOR : Cursor.TEXT_CURSOR));
                }
            };
            text.addMouseListener(nav);
            text.addMouseMotionListener(nav);
        }

        void applyTheme() {
            UiTheme.applySurface(scroll, text);
            label.setForeground(UiTheme.mutedForeground());
            label.setBackground(javax.swing.UIManager.getColor("Panel.background"));
            if (!source.isEmpty()) highlighter.render(text.getStyledDocument(), source);
            else if (fqn != null) showNothingToShow(fqn);
            // a pane showing only its "nothing open yet" message has neither source nor an fqn, so
            // without this it kept the previous theme's muted grey after a switch
            else if (placeholder != null) renderPlain(placeholder);
        }

        private String placeholder;

        void setWrap(boolean on) {
            text.setWrap(on);
            scroll.setHorizontalScrollBarPolicy(on
                    ? JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                    : JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            if (!source.isEmpty()) highlighter.render(text.getStyledDocument(), source);
            text.revalidate();
            scroll.revalidate();
            scroll.repaint();
            sourceViewChanged();
        }

        /**
         * What the viewer shows when there is no file behind the name: an explanation, the roots actually
         * searched, where those roots came from, and the way to change them. An empty editor says "nothing
         * here" when the truth is "configured to look in the wrong place", and the roots are the one fact
         * that separates them.
         */
        void showNothingToShow(String missingFqn) {
            List<java.nio.file.Path> roots = service == null ? List.of() : service.resolver().roots();
            renderPlain(nothingToShowText(missingFqn, roots, lookupHint.get()));
        }

        /** Plain, muted text — messages must not be coloured as if they were code. */
        void renderPlain(String message) {
            navigation++; snapshot = null; sourceViewChanged();
            this.placeholder = message;
            javax.swing.text.StyledDocument doc = text.getStyledDocument();
            try {
                doc.remove(0, doc.getLength());
                javax.swing.text.SimpleAttributeSet style = new javax.swing.text.SimpleAttributeSet();
                javax.swing.text.StyleConstants.setForeground(style, UiTheme.mutedForeground());
                doc.insertString(0, message, style);
            } catch (BadLocationException ignore) {
                // the document was just emptied; nothing sensible to recover
            }
            text.setCaretPosition(0);
        }

        void navigateAt(int offset) {
            if (model == null || source.isEmpty()) return;
            Ref ref = SourceNavigation.resolveAt(source, offset);
            if (ref == null) return;
            // receiver.method() -> open the field's type at the method
            if (ref.receiver() != null && model.hasInstance(ref.receiver())) {
                String target = model.fieldTypeFqn(ref.receiver());
                if (target != null) openFqnAtMethod(target, ref.methodCall() ? ref.identifier() : null);
                return;
            }
            // a field name -> open its type
            if (model.hasInstance(ref.identifier())) {
                String target = model.fieldTypeFqn(ref.identifier());
                if (target != null) openFqn(target);
                return;
            }
            // a Type -> open it if resolvable
            if (!ref.identifier().isEmpty() && Character.isUpperCase(ref.identifier().charAt(0))) {
                String target = model.resolveSimpleType(ref.identifier());
                if (target != null && service.sourceForFqn(target).isPresent()) openFqn(target);
            }
        }

        Rectangle logicalBand(int line) throws BadLocationException {
            var root = text.getDocument().getDefaultRootElement();
            if (line < 1 || line > root.getElementCount()) throw new BadLocationException("line", line);
            var element = root.getElement(line - 1);
            int start = Math.min(element.getStartOffset(), text.getDocument().getLength());
            int end = Math.max(start, Math.min(element.getEndOffset() - 1, text.getDocument().getLength()));
            Rectangle2D first = text.modelToView2D(start), last = text.modelToView2D(end);
            if (first == null || last == null) throw new BadLocationException("no layout", start);
            Rectangle view = scroll.getViewport().getViewRect();
            return JavaLineGeometry.band(first, last, view);
        }

        void scrollToOffset(int offset) {
            long ticket = ++navigation;
            try {
                text.setCaretPosition(Math.min(offset, text.getDocument().getLength()));
                Rectangle2D r = text.modelToView2D(offset);
                if (r != null) {
                    Rectangle view = new Rectangle((int) r.getX(), (int) r.getY(), 10,
                            text.getVisibleRect().height);
                    text.scrollRectToVisible(view);   // bring the target near the top
                }
            } catch (BadLocationException | IllegalArgumentException ignore) {
                SwingUtilities.invokeLater(() -> { if (ticket == navigation) text.setCaretPosition(0); });
            }
        }
    }
}
