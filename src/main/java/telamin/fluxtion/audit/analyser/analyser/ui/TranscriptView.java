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
import java.awt.FontMetrics;
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
                       long logGeneration, boolean streaming, String tip, long atMillis, boolean recorded) {
        public Item {
            text = text == null ? "" : text;
            status = status == null ? "" : status;
        }

        /** An entry with no time of its own. */
        public Item(String key, TranscriptStyle.Role role, String text, String status, long revision, long logGeneration,
                    boolean streaming, String tip) {
            this(key, role, text, status, revision, logGeneration, streaming, tip, 0, false);
        }

        /** An entry with no tooltip of its own. */
        public Item(String key, TranscriptStyle.Role role, String text, String status, long revision, long logGeneration,
                    boolean streaming) {
            this(key, role, text, status, revision, logGeneration, streaming, null);
        }

        /**
         * Owner, 2026-10-18: this entry at {@code atMillis} — the epoch millis the SESSION recorded for the turn (0: none,
         * no time is shown). {@code recorded}: a recorded conversation's time (a walk's dialogue), labelled as such.
         */
        public Item at(long atMillis, boolean recorded) {
            return new Item(key, role, text, status, revision, logGeneration, streaming, tip, atMillis, recorded);
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
                timeLines.clear();
                renderedDark = dark;
                setBackground(TranscriptStyle.palette(dark).background());
            }
            int from = rendered.size();
            if (!rendered.isEmpty()) {
                Rendered last = rendered.get(rendered.size() - 1);
                Item now = items.get(rendered.size() - 1);
                if (!last.item().equals(now)) {
                    timeLines.removeIf(at -> at.getOffset() >= last.start());
                    doc.remove(last.start(), last.length());
                    lastEdits++;
                    rendered.remove(rendered.size() - 1);
                    from = rendered.size();
                }
            }
            for (int i = from; i < items.size(); i++) {
                int start = doc.getLength();
                previousAt = 0;
                for (int j = i - 1; j >= 0 && previousAt == 0; j--) previousAt = items.get(j).atMillis();
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
                int line = doc.getLength();
                put(doc, " " + TranscriptStyle.marker(TranscriptStyle.Role.YOU) + " ", who, item);
                turnTime(doc, item, band, line);
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
                int line = doc.getLength();
                if (nl > 0 && text.substring(0, nl).endsWith(":")) {
                    put(doc, VisibleMarks.show(text.substring(0, nl)), bold(base(p.body(), dark)), item);
                    turnTime(doc, item, base(p.body(), dark), line);
                    put(doc, "\n", bold(base(p.body(), dark)), item);
                    text = text.substring(nl + 1);
                } else if (item.atMillis() > 0) {
                    turnTime(doc, item, base(p.body(), dark), line);   // a recorded line with no speaker: the time alone
                    put(doc, "\n", base(p.body(), dark), item);
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
        int headLine = doc.getLength();
        put(doc, TranscriptStyle.answerName(speaker), head, item);
        SimpleAttributeSet note = smaller(base(p.muted(), dark));
        put(doc, " · ", note, item);
        insertComponent(doc, unverifiedMark(p), item);
        turnTime(doc, item, note, headLine);
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
                    if (b.table() != null) {
                        renderTable(doc, b.table(), item, dark, stale, candidates);
                    } else {
                        SimpleAttributeSet m = mono(base(p.codeText(), dark));
                        StyleConstants.setBackground(m, p.codeBackground());
                        put(doc, VisibleMarks.show(b.code()) + "\n", m, item);
                    }
                }
                case RULE -> put(doc, "─".repeat(monoColumns()) + "\n", mono(base(p.muted(), dark)), item);
                default -> {
                    SimpleAttributeSet a = base(ink, dark);
                    String prefix = "";
                    switch (b.kind()) {
                        case HEADING -> {
                            StyleConstants.setBold(a, true);
                            // levels 1–3 scale up; 4–6 render like level 3's weight but at the base size (§5.9 gap)
                            if (b.level() <= 3) StyleConstants.setFontSize(a, fontSize() + (4 - b.level()) * 2);
                        }
                        case BULLET -> prefix = "  ".repeat(b.level()) + (b.level() == 1 ? "• " : b.level() == 2 ? "◦ " : "▪ ");
                        case TASK_OPEN -> prefix = "  ".repeat(b.level()) + "☐ ";
                        case TASK_DONE -> prefix = "  ".repeat(b.level()) + "☑ ";
                        case NUMBERED -> prefix = "  ".repeat(b.level()) + b.number() + ". ";
                        case QUOTE -> {
                            // a plain bar and the muted colour: an italic run behind a block glyph lost its letters in
                            // the system font (seen in the P2a screenshots); nested quotes repeat the bar, capped at 3
                            prefix = "| ".repeat(Math.max(1, b.level()));
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
        renderRun(doc, r, around, item, dark, stale, candidates, false);
    }

    private void renderRun(StyledDocument doc, SafeMarkdown.Run r, SimpleAttributeSet around, Item item, boolean dark,
                           boolean stale, int[] candidates, boolean tableCell) throws BadLocationException {
        TranscriptStyle.Palette p = TranscriptStyle.palette(dark);
        SimpleAttributeSet a = new SimpleAttributeSet(around);
        switch (r.kind()) {
            case STRONG -> StyleConstants.setBold(a, true);
            case EMPHASIS -> StyleConstants.setItalic(a, true);
            case STRIKE -> StyleConstants.setStrikeThrough(a, true);
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
                put(doc, inlineText(code, tableCell), a, item);
                return;
            }
            case LINK, IMAGE -> {
                if (r.kind() == SafeMarkdown.Kind.IMAGE) {          // never fetched: its words, and the URL as a link
                    put(doc, inlineText(r.text().substring(0, r.text().lastIndexOf(']') + 1), tableCell) + " ", a, item);
                }
                SimpleAttributeSet link = new SimpleAttributeSet(a);
                StyleConstants.setUnderline(link, true);
                StyleConstants.setForeground(link, p.link());
                link.addAttribute(LINK_ATTR, r.url());
                String shown = r.kind() == SafeMarkdown.Kind.IMAGE ? r.url() : r.text();
                put(doc, inlineText(shown, tableCell), link, item);
                if (r.kind() == SafeMarkdown.Kind.LINK && !r.text().equals(r.url())) {
                    put(doc, " (" + inlineText(r.url(), tableCell) + ")", smaller(base(p.muted(), dark)), item);
                }
                return;
            }
            default -> { }
        }
        renderProse(doc, r.text(), a, item, stale, candidates, tableCell);
    }

    private static String inlineText(String text, boolean tableCell) {
        String shown = VisibleMarks.show(text);
        // A cell's literal tab is content, not one of the grid's structural tab stops. Copy still uses the source.
        return tableCell ? shown.replace("\t", "⟨U+0009⟩") : shown;
    }

    // ---- GFM tables -----------------------------------------------------------------------------------------------
    // A table's width decision is made fresh at render time from the view's CURRENT width (fitsWidth, below): this
    // view only re-renders in full on New chat, a trim or a theme change (class javadoc), so a table rendered wide and
    // then narrowed by a resize keeps its grid until the entry re-renders (a later revision, or one of those three).
    //
    // Columns are aligned with Swing paragraph TAB STOPS at PIXEL positions, never by padding with space characters:
    // a cell holding a glyph that is not one monospace cell wide (✅, ⚠, an em dash, …) has no well-defined "character
    // count", so counting characters (the first cut, r12) put each row's separator at a different x. A tab stop is
    // exact regardless of what came before it on that line, so every row's separator and every column's content land
    // at the SAME x — self-correcting at every column boundary, immune to any one cell's odd-width glyphs.

    /** A grid when it fits the view's current width; otherwise stacked {@code Header: value} records (§5.9 gap). */
    private void renderTable(StyledDocument doc, SafeMarkdown.Table table, Item item, boolean dark, boolean stale,
                              int[] candidates) throws BadLocationException {
        int cols = table.header().size();
        if (cols == 0) return;
        FontMetrics plainFm = getFontMetrics(new Font(UiTheme.monoFamily(), Font.PLAIN, fontSize()));
        TranscriptStyle.Palette p = TranscriptStyle.palette(dark);
        SimpleAttributeSet plain = mono(base(p.codeText(), dark));
        SimpleAttributeSet muted = mono(base(p.muted(), dark));
        SimpleAttributeSet headerAttr = bold(plain);
        // Run the SAME renderer against a scratch document. This includes URL disclosures, VisibleMarks and
        // component substitution. Use a private counter: measurement must not consume the answer's chip budget.
        StyledDocument measuring = new javax.swing.text.DefaultStyledDocument();
        int[] measuredCandidates = {candidates[0]};
        int[] colPx = new int[cols];
        for (int c = 0; c < cols; c++) colPx[c] = cellWidthPx(table.header().get(c), headerAttr, measuring,
                item, dark, stale, measuredCandidates);
        for (List<SafeMarkdown.Cell> row : table.rows()) {
            for (int c = 0; c < cols && c < row.size(); c++) {
                colPx[c] = Math.max(colPx[c], cellWidthPx(row.get(c), plain, measuring,
                        item, dark, stale, measuredCandidates));
            }
        }
        int spacePx = Math.max(1, plainFm.stringWidth(" "));
        int pipePx = Math.max(1, plainFm.stringWidth("│"));
        int dashPx = Math.max(1, plainFm.stringWidth("─"));
        int sepRegionPx = spacePx + pipePx + spacePx;      // the full " │ " gap between two columns

        if (!table.rows().isEmpty() && !fitsWidth(colPx, sepRegionPx, (int) textWidth())) {
            renderStackedTable(doc, table, item, dark, stale, candidates);
            return;
        }

        // colStart[c]..colEnd[c] is column c's reserved content width; pipeStart[c] is one space past colEnd[c], where
        // the next "│" is drawn; colStart[c+1] is one more space past the pipe. Every position is non-decreasing and
        // every gap between consecutive positions is at least one real character wide, so a tab from any point a row's
        // actual content happens to reach is always a strictly FORWARD, unambiguous move to the next one.
        int[] colStart = new int[cols];
        int[] colEnd = new int[cols];
        int[] pipeStart = new int[cols];
        for (int c = 0; c < cols; c++) {
            colEnd[c] = colStart[c] + colPx[c];
            if (c < cols - 1) {
                pipeStart[c] = colEnd[c] + spacePx;
                colStart[c + 1] = pipeStart[c] + pipePx + spacePx;
            }
        }
        List<javax.swing.text.TabStop> contentStops = new ArrayList<>();
        List<javax.swing.text.TabStop> ruleStops = new ArrayList<>();
        for (int c = 0; c < cols; c++) {
            SafeMarkdown.Align align = table.aligns().get(c);
            float target = switch (align) {
                case RIGHT -> colEnd[c];
                case CENTER -> colStart[c] + colPx[c] / 2f;
                case LEFT -> colStart[c];
            };
            int tabAlign = switch (align) {
                case RIGHT -> javax.swing.text.TabStop.ALIGN_RIGHT;
                case CENTER -> javax.swing.text.TabStop.ALIGN_CENTER;
                case LEFT -> javax.swing.text.TabStop.ALIGN_LEFT;
            };
            contentStops.add(new javax.swing.text.TabStop(target, tabAlign, javax.swing.text.TabStop.LEAD_NONE));
            // the rule row always fills from the column's LEFT edge, whatever the column's own text alignment is
            ruleStops.add(new javax.swing.text.TabStop(colStart[c], javax.swing.text.TabStop.ALIGN_LEFT,
                    javax.swing.text.TabStop.LEAD_NONE));
            if (c < cols - 1) {
                javax.swing.text.TabStop pipeStop = new javax.swing.text.TabStop(pipeStart[c],
                        javax.swing.text.TabStop.ALIGN_LEFT, javax.swing.text.TabStop.LEAD_NONE);
                contentStops.add(pipeStop);     // the SAME pipe position in both sets, so "┼" lands under every "│"
                ruleStops.add(pipeStop);
            }
        }
        javax.swing.text.TabSet contentTabs =
                new javax.swing.text.TabSet(contentStops.toArray(new javax.swing.text.TabStop[0]));
        javax.swing.text.TabSet ruleTabs = new javax.swing.text.TabSet(ruleStops.toArray(new javax.swing.text.TabStop[0]));

        int headerStart = doc.getLength();
        renderTableContentRow(doc, table.header(), table.aligns(), headerAttr, muted, item, dark, stale, candidates);
        put(doc, "\n", plain, item);
        applyTabSet(doc, headerStart, contentTabs);

        int ruleStart = doc.getLength();
        renderTableRuleRow(doc, colPx, dashPx, muted, item);
        put(doc, "\n", muted, item);
        applyTabSet(doc, ruleStart, ruleTabs);

        for (List<SafeMarkdown.Cell> row : table.rows()) {
            int rowStart = doc.getLength();
            renderTableContentRow(doc, row, table.aligns(), plain, muted, item, dark, stale, candidates);
            put(doc, "\n", plain, item);
            applyTabSet(doc, rowStart, contentTabs);
        }
    }

    /**
     * One header or body row: a TAB to each column's (alignment-specific) pixel stop, the cell's inline runs, then —
     * for every column but the last — a TAB to the shared pipe stop and a muted {@code │}. Column 0 skips its leading
     * tab only when it is LEFT-aligned (the paragraph already starts at x=0; tabbing to x=0 from x=0 is never done, to
     * avoid relying on how a same-position tab resolves).
     */
    private void renderTableContentRow(StyledDocument doc, List<SafeMarkdown.Cell> cells, List<SafeMarkdown.Align> aligns,
                                        SimpleAttributeSet cellAttr, SimpleAttributeSet pipeAttr, Item item, boolean dark,
                                        boolean stale, int[] candidates) throws BadLocationException {
        int cols = aligns.size();
        for (int c = 0; c < cols; c++) {
            if (!(c == 0 && aligns.get(c) == SafeMarkdown.Align.LEFT)) put(doc, "\t", pipeAttr, item);
            SafeMarkdown.Cell cell = c < cells.size() ? cells.get(c) : new SafeMarkdown.Cell(List.of());
            for (SafeMarkdown.Run r : cell.runs()) renderRun(doc, r, cellAttr, item, dark, stale, candidates, true);
            if (c < cols - 1) {
                put(doc, "\t", pipeAttr, item);
                put(doc, "│", pipeAttr, item);
            }
        }
    }

    /** The separator row: {@code ─} filling each column's full pixel width (rounded UP, never short) and a {@code ┼}. */
    private void renderTableRuleRow(StyledDocument doc, int[] colPx, int dashPx, SimpleAttributeSet muted, Item item)
            throws BadLocationException {
        int cols = colPx.length;
        for (int c = 0; c < cols; c++) {
            if (c > 0) put(doc, "\t", muted, item);
            int dashCount = Math.max(1, (int) Math.ceil(colPx[c] / (double) dashPx));
            put(doc, "─".repeat(dashCount), muted, item);
            if (c < cols - 1) {
                put(doc, "\t", muted, item);
                put(doc, "┼", muted, item);
            }
        }
    }

    /** {@code paragraphStart} is the offset the paragraph containing this row begins at (before its own text). */
    private static void applyTabSet(StyledDocument doc, int paragraphStart, javax.swing.text.TabSet tabs) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setTabSet(a, tabs);
        doc.setParagraphAttributes(paragraphStart, 1, a, false);
    }

    /** Too narrow for a grid: each row as its own stacked {@code Header: value} lines, a blank line between rows. */
    private void renderStackedTable(StyledDocument doc, SafeMarkdown.Table table, Item item, boolean dark, boolean stale,
                                     int[] candidates) throws BadLocationException {
        TranscriptStyle.Palette p = TranscriptStyle.palette(dark);
        SimpleAttributeSet plain = mono(base(p.codeText(), dark));
        SimpleAttributeSet label = bold(plain);
        int cols = table.header().size();
        List<List<SafeMarkdown.Cell>> rows = table.rows();
        for (int r = 0; r < rows.size(); r++) {
            List<SafeMarkdown.Cell> row = rows.get(r);
            for (int c = 0; c < cols; c++) {
                for (SafeMarkdown.Run run : table.header().get(c).runs()) renderRun(doc, run, label, item, dark, stale, candidates, true);
                put(doc, ": ", plain, item);
                SafeMarkdown.Cell cell = c < row.size() ? row.get(c) : new SafeMarkdown.Cell(List.of());
                for (SafeMarkdown.Run run : cell.runs()) renderRun(doc, run, plain, item, dark, stale, candidates, true);
                put(doc, "\n", plain, item);
            }
            if (r < rows.size() - 1) put(doc, "\n", plain, item);
        }
    }

    /**
     * Measure the renderer's final character runs, not the Markdown model. Links include their smaller destination,
     * images include their caption, controls have already become visible marks, and a chip occupies its button's
     * preferred width once (its underlying copyable text is not a second painted string).
     */
    private int cellWidthPx(SafeMarkdown.Cell cell, SimpleAttributeSet around, StyledDocument measuring,
                            Item item, boolean dark, boolean stale, int[] candidates) throws BadLocationException {
        measuring.remove(0, measuring.getLength());
        for (SafeMarkdown.Run run : cell.runs()) renderRun(measuring, run, around, item, dark, stale, candidates, true);
        int w = 0;
        for (int at = 0; at < measuring.getLength();) {
            Element e = measuring.getCharacterElement(at);
            int end = Math.min(e.getEndOffset(), measuring.getLength());
            AttributeSet attributes = e.getAttributes();
            java.awt.Component component = StyleConstants.getComponent(attributes);
            w += component == null
                    ? getFontMetrics(measuring.getFont(attributes)).stringWidth(measuring.getText(at, end - at))
                    : component.getPreferredSize().width;
            at = end;
        }
        return w;
    }

    /**
     * Pure (no {@code Graphics} needed — headlessly testable): would {@code columnWidthsPx} pixels, joined by
     * {@code sepWidthPx} between columns, fit in {@code availablePx}? Both widths are already real pixel measurements
     * (from font metrics), not character counts times an assumed per-character width.
     */
    static boolean fitsWidth(int[] columnWidthsPx, int sepWidthPx, int availablePx) {
        long total = 0;
        for (int w : columnWidthsPx) total += w;
        if (columnWidthsPx.length > 1) total += (long) sepWidthPx * (columnWidthsPx.length - 1);
        return total <= availablePx;
    }

    /** The monospace font's advance width in pixels, at the body size. */
    private int monoCharWidth() {
        Font f = new Font(UiTheme.monoFamily(), Font.PLAIN, fontSize());
        return Math.max(1, getFontMetrics(f).charWidth('0'));
    }

    /** How many monospace characters fit the view's current text width — used to size a drawn rule. */
    private int monoColumns() {
        return Math.max(10, Math.min(120, (int) (textWidth() / monoCharWidth())));
    }

    private static final Pattern REFERENCE = Pattern.compile(
            "\\brecords? #(\\d{1,9})|\\bnode ([A-Za-z0-9_.$:-]{1,100})|\"([^\"\\n]{1,80})\"");

    /** Prose with its chips: {@code record #12}, {@code node riskMonitor}, a quoted chart name — nothing else. */
    private void renderProse(StyledDocument doc, String text, SimpleAttributeSet a, Item item, boolean stale,
                             int[] candidates) throws BadLocationException {
        renderProse(doc, text, a, item, stale, candidates, false);
    }

    private void renderProse(StyledDocument doc, String text, SimpleAttributeSet a, Item item, boolean stale,
                             int[] candidates, boolean tableCell) throws BadLocationException {
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
                    put(doc, inlineText(text.substring(at, m.start()), tableCell) + "node ", a, item);
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
            put(doc, inlineText(text.substring(at, m.start()), tableCell) + (quoted ? "\"" : ""), a, item);
            chip(doc, chipLabel, tip, item, stale, go);
            if (quoted) put(doc, "\"", a, item);
            at = m.end();
        }
        put(doc, inlineText(text.substring(at), tableCell), a, item);
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

    // ---- owner, 2026-10-18: each turn's time, from the session's record of it ------------------------------------------

    /** The client property marking a turn's time label. */
    static final String TURN_TIME = "transcript.turnTime";
    /** The time on a turn's header line. */
    static final java.time.format.DateTimeFormatter CLOCK = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
    /** The day, shown on the first turn of a day. */
    static final java.time.format.DateTimeFormatter DAY = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM",
            Locale.getDefault(Locale.Category.FORMAT));
    /** The full local date and time: the label's tooltip and accessible description. */
    static final java.time.format.DateTimeFormatter FULL = java.time.format.DateTimeFormatter.ofPattern(
            "EEEE d MMMM yyyy, HH:mm:ss", Locale.getDefault(Locale.Category.FORMAT));

    /** The time zone the times are shown in: the machine's own. */
    static java.time.ZoneId zone() {
        return java.time.ZoneId.systemDefault();
    }

    /** The newest time among the entries before the one being rendered (0: none), for the day's first turn. */
    private long previousAt;
    /** The header lines that carry a time, so their right-aligned tab follows the view's width. */
    private final List<javax.swing.text.Position> timeLines = new ArrayList<>();
    private int tabWidth = -1;

    /** What a turn's time label says: HH:mm, with the day on the first turn of a day; "recorded" for a recorded one. */
    static String timeWords(long atMillis, long previousAtMillis, boolean recorded) {
        var at = java.time.Instant.ofEpochMilli(atMillis).atZone(zone());
        boolean newDay = previousAtMillis <= 0
                || !java.time.Instant.ofEpochMilli(previousAtMillis).atZone(zone()).toLocalDate().equals(at.toLocalDate());
        String shown = (newDay ? DAY.format(at) + " · " : "") + CLOCK.format(at);
        return recorded ? "recorded " + shown : shown;
    }

    /** The full local date and time of a turn — its label's tooltip and accessible description. */
    static String timeFull(long atMillis, boolean recorded) {
        String full = FULL.format(java.time.Instant.ofEpochMilli(atMillis).atZone(zone()));
        return recorded ? "Recorded " + full : full;
    }

    /**
     * A turn's time, right-aligned on its header line (the line starting at {@code line}), muted and small: a label whose
     * text is also the document's own. The session recorded the time; nothing here reads a clock. No time, nothing shown.
     */
    private void turnTime(StyledDocument doc, Item item, AttributeSet around, int line) throws BadLocationException {
        if (item.atMillis() <= 0) return;
        TranscriptStyle.Palette p = TranscriptStyle.palette(renderedDark);
        String shown = timeWords(item.atMillis(), previousAt, item.recorded());
        String full = timeFull(item.atMillis(), item.recorded());
        javax.swing.JLabel l = UiText.label(shown);
        l.setForeground(p.muted());
        l.setFont(getFont().deriveFont((float) Math.max(9, fontSize() - 2)));
        l.setToolTipText(UiText.tooltip(full));
        l.getAccessibleContext().setAccessibleName(shown);
        l.getAccessibleContext().setAccessibleDescription(full);
        l.putClientProperty(TURN_TIME, Boolean.TRUE);
        put(doc, "\t", around, item);
        insertComponent(doc, l, item, shown);
        timeLines.add(doc.createPosition(line));
        rightTab(doc, line);
    }

    /** The header line at {@code offset} gets one right-aligned tab stop at the view's text width. */
    private void rightTab(StyledDocument doc, int offset) {
        SimpleAttributeSet tab = new SimpleAttributeSet();
        StyleConstants.setTabSet(tab, new javax.swing.text.TabSet(new javax.swing.text.TabStop[]{
                new javax.swing.text.TabStop(textWidth(), javax.swing.text.TabStop.ALIGN_RIGHT,
                        javax.swing.text.TabStop.LEAD_NONE)}));
        doc.setParagraphAttributes(offset, 1, tab, false);
    }

    private float textWidth() {
        Insets in = getInsets();
        int w = getWidth() - in.left - in.right - 6;
        return Math.max(160, w);
    }

    /** Owner, 2026-10-18: a turn's time stays right-aligned as the view's width changes. */
    @Override
    @SuppressWarnings("deprecation")
    public void reshape(int x, int y, int w, int h) {
        super.reshape(x, y, w, h);
        retab();
    }

    /** The view was resized: every time stays at the right edge. */
    private void retab() {
        if (timeLines == null) return;                    // during construction
        int w = (int) textWidth();
        if (w == tabWidth) return;
        tabWidth = w;
        StyledDocument doc = getStyledDocument();
        for (javax.swing.text.Position at : timeLines) {
            if (at.getOffset() < doc.getLength()) rightTab(doc, at.getOffset());
        }
    }

    /** For the tests: the time labels in the document, in order. */
    List<javax.swing.JLabel> timeLabels() {
        List<javax.swing.JLabel> out = new ArrayList<>();
        StyledDocument doc = getStyledDocument();
        for (int i = 0; i < doc.getLength(); i++) {
            Object c = StyleConstants.getComponent(doc.getCharacterElement(i).getAttributes());
            if (c instanceof javax.swing.JLabel l && l.getClientProperty(TURN_TIME) != null && !out.contains(l)) out.add(l);
        }
        return out;
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
