package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.source.EventProcessorModel;
import telamin.fluxtion.audit.analyser.analyser.source.SourceNavigation;
import telamin.fluxtion.audit.analyser.analyser.source.SourceNavigation.Ref;
import telamin.fluxtion.audit.analyser.analyser.source.SourceService;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
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
 * Read-only, colourised source viewer with click-to-source navigation (spec §9):
 * <ul>
 *   <li>{@link #showDispatchFor} scrolls the EventProcessor to the method that dispatches the
 *       selected record (its callback);</li>
 *   <li><b>Ctrl-click</b> an identifier navigates — a node field's {@code receiver.method()} opens
 *       that node's class at the method; a field opens its type; a Type opens its source;</li>
 *   <li>{@link #openInstance} opens a node's class (and scrolls to a method).</li>
 * </ul>
 * The currently shown file is itself parsed into an {@link EventProcessorModel} so navigation works
 * from any file (the processor or a node class).
 */
public final class SourcePanel extends JPanel {

    private final JComboBox<String> processorCombo = new JComboBox<>();
    private final JLabel currentLabel = new JLabel(" ");
    private final WrapTextPane source = new WrapTextPane(false);
    private final JScrollPane sourceScroll = new JScrollPane(source);
    private final JavaHighlighter highlighter = new JavaHighlighter();

    private SourceService service;
    private boolean syncing;

    private String currentFqn;
    private String currentSource = "";
    private EventProcessorModel currentModel;
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
        javax.swing.JCheckBox wrap = new javax.swing.JCheckBox("Wrap", false);
        wrap.addActionListener(e -> setSourceWrap(wrap.isSelected()));
        top.add(wrap);
        top.add(new JLabel("  (Ctrl-click a node/method/type to navigate)"));
        add(top, BorderLayout.NORTH);

        installBackKeyBindings();

        source.setEditable(false);
        source.setFont(new Font("Monospaced", Font.PLAIN, 12));
        applySourceBackground();
        sourceScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);   // no-wrap default
        JPanel center = new JPanel(new BorderLayout());
        currentLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        center.add(currentLabel, BorderLayout.NORTH);
        center.add(sourceScroll, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        openSel.addActionListener(e -> showSelectedProcessor());
        processorCombo.addActionListener(e -> {
            if (syncing || service == null) return;
            Object sel = processorCombo.getSelectedItem();
            if (sel != null) {
                service.select(sel.toString());
                showSelectedProcessor();
            }
        });

        MouseAdapter nav = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (e.isControlDown() || e.isMetaDown()) navigateAt(source.viewToModel2D(e.getPoint()));
            }
            @Override public void mouseMoved(MouseEvent e) {
                source.setCursor(Cursor.getPredefinedCursor(
                        (e.isControlDown() || e.isMetaDown()) ? Cursor.HAND_CURSOR : Cursor.TEXT_CURSOR));
            }
        };
        source.addMouseListener(nav);
        source.addMouseMotionListener(nav);
    }

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

    /** Re-colour the current source (e.g. after a theme change). */
    public void refresh() {
        applySourceBackground();
        if (!currentSource.isEmpty()) highlighter.render(source.getStyledDocument(), currentSource);
    }

    /** A subtle, theme-aware editor background so the source viewer reads apart from other panels. */
    private void applySourceBackground() {
        java.awt.Color editor = ThemeManager.isDark()
                ? new java.awt.Color(0x1B1F24) : new java.awt.Color(0xFBFCFE);
        source.setBackground(editor);
        // The viewport shows through wherever the pane does not reach — during a resize, and around the
        // margins. Matching it keeps the editor a single surface instead of two shades meeting mid-panel.
        sourceScroll.getViewport().setBackground(editor);
        sourceScroll.setBackground(editor);
    }

    /**
     * What the viewer shows when there is no file behind the name: an explanation, the roots actually
     * searched, and the way to add another. An empty editor says "nothing here" when the truth is
     * "configured to look in the wrong place", and the roots are the one fact that distinguishes them.
     */
    private void showNothingToShow(String fqn) {
        List<java.nio.file.Path> roots = service == null ? List.of() : service.resolver().roots();

        StringBuilder sb = new StringBuilder();
        sb.append("No source to show\n\n")
          .append(fqn).append('\n')
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

    /** Plain, muted text in the editor — used for messages, which must not be coloured as if they were code. */
    private void renderPlain(String text) {
        javax.swing.text.StyledDocument doc = source.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
            javax.swing.text.SimpleAttributeSet style = new javax.swing.text.SimpleAttributeSet();
            javax.swing.text.StyleConstants.setForeground(style, UiTheme.mutedForeground());
            doc.insertString(0, text, style);
        } catch (javax.swing.text.BadLocationException ignore) {
            // the document was just emptied; nothing sensible to recover
        }
        source.setCaretPosition(0);
    }

    /** Toggle source line-wrap: swap the view behaviour, sync the scrollbar, and rebuild the views. */
    private void setSourceWrap(boolean wrap) {
        source.setWrap(wrap);
        sourceScroll.setHorizontalScrollBarPolicy(wrap
                ? JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                : JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        if (!currentSource.isEmpty()) highlighter.render(source.getStyledDocument(), currentSource);
        source.revalidate();
        sourceScroll.revalidate();
        sourceScroll.repaint();
    }

    /** Scroll the EventProcessor source to the method that dispatches this record (its callback). */
    public void showDispatchFor(LogRecord record) {
        if (service == null || record == null) return;
        this.dispatchRecord = record;
        openFqnAtMethod(service.selectedFqn(), record.callback());
    }

    /** Open a node's declaring class (via the selected processor) and scroll to {@code method}. */
    public void openInstance(String instanceId, String method) {
        if (service == null) return;
        String fqn = service.fqnForInstance(instanceId);
        if (fqn != null) {
            openFqnAtMethod(fqn, method);
        } else {
            currentLabel.setText("no source mapping for node '" + instanceId + "'");
        }
    }

    public void openFqn(String fqn) {
        navigate(fqn, null);
    }

    public void openFqnAtMethod(String fqn, String method) {
        navigate(fqn, method);
    }

    /** Navigate to a source file (recording history when the file changes) and scroll to a method. */
    private void navigate(String fqn, String method) {
        if (service == null || fqn == null) return;
        if (!Objects.equals(fqn, currentFqn)) {
            if (currentFqn != null) {
                backStack.push(currentFqn);
                backButton.setEnabled(true);
            }
            render(fqn);
        }
        int off = method == null ? -1 : SourceNavigation.methodDeclOffset(currentSource, method);
        scrollToOffset(off >= 0 ? off : 0);
    }

    /** Navigate back to the previously shown source file (Alt+Left / Cmd|Ctrl+[). */
    private void back() {
        if (backStack.isEmpty()) return;
        String prev = backStack.pop();
        render(prev);
        backButton.setEnabled(!backStack.isEmpty());
        // if we've returned to the EventProcessor, scroll to the triggering handler for the record
        if (service != null && prev.equals(service.selectedFqn()) && dispatchRecord != null) {
            int off = SourceNavigation.methodDeclOffset(currentSource, dispatchRecord.callback());
            scrollToOffset(off >= 0 ? off : 0);
        }
    }

    private void render(String fqn) {
        Optional<String> src = service.sourceForFqn(fqn);
        currentFqn = fqn;
        if (src.isPresent()) {
            currentSource = src.get();
            currentModel = EventProcessorModel.parse(fqn, currentSource);
            currentLabel.setText(fqn);
            highlighter.render(source.getStyledDocument(), currentSource);
            source.setCaretPosition(0);
        } else {
            currentSource = "";
            currentModel = null;
            currentLabel.setText(fqn + "  —  not found under the configured source roots");
            showNothingToShow(fqn);
        }
    }

    private void installBackKeyBindings() {
        var im = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        var am = getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), "nav-back");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "nav-back");
        am.put("nav-back", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { back(); }
        });
    }

    private static String menuKeyName() {
        return Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() == InputEvent.META_DOWN_MASK ? "Cmd" : "Ctrl";
    }

    private void navigateAt(int offset) {
        if (currentModel == null || currentSource.isEmpty()) return;
        Ref ref = SourceNavigation.resolveAt(currentSource, offset);
        if (ref == null) return;
        // receiver.method() -> open the field's type at the method
        if (ref.receiver() != null && currentModel.hasInstance(ref.receiver())) {
            String fqn = currentModel.fieldTypeFqn(ref.receiver());
            if (fqn != null) openFqnAtMethod(fqn, ref.methodCall() ? ref.identifier() : null);
            return;
        }
        // a field name -> open its type
        if (currentModel.hasInstance(ref.identifier())) {
            String fqn = currentModel.fieldTypeFqn(ref.identifier());
            if (fqn != null) openFqn(fqn);
            return;
        }
        // a Type -> open it if resolvable
        if (!ref.identifier().isEmpty() && Character.isUpperCase(ref.identifier().charAt(0))) {
            String fqn = currentModel.resolveSimpleType(ref.identifier());
            if (fqn != null && service.sourceForFqn(fqn).isPresent()) openFqn(fqn);
        }
    }

    private void scrollToOffset(int offset) {
        try {
            source.setCaretPosition(Math.min(offset, source.getDocument().getLength()));
            Rectangle2D r = source.modelToView2D(offset);
            if (r != null) {
                Rectangle view = new Rectangle((int) r.getX(), (int) r.getY(), 10, source.getVisibleRect().height);
                source.scrollRectToVisible(view);   // bring the target near the top
            }
        } catch (BadLocationException | IllegalArgumentException ignore) {
            SwingUtilities.invokeLater(() -> source.setCaretPosition(0));
        }
    }
}
