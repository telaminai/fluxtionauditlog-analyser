package telamin.fluxtion.audit.analyser.analyser.ui;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.StyledEditorKit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P2 (spec §5.9, §10): the Claude Code transcript, drawn — safe Markdown, what is seen is what is copied, links only
 * after their confirmation, chips that only navigate, incremental rendering, collapsible tool lines. Headless: a
 * {@code JTextPane} renders without a window.
 */
class TranscriptViewTest {

    static TranscriptView.Item answer(String key, String text) {
        return new TranscriptView.Item(key, TranscriptStyle.Role.CLAUDE, text, "COMPLETED", 1, 0, false);
    }

    static TranscriptView.Item you(String key, String text) {
        return new TranscriptView.Item(key, TranscriptStyle.Role.YOU, text, "SENT", 0, 0, false);
    }

    static <T> T onEdt(java.util.concurrent.Callable<T> c) throws Exception {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Exception> err = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                out.set(c.call());
            } catch (Exception e) {
                err.set(e);
            }
        });
        if (err.get() != null) throw err.get();
        return out.get();
    }

    static String all(TranscriptView v) throws Exception {
        return v.getDocument().getText(0, v.getDocument().getLength());
    }

    /** Lay out and paint the view into an image, as a screen would (an HTML view would fetch here). */
    static void paint(TranscriptView v) {
        v.setSize(900, 2000);
        v.doLayout();
        BufferedImage img = new BufferedImage(900, 2000, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        v.paint(g);
        g.dispose();
    }

    // ---- ★ safe Markdown ----------------------------------------------------------------------------------------------

    /**
     * ★ Witness: set the view's content type to {@code text/html} — the {@code <img>} disappears into markup, the
     * literal assertion fails, and the loopback server sees the fetch.
     */
    @Test
    @DisplayName("★ safe Markdown: HTML, images, scripts and javascript:/file: links are literal text; nothing is fetched")
    void safeMarkdown() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger hits = new AtomicInteger();
        server.createContext("/", ex -> {
            hits.incrementAndGet();
            ex.sendResponseHeaders(204, -1);
            ex.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            String text = "# Heading\nSee <img src=\"" + base + "/img\"> and <script>alert(1)</script>.\n"
                    + "<div onclick=\"x()\">a block</div>\n![x](" + base + "/md)\n"
                    + "[run](javascript:alert(1)) and [local](file:///etc/passwd)";
            String shown = onEdt(() -> {
                TranscriptView v = new TranscriptView();
                v.render(List.of(answer("a", text)));
                paint(v);
                assertInstanceOf(StyledEditorKit.class, v.getEditorKit());
                assertFalse(v.getEditorKit() instanceof javax.swing.text.html.HTMLEditorKit, "never an HTML kit");
                assertEquals("text/plain", v.getContentType().startsWith("text/plain") ? "text/plain" : v.getContentType());
                return all(v);
            });
            assertTrue(shown.contains("<img src=\"" + base + "/img\">"), "the tag is its characters");
            assertTrue(shown.contains("<script>alert(1)</script>"));
            assertTrue(shown.contains("<div onclick=\"x()\">a block</div>"));
            assertTrue(shown.contains("[image: x]"), "an image is its words, never fetched");
            assertTrue(shown.contains("javascript:alert(1)") && shown.contains("file:///etc/passwd"), "shown, never opened");
            Thread.sleep(200);
            assertEquals(0, hits.get(), "no network request is made");
        } finally {
            server.stop(0);
        }
    }

    /** ★ r8 F21. Witness: search for each closer with indexOf from the delimiter (quadratic) — the bound is exceeded. */
    @Test
    @DisplayName("★ the Markdown parser renders 64 KB of unclosed emphasis within a bounded time")
    void parserIsLinear() {
        for (String unit : List.of("*a ", "_a ", "[a", "`a ", "**a ")) {
            String pathological = unit.repeat(65_536 / unit.length());
            long t0 = System.nanoTime();
            SafeMarkdown.parse(pathological);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            assertTrue(ms < 750, unit + " took " + ms + " ms");
        }
    }

    @Test
    @DisplayName("headings, lists, quotes, inline and fenced code, tables as monospace")
    void markdownSubset() {
        var blocks = SafeMarkdown.parse("## Two\n- a\n  - b\n1. one\n> quoted\n`x` and **bold** and *it*\n```java\nint a = 1;\n```\n| a | b |");
        assertEquals(SafeMarkdown.BlockKind.HEADING, blocks.get(0).kind());
        assertEquals(2, blocks.get(0).level());
        assertEquals(SafeMarkdown.BlockKind.BULLET, blocks.get(1).kind());
        assertEquals(2, blocks.get(2).level(), "nested");
        assertEquals(SafeMarkdown.BlockKind.NUMBERED, blocks.get(3).kind());
        assertEquals(SafeMarkdown.BlockKind.QUOTE, blocks.get(4).kind());
        var runs = blocks.get(5).runs();
        assertEquals(SafeMarkdown.Kind.CODE, runs.get(0).kind());
        assertTrue(runs.stream().anyMatch(r -> r.kind() == SafeMarkdown.Kind.STRONG && r.text().equals("bold")));
        assertTrue(runs.stream().anyMatch(r -> r.kind() == SafeMarkdown.Kind.EMPHASIS && r.text().equals("it")));
        assertEquals(SafeMarkdown.BlockKind.CODE, blocks.get(6).kind());
        assertEquals("int a = 1;", blocks.get(6).code());
        assertEquals("java", blocks.get(6).language());
        assertEquals(SafeMarkdown.BlockKind.TABLE, blocks.get(7).kind());
    }

    // ---- ★ what is seen is what is copied ---------------------------------------------------------------------------

    /** ★ r8 F16. Witness: render with a plain {@code insertString} of the text — the raw characters remain. */
    @Test
    @DisplayName("★ ESC, U+202E and U+200B show as marks in prose and code; Copy copies the source and says so")
    void seenIsCopied() throws Exception {
        String source = "Run \u001B[31mrm\u001B[0m now‮txt.exe​\n```\nrm -rf ‮/\n```";
        List<String> said = new ArrayList<>();
        AtomicReference<String> clip = new AtomicReference<>();
        String shown = onEdt(() -> {
            TranscriptView v = new TranscriptView();
            v.setNavigator(navigator(said, new ArrayList<>()));
            v.setClipboard(clip::set);
            v.render(List.of(answer("a", source)));
            v.buttons("Copy").get(0).doClick();
            return all(v);
        });
        assertTrue(shown.contains("␛[31mrm␛[0m"), shown);
        assertTrue(shown.contains("⟨U+202E⟩txt.exe⟨U+200B⟩"), shown);
        assertTrue(shown.contains("rm -rf ⟨U+202E⟩/"), "in a code block too: " + shown);
        assertFalse(shown.contains("‮") || shown.contains("\u001B") || shown.contains("​"), "no raw character");
        assertEquals(TranscriptView.proseOf(source), clip.get(), "Copy copies the source exactly");
        assertEquals("copied; it contains 5 hidden characters, shown as marks", said.get(said.size() - 1));
    }

    @Test
    @DisplayName("a code block's Copy copies the code exactly; an answer's Copy never includes a tool line")
    void copyCode() throws Exception {
        AtomicReference<String> clip = new AtomicReference<>();
        String code = "class A {\n  int x = 1; // ok\n}";
        onEdt(() -> {
            TranscriptView v = new TranscriptView();
            v.setClipboard(clip::set);
            v.render(List.of(answer("a", "Here:\n▸ analyser_read {\"recordIndex\":1} — ✓\n```java\n" + code + "\n```")));
            v.buttons("Copy code").get(0).doClick();
            assertEquals(code, clip.get());
            v.buttons("Copy").get(0).doClick();
            assertFalse(clip.get().contains("▸ analyser_read"), "no tool line in an answer's copy");
            return null;
        });
    }

    // ---- ★ links ------------------------------------------------------------------------------------------------------

    /** ★ Witness: open the link on the click (skip the confirmation) — the declined click opens it and this fails. */
    @Test
    @DisplayName("★ a link opens only after the confirmation showing its punycode host, and only http/https")
    void links() throws Exception {
        List<URI> opened = new ArrayList<>();
        List<String> asked = new ArrayList<>();
        boolean[] answer = {false};
        onEdt(() -> {
            TranscriptView v = new TranscriptView();
            v.setLinkSeams((parent, message) -> {
                asked.add(message);
                return answer[0];
            }, opened::add);
            v.render(List.of(answer("a", "Go to https://аpple.example/a?b and [x](javascript:alert(1))")));
            String text = all(v);
            int link = text.indexOf("https://");
            assertEquals("the link was not opened", v.clickLinkAt(link + 3));
            assertEquals(0, opened.size(), "declined: nothing opened");
            assertTrue(asked.get(0).contains("Scheme and host:\n    https://xn--pple-43d.example"), asked.get(0));
            answer[0] = true;
            v.clickLinkAt(link + 3);
            assertEquals(1, opened.size(), "opened only after yes");
            int js = text.indexOf("x (javascript");
            assertTrue(v.clickLinkAt(js).contains("opens only http and https"), "a javascript: link is never offered");
            assertEquals(2, asked.size(), "and is never asked about");
            return null;
        });
        assertEquals("https://xn--pple-43d.example", LinkConfirm.origin("https://аpple.example/x"));
        assertEquals("https://proxy.example:8443", LinkConfirm.origin("https://u:p@proxy.example:8443/v1?k=x"));
        assertTrue(LinkConfirm.message("https://a.example/‮gpj.exe").contains("⟨U+202E⟩"), "bidi as a mark");
    }

    // ---- ★ chips -----------------------------------------------------------------------------------------------------

    static TranscriptView.Navigator navigator(List<String> said, List<String> went) {
        return new TranscriptView.Navigator() {
            @Override public void revealRecord(int index) { went.add("record " + index); }
            @Override public void revealNode(String id) { went.add("node " + id); }
            @Override public void revealChart(String name) { went.add("chart " + name); }
            @Override public void say(String words) { said.add(words); }
        };
    }

    static TranscriptView.Chips chips(Set<String> nodes, Set<String> charts, int records, long generation) {
        return new TranscriptView.Chips() {
            @Override public boolean isRecord(int index) { return index >= 0 && index < records; }
            @Override public boolean isNode(String id) { return nodes.contains(id); }
            @Override public boolean isChart(String name) { return charts.contains(name); }
            @Override public long generation() { return generation; }
        };
    }

    /** ★ r8 F20. Witness: chip whole words that equal a node id — the prose word "risk" becomes a chip. */
    @Test
    @DisplayName("★ chips need code formatting or the word node; prose never becomes navigation")
    void chipsNeedCodeOrNode() throws Exception {
        List<String> went = new ArrayList<>();
        List<JButton> chips = onEdt(() -> {
            TranscriptView v = new TranscriptView();
            v.setChips(chips(Set.of("risk", "map"), Set.of("DEMO spread"), 100, 1));
            v.setNavigator(navigator(new ArrayList<>(), went));
            v.render(List.of(answer("a", "The risk is high on the map. `risk` logged; so did node map. See record #12 "
                    + "and record #500, and the chart \"DEMO spread\".")));
            return v.chipButtons();
        });
        List<String> labels = chips.stream().map(JButton::getText).toList();
        assertEquals(List.of("risk", "map", "record #12", "DEMO spread"), labels, "record #500 is outside the log");
        for (JButton b : chips) {
            assertEquals(Boolean.TRUE, b.getClientProperty(UiText.HTML_DISABLE), "a chip is plain (r8 F15)");
        }
        onEdt(() -> {
            chips.forEach(JButton::doClick);
            return null;
        });
        assertEquals(List.of("node risk", "node map", "record 12", "chart DEMO spread"), went, "chips only navigate");
    }

    @Test
    @DisplayName("matching stays within its bound on 5,000 nodes")
    void chipsAreBounded() throws Exception {
        Set<String> many = new java.util.HashSet<>();
        for (int i = 0; i < 5_000; i++) many.add("n" + i);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 400; i++) text.append("`n").append(i).append("` ");
        int n = onEdt(() -> {
            TranscriptView v = new TranscriptView();
            v.setChips(chips(many, Set.of(), 0, 1));
            long t0 = System.nanoTime();
            v.render(List.of(answer("a", text.toString())));
            assertTrue((System.nanoTime() - t0) / 1_000_000 < 3_000, "bounded time");
            return v.chipButtons().size();
        });
        assertEquals(TranscriptView.MAX_CANDIDATES, n, "the first 200 candidates of an answer, and no more");
    }

    /**
     * ★ r8 F15: a chip's label can come from a bundle (a node id, a chart name) and is never Swing HTML: an {@code <img>}
     * in it is its characters and is never fetched, on layout, paint or hover (tooltip). Witness: build the chip with a
     * plain {@code new JButton(label)} — the loopback server sees the fetch.
     */
    @Test
    @DisplayName("★ a chip from bundle-authored text renders as its characters; the loopback server sees no request")
    void chipsAreNeverHtml() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger hits = new AtomicInteger();
        server.createContext("/", ex -> {
            hits.incrementAndGet();
            ex.sendResponseHeaders(204, -1);
            ex.close();
        });
        server.start();
        try {
            String evil = "<html><img src='http://127.0.0.1:" + server.getAddress().getPort() + "/c'>";
            String tip = onEdt(() -> {
                TranscriptView v = new TranscriptView();
                v.setChips(chips(Set.of(evil), Set.of(), 0, 1));
                v.render(List.of(answer("a", "The node is `" + evil + "`.")));
                paint(v);
                JButton chip = v.chipButtons().get(0);
                chip.setSize(chip.getPreferredSize());
                BufferedImage img = new BufferedImage(Math.max(1, chip.getWidth()), Math.max(1, chip.getHeight()),
                        BufferedImage.TYPE_INT_RGB);
                chip.paint(img.createGraphics());
                javax.swing.JToolTip t = chip.createToolTip();
                t.setTipText(chip.getToolTipText());
                t.getPreferredSize();
                assertEquals(evil, chip.getText(), "its characters");
                return chip.getToolTipText();
            });
            assertFalse(UiText.rendersAsHtml(tip), "the tooltip is inert too");
            Thread.sleep(200);
            assertEquals(0, hits.get(), "no request from configuration or a bundle's words");
        } finally {
            server.stop(0);
        }
    }

    /** ★ Witness: a chip that navigates whatever its generation — the stale click reveals record 3 and this fails. */
    @Test
    @DisplayName("★ a chip from an earlier log generation is muted, says why, and navigates nowhere")
    void staleChips() throws Exception {
        List<String> said = new ArrayList<>();
        List<String> went = new ArrayList<>();
        onEdt(() -> {
            TranscriptView v = new TranscriptView();
            v.setChips(chips(Set.of(), Set.of(), 100, 4));
            v.setNavigator(navigator(said, went));
            v.render(List.of(new TranscriptView.Item("a", TranscriptStyle.Role.CLAUDE, "See record #3.", "COMPLETED", 1, 3,
                    false)));
            v.chipButtons().get(0).doClick();
            return null;
        });
        assertEquals(List.of(), went);
        assertEquals("this answer was about generation 3; the log is now generation 4", said.get(0));
    }

    // ---- ★ incremental rendering --------------------------------------------------------------------------------------

    /** ★ Witness: re-render every entry on each call (a full re-render) — the edit count and earlier styling fail. */
    @Test
    @DisplayName("★ streaming re-renders only its own entry; earlier entries keep their styling")
    void incremental() throws Exception {
        onEdt(() -> {
            TranscriptView v = new TranscriptView();
            var first = answer("a1", "# Title\nSome **bold** text with `code`.");
            v.render(List.of(you("u1", "Q?"), first));
            String before = v.attributesOf(1);
            var streaming = new TranscriptView.Item("a2", TranscriptStyle.Role.CLAUDE, "Partial", "STREAMING", 1, 0, true);
            v.render(List.of(you("u1", "Q?"), first, you("u2", "Again?"), streaming));
            assertEquals(2, v.lastEdits(), "two entries appended, nothing else touched");
            var more = new TranscriptView.Item("a2", TranscriptStyle.Role.CLAUDE, "Partial and more", "STREAMING", 2, 0, true);
            v.render(List.of(you("u1", "Q?"), first, you("u2", "Again?"), more));
            assertEquals(2, v.lastEdits(), "only the streaming entry: removed and inserted");
            assertEquals(before, v.attributesOf(1), "the earlier answer's styling is unchanged");
            assertTrue(v.shownText(3).contains(TranscriptView.CARET), "a caret while it streams");
            var done = new TranscriptView.Item("a2", TranscriptStyle.Role.CLAUDE, "Partial and more", "COMPLETED", 2, 0, false);
            v.render(List.of(you("u1", "Q?"), first, you("u2", "Again?"), done));
            assertFalse(v.shownText(3).contains(TranscriptView.CARET), "gone when the turn ends");
            v.render(List.of(you("u9", "new chat")));
            assertEquals(1, v.renderedCount(), "New chat (fewer entries) re-renders whole");
            return null;
        });
    }

    /**
     * Direct dispatch through the component's own binding (posted KeyEvents are dropped with no focus owner on this
     * machine, and headless): the keystroke's action, from the view's input and action maps, performed as Swing would.
     */
    static void press(javax.swing.JComponent c, int key) {
        KeyStroke ks = KeyStroke.getKeyStroke(key, InputEvent.ALT_DOWN_MASK);
        Object name = c.getInputMap().get(ks);
        assertNotNull(name, "a binding for " + ks);
        KeyEvent e = new KeyEvent(c, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), InputEvent.ALT_DOWN_MASK, key,
                KeyEvent.CHAR_UNDEFINED);
        assertTrue(SwingUtilities.notifyAction(c.getActionMap().get(name), ks, e, c, e.getModifiersEx()));
    }

    // ---- tool lines, turns, fonts ------------------------------------------------------------------------------------

    @Test
    @DisplayName("a tool line collapses to its name and mark, and expands to its input as escaped data")
    void toolLines() throws Exception {
        onEdt(() -> {
            TranscriptView v = new TranscriptView();
            v.render(List.of(answer("a", "Reading.\n▸ analyser_read {\"recordIndex\":3,\"note\":\"x‮y\"} — ✓\n"
                    + "▸ analyser_open {\"log\":\"a\"} — denied by the posture")));
            String collapsed = all(v);
            assertTrue(collapsed.contains("analyser_read  ✓"), collapsed);
            assertTrue(collapsed.contains("analyser_open  denied by the posture"), collapsed);
            assertFalse(collapsed.contains("recordIndex"), "collapsed: no input");
            v.buttons("▸").get(0).doClick();
            String open = all(v);
            assertTrue(open.contains("\"recordIndex\": 3"), "pretty-printed: " + open);
            assertTrue(open.contains("x⟨U+202E⟩y"), "escaped data");
            assertEquals(1, v.buttons("▾").size());
            return null;
        });
    }

    @Test
    @DisplayName("Alt+Up / Alt+Down move between turns")
    void turns() throws Exception {
        onEdt(() -> {
            TranscriptView v = new TranscriptView();
            v.render(List.of(you("u1", "one"), answer("a1", "two"), you("u2", "three")));
            press(v, KeyEvent.VK_DOWN);
            assertEquals(0, v.focusedTurn());
            press(v, KeyEvent.VK_DOWN);
            assertEquals(1, v.focusedTurn());
            press(v, KeyEvent.VK_UP);
            assertEquals(0, v.focusedTurn());
            return null;
        });
    }
}
