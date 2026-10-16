package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SF-2 — the source rule: no text this code computes reaches a Swing surface that interprets {@code <html>} unless it
 * has been made plain first.
 *
 * <p><b>The design, and why it is not a taint list.</b> The obvious rule — "a report title must not reach a label" —
 * needs to know which expressions carry foreign text, and that list rots: a node id, a folder name, a bundle note, a
 * chart name, a profile value, an exception message that echoes any of them. So the rule is STRUCTURAL instead. Every
 * place Swing parses text as HTML is a door (a label/button/menu-item constructor or {@code setText}, a tooltip, an
 * option-pane message or option, a tab title, a titled border, a list/combo/table/tree cell). At each door the text is
 * either a CONSTANT — string literals, ALL_CAPS constants, a ternary between constants — or it must arrive through
 * {@link UiText}: the component is {@link UiText#plain plain} (or built by a factory), the tooltip is
 * {@link UiText#tooltip}, the option-pane string is {@link UiText#inert}, the cell container is
 * {@link UiText#plainCells}, or the text is the analyser's OWN {@code "<html>…"} literal whose every computed piece is
 * {@link UiText#escape escaped}. Making a component plain costs nothing when its text happens to be our own, so the
 * remedy is always the same and the rule needs no judgement about where the text came from — which is what lets it
 * catch the NEXT instance, whoever wrote the text.
 *
 * <p><b>Why that is not a flood.</b> A component is made plain once, where it is declared: {@code MainFrame.status}
 * has eighty {@code setText} calls and one {@code UiText.plain}. What the rule flags is a door, not a call.
 *
 * <p><b>What it cannot see</b>, said so it is not trusted for it: a text assembled in one method and set in another is
 * judged by the expression at the door ({@code setText(line)} is computed, so the receiver must be plain — that is the
 * point); a receiver whose declaration it cannot find is reported, never assumed safe; a renderer's bare
 * {@code setText} requires {@code UiText.plain(this)} somewhere in its file, not in its own class body. The few doors
 * that are deliberately the analyser's own markup assembled elsewhere are {@link #REVIEWED}, each with its reason, and a
 * stale entry fails.
 *
 * <p>Witness: {@link #theRuleCatchesEachDoor()} feeds the scanner the shipped defects' shapes — {@code new JButton(title
 * + …)} (the recent-project card), a report title in a list's default renderer, a node id spliced into a tooltip's own
 * markup — and each must be flagged, while the fixed shapes pass. Mutating {@code StartPanel} back to
 * {@code new JButton(title + …)} turns {@link #everyDoorIsPlain()} red.
 */
class ForeignTextIsPlainTest {

    private static final Path UI = Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui");

    /**
     * Doors that are the analyser's own markup or own words, assembled away from the door, so the expression at the
     * door cannot show it. Key: file + the start of the (whitespace-normalised) door text. Value: why it is safe, and what
     * keeps it so.
     */
    static final Map<String, String> REVIEWED = new LinkedHashMap<>();

    static {
        REVIEWED.put("TopologyPanel.java: status.setToolTipText(line.isBlank() ? null : statusTooltip(line))",
                "statusTooltip escapes the whole line before adding its own <br> markup (TopologyStatusTooltipTest)");
        REVIEWED.put("UiTheme.java: createTitledBorder( BorderFactory.createMatteBorder(1, 0, 0, 0, line), title)",
                "UiTheme.section's parameter: every UiTheme.section(…) call site is itself a door this rule checks");
        REVIEWED.put("ReportsPanel.java: list.setCellRenderer((source, value, index, selected, focus) -> {",
                "the report list's own two-line markup: the value reaches the fresh renderer inert and the title and "
                        + "name are escaped (ForeignTextFrameTest#aReportTitleIsItsCharacters); plainCells would strip it");
        REVIEWED.put("GraphTabs.java: graphSelector.setRenderer((ListCellRenderer<? super GraphPanel>)",
                "a fresh renderer per cell, first given a GraphPanel (its toString is not HTML), made plain before the "
                        + "chart name is set");
        REVIEWED.put("ReportsPanel.java: new JList<>(names)", "its renderer is the reviewed own-markup one above");
        REVIEWED.put("GraphTabs.java: new JComboBox<>()", "graphSelector: its renderer is the reviewed one above");
        REVIEWED.put("TopologyCanvas.java: getToolTipText returns nodeTooltip(",
                "TopologyCanvas.nodeTooltip escapes the node's id, class and javadoc inside its own markup "
                        + "(UiTextTest#aNodeTooltipEscapesTheNodesWords — the id used to be spliced in raw)");
        REVIEWED.put("ChartPanel.java: getToolTipText returns \"<html><div width='600'>\" + text + \"</div></html>\"",
                "text is the chart's explanation lines, each UiText.escape'd, joined by the panel's own <br>");
        REVIEWED.put("HistoryComboBox.java: setText(current)",
                "HistoryComboBox's own setText, which writes its editor JTextField — not a label; its cells are "
                        + "plainCells where MainFrame builds it");
        REVIEWED.put("ConfigPanel.java: new JLabel(\"<html><div style='width:\" + NOTE_WIDTH + \"px'>\" + innerHtml + \"</div></html>\")",
                "mutedNote's parameter is the panel's own help markup: every caller passes a literal");
    }

    // ---- the doors -------------------------------------------------------------------------------------------------

    /** Constructors whose first String argument becomes HTML-interpreted text. */
    private static final Pattern CONSTRUCT = Pattern.compile(
            "new\\s+(?:javax\\.swing\\.)?(JLabel|JButton|JToggleButton|JCheckBox|JRadioButton|JMenuItem|JMenu"
                    + "|JCheckBoxMenuItem|JRadioButtonMenuItem)\\s*\\(");
    /** {@code x.setText(}, {@code x.addTab(}, … — and the receiver-less form inside a component subclass. */
    private static final Pattern RECEIVER_CALL = Pattern.compile(
            "(?<![\\w$])((?:[\\w$]+\\s*\\.\\s*)*)(setText|addTab|insertTab|setTitleAt)\\s*\\(");
    private static final Pattern TOOLTIP = Pattern.compile("(?<![\\w$])(setToolTipText|setToolTipTextAt)\\s*\\(");
    private static final Pattern OPTION_PANE = Pattern.compile(
            "JOptionPane\\s*\\.\\s*(showMessageDialog|showConfirmDialog|showOptionDialog|showInputDialog)\\s*\\(");
    private static final Pattern TITLED = Pattern.compile("(?:createTitledBorder|new\\s+(?:javax\\.swing\\.border\\.)?TitledBorder"
            + "|UiTheme\\s*\\.\\s*section)\\s*\\(");
    private static final Pattern TOOLTIP_OVERRIDE = Pattern.compile("String\\s+getToolTipText\\s*\\([^)]*\\)");
    private static final Pattern RENDERER = Pattern.compile(
            "(?:([\\w$]+)\\s*)?\\.\\s*(setCellRenderer|setRenderer|setDefaultRenderer)\\s*\\(");
    private static final Pattern CELLS = Pattern.compile(
            "new\\s+(?:javax\\.swing\\.)?(JList|JComboBox|JTable|JTree|HistoryComboBox)\\s*(?:<[^>()]*>)?\\s*\\(");

    /** Declared types whose text Swing interprets as HTML. */
    private static final Set<String> HTML_TYPES = Set.of("JLabel", "JButton", "JToggleButton", "JCheckBox", "JRadioButton",
            "JMenuItem", "JMenu", "JCheckBoxMenuItem", "JRadioButtonMenuItem", "AbstractButton", "JTabbedPane", "Card");
    /** The UiText calls that make a component plain where it is declared. */
    private static final Pattern PLAIN_INIT = Pattern.compile("UiText\\s*\\.\\s*(plain|label|button|menuItem|menu|checkBox"
            + "|radio|checkBoxMenuItem|radioMenuItem)\\s*\\(");

    @Test
    @DisplayName("SF-2: every computed text reaches an HTML-interpreting Swing surface through UiText")
    void everyDoorIsPlain() throws Exception {
        List<String> offenders = new ArrayList<>();
        Set<String> used = new TreeSet<>();
        try (var files = Files.list(UI)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String name = f.getFileName().toString();
                if (name.equals("UiText.java")) continue;            // the helper builds the doors it guards
                for (String door : doors(name, Files.readString(f))) {
                    String key = REVIEWED.keySet().stream().filter(door::startsWith).findFirst().orElse(null);
                    if (key != null) used.add(key);
                    else offenders.add(door);
                }
            }
        }
        assertTrue(offenders.isEmpty(), offenders.size() + " door(s):\n  " + String.join("\n  ", offenders) + "\nSF-2: computed text at an HTML door — make the component UiText.plain (or "
                + "build it with UiText.label/button/menuItem…), the tooltip UiText.tooltip(…), the option-pane string "
                + "UiText.inert(…), the list/combo/table UiText.plainCells(…), or escape each piece of your own <html> "
                + "with UiText.escape(…). See the class comment of ForeignTextIsPlainTest.");
        Set<String> stale = new TreeSet<>(REVIEWED.keySet());
        stale.removeAll(used);
        assertEquals(Set.of(), stale, "a REVIEWED door no longer exists — remove its entry");
    }

    @Test
    @DisplayName("SF-2 witness: the scanner flags each shipped defect's shape and passes its fix")
    void theRuleCatchesEachDoor() {
        // the recent-project button: a folder name first
        assertEquals(1, doors("X.java", "JButton b = new JButton(title + \" / \" + path);").size());
        assertEquals(0, doors("X.java", "JButton b = UiText.button(title + \" / \" + path);").size());
        // a report title in a list's default renderer
        assertEquals(1, doors("X.java", "JList<String> list = new JList<>(names);").size());
        assertEquals(0, doors("X.java", "JList<String> list = UiText.plainCells(new JList<>(names));").size());
        // a node id spliced into a tooltip's own markup
        assertEquals(1, doors("X.java", "c.setToolTipText(\"<html><b>\" + node.id() + \"</b></html>\");").size());
        assertEquals(0, doors("X.java", "c.setToolTipText(\"<html><b>\" + UiText.escape(node.id()) + \"</b></html>\");").size());
        // a tooltip of foreign words, and the owner's flag does not reach a tooltip
        assertEquals(1, doors("X.java", "JLabel l = UiText.label(\"x\"); l.setToolTipText(path);").size());
        assertEquals(0, doors("X.java", "JLabel l = UiText.label(\"x\"); l.setToolTipText(UiText.tooltip(path));").size());
        // a label whose text is set later
        assertEquals(1, doors("X.java", "private final JLabel status = new JLabel(\" \"); void f() { status.setText(msg); }").size());
        assertEquals(0, doors("X.java", "private final JLabel status = UiText.plain(new JLabel(\" \")); void f() { status.setText(msg); }").size());
        // an option-pane message naming a walk, and its options
        assertEquals(1, doors("X.java", "JOptionPane.showConfirmDialog(this, \"Delete \" + w.name() + \"?\");").size());
        assertEquals(0, doors("X.java", "JOptionPane.showConfirmDialog(this, UiText.inert(\"Delete \" + w.name() + \"?\"));").size());
        assertEquals(1, doors("X.java", "JOptionPane.showInputDialog(this, \"Which?\", \"t\", 1, null, names.toArray(), null);").size());
        // an overridden tooltip that returns foreign text
        assertEquals(1, doors("X.java", "public String getToolTipText(MouseEvent e) { return hit.label() + \" · \" + y; }").size());
        assertEquals(0, doors("X.java", "public String getToolTipText(MouseEvent e) { return UiText.tooltip(hit.label()); }").size());
        // constants are never flagged: literals, ALL_CAPS constants, ternaries between them
        assertEquals(0, doors("X.java", "JButton b = new JButton(open ? \"Close\" : \"Open \" + ReferenceSet.FILE_NAME);").size());
        assertEquals(0, doors("X.java", "JLabel l = new JLabel(); l.setText(ok ? \"yes\" : null);").size());
        // a receiver the scanner cannot resolve is reported, never assumed safe
        assertEquals(1, doors("X.java", "void f() { mystery.setText(msg); }").size());
        // a text component is not an HTML door
        assertEquals(0, doors("X.java", "JTextArea area = new JTextArea(); area.setText(msg);").size());
    }

    @Test
    @DisplayName("SF-2: the factories set html.disable before the text, so the constructor never builds an HTML view")
    void factoriesArePlainBeforeTheirText() {
        String html = "<html><b>x</b>";
        for (javax.swing.JComponent c : List.of(UiText.label(html), UiText.button(html), UiText.menuItem(html),
                UiText.menu(html), UiText.checkBox(html), UiText.radio(html), UiText.checkBoxMenuItem(html),
                UiText.radioMenuItem(html))) {
            assertEquals(Boolean.TRUE, c.getClientProperty(UiText.HTML_DISABLE), c.getClass().getSimpleName());
            assertNull(c.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey),
                    c.getClass().getSimpleName() + " built an HTML view");
        }
    }

    // ---- the scanner -----------------------------------------------------------------------------------------------

    /** Every unsafe door in {@code src}, as {@code "File.java: <door text>"}. */
    static List<String> doors(String file, String raw) {
        String src = stripComments(raw);
        List<String> out = new ArrayList<>();
        boolean fileHasPlainThis = src.contains("UiText.plain(this)") || src.contains("UiText.plainCells(this)");

        Matcher m = CONSTRUCT.matcher(src);
        while (m.find()) {
            int open = m.end() - 1;
            List<String> args = args(src, open);
            if (args.isEmpty()) continue;
            String text = args.get(0);
            if (text.startsWith("new ") || isNonStringVariable(src, text, m.start())) continue;   // an Icon or Action
            if (insideUiPlain(src, m.start())) {
                // plain(new JLabel(constant)) is fine; plain(new JLabel(computed)) built the view first
                if (!safeText(text)) out.add(file + ": " + door(src, m.start(), open));
                continue;
            }
            if (!safeText(text)) out.add(file + ": " + door(src, m.start(), open));
        }

        m = RECEIVER_CALL.matcher(src);
        while (m.find()) {
            String chain = m.group(1).replaceAll("\\s", "");
            String call = m.group(2);
            int open = m.end() - 1;
            if (isDeclaration(src, m.start(2))) continue;                       // `public void setText(String s)`
            List<String> args = args(src, open);
            String text = switch (call) {
                case "setTitleAt" -> args.size() > 1 ? args.get(1) : "";
                case "insertTab", "addTab" -> args.isEmpty() ? "" : args.get(0);
                default -> args.isEmpty() ? "" : args.get(0);
            };
            if (safeText(text)) continue;
            int start = m.start(1) < 0 ? m.start(2) : m.start(1);
            if (chain.isEmpty()) {
                // `UiText.plain(x).setText(…)`, a receiver that is a call: look back past the closing paren
                int dot = prevNonSpace(src, m.start(2) - 1);
                if (dot >= 0 && src.charAt(dot) == '.') {
                    int close = prevNonSpace(src, dot - 1);
                    if (close >= 0 && src.charAt(close) == ')') {
                        int openCall = matchingOpen(src, close);
                        String before = src.substring(Math.max(0, openCall - 40), openCall).replaceAll("\\s", "");
                        if (before.endsWith("UiText.plain")) continue;
                        out.add(file + ": " + door(src, start, open));
                        continue;
                    }
                }
                // receiver-less: a component subclass setting its own text (a renderer)
                if (!fileHasPlainThis) out.add(file + ": " + door(src, start, open));
                continue;
            }
            String receiver = chain.substring(0, chain.length() - 1);
            String last = receiver.substring(receiver.lastIndexOf('.') + 1);
            if (last.equals("this")) {
                if (!fileHasPlainThis) out.add(file + ": " + door(src, start, open));
                continue;
            }
            Declaration d = declaration(src, last, m.start());
            if (d == null) {
                out.add(file + ": " + door(src, start, open) + "   [receiver '" + last + "' not resolved]");
                continue;
            }
            if (!HTML_TYPES.contains(d.type)) continue;                          // a text component, a model, a filter
            if (PLAIN_INIT.matcher(d.initializer).find()) continue;
            if (Pattern.compile("UiText\\s*\\.\\s*plain(?:Cells)?\\s*\\(\\s*" + Pattern.quote(last) + "\\s*\\)").matcher(src).find()) continue;
            out.add(file + ": " + door(src, start, open));
        }

        m = TOOLTIP.matcher(src);
        while (m.find()) {
            if (isDeclaration(src, m.start(1))) continue;
            int open = m.end() - 1;
            List<String> args = args(src, open);
            String text = m.group(1).equals("setToolTipTextAt") ? (args.size() > 1 ? args.get(1) : "")
                    : (args.isEmpty() ? "" : args.get(0));
            if (!safe(text, "UiText.tooltip", "UiText.inert")) out.add(file + ": " + door(src, receiverStart(src, m.start()), open));
        }

        m = OPTION_PANE.matcher(src);
        while (m.find()) {
            int open = m.end() - 1;
            List<String> args = args(src, open);
            if (args.size() < 2) continue;
            String message = args.get(1);
            if (!safe(message, "UiText.inert") && !isComponent(src, message, m.start())) {
                out.add(file + ": " + door(src, m.start(), open));
                continue;
            }
            // the options of showOptionDialog (7th) and the selection values of showInputDialog (6th) become buttons
            // and combo-box cells; their initial values (8th / 7th) are shown too
            List<String> shown = new ArrayList<>();
            if (m.group(1).equals("showOptionDialog") && args.size() >= 7) shown.addAll(args.subList(6, Math.min(8, args.size())));
            // with no selection values the initial value is the text field's content, which is not HTML
            if (m.group(1).equals("showInputDialog") && args.size() >= 6 && !args.get(5).equals("null")) {
                shown.addAll(args.subList(5, Math.min(7, args.size())));
            }
            for (String s : shown) {
                if (!safe(s, "UiText.inert") && !isInertVariable(src, s, m.start())) {
                    out.add(file + ": " + door(src, m.start(), open) + "   [options]");
                    break;
                }
            }
        }

        m = TITLED.matcher(src);
        while (m.find()) {
            int open = m.end() - 1;
            List<String> args = args(src, open);
            if (args.isEmpty()) continue;
            String title = args.size() == 1 ? args.get(0) : args.get(1);
            if (args.size() > 1 && m.group().startsWith("createTitledBorder") && !args.get(0).contains("Border")
                    && !args.get(0).equals("null")) title = args.get(0);
            if (!safe(title, "UiText.inert")) out.add(file + ": " + door(src, m.start(), open));
        }

        // an overridden getToolTipText is a tooltip door at each of its returns
        m = TOOLTIP_OVERRIDE.matcher(src);
        while (m.find()) {
            int brace = src.indexOf('{', m.end());
            int end = matchingBrace(src, brace);
            if (brace < 0 || end < 0) continue;
            Matcher ret = Pattern.compile("\\breturn\\s+").matcher(src.substring(0, end));
            ret.region(brace, end);
            while (ret.find()) {
                int stop = statementEnd(src, ret.end());
                String expr = src.substring(ret.end(), stop).trim();
                if (expr.startsWith("super.getToolTipText(") || safe(expr, "UiText.tooltip", "UiText.inert")) continue;
                out.add(file + ": getToolTipText returns " + expr.replaceAll("\\s+", " "));
            }
        }

        // a renderer replaces the one plainCells wrapped, so setting one is a door of its own
        m = RENDERER.matcher(src);
        while (m.find()) {
            int open = m.end() - 1;
            List<String> args = args(src, open);
            if (args.isEmpty()) continue;
            String r = args.get(args.size() - 1).trim();
            if (isCallOf(r, "UiText.plainRenderer") || isCallOf(r, "UiText.plain")) continue;
            if (fileHasPlainThis) continue;                    // the renderer class flags itself
            if (r.matches("[\\w$]+")) {
                Declaration d = declaration(src, r, m.start());
                if (d != null && PLAIN_INIT.matcher(d.initializer).find()) continue;
                if (Pattern.compile("UiText\\s*\\.\\s*plain\\s*\\(\\s*" + Pattern.quote(r) + "\\s*\\)").matcher(src).find()) continue;
            }
            String receiver = m.group(1);
            if (receiver != null && Pattern.compile("UiText\\s*\\.\\s*plainCells\\s*\\(\\s*" + Pattern.quote(receiver) + "\\s*\\)")
                    .matcher(src.substring(open)).find()) continue;
            out.add(file + ": " + door(src, m.start(), open));
        }

        m = CELLS.matcher(src);
        while (m.find()) {
            int open = m.end() - 1;
            List<String> args = args(src, open);
            if (!args.isEmpty() && args.stream().allMatch(ForeignTextIsPlainTest::constantArray)) continue;
            if (insideUiPlainCells(src, m.start())) continue;
            String var = assignedTo(src, m.start());
            if (var != null && Pattern.compile("UiText\\s*\\.\\s*plainCells\\s*\\(\\s*" + Pattern.quote(var) + "\\s*\\)")
                    .matcher(src).find()) continue;
            if (var != null && Pattern.compile(Pattern.quote(var) + "\\s*\\.\\s*set(?:Cell)?Renderer\\s*\\(\\s*UiText\\s*\\.\\s*plainRenderer")
                    .matcher(src).find()) continue;
            out.add(file + ": " + door(src, m.start(), open));
        }
        return out;
    }

    // ---- expressions -----------------------------------------------------------------------------------------------

    /** A constant, or the analyser's own markup with every computed piece escaped, or already inert. */
    static boolean safeText(String expr) {
        return safe(expr, "UiText.inert");
    }

    static boolean safe(String expr, String... wrappers) {
        String e = unwrap(expr.trim());
        if (e.isEmpty() || constant(e)) return true;
        for (String w : wrappers) if (isCallOf(e, w)) return true;
        int q = topLevel(e, '?');
        if (q >= 0) {
            int c = matchingColon(e, q);
            if (c > 0) return safe(e.substring(q + 1, c), wrappers) && safe(e.substring(c + 1), wrappers);
        }
        return ownHtml(e);
    }

    /** {@code "<html>…" + UiText.escape(x) + "…"}: our markup, their words escaped. */
    static boolean ownHtml(String e) {
        List<String> pieces = splitTop(e, '+');
        if (pieces.isEmpty() || !pieces.get(0).trim().toLowerCase(Locale.ROOT).startsWith("\"<html")) return false;
        for (String p : pieces) {
            String t = unwrap(p.trim());
            if (constant(t) || isCallOf(t, "UiText.escape")) continue;
            int q = topLevel(t, '?');
            if (q >= 0) {
                int c = matchingColon(t, q);
                if (c > 0 && htmlPiece(t.substring(q + 1, c)) && htmlPiece(t.substring(c + 1))) continue;
            }
            return false;
        }
        return true;
    }

    private static boolean htmlPiece(String p) {
        String t = unwrap(p.trim());
        if (constant(t) || isCallOf(t, "UiText.escape")) return true;
        for (String piece : splitTop(t, '+')) {
            String s = unwrap(piece.trim());
            if (!constant(s) && !isCallOf(s, "UiText.escape")) return false;
        }
        return true;
    }

    /** String/char/number literals, null, ALL_CAPS constants, and + / ternaries between them. */
    static boolean constant(String expr) {
        String e = unwrap(expr.trim());
        if (e.isEmpty()) return false;
        int q = topLevel(e, '?');
        if (q >= 0) {
            int c = matchingColon(e, q);
            return c > 0 && constant(e.substring(q + 1, c)) && constant(e.substring(c + 1));
        }
        List<String> pieces = splitTop(e, '+');
        if (pieces.size() > 1) return pieces.stream().allMatch(ForeignTextIsPlainTest::constant);
        if (e.equals("null")) return true;
        if (e.startsWith("\"") && e.endsWith("\"") && e.length() >= 2 && literalEndsAt(e, 0) == e.length() - 1) return true;
        if (e.startsWith("'") && e.endsWith("'")) return true;
        if (e.matches("-?\\d[\\d_]*(\\.\\d+)?[LlFfDd]?")) return true;
        return e.replaceAll("\\s*\\.\\s*", ".").matches("(?:[\\w$]+\\.)*[A-Z][A-Z0-9_]+");
    }

    private static boolean constantArray(String arg) {
        String a = arg.trim();
        if (constant(a)) return true;
        Matcher m = Pattern.compile("^new\\s+String\\s*\\[\\s*]\\s*\\{(.*)}$", Pattern.DOTALL).matcher(a);
        return m.find() && splitTop(m.group(1), ',').stream().allMatch(ForeignTextIsPlainTest::constant);
    }

    private static boolean isCallOf(String e, String fn) {
        String compact = e.replaceAll("^\\s+", "");
        if (!compact.replaceAll("\\s", "").startsWith(fn + "(")) return false;
        int open = compact.indexOf('(');
        int close = matchingClose(compact, open);
        return close == compact.length() - 1;
    }

    private static String unwrap(String e) {
        String t = e.trim();
        while (t.startsWith("(") && matchingClose(t, 0) == t.length() - 1) t = t.substring(1, t.length() - 1).trim();
        // a cast such as (String) x
        Matcher cast = Pattern.compile("^\\((?:[\\w$.]+)\\)\\s*(.+)$", Pattern.DOTALL).matcher(t);
        if (cast.find() && Character.isUpperCase(t.charAt(1))) return unwrap(cast.group(1));
        return t;
    }

    // ---- declarations ----------------------------------------------------------------------------------------------

    private record Declaration(String type, String initializer) { }

    /** The nearest declaration of {@code name} before {@code at}, else the first after it (a field declared below). */
    private static Declaration declaration(String src, String name, int at) {
        Pattern p = Pattern.compile("(?<![\\w$.])(?:final\\s+)?((?:[\\w$]+\\.)*([A-Z][\\w$]*))\\s*(?:<[^;=(){}]*>)?\\s*(?:\\[\\s*]\\s*)*\\s+"
                + Pattern.quote(name) + "\\s*(=|;|,|\\))");
        Matcher m = p.matcher(src);
        Declaration before = null, after = null;
        while (m.find()) {
            String type = m.group(2);
            if (type.equals("return") || type.equals("new")) continue;
            String init = "";
            if (m.group(3).equals("=")) {
                int end = statementEnd(src, m.end());
                init = src.substring(m.end(), end);
            }
            Declaration d = new Declaration(type, init);
            if (m.start() < at) before = d;
            else if (after == null) after = d;
        }
        Matcher v = Pattern.compile("(?<![\\w$.])var\\s+" + Pattern.quote(name) + "\\s*=\\s*").matcher(src);
        while (v.find()) {
            if (v.start() > at) break;
            int end = statementEnd(src, v.end());
            String init = src.substring(v.end(), end);
            Matcher t = Pattern.compile("new\\s+(?:[\\w$]+\\.)*([A-Z][\\w$]*)").matcher(init);
            String type = PLAIN_INIT.matcher(init).find() ? "JLabel" : t.find() ? t.group(1) : "?";
            before = new Declaration(type, init);
        }
        return before != null ? before : after;
    }

    private static boolean isNonStringVariable(String src, String expr, int at) {
        String e = expr.trim();
        if (!e.matches("[\\w$]+")) return false;
        Declaration d = declaration(src, e, at);
        return d != null && !d.type.equals("String") && !d.type.equals("Object") && !d.type.equals("CharSequence");
    }

    /** A message that is a Swing component (a form, a scroll pane): Swing adds it, it does not parse it. */
    private static boolean isComponent(String src, String expr, int at) {
        String e = unwrap(expr.trim());
        if (e.startsWith("new ")) return !e.startsWith("new String");
        if (!e.matches("[\\w$]+")) return false;
        Declaration d = declaration(src, e, at);
        return d != null && !d.type.equals("String") && !d.type.equals("Object") && !d.type.equals("CharSequence")
                && !d.type.equals("StringBuilder");
    }

    /** {@code Object[] shown = UiText.inert(…)}: the options were made inert where they were built. */
    private static boolean isInertVariable(String src, String expr, int at) {
        String e = expr.trim();
        Matcher index = Pattern.compile("^([\\w$]+)\\s*\\[.*]$").matcher(e);
        if (index.find()) e = index.group(1);
        if (!e.matches("[\\w$]+")) return false;
        Declaration d = declaration(src, e, at);
        if (d == null) return false;
        if (d.initializer.contains("UiText.inert(")) return true;
        // Object[] options = {"Open", "Cancel"}: the analyser's own words
        Matcher brace = Pattern.compile("^(?:new\\s+\\w+\\s*\\[\\s*]\\s*)?\\{(.*)}$", Pattern.DOTALL).matcher(d.initializer.trim());
        return brace.find() && splitTop(brace.group(1), ',').stream().allMatch(ForeignTextIsPlainTest::constant);
    }

    private static boolean isDeclaration(String src, int nameAt) {
        String before = src.substring(Math.max(0, nameAt - 30), nameAt);
        return before.matches("(?s).*\\b(void|String)\\s+$");
    }

    private static boolean insideUiPlain(String src, int at) {
        return src.substring(Math.max(0, at - 30), at).replaceAll("\\s", "").endsWith("UiText.plain(");
    }

    private static boolean insideUiPlainCells(String src, int at) {
        return src.substring(Math.max(0, at - 30), at).replaceAll("\\s", "").endsWith("UiText.plainCells(");
    }

    /** {@code name = new JList…} or {@code Type name = new JList…}: the variable a cell container is assigned to. */
    private static String assignedTo(String src, int at) {
        String before = src.substring(Math.max(0, at - 160), at);
        Matcher m = Pattern.compile("([\\w$]+)\\s*=\\s*$").matcher(before);
        return m.find() ? m.group(1) : null;
    }

    // ---- lexical helpers -------------------------------------------------------------------------------------------

    /** Comments become spaces (strings and line numbers are kept). */
    static String stripComments(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                int end = s.startsWith("\"\"\"", i) ? s.indexOf("\"\"\"", i + 3) + 2 : literalEndsAt(s, i);
                if (end < i) end = s.length() - 1;
                out.append(s, i, end + 1);
                i = end + 1;
            } else if (s.startsWith("//", i)) {
                int end = s.indexOf('\n', i);
                if (end < 0) end = s.length();
                out.append(" ".repeat(end - i));
                i = end;
            } else if (s.startsWith("/*", i)) {
                int end = s.indexOf("*/", i + 2);
                end = end < 0 ? s.length() : end + 2;
                for (int k = i; k < end; k++) out.append(s.charAt(k) == '\n' ? '\n' : ' ');
                i = end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int literalEndsAt(String s, int start) {
        char q = s.charAt(start);
        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == q) return i;
        }
        return -1;
    }

    /** The top-level comma-separated arguments of the call whose '(' is at {@code open}. */
    static List<String> args(String s, int open) {
        int close = matchingClose(s, open);
        if (close < 0) return List.of();
        String inner = s.substring(open + 1, close);
        if (inner.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String a : splitTop(inner, ',')) out.add(a.trim().replaceAll("\\s+", " "));
        return out;
    }

    /** Splits on {@code sep} outside strings and brackets ({@code <} is not a bracket: it is also less-than). */
    static List<String> splitTop(String s, char sep) {
        List<String> out = new ArrayList<>();
        int depth = 0, from = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') { int e = literalEndsAt(s, i); if (e < 0) break; i = e; continue; }
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            else if (c == sep && depth == 0) {
                if (sep == '+' && (i + 1 < s.length() && s.charAt(i + 1) == '+' || i > 0 && s.charAt(i - 1) == '+')) continue;
                out.add(s.substring(from, i));
                from = i + 1;
            } else if (sep == '+' && c == '?' && depth == 0) {
                return List.of(s);                    // a top-level ternary binds looser than +: not a concatenation
            }
        }
        out.add(s.substring(from));
        return out;
    }

    private static int topLevel(String s, char target) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') { int e = literalEndsAt(s, i); if (e < 0) return -1; i = e; continue; }
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            else if (c == target && depth == 0) {
                if (c == '?' && i + 1 < s.length() && (s.charAt(i + 1) == '.' || s.charAt(i + 1) == '?')) continue;
                return i;
            }
        }
        return -1;
    }

    private static int matchingColon(String s, int q) {
        int depth = 0, nested = 0;
        for (int i = q + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') { int e = literalEndsAt(s, i); if (e < 0) return -1; i = e; continue; }
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            else if (depth == 0 && c == '?') nested++;
            else if (depth == 0 && c == ':' && !(i + 1 < s.length() && s.charAt(i + 1) == ':') && !(i > 0 && s.charAt(i - 1) == ':')) {
                if (nested == 0) return i;
                nested--;
            }
        }
        return -1;
    }

    static int matchingClose(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') { int e = literalEndsAt(s, i); if (e < 0) return -1; i = e; continue; }
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i;
        }
        return -1;
    }

    private static int matchingBrace(String s, int open) {
        if (open < 0) return -1;
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') { int e = literalEndsAt(s, i); if (e < 0) return -1; i = e; continue; }
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i;
        }
        return -1;
    }

    private static int matchingOpen(String s, int close) {
        int depth = 0;
        for (int i = close; i >= 0; i--) {
            char c = s.charAt(i);
            if (c == ')') depth++;
            else if (c == '(' && --depth == 0) return i;
        }
        return -1;
    }

    private static int prevNonSpace(String s, int i) {
        while (i >= 0 && Character.isWhitespace(s.charAt(i))) i--;
        return i;
    }

    private static int statementEnd(String s, int from) {
        int depth = 0;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') { int e = literalEndsAt(s, i); if (e < 0) return s.length(); i = e; continue; }
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') { if (depth == 0) return i; depth--; }
            else if ((c == ';' || c == ',') && depth == 0) return i;
        }
        return s.length();
    }

    private static int receiverStart(String s, int at) {
        int i = at - 1;
        if (i >= 0 && s.charAt(i) == '.') {
            i--;
            while (i >= 0 && (Character.isJavaIdentifierPart(s.charAt(i)) || s.charAt(i) == '.')) i--;
            return i + 1;
        }
        return at;
    }

    /** The door's text, normalised, from {@code start} through its closing paren. */
    private static String door(String s, int start, int open) {
        int close = matchingClose(s, open);
        return s.substring(start, close < 0 ? Math.min(s.length(), open + 80) : close + 1).replaceAll("\\s+", " ").trim();
    }
}
