package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.source.EventProcessorModel;
import telamin.fluxtion.audit.analyser.analyser.source.SourceNavigation;
import telamin.fluxtion.audit.analyser.analyser.source.SourceNavigation.Ref;
import telamin.fluxtion.audit.analyser.analyser.source.SourceService;

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
    public enum Mode { PROCESSOR, NODE, SPLIT }

    private final JComboBox<String> processorCombo = new JComboBox<>();
    private final JavaHighlighter highlighter = new JavaHighlighter();

    private final Pane processorPane = new Pane("EventProcessor");
    private final Pane nodePane = new Pane("Node");
    private final JSplitPane split =
            new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, processorPane, nodePane);
    private final JPanel host = new JPanel(new BorderLayout());
    private Mode mode = Mode.SPLIT;

    private SourceService service;
    private boolean syncing;
    private boolean wrap;

    private final Deque<String> backStack = new ArrayDeque<>();
    private final JButton backButton = new JButton("◀ Back");
    private LogRecord dispatchRecord;   // the record whose dispatch method we scroll to on the EP

    public SourcePanel() {
        super(new BorderLayout());
        setBorder(UiTheme.pad());

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
        processor.setToolTipText("Only the generated EventProcessor — the dispatch and its guards");
        node.setToolTipText("Only the node class you navigated to");
        both.setToolTipText("Both: the call site above, the method it calls below");
        processor.addActionListener(e -> setMode(Mode.PROCESSOR));
        node.addActionListener(e -> setMode(Mode.NODE));
        both.addActionListener(e -> setMode(Mode.SPLIT));
        for (JToggleButton b : List.of(processor, node, both)) {
            b.setFocusable(false);
            buttons.add(b);
            group.add(b);
        }
        return group;
    }

    public void setMode(Mode newMode) {
        if (newMode == null || mode == newMode) return;
        mode = newMode;
        applyMode();
    }

    /** Rebuild the centre for the current mode, keeping the split's divider position across switches. */
    private void applyMode() {
        int divider = split.getDividerLocation();
        host.removeAll();
        switch (mode) {
            case PROCESSOR -> host.add(processorPane, BorderLayout.CENTER);
            case NODE -> host.add(nodePane, BorderLayout.CENTER);
            case SPLIT -> {
                split.setTopComponent(processorPane);
                split.setBottomComponent(nodePane);
                host.add(split, BorderLayout.CENTER);
                if (divider > 0) split.setDividerLocation(divider);
            }
        }
        host.revalidate();
        host.repaint();
    }

    /**
     * Promote to Split when a navigation would otherwise land in a hidden pane. Following a call into a
     * node while showing only the processor would look like nothing happened, which is worse than the
     * mode changing under the user.
     */
    private void revealPaneFor(Pane pane) {
        if (mode == Mode.SPLIT) return;
        if ((mode == Mode.PROCESSOR && pane == nodePane) || (mode == Mode.NODE && pane == processorPane)) {
            setMode(Mode.SPLIT);
        }
    }

    // ---- wiring -----------------------------------------------------------------------------------

    public void bind(SourceService service) {
        this.service = service;
    }

    public void setProcessors(List<String> fqns, String selected) {
        syncing = true;
        try {
            processorCombo.setModel(new DefaultComboBoxModel<>(fqns.toArray(new String[0])));
            if (selected != null) processorCombo.setSelectedItem(selected);
        } finally {
            syncing = false;
        }
    }

    public void showSelectedProcessor() {
        if (service != null) openFqn(service.selectedFqn());
    }

    /** True once the processor half has a file in it — used to avoid re-navigating (and re-scrolling) it. */
    public boolean hasProcessorOpen() {
        return processorPane.fqn != null && !processorPane.source.isEmpty();
    }

    /** Re-colour both panes (e.g. after a theme change). */
    public void refresh() {
        processorPane.applyTheme();
        nodePane.applyTheme();
    }

    /** Toggle source line-wrap in both panes: swap the view behaviour, sync the scrollbars, re-render. */
    private void setSourceWrap(boolean on) {
        this.wrap = on;
        processorPane.setWrap(on);
        nodePane.setWrap(on);
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
        navigate(fqn, null);
        // an event class may be nested (Events.MarketDataEvent); the overload names the simple type
        String simple = simpleName(eventSimpleName.strip());
        int off = SourceNavigation.eventHandlerOffset(processorPane.source, simple);
        if (off < 0) return;
        // navigate() may have just replaced the document, and a scroll issued before the new view has
        // been laid out lands roughly a screen out — the target ends up at the bottom instead of the top.
        // Deferring puts it after layout.
        SwingUtilities.invokeLater(() -> processorPane.scrollToOffset(off));
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
        if (service == null || fqn == null) return;
        Pane pane = paneFor(fqn);
        if (!Objects.equals(fqn, pane.fqn)) {
            if (pane.fqn != null) {
                backStack.push(pane.fqn);
                backButton.setEnabled(true);
            }
            pane.render(fqn);
        }
        revealPaneFor(pane);
        // With no method to aim at, land on the TYPE rather than at line 1. A Fluxtion graph's node
        // classes are commonly nested in one holder, so opening the file is only half the answer —
        // the reader still has to find the class among its siblings. Single-type files are unaffected:
        // the declaration is at the top anyway.
        int off = method != null
                ? SourceNavigation.methodDeclOffset(pane.source, method)
                : SourceNavigation.typeDeclOffset(pane.source, simpleName(fqn));
        pane.scrollToOffset(off >= 0 ? off : 0);
    }

    /** Navigate back to the previously shown source file (Alt+Left / Cmd|Ctrl+[). */
    private void back() {
        if (backStack.isEmpty()) return;
        String prev = backStack.pop();
        Pane pane = paneFor(prev);
        pane.render(prev);
        revealPaneFor(pane);
        backButton.setEnabled(!backStack.isEmpty());
        // if we've returned to the EventProcessor, scroll to the triggering handler for the record
        if (service != null && prev.equals(service.selectedFqn()) && dispatchRecord != null) {
            int off = SourceNavigation.methodDeclOffset(pane.source, dispatchRecord.callback());
            pane.scrollToOffset(off >= 0 ? off : 0);
        }
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
        private final JLabel label = new JLabel(" ");
        private String fqn;
        private String source = "";
        private EventProcessorModel model;

        Pane(String role) {
            super(new BorderLayout());
            text.setEditable(false);
            text.setFont(UiTheme.mono(12));
            scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            label.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
            UiTheme.status(label);
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
        }

        void render(String newFqn) {
            Optional<String> src = service.sourceForFqn(newFqn);
            fqn = newFqn;
            if (src.isPresent()) {
                source = src.get();
                model = EventProcessorModel.parse(newFqn, source);
                label.setText(newFqn);
                highlighter.render(text.getStyledDocument(), source);
                text.setWrap(wrap);
                text.setCaretPosition(0);
            } else {
                source = "";
                model = null;
                label.setText(newFqn + "  —  not found under the configured source roots");
                showNothingToShow(newFqn);
            }
        }

        /**
         * What the viewer shows when there is no file behind the name: an explanation, the roots actually
         * searched, and the way to add another. An empty editor says "nothing here" when the truth is
         * "configured to look in the wrong place", and the roots are the one fact that separates them.
         */
        void showNothingToShow(String missingFqn) {
            List<java.nio.file.Path> roots = service == null ? List.of() : service.resolver().roots();

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
            sb.append("Add one:  File ▸ Settings… ▸ Source roots ▸ Add…\n")
              .append("or drag a project folder onto that tab — a project expands to its src/main/java,\n")
              .append("sub-modules included.\n\n")
              .append("A source root is the folder that directly contains your top-level package directory.");

            renderPlain(sb.toString());
        }

        /** Plain, muted text — messages must not be coloured as if they were code. */
        void renderPlain(String message) {
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

        void scrollToOffset(int offset) {
            try {
                text.setCaretPosition(Math.min(offset, text.getDocument().getLength()));
                Rectangle2D r = text.modelToView2D(offset);
                if (r != null) {
                    Rectangle view = new Rectangle((int) r.getX(), (int) r.getY(), 10,
                            text.getVisibleRect().height);
                    text.scrollRectToVisible(view);   // bring the target near the top
                }
            } catch (BadLocationException | IllegalArgumentException ignore) {
                SwingUtilities.invokeLater(() -> text.setCaretPosition(0));
            }
        }
    }
}
