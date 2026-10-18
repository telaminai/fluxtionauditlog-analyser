package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M74 P2 (spec §5.9): the Claude Code conversation, drawn. It RENDERS the published entries and the store's words and
 * decides nothing (rule 9). A {@code JTextPane} over a {@code StyledDocument} with a {@link StyledEditorKit} — never an
 * HTML editor kit, so there is no HTML renderer for a model's {@code <img>} or {@code <script>} to reach.
 *
 * <ul>
 *   <li><b>Roles by style and by words</b> ({@link TranscriptStyle}); light and dark; a theme change re-styles.</li>
 *   <li><b>Safe Markdown</b> for Claude's words ({@link SafeMarkdown}); the person's turns as typed.</li>
 *   <li><b>What is seen is what is copied</b>: every control, bidi and format character is a visible mark
 *       ({@link VisibleMarks}) in body text and code; Copy copies the source and says when it holds hidden characters.</li>
 *   <li><b>Links</b> open only after {@link LinkConfirm}'s confirmation, and only http/https.</li>
 *   <li><b>Chips</b> for analyser references that resolve in the current snapshot — {@code record #12}, a node id written
 *       as code or after the word <i>node</i>, a chart's name written as code or quoted — that only NAVIGATE (D-L3).</li>
 *   <li><b>Incremental</b>: a new entry is appended; only the streaming (last) entry is re-rendered on each revision;
 *       earlier entries keep their styling. A full re-render happens only on New chat, a trim or a theme change.</li>
 *   <li><b>Tool lines</b> collapse and expand, showing the call's input as escaped data, at most 64 KB.</li>
 * </ul>
 */
public final class TranscriptView extends JTextPane {

    /** One entry as the panel hands it over: words from the store, state from the snapshot. */
    public record Item(String key, TranscriptStyle.Role role, String text, String status, long revision,
                       long logGeneration, boolean streaming, String tip) {
        public Item {
            text = text == null ? "" : text;
            status = status == null ? "" : status;
        }

        /** An entry with no tooltip of its own. */
        public Item(String key, TranscriptStyle.Role role, String text, String status, long revision, long logGeneration,
                    boolean streaming) {
            this(key, role, text, status, revision, logGeneration, streaming, null);
        }
    }

    /** What resolves in the current snapshot — a set lookup per candidate, nothing guessed. */
    public interface Chips {
        boolean isRecord(int index);

        boolean isNode(String id);

        boolean isChart(String name);

        /** The open log's generation now. */
        long generation();

        Chips NONE = new Chips() {
            @Override public boolean isRecord(int index) { return false; }
            @Override public boolean isNode(String id) { return false; }
            @Override public boolean isChart(String name) { return false; }
            @Override public long generation() { return 0; }
        };
    }

    /** Where a chip goes: REVEAL methods only (D-L3) — select and reveal, spotlight, open a tab; never an edit. */
    public interface Navigator {
        void revealRecord(int index);

        void revealNode(String id);

        void revealChart(String name);

        /** Words for the status line (a stale chip, a copy, a refused link). */
        void say(String words);

        Navigator NONE = new Navigator() {
            @Override public void revealRecord(int index) { }
            @Override public void revealNode(String id) { }
            @Override public void revealChart(String name) { }
            @Override public void say(String words) { }
        };
    }

    /** At most this many chip candidates per answer, and node ids considered. */
    public static final int MAX_CANDIDATES = 200;
    public static final int MAX_NODE_IDS = 5_000;
    /** A tool line's input is shown up to this many bytes. */
    public static final int MAX_INPUT = 64 * 1024;
    /** A code block over this is shown untinted. */
    public static final int MAX_TINT_CHARS = 64 * 1024;
    public static final int MAX_TINT_LINES = 2_000;
    public static final String CARET = "▍";

    static final String LINK_ATTR = "transcript.link";
    static final String ITEM_ATTR = "transcript.item";

    private Chips chips = Chips.NONE;
    private Navigator navigator = Navigator.NONE;
    /** M74 UX: who an answer is from — "Claude Code", or "Assistant" for the in-app assistant (one view, both modes). */
    private String speaker = TranscriptStyle.marker(TranscriptStyle.Role.CLAUDE);
    /** M74 UX: a tool line's expanded block holds the call's input (Claude Code) or its result (the in-app assistant). */
    private String expandedWhat = "input";
    private LinkConfirm.Asker asker = LinkConfirm.DIALOG;
    private Consumer<URI> opener = uri -> {
        try {
            java.awt.Desktop.getDesktop().browse(uri);
        } catch (Exception e) {
            navigator.say("the browser could not be opened: " + e.getClass().getSimpleName());
        }
    };
    private Consumer<String> clipboard = text -> java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
            .setContents(new java.awt.datatransfer.StringSelection(text), null);

    /** What is on screen, in order: each entry's key, revision/status/text identity and its document range. */
    private final List<Rendered> rendered = new ArrayList<>();
    private final Set<String> expanded = new HashSet<>();
    private boolean renderedDark;
    private int focusedTurn = -1;
    private Object focusHighlight;
    /** Document edits made by the last {@link #render} (for the incremental-rendering test). */
    private int lastEdits;

    private record Rendered(Item item, int start, int length) {
    }

    public TranscriptView() {
        setEditorKit(new StyledEditorKit());
        setEditable(false);
        setMargin(new Insets(8, 10, 8, 10));
        getAccessibleContext().setAccessibleName("Claude Code conversation");
        javax.swing.ToolTipManager.sharedInstance().registerComponent(this);   // M78 D1: an answer's turn time
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                String url = linkAt(viewToModel2D(e.getPoint()));
                if (url != null) navigator.say(LinkConfirm.click(TranscriptView.this, url, asker, opener));
            }

            @Override public void mouseExited(MouseEvent e) {
                // owner, 2026-10-18 (the Copy flickered): the view reports an exit when the pointer crosses onto one of
                // its own components — the Copy button itself, a chip, a mark — though it never left the transcript; only
                // a pointer outside the view's bounds is off every entry
                if (!contains(e.getPoint())) hover(null);
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int pos = viewToModel2D(e.getPoint());
                boolean link = linkAt(pos) != null;
                setCursor(Cursor.getPredefinedCursor(link ? Cursor.HAND_CURSOR : Cursor.TEXT_CURSOR));
                hover(itemAt(pos));
            }
        });
        // Alt+↑ / Alt+↓ move between turns (§5.9 accessibility)
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK), "transcript-previous-turn");
        getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK), "transcript-next-turn");
        getActionMap().put("transcript-previous-turn", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { moveTurn(-1); }
        });
        getActionMap().put("transcript-next-turn", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { moveTurn(1); }
        });
    }

    public void setChips(Chips chips) {
        this.chips = chips == null ? Chips.NONE : chips;
    }

    public void setNavigator(Navigator navigator) {
        this.navigator = navigator == null ? Navigator.NONE : navigator;
    }

    /**
     * M74 UX: who the answers are from, and what an expanded tool line shows. A change re-renders everything on the next
     * {@link #render} (the unified panel calls this when its mode changes).
     */
    public void setSpeaker(String speaker, String expandedWhat) {
        String next = speaker == null || speaker.isBlank() ? TranscriptStyle.marker(TranscriptStyle.Role.CLAUDE) : speaker;
        String what = expandedWhat == null || expandedWhat.isBlank() ? "input" : expandedWhat;
        if (next.equals(this.speaker) && what.equals(this.expandedWhat)) return;
        this.speaker = next;
        this.expandedWhat = what;
        getAccessibleContext().setAccessibleName(next + " conversation");
        rendered.clear();
        renderedDark = !ThemeManager.isDark();          // the next render is a full one
    }

    /** Who the answers are from. */
    public String speaker() {
        return speaker;
    }

    /** Test seams: the link confirmation, the browser and the clipboard. */
    void setLinkSeams(LinkConfirm.Asker asker, Consumer<URI> opener) {
        this.asker = asker;
        this.opener = opener;
    }

    void setClipboard(Consumer<String> clipboard) {
        this.clipboard = clipboard;
    }

    int lastEdits() {
        return lastEdits;
    }

    // ---- rendering ----------------------------------------------------------------------------------------------------

    /**
     * Render {@code items}: append what is new, re-render the last entry when it changed, and keep everything earlier
     * exactly as it was. A change anywhere else — a trim, New chat, a theme change — is a full re-render.
     */
    public void render(List<Item> items) {
        boolean dark = ThemeManager.isDark();
        lastEdits = 0;
        StyledDocument doc = getStyledDocument();
        boolean full = dark != renderedDark || items.size() < rendered.size();
        for (int i = 0; i < rendered.size() - 1 && !full && i < items.size(); i++) {
            if (!rendered.get(i).item().equals(items.get(i))) full = true;      // an earlier entry changed: re-render
        }
        if (!full && !rendered.isEmpty() && items.size() >= rendered.size()
                && !Objects.equals(rendered.get(rendered.size() - 1).item().key(), items.get(rendered.size() - 1).key())) {
            full = true;
        }
        try {
            if (full) {
                if (doc.getLength() > 0) {
                    doc.remove(0, doc.getLength());
                    lastEdits++;
                }
                rendered.clear();
                hoverCopies.clear();
                renderedDark = dark;
                setBackground(TranscriptStyle.palette(dark).background());
            }
            int from = rendered.size();
            if (!rendered.isEmpty()) {
                Rendered last = rendered.get(rendered.size() - 1);
                Item now = items.get(rendered.size() - 1);
                if (!last.item().equals(now)) {
                    doc.remove(last.start(), last.length());
                    lastEdits++;
                    rendered.remove(rendered.size() - 1);
                    from = rendered.size();
                }
            }
            for (int i = from; i < items.size(); i++) {
                int start = doc.getLength();
                renderItem(doc, items.get(i), dark);
                lastEdits++;
                rendered.add(new Rendered(items.get(i), start, doc.getLength() - start));
            }
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A theme change: re-style everything (the one full re-render besides New chat and a trim). */
    public void restyle() {
        List<Item> items = rendered.stream().map(Rendered::item).toList();
        rendered.clear();
        renderedDark = !ThemeManager.isDark();
        render(items);
    }

    private void renderItem(StyledDocument doc, Item item, boolean dark) throws BadLocationException {
        TranscriptStyle.Palette p = TranscriptStyle.palette(dark);
        switch (item.role()) {
            case YOU -> {
                SimpleAttributeSet band = base(p.youText(), dark);
                StyleConstants.setBackground(band, p.youBand());
                SimpleAttributeSet who = bold(band);
                StyleConstants.setForeground(who, p.link());
                put(doc, " " + TranscriptStyle.marker(TranscriptStyle.Role.YOU) + " ", who, item);
                put(doc, "\n", band, item);
                put(doc, VisibleMarks.show(item.text()), band, item);      // as typed: plain, never Markdown
                put(doc, "\n\n", base(p.body(), dark), item);
            }
            case CLAUDE, STOPPED -> renderAnswer(doc, item, dark);
            case NOTE -> {
                SimpleAttributeSet a = smaller(base(p.muted(), dark));
                StyleConstants.setItalic(a, true);
                put(doc, VisibleMarks.show(item.text()) + "\n\n", a, item);
            }
            // M74 UX: a tool line of its own (the in-app assistant's actions): the same line, marks and toggle as Claude
            // Code's tool lines inside an answer
            case TOOL -> {
                int i = 0;
                for (String line : item.text().split("\n", -1)) {
                    if (line.startsWith("▸ ")) renderTool(doc, line, item, i++, dark);
                }
            }
            // M74 UX: a demonstration's lines, as written: the first line of a block bold (who speaks), the rest plain
            case PLAIN -> {
                String text = item.text();
                int nl = text.indexOf('\n');
                if (nl > 0 && text.substring(0, nl).endsWith(":")) {
                    put(doc, VisibleMarks.show(text.substring(0, nl + 1)), bold(base(p.body(), dark)), item);
                    text = text.substring(nl + 1);
                }
                put(doc, VisibleMarks.show(text) + "\n", base(p.body(), dark), item);
            }
        }
    }

    private void renderAnswer(StyledDocument doc, Item item, boolean dark) throws BadLocationException {
        TranscriptStyle.Palette p = TranscriptStyle.palette(dark);
        boolean stopped = item.role() == TranscriptStyle.Role.STOPPED;
        SimpleAttributeSet head = bold(base(p.link(), dark));
        // M78 D1 (OD-Q.4): "Claude · unverified" on every answer — the full sentence is the word's tooltip and its
        // accessible description, so the signal stays on screen in two words and is read whole when asked for
        put(doc, TranscriptStyle.answerName(speaker), head, item);
        SimpleAttributeSet note = smaller(base(p.muted(), dark));
        put(doc, " · ", note, item);
        insertComponent(doc, unverifiedMark(p), item);
        put(doc, "\n", note, item);
        Color ink = stopped ? p.muted() : p.body();
        boolean stale = item.logGeneration() != 0 && chips.generation() != 0 && item.logGeneration() != chips.generation();
        int[] candidates = {0};
        List<String> lines = List.of(item.text().split("\n", -1));
        // the answer is text blocks and the adapter's one-line tool records (▸ name {input} — outcome), in order
        StringBuilder prose = new StringBuilder();
        int toolIndex = 0;
        int lastTool = -1;
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).startsWith("▸ ")) lastTool = i;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("▸ ")) {
                renderMarkdown(doc, prose.toString(), item, ink, dark, stale, candidates, item.streaming());
                prose.setLength(0);
                renderTool(doc, line, item, toolIndex++, dark, i == lastTool && AWAITING_APPROVAL.equals(item.status()));
            } else {
                if (!prose.isEmpty()) prose.append('\n');
                prose.append(line);
            }
        }
        renderMarkdown(doc, prose.toString(), item, ink, dark, stale, candidates, item.streaming());
        if (item.streaming()) put(doc, CARET, base(p.muted(), dark), item);
        if (stopped) {
            put(doc, "\n" + ("PROCESS_EXIT".equals(item.status()) ? "— ended: the process exited" : "— stopped"),
                    smaller(base(p.muted(), dark)), item);
        }
        if (!item.streaming() && !proseOf(item.text()).isBlank()) {
            put(doc, "\n", base(ink, dark), item);
            // M78 D1 (OD-Q.11): the answer's Copy shows under the pointer or with the keyboard focus only
            JButton copy = insertButton(doc, "Copy", "Copy this answer's text (Markdown source; never a tool line)", item,
                    () -> copy(proseOf(item.text())), true);
            hoverCopies.put(item.key(), copy);
        }
        put(doc, "\n\n", base(ink, dark), item);
    }

    /** An answer's own words without its tool lines: what its Copy copies (§5.9). */
    static String proseOf(String answer) {
        StringBuilder sb = new StringBuilder();
        for (String line : answer.split("\n", -1)) {
            if (line.startsWith("▸ ")) continue;
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(line);
        }
        return sb.toString().strip();
    }

    private void renderMarkdown(StyledDocument doc, String text, Item item, Color ink, boolean dark, boolean stale,
                                int[] candidates, boolean streaming) throws BadLocationException {
        if (text.isEmpty()) return;
        TranscriptStyle.Palette p = TranscriptStyle.palette(dark);
        // the block still streaming is plain text until it completes (§5.9): the last line of a streaming answer
        String complete = text;
        String tail = "";
        if (streaming) {
            int cut = text.lastIndexOf('\n');
            complete = cut < 0 ? "" : text.substring(0, cut);
            tail = cut < 0 ? text : text.substring(cut + 1);
            if (complete.contains("```") && countFences(complete) % 2 == 1) {          // an open code block streams too
                int open = complete.lastIndexOf("```");
                tail = complete.substring(open) + "\n" + tail;
                complete = complete.substring(0, open);
            }
        }
        for (SafeMarkdown.Block b : SafeMarkdown.parse(complete)) {
            switch (b.kind()) {
                case BLANK -> put(doc, "\n", base(ink, dark), item);
                case CODE -> renderCode(doc, b.code(), b.language(), item, dark);
                case TABLE -> {
                    SimpleAttributeSet m = mono(base(p.codeText(), dark));
                    StyleConstants.setBackground(m, p.codeBackground());
                    put(doc, VisibleMarks.show(b.code()) + "\n", m, item);
                }
                default -> {
                    SimpleAttributeSet a = base(ink, dark);
                    String prefix = "";
                    switch (b.kind()) {
                        case HEADING -> {
                            StyleConstants.setBold(a, true);
                            StyleConstants.setFontSize(a, fontSize() + (4 - b.level()) * 2);
                        }
                        case BULLET -> prefix = "  ".repeat(b.level()) + (b.level() == 1 ? "• " : b.level() == 2 ? "◦ " : "▪ ");
                        case NUMBERED -> prefix = "  ".repeat(b.level()) + b.number() + ". ";
                        case QUOTE -> {
                            // a plain bar and the muted colour: an italic run behind a block glyph lost its letters in
                            // the system font (seen in the P2a screenshots)
                            prefix = "| ";
                            StyleConstants.setForeground(a, p.muted());
                        }
                        default -> { }
                    }
                    if (!prefix.isEmpty()) put(doc, prefix, a, item);
                    for (SafeMarkdown.Run r : b.runs()) renderRun(doc, r, a, item, dark, stale, candidates);
                    put(doc, "\n", a, item);
                }
            }
        }
        if (!tail.isEmpty()) put(doc, VisibleMarks.show(tail), base(ink, dark), item);
    }

    private static int countFences(String s) {
        int n = 0;
        for (int i = s.indexOf("```"); i >= 0; i = s.indexOf("```", i + 3)) n++;
        return n;
    }

    private void renderRun(StyledDocument doc, SafeMarkdown.Run r, SimpleAttributeSet around, Item item, boolean dark,
                           boolean stale, int[] candidates) throws BadLocationException {
        TranscriptStyle.Palette p = TranscriptStyle.palette(dark);
        SimpleAttributeSet a = new SimpleAttributeSet(around);
        switch (r.kind()) {
            case STRONG -> StyleConstants.setBold(a, true);
            case EMPHASIS -> StyleConstants.setItalic(a, true);
            case CODE -> {
                String code = r.text();
                if (candidates[0] < MAX_CANDIDATES) {
                    candidates[0]++;
                    if (chips.isNode(code)) {
                        chip(doc, code, "node " + code + " — selects and spotlights the node", item, stale,
                                () -> navigator.revealNode(code));
                        return;
                    }
                    if (chips.isChart(code)) {
                        chip(doc, code, "chart " + code + " — opens the chart", item, stale,
                                () -> navigator.revealChart(code));
                        return;
                    }
                }
                mono(a);
                StyleConstants.setBackground(a, p.codeBackground());
                StyleConstants.setForeground(a, p.codeText());
                put(doc, VisibleMarks.show(code), a, item);
                return;
            }
            case LINK, IMAGE -> {
                if (r.kind() == SafeMarkdown.Kind.IMAGE) {          // never fetched: its words, and the URL as a link
                    put(doc, VisibleMarks.show(r.text().substring(0, r.text().lastIndexOf(']') + 1)) + " ", a, item);
                }
                SimpleAttributeSet link = new SimpleAttributeSet(a);
                StyleConstants.setUnderline(link, true);
                StyleConstants.setForeground(link, p.link());
                link.addAttribute(LINK_ATTR, r.url());
                String shown = r.kind() == SafeMarkdown.Kind.IMAGE ? r.url() : r.text();
                put(doc, VisibleMarks.show(shown), link, item);
                if (r.kind() == SafeMarkdown.Kind.LINK && !r.text().equals(r.url())) {
                    put(doc, " (" + VisibleMarks.show(r.url()) + ")", smaller(base(p.muted(), dark)), item);
                }
                return;
            }
            default -> { }
        }
        renderProse(doc, r.text(), a, item, stale, candidates);
    }

    private static final Pattern REFERENCE = Pattern.compile(
            "\\brecords? #(\\d{1,9})|\\bnode ([A-Za-z0-9_.$:-]{1,100})|\"([^\"\\n]{1,80})\"");

    /** Prose with its chips: {@code record #12}, {@code node riskMonitor}, a quoted chart name — nothing else. */
    private void renderProse(StyledDocument doc, String text, SimpleAttributeSet a, Item item, boolean stale,
                             int[] candidates) throws BadLocationException {
        Matcher m = REFERENCE.matcher(text);
        int at = 0;
        while (m.find() && candidates[0] < MAX_CANDIDATES) {
            candidates[0]++;
            String chipLabel = null;
            String tip = null;
            Runnable go = null;
            if (m.group(1) != null) {
                int index = Integer.parseInt(m.group(1));
                if (chips.isRecord(index)) {
                    chipLabel = m.group();
                    tip = "record " + index + " — opens the record";
                    go = () -> navigator.revealRecord(index);
                }
            } else if (m.group(2) != null) {
                String id = trimPunctuation(m.group(2));
                if (chips.isNode(id)) {
                    put(doc, VisibleMarks.show(text.substring(at, m.start())) + "node ", a, item);
                    chip(doc, id, "node " + id + " — selects and spotlights the node", item, stale,
                            () -> navigator.revealNode(id));
                    at = m.start(2) + id.length();
                    continue;
                }
            } else if (m.group(3) != null && chips.isChart(m.group(3))) {
                String name = m.group(3);
                chipLabel = name;
                tip = "chart " + name + " — opens the chart";
                go = () -> navigator.revealChart(name);
            }
            if (chipLabel == null) continue;
            // a quoted chart name keeps its quotes as text around the chip: every character the model wrote stays
            boolean quoted = m.group(3) != null;
            put(doc, VisibleMarks.show(text.substring(at, m.start())) + (quoted ? "\"" : ""), a, item);
            chip(doc, chipLabel, tip, item, stale, go);
            if (quoted) put(doc, "\"", a, item);
            at = m.end();
        }
        put(doc, VisibleMarks.show(text.substring(at)), a, item);
    }

    private static String trimPunctuation(String id) {
        int end = id.length();
        while (end > 0 && ".:-".indexOf(id.charAt(end - 1)) >= 0) end--;
        return id.substring(0, end);
    }

    /**
     * A chip: a flat, plain-text button in the line (its label and tooltip are {@code html.disable}d — a chart name or
     * node id can come from a bundle, r8 F15). It only navigates; an answer written under an earlier log generation
     * shows its chips muted, and a click says why and goes nowhere.
     */
    private void chip(StyledDocument doc, String label, String tip, Item item, boolean stale, Runnable go)
            throws BadLocationException {
        TranscriptStyle.Palette p = TranscriptStyle.palette(renderedDark);
        JButton b = UiText.button(VisibleMarks.show(label));
        b.setToolTipText(UiText.tooltip(stale ? tip + " (from an earlier load of the log)" : tip));
        b.getAccessibleContext().setAccessibleName(tip);
        b.setMargin(new Insets(0, 4, 0, 4));
        b.setFocusable(true);
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(stale ? p.muted() : p.chipText(), 1, true),
                BorderFactory.createEmptyBorder(0, 4, 0, 4)));
        b.setContentAreaFilled(true);
        b.setBackground(stale ? p.codeBackground() : p.chipBackground());
        b.setForeground(stale ? p.muted() : p.chipText());
        b.setFont(getFont().deriveFont((float) fontSize()));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.putClientProperty("transcript.chip", label);
        b.addActionListener(e -> {
            if (stale) {
                navigator.say("this answer was about load " + item.logGeneration() + " of the log; it has been "
                        + "reloaded since (load " + chips.generation() + ")");
            } else {
                go.run();
            }
        });
        // owner, 2026-10-18 (the onboard transcript read "tracing an order from   through to …"): the chip's characters
        // are the document's own text under the button, never a placeholder space — so what is selected, copied or read
        // aloud is every character the model wrote, chip or not
        insertComponent(doc, b, item, VisibleMarks.show(label));
    }

    /**
     * M78 D1 (§3): the status an answer carries while the session says a permission waits for the person — its open tool
     * line then ends "— approve?" (the dialog, unchanged, asks the question).
     */
    static final String AWAITING_APPROVAL = "AWAITING_APPROVAL";
    static final String APPROVE = "— approve?";

    private void renderTool(StyledDocument doc, String line, Item item, int index, boolean dark) throws BadLocationException {
        renderTool(doc, line, item, index, dark, false);
    }

    private void renderTool(StyledDocument doc, String line, Item item, int index, boolean dark, boolean asks)
            throws BadLocationException {
        TranscriptStyle.Palette p = TranscriptStyle.palette(dark);
        String body = line.substring(2);
        // the adapter appends one " — outcome" per step: P3's decision ("approved by you", "denied by you", …) and then
        // the tool's own result (✓, refused) — every step is shown, the last one colours the mark
        java.util.LinkedList<String> steps = new java.util.LinkedList<>();
        while (true) {
            int dash = body.lastIndexOf(" — ");
            if (dash < 0) break;
            String seg = body.substring(dash + 3);
            if (TranscriptStyle.Mark.of(seg) == TranscriptStyle.Mark.PENDING && !seg.startsWith("withdrawn")) break;
            steps.addFirst(seg);
            body = body.substring(0, dash);
        }
        String outcome = String.join(" · ", steps);
        int space = body.indexOf(' ');
        String name = space < 0 ? body : body.substring(0, space);
        String input = space < 0 ? "" : body.substring(space + 1);
        TranscriptStyle.Mark mark = steps.isEmpty() ? TranscriptStyle.Mark.PENDING : TranscriptStyle.Mark.of(steps.getLast());
        String key = item.key() + "#tool" + index;
        boolean open = expanded.contains(key);
        JButton toggle = UiText.button(open ? "▾" : "▸");
        toggle.setMargin(new Insets(0, 2, 0, 2));
        toggle.setBorderPainted(false);
        toggle.setContentAreaFilled(false);
        toggle.setFont(UiTheme.mono(fontSize()));
        atLeastTarget(toggle);                    // review §4.3 (WCAG 2.5.8): 24×24, never the 19×19 a glyph gives
        toggle.getAccessibleContext().setAccessibleName(VisibleMarks.show(name) + " — "
                + TranscriptStyle.spoken(mark, steps.isEmpty() ? null : VisibleMarks.show(outcome))
                + (open ? ", expanded" : ", collapsed: press to show its " + expandedWhat));
        toggle.setToolTipText(UiText.tooltip(open ? "Hide this call's " + expandedWhat : "Show this call's " + expandedWhat));
        toggle.addActionListener(e -> {
            if (!expanded.remove(key)) expanded.add(key);
            restyleKeepingTheme();
        });
        insertComponent(doc, toggle, item);
        SimpleAttributeSet m = mono(base(p.body(), dark));
        put(doc, " " + VisibleMarks.show(name), m, item);
        SimpleAttributeSet markStyle = mono(bold(base(TranscriptStyle.mark(mark, dark), dark)));
        if (asks && mark == TranscriptStyle.Mark.PENDING) {
            SimpleAttributeSet ask = mono(bold(base(UiTheme.noteForeground(), dark)));
            put(doc, " " + APPROVE + "\n", ask, item);
        } else {
            put(doc, "  " + (mark == TranscriptStyle.Mark.PENDING ? mark.words : VisibleMarks.show(outcome)) + "\n",
                    markStyle, item);
        }
        if (open && !input.isBlank()) {
            SimpleAttributeSet code = mono(base(p.codeText(), dark));
            StyleConstants.setBackground(code, p.codeBackground());
            String shown = pretty(input);
            byte[] bytes = shown.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String more = "";
            if (bytes.length > MAX_INPUT) {
                shown = new String(bytes, 0, MAX_INPUT, java.nio.charset.StandardCharsets.UTF_8);
                more = "\n(" + (bytes.length - MAX_INPUT) + " more bytes not shown)";
            }
            put(doc, VisibleMarks.show(shown) + more + "\n", code, item);
        }
    }

    /**
     * A tool call's input, re-indented as the permission dialog shows it: token by token, every token kept as the line
     * carried it (M74 UX, P3 leftover c: re-parsing wrote an integer 3 as 3.0). As data, never run.
     */
    static String pretty(String input) {
        return PermissionText.pretty(input);
    }

    private void renderCode(StyledDocument doc, String code, String language, Item item, boolean dark)
            throws BadLocationException {
        TranscriptStyle.Palette p = TranscriptStyle.palette(dark);
        SimpleAttributeSet base = mono(base(p.codeText(), dark));
        StyleConstants.setBackground(base, p.codeBackground());
        insertButton(doc, "Copy code", "Copy this code block exactly", item, () -> copy(code));
        put(doc, (language == null || language.isBlank() ? "" : "  " + VisibleMarks.show(language)) + "\n",
                smaller(base(p.muted(), dark)), item);
        boolean tint = code.length() <= MAX_TINT_CHARS && code.chars().filter(c -> c == '\n').count() < MAX_TINT_LINES
                && CodeTint.supports(language);
        if (!tint) {
            put(doc, VisibleMarks.show(code) + "\n", base, item);
            return;
        }
        for (CodeTint.Token t : CodeTint.lex(code, language)) {
            SimpleAttributeSet a = new SimpleAttributeSet(base);
            switch (t.kind()) {
                case KEYWORD -> StyleConstants.setForeground(a, p.keyword());
                case STRING -> StyleConstants.setForeground(a, p.string());
                case COMMENT -> {
                    StyleConstants.setForeground(a, p.comment());
                    StyleConstants.setItalic(a, true);
                }
                case NUMBER -> StyleConstants.setForeground(a, p.number());
                default -> { }
            }
            put(doc, VisibleMarks.show(t.text()), a, item);
        }
        put(doc, "\n", base, item);
    }

    private void copy(String source) {
        clipboard.accept(source);
        navigator.say(VisibleMarks.copiedWords(source));
    }

    private JButton insertButton(StyledDocument doc, String label, String tip, Item item, Runnable action)
            throws BadLocationException {
        return insertButton(doc, label, tip, item, action, false);
    }

    private JButton insertButton(StyledDocument doc, String label, String tip, Item item, Runnable action, boolean onHover)
            throws BadLocationException {
        JButton b = onHover ? new HoverButton(label) : UiText.button(label);
        b.setToolTipText(UiText.tooltip(tip));
        b.getAccessibleContext().setAccessibleName(tip);
        b.setMargin(new Insets(0, 4, 0, 4));
        b.setFont(getFont().deriveFont((float) Math.max(9, fontSize() - 2)));
        atLeastTarget(b);
        b.addActionListener(e -> action.run());
        insertComponent(doc, b, item);
        return b;
    }

    // ---- M78 D1: the quiet answer — "Claude · unverified", Copy on hover or focus, the turn time as a tooltip ---------

    /** Each answer's Copy, by entry key: shown only for the answer under the pointer, or while it has the focus. */
    private final java.util.Map<String, JButton> hoverCopies = new java.util.HashMap<>();
    private String hovered;

    /** "unverified": a word in the line, the whole {@link TranscriptStyle#NOT_VERIFIED} as its tip and description. */
    private javax.swing.JLabel unverifiedMark(TranscriptStyle.Palette p) {
        javax.swing.JLabel l = UiText.label(TranscriptStyle.UNVERIFIED);
        l.setForeground(p.muted());
        l.setFont(getFont().deriveFont((float) Math.max(9, fontSize() - 2)));
        l.setToolTipText(UiText.tooltip(TranscriptStyle.NOT_VERIFIED));
        l.getAccessibleContext().setAccessibleName(TranscriptStyle.UNVERIFIED);
        l.getAccessibleContext().setAccessibleDescription(TranscriptStyle.NOT_VERIFIED);
        l.putClientProperty(UNVERIFIED_MARK, Boolean.TRUE);
        return l;
    }

    static final String UNVERIFIED_MARK = "transcript.unverified";

    /**
     * Owner, 2026-10-18 (the Copy flickered): the pointer over one of the view's own components — the Copy button, a chip,
     * a mark, a tool line's toggle — is over that component's entry, so its Copy stays shown; the view never sees those
     * moves. Leaving a component counts as leaving the transcript only when the pointer is outside the view as well.
     */
    private final MouseAdapter childHover = new MouseAdapter() {
        @Override public void mouseEntered(MouseEvent e) { overChild(e); }

        @Override public void mouseMoved(MouseEvent e) { overChild(e); }

        @Override public void mouseExited(MouseEvent e) {
            java.awt.Point at = javax.swing.SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), TranscriptView.this);
            if (!contains(at)) hover(null);
        }

        private void overChild(MouseEvent e) {
            String key = keyOf(e.getComponent());
            if (key != null) hover(key);
        }
    };

    /** The client property naming the entry a component of this view belongs to. */
    static final String ENTRY_KEY = "transcript.entry";

    /** The entry a component of this view belongs to, or null. */
    private static String keyOf(java.awt.Component c) {
        return c instanceof JComponent j && j.getClientProperty(ENTRY_KEY) instanceof String s ? s : null;
    }

    /** The pointer moved over entry {@code key} (null: off every entry): only that answer's Copy shows. */
    private void hover(String key) {
        if (Objects.equals(key, hovered)) return;
        JButton was = hovered == null ? null : hoverCopies.get(hovered);
        hovered = key;
        JButton now = key == null ? null : hoverCopies.get(key);
        if (was != null) was.repaint();
        if (now != null) now.repaint();
    }

    /** For the tests: hover the pointer over entry {@code index} (-1: off the transcript). */
    void hoverForTest(int index) {
        hover(index < 0 ? null : rendered.get(index).item().key());
    }

    /** Whether entry {@code key}'s Copy is painted now (under the pointer, or focused). */
    boolean copyShown(String key) {
        JButton b = hoverCopies.get(key);
        return b instanceof HoverButton h && h.shown();
    }

    /** A Copy that keeps its place and its focus, and is painted only on hover or focus (OD-Q.11). */
    private final class HoverButton extends JButton {
        HoverButton(String label) {
            super(label);
            UiText.plain(this);
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) { repaint(); }
                @Override public void focusLost(java.awt.event.FocusEvent e) { repaint(); }
            });
        }

        boolean shown() {
            if (isFocusOwner()) return true;
            for (var e : hoverCopies.entrySet()) if (e.getValue() == this) return e.getKey().equals(hovered);
            return false;
        }

        @Override public void paint(java.awt.Graphics g) {
            if (shown()) super.paint(g);
        }
    }

    /** The entry key at document position {@code pos}, or null. */
    private String itemAt(int pos) {
        if (pos < 0) return null;
        Object k = getStyledDocument().getCharacterElement(pos).getAttributes().getAttribute(ITEM_ATTR);
        return k instanceof String s ? s : null;
    }

    /** M78 D1 (OD-Q.8): an entry's own tooltip — an answer's turn time — wherever the pointer is over its words. */
    @Override
    public String getToolTipText(MouseEvent e) {
        String key = itemAt(viewToModel2D(e.getPoint()));
        if (key != null) {
            for (Rendered r : rendered) {
                if (r.item().key().equals(key)) return r.item().tip() == null ? null : UiText.tooltip(r.item().tip());
            }
        }
        return null;
    }

    /** The document offset where the newest answer begins (its "Claude · unverified" line), or -1 for none. */
    int lastAnswerStart() {
        for (int i = rendered.size() - 1; i >= 0; i--) {
            TranscriptStyle.Role r = rendered.get(i).item().role();
            if (r == TranscriptStyle.Role.CLAUDE || r == TranscriptStyle.Role.STOPPED) return rendered.get(i).start();
        }
        return -1;
    }

    /** For the tests: every "unverified" mark in the document, in order. */
    List<javax.swing.JLabel> unverifiedMarks() {
        List<javax.swing.JLabel> out = new ArrayList<>();
        StyledDocument doc = getStyledDocument();
        for (int i = 0; i < doc.getLength(); i++) {
            Object c = StyleConstants.getComponent(doc.getCharacterElement(i).getAttributes());
            if (c instanceof javax.swing.JLabel l && l.getClientProperty(UNVERIFIED_MARK) != null && !out.contains(l)) {
                out.add(l);
            }
        }
        return out;
    }

    /** The least size of a target in the transcript (WCAG 2.5.8 minimum: 24×24 CSS px). */
    static final int TARGET_MIN = 24;

    /** Grow {@code b} to at least {@link #TARGET_MIN} in each direction, keeping its own size when larger. */
    static void atLeastTarget(JComponent b) {
        java.awt.Dimension d = b.getPreferredSize();
        java.awt.Dimension min = new java.awt.Dimension(Math.max(TARGET_MIN, d.width), Math.max(TARGET_MIN, d.height));
        b.setPreferredSize(min);
        b.setMinimumSize(min);
    }

    private void insertComponent(StyledDocument doc, JComponent c, Item item) throws BadLocationException {
        insertComponent(doc, c, item, " ");
    }

    /**
     * A component standing for {@code text} in the document: the component is drawn, and {@code text} is what the
     * document holds for it — what a selection copies and a screen reader reads. A chip passes its own label, so the
     * model's words are never reduced to a placeholder; the view's chrome (a Copy button, a mark) passes a space.
     */
    private void insertComponent(StyledDocument doc, JComponent c, Item item, String text) throws BadLocationException {
        c.setAlignmentY(0.8f);
        c.putClientProperty(ENTRY_KEY, item.key());
        c.addMouseListener(childHover);
        c.addMouseMotionListener(childHover);
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setComponent(a, c);
        a.addAttribute(ITEM_ATTR, item.key());
        doc.insertString(doc.getLength(), text == null || text.isEmpty() ? " " : text, a);
    }

    private void restyleKeepingTheme() {
        List<Item> items = rendered.stream().map(Rendered::item).toList();
        rendered.clear();
        try {
            getStyledDocument().remove(0, getStyledDocument().getLength());
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        render(items);
    }

    private static void put(StyledDocument doc, String text, AttributeSet a, Item item) throws BadLocationException {
        if (text.isEmpty()) return;
        SimpleAttributeSet withItem = new SimpleAttributeSet(a);
        withItem.addAttribute(ITEM_ATTR, item.key());
        doc.insertString(doc.getLength(), text, withItem);
    }

    // ---- styles -------------------------------------------------------------------------------------------------------

    /** The body size: the application's font size (§5.9), from {@code UIManager}. */
    private int fontSize() {
        Font f = UIManager.getFont("TextPane.font");
        if (f == null) f = UIManager.getFont("Label.font");
        return f == null ? 13 : f.getSize();
    }

    private SimpleAttributeSet base(Color fg, boolean dark) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, fg);
        StyleConstants.setFontSize(a, fontSize());
        Font f = UIManager.getFont("TextPane.font");
        if (f != null) StyleConstants.setFontFamily(a, f.getFamily());
        return a;
    }

    private static SimpleAttributeSet bold(MutableAttributeSet a) {
        SimpleAttributeSet b = new SimpleAttributeSet(a);
        StyleConstants.setBold(b, true);
        return b;
    }

    private SimpleAttributeSet smaller(SimpleAttributeSet a) {
        StyleConstants.setFontSize(a, Math.max(9, fontSize() - 2));
        return a;
    }

    private SimpleAttributeSet mono(SimpleAttributeSet a) {
        StyleConstants.setFontFamily(a, UiTheme.monoFamily());
        StyleConstants.setFontSize(a, fontSize());
        return a;
    }

    // ---- links and turns ----------------------------------------------------------------------------------------------

    private String linkAt(int pos) {
        if (pos < 0) return null;
        Element e = getStyledDocument().getCharacterElement(pos);
        Object url = e.getAttributes().getAttribute(LINK_ATTR);
        return url instanceof String s ? s : null;
    }

    /** For the tests: click the link at document position {@code pos}, as the mouse would. */
    String clickLinkAt(int pos) {
        String url = linkAt(pos);
        if (url == null) return null;
        String said = LinkConfirm.click(this, url, asker, opener);
        navigator.say(said);
        return said;
    }

    private void moveTurn(int delta) {
        List<Integer> turns = new ArrayList<>();
        for (int i = 0; i < rendered.size(); i++) {
            TranscriptStyle.Role r = rendered.get(i).item().role();
            if (r == TranscriptStyle.Role.YOU || r == TranscriptStyle.Role.CLAUDE || r == TranscriptStyle.Role.STOPPED
                    || r == TranscriptStyle.Role.PLAIN) {
                turns.add(i);
            }
        }
        if (turns.isEmpty()) return;
        int at = turns.indexOf(focusedTurn);
        int next = at < 0 ? (delta > 0 ? 0 : turns.size() - 1) : Math.max(0, Math.min(turns.size() - 1, at + delta));
        focusedTurn = turns.get(next);
        Rendered r = rendered.get(focusedTurn);
        try {
            if (focusHighlight != null) getHighlighter().removeHighlight(focusHighlight);
            focusHighlight = getHighlighter().addHighlight(r.start(), r.start() + Math.max(1, r.length() - 1),
                    new DefaultHighlighter.DefaultHighlightPainter(TranscriptStyle.palette(renderedDark).chipBackground()));
            setCaretPosition(r.start());
            getAccessibleContext().setAccessibleDescription(r.item().role() == TranscriptStyle.Role.YOU
                    ? "your turn" : speaker + "'s answer");
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** For the tests: the index of the focused turn. */
    int focusedTurn() {
        return focusedTurn;
    }

    /** For the tests: the document's character attributes at each offset of entry {@code index}, as text. */
    String attributesOf(int index) {
        Rendered r = rendered.get(index);
        StringBuilder sb = new StringBuilder();
        StyledDocument doc = getStyledDocument();
        for (int i = r.start(); i < r.start() + r.length(); i++) {
            AttributeSet a = doc.getCharacterElement(i).getAttributes();
            sb.append(StyleConstants.isBold(a)).append(StyleConstants.isItalic(a)).append(StyleConstants.getForeground(a))
                    .append(StyleConstants.getFontFamily(a)).append(a.getAttribute(LINK_ATTR)).append('|');
        }
        return sb.toString();
    }

    /** For the tests: the plain text of entry {@code index} as shown. */
    String shownText(int index) {
        Rendered r = rendered.get(index);
        try {
            return getStyledDocument().getText(r.start(), r.length());
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    int renderedCount() {
        return rendered.size();
    }

    /** For the tests: every chip button in the document, by label. */
    List<JButton> chipButtons() {
        List<JButton> out = new ArrayList<>();
        StyledDocument doc = getStyledDocument();
        for (int i = 0; i < doc.getLength(); i++) {
            Object c = StyleConstants.getComponent(doc.getCharacterElement(i).getAttributes());
            if (c instanceof JButton b && b.getClientProperty("transcript.chip") != null && !out.contains(b)) out.add(b);
        }
        return out;
    }

    /** For the tests: every button labelled {@code label} in the document. */
    List<JButton> buttons(String label) {
        List<JButton> out = new ArrayList<>();
        StyledDocument doc = getStyledDocument();
        for (int i = 0; i < doc.getLength(); i++) {
            Object c = StyleConstants.getComponent(doc.getCharacterElement(i).getAttributes());
            if (c instanceof JButton b && label.equals(b.getText()) && !out.contains(b)) out.add(b);
        }
        return out;
    }

    /** The view's lower-case words for a language tag. */
    static String lang(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
