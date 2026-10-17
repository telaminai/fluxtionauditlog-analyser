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
        REVIEWED.put("ConfigPanel.java: createTitledBorder(title)",
                "group's parameter (M74 UX, Settings ▸ Assistant by profile): every caller passes a literal heading");
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
            "JOptionPane\\s*\\.\\s*(showMessageDialog|showConfirmDialog|showOptionDialog|showInputDialog)\\s*\\("
                    + "|new\\s+(?:javax\\.swing\\.)?JOptionPane\\s*\\(");
    /** An Action's name is a button's or menu item's text; its descriptions are tooltips. */
    private static final Pattern ACTION = Pattern.compile("new\\s+(?:javax\\.swing\\.)?AbstractAction\\s*\\(");
    private static final Pattern PUT_VALUE = Pattern.compile("putValue\\s*\\(\\s*(?:(?:javax\\.swing\\.)?Action\\s*\\.\\s*)?"
            + "(?:NAME|SHORT_DESCRIPTION|LONG_DESCRIPTION)\\s*,");
    private static final Pattern TOOLTIP_PROPERTY = Pattern.compile("putClientProperty\\s*\\(\\s*(?:(?:(?:javax\\.swing\\.)?"
            + "JComponent\\s*\\.\\s*)?TOOL_TIP_TEXT_KEY|\"ToolTipText\")\\s*,");
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

    /** Review of 7f825492: each shape below returned no door before the fix, and must now return one. */
    @Test
    @DisplayName("SF-2 witness: names resolve where they are used, and every door the review found is scanned")
    void theRuleSeesTheShapesTheReviewFound() {
        // the live hole: a for-each String shadowing an earlier non-String of the same name (MainFrame.fillRecent)
        String shadowed = "class X { javax.swing.Icon p; void f(List<String> paths) { for (String p : paths) {"
                + " JMenuItem item = new JMenuItem(p); } } }";
        assertEquals(1, doors("X.java", shadowed).size(), "for-each");
        assertEquals(0, doors("X.java", shadowed.replace("new JMenuItem(p)", "UiText.menuItem(p)")).size());
        assertEquals(1, doors("X.java", "class X { javax.swing.Icon p; void f() { paths.forEach(p -> menu.add(new JMenuItem(p))); } }")
                .size(), "a lambda parameter");
        assertEquals(1, doors("X.java", "class X { void f() { var label = new JLabel(); label.setText(name); } }").size(), "var");
        assertEquals(0, doors("X.java", "class X { void f() { var label = UiText.label(\"\"); label.setText(name); } }").size());
        // an anonymous class sees the locals of the method it is written in
        assertEquals(1, doors("X.java", "class X { void f() { JLabel l = new JLabel(); menu.addMenuListener(new MenuListener() {"
                + " public void menuSelected(MenuEvent e) { l.setText(name); } }); } }").size(), "a captured local");
        // an option-pane message held in a var, an Object[] message, and a constructed JOptionPane
        assertEquals(1, doors("X.java", "class X { void f() { var msg = \"Delete \" + name; JOptionPane.showMessageDialog(this, msg); } }").size());
        assertEquals(0, doors("X.java", "class X { void f() { var msg = UiText.inert(\"Delete \" + name); JOptionPane.showMessageDialog(this, msg); } }").size());
        assertEquals(1, doors("X.java", "class X { void f() { JOptionPane.showMessageDialog(this, new Object[]{\"Delete\", name}); } }").size());
        assertEquals(0, doors("X.java", "class X { void f() { JOptionPane.showMessageDialog(this, new Object[]{\"Delete\", UiText.inert(name), new JPanel()}); } }").size());
        assertEquals(1, doors("X.java", "class X { void f() { var n = new JOptionPane(\"Verified \" + id, 1); n.createDialog(this, \"t\"); } }").size());
        assertEquals(0, doors("X.java", "class X { void f() { var n = new JOptionPane(UiText.inert(\"Verified \" + id), 1); } }").size());
        // an Action's name is a button's text, its description a tooltip
        assertEquals(1, doors("X.java", "class X { Action a = new AbstractAction(name) { }; }").size());
        assertEquals(0, doors("X.java", "class X { Action a = new AbstractAction(\"Open\") { }; }").size());
        assertEquals(1, doors("X.java", "class X { void f() { a.putValue(Action.NAME, name); } }").size());
        assertEquals(1, doors("X.java", "class X { void f() { a.putValue(Action.SHORT_DESCRIPTION, path); } }").size());
        assertEquals(0, doors("X.java", "class X { void f() { a.putValue(Action.SHORT_DESCRIPTION, UiText.tooltip(path)); } }").size());
        // UiText.plain(label) in one method does not make another method's label plain
        assertEquals(1, doors("X.java", "class X { void a() { JLabel label = new JLabel(); UiText.plain(label); }"
                + " void b() { JLabel label = new JLabel(); label.setText(name); } }").size());
        // UiText.plain(this) in one class does not cover another class's own text, nor a renderer it does not create
        assertEquals(1, doors("X.java", "class A extends JLabel { A() { UiText.plain(this); } }"
                + " class B extends JLabel { void f() { setText(name); } }").size());
        assertEquals(1, doors("X.java", "class A extends JLabel { A() { UiText.plain(this); } }"
                + " class B { void g() { list.setCellRenderer(new DefaultListCellRenderer()); } }").size());
        assertEquals(0, doors("X.java", "class B { void g() { list.setCellRenderer(new DefaultListCellRenderer() {"
                + " { UiText.plain(this); } }); } }").size());
        // a tab's tooltip, and a tooltip set as a client property
        assertEquals(1, doors("X.java", "class X { void f() { tabs.addTab(\"Walks\", null, panel, path); } }").size());
        assertEquals(0, doors("X.java", "class X { void f() { tabs.addTab(\"Walks\", null, panel, UiText.tooltip(path)); } }").size());
        assertEquals(1, doors("X.java", "class X { void f() { c.putClientProperty(JComponent.TOOL_TIP_TEXT_KEY, path); } }").size());
        // a reviewed parameter door is only safe while every call passes a literal
        assertEquals(1, doors("ConfigPanel.java", "class X { void f() { p.add(mutedNote(help)); } }").size());
        assertEquals(0, doors("ConfigPanel.java", "class X { void f() { p.add(mutedNote(\"<b>help</b>\")); } }").size());
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

    /**
     * Methods whose String parameter becomes a door inside them, so every CALL must pass a constant — the reviewed
     * door inside the method is only safe while that holds. A call from inside another listed method may pass that
     * method's own parameter on.
     */
    static final Map<String, List<String>> PARAMETER_DOORS = Map.of("ConfigPanel.java", List.of("mutedNote", "form"));

    /** Every unsafe door in {@code src}, as {@code "File.java: <door text>"}. */
    static List<String> doors(String file, String raw) {
        Source src = new Source(stripComments(raw));
        String s = src.text;
        List<String> out = new ArrayList<>();

        Matcher m = CONSTRUCT.matcher(s);
        while (m.find()) {
            int open = m.end() - 1;
            List<String> args = args(s, open);
            if (args.isEmpty()) continue;
            String text = args.get(0);
            if (text.startsWith("new ") || src.isNonStringVariable(text, m.start())) continue;   // an Icon or Action
            if (!safeText(text) && !src.isInertVariable(text, m.start())) out.add(file + ": " + door(s, m.start(), open));
        }

        m = ACTION.matcher(s);
        while (m.find()) {
            int open = m.end() - 1;
            List<String> args = args(s, open);
            if (!args.isEmpty() && !safeText(args.get(0)) && !src.isInertVariable(args.get(0), m.start())) {
                out.add(file + ": " + door(s, m.start(), open));
            }
        }

        m = PUT_VALUE.matcher(s);
        while (m.find()) {
            int open = s.indexOf('(', m.start());
            List<String> args = args(s, open);
            if (args.size() > 1 && !safe(args.get(1), "UiText.tooltip", "UiText.inert")) {
                out.add(file + ": " + door(s, receiverStart(s, m.start()), open));
            }
        }

        m = TOOLTIP_PROPERTY.matcher(s);
        while (m.find()) {
            int open = s.indexOf('(', m.start());
            List<String> args = args(s, open);
            if (args.size() > 1 && !safe(args.get(1), "UiText.tooltip", "UiText.inert")) {
                out.add(file + ": " + door(s, receiverStart(s, m.start()), open));
            }
        }

        m = RECEIVER_CALL.matcher(s);
        while (m.find()) {
            String chain = m.group(1).replaceAll("\\s", "");
            String call = m.group(2);
            int open = m.end() - 1;
            if (isDeclaration(s, m.start(2))) continue;                       // `public void setText(String s)`
            List<String> args = args(s, open);
            int start = chain.isEmpty() ? m.start(2) : m.start(1);
            // addTab(title, icon, component, tip) and insertTab(title, icon, component, tip, index): the tip is a tooltip
            if ((call.equals("addTab") || call.equals("insertTab")) && args.size() >= 4
                    && !safe(args.get(3), "UiText.tooltip", "UiText.inert")) {
                out.add(file + ": " + door(s, start, open) + "   [tab tooltip]");
            }
            String text = switch (call) {
                case "setTitleAt" -> args.size() > 1 ? args.get(1) : "";
                default -> args.isEmpty() ? "" : args.get(0);
            };
            if (safeText(text)) continue;
            if (chain.isEmpty()) {
                // `UiText.plain(x).setText(…)`, a receiver that is a call: look back past the closing paren
                int dot = prevNonSpace(s, m.start(2) - 1);
                if (dot >= 0 && s.charAt(dot) == '.') {
                    int close = prevNonSpace(s, dot - 1);
                    if (close >= 0 && s.charAt(close) == ')') {
                        int openCall = matchingOpen(s, close);
                        String before = s.substring(Math.max(0, openCall - 40), openCall).replaceAll("\\s", "");
                        if (!before.endsWith("UiText.plain")) out.add(file + ": " + door(s, start, open));
                        continue;
                    }
                }
                // receiver-less: a component subclass setting its own text — that CLASS must flag itself
                if (!src.classPlainsItself(m.start())) out.add(file + ": " + door(s, start, open));
                continue;
            }
            String receiver = chain.substring(0, chain.length() - 1);
            String last = receiver.substring(receiver.lastIndexOf('.') + 1);
            if (last.equals("this")) {
                if (!src.classPlainsItself(m.start())) out.add(file + ": " + door(s, start, open));
                continue;
            }
            Declaration d = receiver.contains(".") && !receiver.startsWith("this.") ? src.member(last) : src.declaration(last, m.start());
            if (d == null) {
                out.add(file + ": " + door(s, start, open) + "   [receiver '" + last + "' not resolved]");
                continue;
            }
            if (!HTML_TYPES.contains(d.type)) continue;                          // a text component, a model, a filter
            if (src.isPlain(last, d)) continue;
            out.add(file + ": " + door(s, start, open));
        }

        m = TOOLTIP.matcher(s);
        while (m.find()) {
            if (isDeclaration(s, m.start(1))) continue;
            int open = m.end() - 1;
            List<String> args = args(s, open);
            String text = m.group(1).equals("setToolTipTextAt") ? (args.size() > 1 ? args.get(1) : "")
                    : (args.isEmpty() ? "" : args.get(0));
            if (!safe(text, "UiText.tooltip", "UiText.inert") && !src.isInertVariable(text, m.start())) {
                out.add(file + ": " + door(s, receiverStart(s, m.start()), open));
            }
        }

        // JOptionPane.show…(parent, message, …) and new JOptionPane(message, type, option, icon, options, initial)
        m = OPTION_PANE.matcher(s);
        while (m.find()) {
            int open = m.end() - 1;
            List<String> args = args(s, open);
            boolean constructed = m.group(1) == null;
            int at = constructed ? 0 : 1;
            if (args.size() <= at) continue;
            if (!src.messageIsSafe(args.get(at), m.start())) {
                out.add(file + ": " + door(s, m.start(), open));
                continue;
            }
            // the options of showOptionDialog (7th) and the selection values of showInputDialog (6th) become buttons
            // and combo-box cells; their initial values (8th / 7th) are shown too. new JOptionPane: 5th and 6th.
            List<String> shown = new ArrayList<>();
            if (constructed && args.size() >= 5) shown.addAll(args.subList(4, Math.min(6, args.size())));
            if ("showOptionDialog".equals(m.group(1)) && args.size() >= 7) shown.addAll(args.subList(6, Math.min(8, args.size())));
            // with no selection values the initial value is the text field's content, which is not HTML
            if ("showInputDialog".equals(m.group(1)) && args.size() >= 6 && !args.get(5).equals("null")) {
                shown.addAll(args.subList(5, Math.min(7, args.size())));
            }
            for (String o : shown) {
                if (!safe(o, "UiText.inert") && !src.isInertVariable(o, m.start())) {
                    out.add(file + ": " + door(s, m.start(), open) + "   [options]");
                    break;
                }
            }
        }

        m = TITLED.matcher(s);
        while (m.find()) {
            int open = m.end() - 1;
            List<String> args = args(s, open);
            if (args.isEmpty()) continue;
            String title = args.size() == 1 ? args.get(0) : args.get(1);
            if (args.size() > 1 && m.group().startsWith("createTitledBorder") && !args.get(0).contains("Border")
                    && !args.get(0).equals("null")) title = args.get(0);
            if (!safe(title, "UiText.inert")) out.add(file + ": " + door(s, m.start(), open));
        }

        // an overridden getToolTipText is a tooltip door at each of its returns
        m = TOOLTIP_OVERRIDE.matcher(s);
        while (m.find()) {
            int brace = s.indexOf('{', m.end());
            int end = matchingBrace(s, brace);
            if (brace < 0 || end < 0) continue;
            Matcher ret = Pattern.compile("\\breturn\\s+").matcher(s.substring(0, end));
            ret.region(brace, end);
            while (ret.find()) {
                int stop = statementEnd(s, ret.end());
                String expr = s.substring(ret.end(), stop).trim();
                if (expr.startsWith("super.getToolTipText(") || safe(expr, "UiText.tooltip", "UiText.inert")) continue;
                out.add(file + ": getToolTipText returns " + expr.replaceAll("\\s+", " "));
            }
        }

        // a renderer replaces the one plainCells wrapped, so setting one is a door of its own
        m = RENDERER.matcher(s);
        while (m.find()) {
            int open = m.end() - 1;
            List<String> args = args(s, open);
            if (args.isEmpty()) continue;
            String r = args.get(args.size() - 1).trim();
            if (isCallOf(r, "UiText.plainRenderer") || isCallOf(r, "UiText.plain")) continue;
            if (r.matches("[\\w$]+")) {
                Declaration d = src.declaration(r, m.start());
                if (d != null && src.isPlain(r, d)) continue;
            }
            Matcher created = Pattern.compile("^new\\s+(?:[\\w$]+\\.)*([\\w$]+)").matcher(r);
            if (created.find() && src.createdClassPlainsItself(created.group(1), lastArgStart(s, open))) continue;
            String receiver = m.group(1);
            if (receiver != null && Pattern.compile("UiText\\s*\\.\\s*plainCells\\s*\\(\\s*" + Pattern.quote(receiver) + "\\s*\\)")
                    .matcher(s.substring(open)).find()) continue;
            out.add(file + ": " + door(s, m.start(), open));
        }

        m = CELLS.matcher(s);
        while (m.find()) {
            int open = m.end() - 1;
            List<String> args = args(s, open);
            if (!args.isEmpty() && args.stream().allMatch(ForeignTextIsPlainTest::constantArray)) continue;
            if (insideUiPlainCells(s, m.start())) continue;
            String var = assignedTo(s, m.start());
            if (var != null && Pattern.compile("UiText\\s*\\.\\s*plainCells\\s*\\(\\s*" + Pattern.quote(var) + "\\s*\\)")
                    .matcher(s).find()) continue;
            if (var != null && Pattern.compile(Pattern.quote(var) + "\\s*\\.\\s*set(?:Cell)?Renderer\\s*\\(\\s*UiText\\s*\\.\\s*plainRenderer")
                    .matcher(s).find()) continue;
            out.add(file + ": " + door(s, m.start(), open));
        }

        for (String method : PARAMETER_DOORS.getOrDefault(file, List.of())) {
            m = Pattern.compile("(?<![\\w$.])" + Pattern.quote(method) + "\\s*\\(").matcher(s);
            while (m.find()) {
                if (src.isMethodHeader(m.start())) continue;
                int open = m.end() - 1;
                List<String> args = args(s, open);
                if (args.isEmpty() || constant(args.get(0))) continue;
                Method enclosing = src.enclosingMethod(m.start());
                if (enclosing != null && PARAMETER_DOORS.get(file).contains(enclosing.name)
                        && enclosing.parameters.contains(args.get(0).trim())) continue;
                out.add(file + ": " + door(s, m.start(), open) + "   [parameter door: pass a literal]");
            }
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

    // ---- declarations, scoped ----------------------------------------------------------------------------------------

    /** A declaration: its type ("?" when it cannot be told), initializer, position, and the range it is visible in. */
    record Declaration(String type, String initializer, int at, int scopeOpen, int scopeClose) { }

    /** A brace block: a class body (named, anonymous, enum, record) or any other block. */
    record Block(int open, int close, boolean isClass) {
        boolean contains(int at) { return open < at && at < close; }
    }

    /** A method or lambda body directly inside a class, with its name and parameter names (empty for a lambda). */
    record Method(String name, List<String> parameters, int headerStart, Block body) { }

    /** One file's text and its block structure, so a name is resolved where it is used — not anywhere in the file. */
    static final class Source {
        final String text;
        final List<Block> blocks = new ArrayList<>();

        Source(String text) {
            this.text = text;
            java.util.ArrayDeque<Integer> stack = new java.util.ArrayDeque<>();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '"' || c == '\'') {
                    int e = text.startsWith("\"\"\"", i) ? text.indexOf("\"\"\"", i + 3) + 2 : literalEndsAt(text, i);
                    if (e < i) break;
                    i = e;
                } else if (c == '{') stack.push(i);
                else if (c == '}' && !stack.isEmpty()) {
                    int open = stack.pop();
                    blocks.add(new Block(open, i, isClassHeader(header(open))));
                }
            }
            blocks.sort(java.util.Comparator.comparingInt(Block::open));
        }

        /** The text between the previous statement boundary and {@code open}. */
        String header(int open) {
            int from = open - 1;
            int depth = 0;
            while (from >= 0) {
                char c = text.charAt(from);
                if (c == ')') depth++;
                else if (c == '(') depth--;
                else if (depth == 0 && (c == ';' || c == '{' || c == '}')) break;
                from--;
            }
            return text.substring(from + 1, open);
        }

        private static final Pattern CLASS_HEADER = Pattern.compile(
                "(?s)(?:.*\\b(?:class|interface|enum)\\s+[\\w$]+[^{};]*|.*\\brecord\\s+[\\w$]+\\s*(?:<[^>]*>)?\\s*\\(.*"
                        + "|.*\\bnew\\s+[\\w$.]+\\s*(?:<[^;{}]*>)?\\s*\\((?:[^()]|\\((?:[^()]|\\([^()]*\\))*\\))*\\)\\s*)$");

        static boolean isClassHeader(String header) {
            return CLASS_HEADER.matcher(header).matches();
        }

        Block innermost(int at) {
            Block best = null;
            for (Block b : blocks) if (b.contains(at) && (best == null || b.open > best.open)) best = b;
            return best;
        }

        /** The innermost class body around {@code at}; a pseudo-block for the whole file when there is none. */
        Block innermostClass(int at) {
            Block best = null;
            for (Block b : blocks) if (b.isClass && b.contains(at) && (best == null || b.open > best.open)) best = b;
            return best != null ? best : new Block(-1, text.length(), true);
        }

        /** The method (or lambda) body directly inside the innermost class around {@code at}, or null at field level. */
        Method enclosingMethod(int at) {
            return enclosingMethodIn(innermostClass(at), at);
        }

        /** The outermost non-class block inside {@code cls} that holds {@code at}: a method, constructor or lambda. */
        Method enclosingMethodIn(Block cls, int at) {
            Block body = null;
            for (Block b : blocks) {
                if (b.isClass || !b.contains(at) || b.open <= cls.open || b.close >= cls.close) continue;
                if (body == null || b.open < body.open) body = b;
            }
            if (body == null) return null;
            String header = header(body.open);
            int headerStart = body.open - header.length();
            Matcher sig = Pattern.compile("(?s)([\\w$]+)\\s*\\(([^()]*(?:\\([^()]*\\)[^()]*)*)\\)\\s*(?:throws[^{]*)?$").matcher(header);
            if (sig.find() && !List.of("if", "for", "while", "switch", "catch", "synchronized", "try").contains(sig.group(1))) {
                List<String> params = new ArrayList<>();
                for (String p : splitTop(sig.group(2), ',')) {
                    String[] words = p.trim().split("\\s+");
                    if (words.length > 0 && !words[words.length - 1].isEmpty()) params.add(words[words.length - 1]);
                }
                return new Method(sig.group(1), params, headerStart, body);
            }
            return new Method("", List.of(), headerStart, body);
        }

        Block outerClass(Block c) {
            Block outer = null;
            for (Block b : blocks) if (b.isClass && b.contains(c.open) && (outer == null || b.open > outer.open)) outer = b;
            return outer != null ? outer : new Block(-1, text.length(), true);
        }

        boolean isMethodHeader(int nameAt) {
            String before = text.substring(Math.max(0, nameAt - 60), nameAt);
            return before.matches("(?s).*\\b(?:void|String|JLabel|JPanel|JComponent|[A-Z][\\w$<>?,\\s]*)\\s+$")
                    && !before.matches("(?s).*\\b(?:return|new|=|\\()\\s*$");
        }

        /**
         * The declaration of {@code name} visible at {@code at}: a local of the enclosing method (a typed or {@code var}
         * local, a for-each variable, a lambda or method parameter), nearest first; else a field of an enclosing class.
         */
        Declaration declaration(String name, int at) {
            // innermost class outward: a local of the method enclosing `at` in that class (an anonymous class sees the
            // locals of the method it is written in), then that class's fields
            for (Block c = innermostClass(at); ; c = outerClass(c)) {
                Method method = enclosingMethodIn(c, at);
                if (method != null) {
                    Declaration local = null;
                    for (Declaration d : candidates(name, method.headerStart, at)) {
                        Block in = innermost(d.at);
                        // a parameter (in the header) or a declaration whose block still holds `at`
                        if (d.at < method.body.open || in == null || in.contains(at)) local = d;
                    }
                    if (local != null) {
                        return new Declaration(local.type, local.initializer, local.at, method.headerStart, method.body.close);
                    }
                }
                Declaration field = fieldOf(c, name);
                if (field != null) return field;
                if (c.open < 0) return null;
            }
        }

        /** {@code name} declared at the class level of {@code c}, wherever in it. */
        Declaration fieldOf(Block c, String name) {
            for (Declaration d : candidates(name, c.open + 1, c.close)) {
                if (innermostClass(d.at).equals(c) && enclosingMethodIn(c, d.at) == null) {
                    return new Declaration(d.type, d.initializer, d.at, c.open, c.close);
                }
            }
            return null;
        }

        /** {@code x.name}: a member, so any class of the file that declares a field {@code name}. */
        Declaration member(String name) {
            for (Block b : blocks) {
                if (!b.isClass) continue;
                Declaration d = fieldOf(b, name);
                if (d != null) return d;
            }
            return fieldOf(new Block(-1, text.length(), true), name);
        }

        /** Every declaration shape of {@code name} in [from, to), in order. */
        private List<Declaration> candidates(String name, int from, int to) {
            List<Declaration> out = new ArrayList<>();
            String region = text.substring(Math.max(0, from), Math.min(to, text.length()));
            int base = Math.max(0, from);
            Matcher typed = Pattern.compile("(?<![\\w$.])(?:final\\s+)?(?:[\\w$]+\\.)*([A-Z][\\w$]*)\\s*(?:<[^;=(){}]*>)?\\s*"
                    + "((?:\\[\\s*]\\s*)*)\\s+" + Pattern.quote(name) + "\\s*(=|;|,|\\)|:(?!:))").matcher(region);
            while (typed.find()) {
                String type = typed.group(1) + (typed.group(2).isBlank() ? "" : "[]");
                String init = typed.group(3).equals("=") ? text.substring(base + typed.end(), statementEnd(text, base + typed.end())) : "";
                out.add(new Declaration(type, init, base + typed.start(), 0, 0));
            }
            Matcher var = Pattern.compile("(?<![\\w$.])var\\s+" + Pattern.quote(name) + "\\s*(=|:)").matcher(region);
            while (var.find()) {
                String init = var.group(1).equals("=") ? text.substring(base + var.end(), statementEnd(text, base + var.end())) : "";
                out.add(new Declaration(var.group(1).equals("=") ? varType(init) : "?", init, base + var.start(), 0, 0));
            }
            // lambda parameters: `name ->` or `(a, name, …) ->` (typed or not)
            Matcher arrow = Pattern.compile("->").matcher(region);
            while (arrow.find()) {
                int a = base + arrow.start();
                int p = prevNonSpace(text, a - 1);
                if (p < 0) continue;
                if (text.charAt(p) == ')') {
                    int o = matchingOpen(text, p);
                    if (o < 0) continue;
                    for (String param : splitTop(text.substring(o + 1, p), ',')) {
                        String[] words = param.trim().split("\\s+");
                        if (words[words.length - 1].equals(name)) {
                            String type = words.length > 1 ? words[words.length - 2].replaceAll("<.*", "") : "?";
                            out.add(new Declaration(type, "", o, 0, 0));
                        }
                    }
                } else {
                    int q = p;
                    while (q >= 0 && Character.isJavaIdentifierPart(text.charAt(q))) q--;
                    if (text.substring(q + 1, p + 1).equals(name)) out.add(new Declaration("?", "", q + 1, 0, 0));
                }
            }
            out.sort(java.util.Comparator.comparingInt(Declaration::at));
            return out;
        }

        /** {@code UiText.plain(name)} (or a factory initializer) within the range the declaration is visible in. */
        boolean isPlain(String name, Declaration d) {
            if (PLAIN_INIT.matcher(d.initializer).find()) return true;
            Matcher m = Pattern.compile("UiText\\s*\\.\\s*plain(?:Cells)?\\s*\\(\\s*" + Pattern.quote(name) + "\\s*\\)").matcher(text);
            while (m.find()) {
                if (m.start() <= d.scopeOpen || m.start() >= d.scopeClose) continue;
                Declaration seen = declaration(name, m.start());
                if (seen != null && seen.at == d.at) return true;
            }
            return false;
        }

        /** {@code UiText.plain(this)} directly in the class whose body holds {@code at} (not in a nested class). */
        boolean classPlainsItself(int at) {
            Block cls = innermostClass(at);
            Matcher m = Pattern.compile("UiText\\s*\\.\\s*plain(?:Cells)?\\s*\\(\\s*this\\s*\\)").matcher(text);
            while (m.find()) if (innermostClass(m.start()).equals(cls)) return true;
            return false;
        }

        /** {@code new X(…)} at {@code at}: an anonymous body or a class X in this file that flags itself. */
        boolean createdClassPlainsItself(String type, int at) {
            int open = at < 0 ? -1 : matchingClose(text, text.indexOf('(', at));
            if (open > 0) {
                int brace = prevNonSpaceForward(text, open + 1);
                if (brace < text.length() && text.charAt(brace) == '{') return classPlainsItself(brace + 1);
            }
            Matcher named = Pattern.compile("\\bclass\\s+" + Pattern.quote(type) + "\\b[^{]*\\{").matcher(text);
            return named.find() && classPlainsItself(named.end());
        }

        boolean isNonStringVariable(String expr, int at) {
            String e = expr.trim();
            if (!e.matches("[\\w$]+")) return false;
            Declaration d = declaration(e, at);
            return d != null && !d.type.equals("?") && !STRINGISH.contains(d.type);
        }

        /** A message Swing adds rather than parses (a form), or text that is constant, inert or our escaped markup. */
        boolean messageIsSafe(String expr, int at) {
            String e = unwrap(expr.trim());
            if (safe(e, "UiText.inert")) return true;
            // an Object[] message: each element is a label of its own
            Matcher array = Pattern.compile("^(?:new\\s+(?:Object|String)\\s*\\[\\s*]\\s*)?\\{(.*)}$", Pattern.DOTALL).matcher(e);
            if (array.find()) {
                for (String element : splitTop(array.group(1), ',')) if (!messageIsSafe(element, at)) return false;
                return true;
            }
            if (e.startsWith("new ")) return !e.matches("(?s)new\\s+(?:String|StringBuilder|Object)\\b.*");
            if (!e.matches("[\\w$]+")) return false;
            Declaration d = declaration(e, at);
            if (d == null) return false;
            if (isInertVariable(e, at)) return true;
            if (d.type.endsWith("[]")) {
                Matcher init = Pattern.compile("^(?:new\\s+\\w+\\s*\\[\\s*]\\s*)?\\{(.*)}$", Pattern.DOTALL).matcher(d.initializer.trim());
                return init.find() && messageIsSafe("{" + init.group(1) + "}", d.at);
            }
            return !d.type.equals("?") && !STRINGISH.contains(d.type);
        }

        /** A variable built inert ({@code UiText.inert(…)}) or from the analyser's own literals. */
        boolean isInertVariable(String expr, int at) {
            String e = expr.trim();
            Matcher index = Pattern.compile("^([\\w$]+)\\s*\\[.*]$").matcher(e);
            if (index.find()) e = index.group(1);
            if (!e.matches("[\\w$]+")) return false;
            Declaration d = declaration(e, at);
            if (d == null) return false;
            String init = d.initializer.trim();
            if (isCallOf(init, "UiText.inert") || isCallOf(init, "UiText.tooltip")) return true;
            Matcher brace = Pattern.compile("^(?:new\\s+\\w+\\s*\\[\\s*]\\s*)?\\{(.*)}$", Pattern.DOTALL).matcher(init);
            if (brace.find()) return splitTop(brace.group(1), ',').stream().allMatch(ForeignTextIsPlainTest::constant);
            return !init.isEmpty() && safe(init, "UiText.inert", "UiText.tooltip") && STRINGISH.contains(d.type);
        }
    }

    private static final Set<String> STRINGISH = Set.of("String", "Object", "CharSequence", "StringBuilder");

    /** The type a {@code var} gets from its initializer, or "?" when that cannot be read off it. */
    static String varType(String init) {
        String t = init.trim();
        Matcher factory = Pattern.compile("^UiText\\s*\\.\\s*(label|button|menuItem|menu|checkBox|radio|checkBoxMenuItem"
                + "|radioMenuItem|plain|plainCells|inert|tooltip|escape)\\s*\\(").matcher(t);
        if (factory.find()) {
            return switch (factory.group(1)) {
                case "label" -> "JLabel";
                case "button" -> "JButton";
                case "menuItem" -> "JMenuItem";
                case "menu" -> "JMenu";
                case "checkBox" -> "JCheckBox";
                case "radio" -> "JRadioButton";
                case "checkBoxMenuItem" -> "JCheckBoxMenuItem";
                case "radioMenuItem" -> "JRadioButtonMenuItem";
                case "inert", "tooltip", "escape" -> "String";
                default -> varType(t.substring(t.indexOf('(') + 1, Math.max(t.indexOf('(') + 1, matchingClose(t, t.indexOf('(')))));
            };
        }
        Matcher cast = Pattern.compile("^\\(\\s*(?:[\\w$]+\\.)*([A-Z][\\w$]*)\\s*\\)").matcher(t);
        if (cast.find()) return cast.group(1);
        Matcher created = Pattern.compile("^new\\s+(?:[\\w$]+\\.)*([A-Z][\\w$]*)\\s*(<[^>]*>)?\\s*(\\[)?").matcher(t);
        if (created.find()) return created.group(1) + (created.group(3) != null ? "[]" : "");
        if (t.startsWith("\"") || t.matches("(?s)String\\s*\\.\\s*(join|format|valueOf)\\s*\\(.*")) return "String";
        for (String piece : splitTop(t, '+')) if (piece.trim().startsWith("\"")) return "String";
        return "?";
    }

    /** Where the last top-level argument of the call whose '(' is at {@code open} begins. */
    private static int lastArgStart(String s, int open) {
        int close = matchingClose(s, open), depth = 0, start = open + 1;
        for (int i = open + 1; i < close; i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') { int e = literalEndsAt(s, i); if (e < 0) break; i = e; continue; }
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            else if (c == ',' && depth == 0) start = i + 1;
        }
        return prevNonSpaceForward(s, start);
    }

    private static int prevNonSpaceForward(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    private static boolean isDeclaration(String src, int nameAt) {
        String before = src.substring(Math.max(0, nameAt - 30), nameAt);
        return before.matches("(?s).*\\b(void|String)\\s+$");
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
