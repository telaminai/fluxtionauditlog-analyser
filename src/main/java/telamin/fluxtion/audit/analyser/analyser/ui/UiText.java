package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButton;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTable;
import javax.swing.ListCellRenderer;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.table.TableCellRenderer;
import java.awt.Component;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text that is somebody else's words, shown as its characters (SF-2).
 *
 * <p>Swing renders any label, button, menu item, tab title, tooltip, list/combo/table cell or option-pane line whose
 * text begins with {@code <html>} as HTML — and an {@code <img src=…>} in it is FETCHED the moment the view is
 * built, not when it is painted (measured: {@code new JLabel(text)} alone sends the request, headless too). A report
 * or walk title, a chart name, a node id, a bundle's note and a recent project's folder name are all authored outside
 * the analyser — by an agent, a bundle's sender or a folder on disk — and nothing in this code had ever said
 * {@code html.disable}.
 *
 * <p>Four tools, because Swing has four doors:
 * <ul>
 *   <li>{@link #plain(JComponent)} — the {@code html.disable} client property, for a label, button, menu item or
 *       tabbed pane whose text is not the analyser's own markup. It must be set BEFORE the text: the factories
 *       ({@link #label}, {@link #button}, …) do that, which is why {@code plain(new JLabel(untrusted))} is the
 *       wrong shape — the constructor has already built the view.</li>
 *   <li>{@link #tooltip(String)} — a tooltip is a fresh {@code JToolTip} that does NOT inherit its owner's
 *       {@code html.disable} (JDK {@code BasicToolTipUI} and FlatLaf's both render the tip text through
 *       {@code BasicHTML.updateRenderer(tip, …)} on the tip itself). So tooltip text is neutralised instead.</li>
 *   <li>{@link #inert(String)} — the same neutralising, for an option-pane message or option. {@code
 *       BasicOptionPaneUI} renders a String message as HTML when it merely CONTAINS {@code <html>} (it cuts out
 *       the {@code <html>…</html>} span and drops the text before it), and each newline-separated line becomes a
 *       {@code JLabel}; the static {@code JOptionPane.show…} methods give no way to set {@code html.disable}.
 *       Also for a WINDOW TITLE ({@code new JDialog(owner, t)}, {@code new JFrame(t)}, {@code setTitle(t)},
 *       {@code createDialog(p, t)}, an option pane's title): FlatLaf draws the title bar itself — the default on
 *       Windows 10/11 — and its title is a {@code JLabel}, so a title that begins with {@code <html>} is rendered.
 *       And for {@code menu.add(String)}: the item it builds does not inherit the menu's {@code html.disable}.</li>
 *   <li>{@link #escape(String)} — for the analyser's OWN {@code <html>} markup that carries a foreign word: the
 *       markup stays, the word is escaped.</li>
 * </ul>
 * Cells: {@link #plainCells(JList)}, {@link #plainCells(JComboBox)} and {@link #plainCells(JTable)}.
 */
public final class UiText {

    /** The client property Swing's {@code BasicHTML.updateRenderer} honours. */
    public static final String HTML_DISABLE = "html.disable";

    /**
     * Inserted between {@code <} and {@code html>}. U+200C is one of the characters Java's font mapping draws as
     * an INVISIBLE glyph ({@code sun.font.CMap.getFormatCharGlyph}: 0x200C–0x200F), so the text still reads as
     * {@code <html>} while no longer satisfying {@code BasicHTML.isHTMLString} (which wants {@code >} at index 5)
     * nor {@code BasicOptionPaneUI}'s {@code contains("<html>")}.
     */
    static final char BREAK = '‌';

    private static final Pattern HTML_TAG = Pattern.compile("<(html>)", Pattern.CASE_INSENSITIVE);

    private UiText() {
    }

    /**
     * Marks a component's text as plain characters: no HTML view is ever built for it. Any view already built
     * is dropped by re-applying the current text — but a constructor that was handed HTML text has already
     * built (and fetched) it, so construct with a factory, or set the text after this call.
     */
    public static <T extends JComponent> T plain(T c) {
        c.putClientProperty(HTML_DISABLE, Boolean.TRUE);
        if (c instanceof JLabel l) BasicHTML.updateRenderer(l, l.getText());
        else if (c instanceof AbstractButton b) BasicHTML.updateRenderer(b, b.getText());
        return c;
    }

    /** A plain label: {@code html.disable} is set before the text. */
    public static JLabel label(String text) {
        JLabel l = plain(new JLabel());
        l.setText(text);
        return l;
    }

    /** A plain label with an alignment, as {@code new JLabel(text, alignment)}. */
    public static JLabel label(String text, int horizontalAlignment) {
        JLabel l = label(text);
        l.setHorizontalAlignment(horizontalAlignment);
        return l;
    }

    public static JButton button(String text) {
        JButton b = plain(new JButton());
        b.setText(text);
        return b;
    }

    public static JMenuItem menuItem(String text) {
        JMenuItem m = plain(new JMenuItem());
        m.setText(text);
        return m;
    }

    public static JMenu menu(String text) {
        JMenu m = plain(new JMenu());
        m.setText(text);
        return m;
    }

    public static JCheckBox checkBox(String text) {
        JCheckBox b = plain(new JCheckBox());
        b.setText(text);
        return b;
    }

    public static JCheckBox checkBox(String text, boolean selected) {
        JCheckBox b = checkBox(text);
        b.setSelected(selected);
        return b;
    }

    public static JRadioButton radio(String text) {
        JRadioButton b = plain(new JRadioButton());
        b.setText(text);
        return b;
    }

    public static JCheckBoxMenuItem checkBoxMenuItem(String text) {
        JCheckBoxMenuItem m = plain(new JCheckBoxMenuItem());
        m.setText(text);
        return m;
    }

    public static JRadioButtonMenuItem radioMenuItem(String text) {
        JRadioButtonMenuItem m = plain(new JRadioButtonMenuItem());
        m.setText(text);
        return m;
    }

    /** True when Swing would render {@code text} as HTML (the {@code BasicHTML.isHTMLString} test). */
    public static boolean rendersAsHtml(String text) {
        return BasicHTML.isHTMLString(text);
    }

    /**
     * {@code text} with every {@code <html>} (any case) broken by an invisible {@link #BREAK}, so neither a label
     * nor an option pane renders it as HTML. Text with no {@code <html>} is returned unchanged (the same instance).
     */
    public static String inert(String text) {
        if (text == null || text.length() < 6 || text.toLowerCase(Locale.ROOT).indexOf("<html>") < 0) return text;
        return HTML_TAG.matcher(text).replaceAll(Matcher.quoteReplacement("<") + BREAK + "$1");
    }

    /** Tooltip text that is not the analyser's own markup: {@link #inert}, since a tooltip ignores its owner's flag. */
    public static String tooltip(String text) {
        return inert(text);
    }

    /** Option labels for {@code JOptionPane.showOptionDialog}/{@code showInputDialog}: each one {@link #inert}. */
    public static Object[] inert(Object[] options) {
        if (options == null) return null;
        Object[] out = new Object[options.length];
        for (int i = 0; i < options.length; i++) {
            out[i] = options[i] instanceof String s ? inert(s) : options[i];
        }
        return out;
    }

    /** {@code text} escaped for the inside of the analyser's own {@code <html>} markup. */
    public static String escape(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(ch);
            }
        }
        return out.toString();
    }

    /**
     * A list's cells as plain text. The list's renderer is a separate component, so the list's own flag would
     * not reach it; the renderer is wrapped so a value whose text would render as HTML reaches it already
     * {@link #inert}, and the component it returns is {@link #plain} as well (a custom renderer that sets its
     * own text is covered by that). The wrapper keeps the renderer it was given, look and feel included.
     */
    public static <E, L extends JList<E>> L plainCells(L list) {
        list.setCellRenderer(plainRenderer(list.getCellRenderer()));
        return list;
    }

    /**
     * A combo box's cells and current value as plain text. Wrapping (rather than flagging the look and feel's
     * renderer) is deliberate: {@code BasicComboBoxUI} REPLACES a {@code UIResource} renderer on every look-and-
     * feel change, which the theme toggle makes, and would take a client property with it.
     */
    public static <E, C extends JComboBox<E>> C plainCells(C combo) {
        combo.setRenderer(plainRenderer(combo.getRenderer()));
        return combo;
    }

    /** A table's default renderers (and header) as plain text. Call after any {@code setDefaultRenderer}. */
    public static <T extends JTable> T plainCells(T table) {
        for (Class<?> type : new Class<?>[]{Object.class, String.class, Number.class, Boolean.class}) {
            TableCellRenderer r = table.getDefaultRenderer(type);
            if (r instanceof JComponent c) plain(c);
        }
        if (table.getTableHeader() != null && table.getTableHeader().getDefaultRenderer() instanceof JComponent h) {
            plain(h);
        }
        return table;
    }

    /** {@link #plainCells(JList)}'s wrapper; idempotent, and transparent to a {@code null} delegate. */
    @SuppressWarnings("unchecked")
    public static <E> ListCellRenderer<E> plainRenderer(ListCellRenderer<? super E> delegate) {
        if (delegate instanceof PlainRenderer<?> already) return (ListCellRenderer<E>) already;
        return new PlainRenderer<>(delegate);
    }

    /** Marker type so wrapping twice is a no-op and the source rule can recognise a plain renderer. */
    static final class PlainRenderer<E> implements ListCellRenderer<E> {
        private final ListCellRenderer<? super E> delegate;

        /** True when the delegate only shows {@code value.toString()}, so handing it an inert String is invisible. */
        private final boolean showsToString;
        /**
         * The look and feel the delegate's UI was last updated for. A list or combo updates its renderer's UI on a theme
         * change only when the renderer IS a Component, which this wrapper is not — so it does it for the delegate.
         */
        private javax.swing.LookAndFeel updatedFor = javax.swing.UIManager.getLookAndFeel();

        /** The wrapped renderer (tests). */
        ListCellRenderer<? super E> delegate() {
            return delegate;
        }

        PlainRenderer(ListCellRenderer<? super E> delegate) {
            this.delegate = delegate != null ? delegate : plain(new javax.swing.DefaultListCellRenderer());
            // a renderer that IS its component (DefaultListCellRenderer and the look and feel's) is flagged before
            // its first cell, so a custom renderer that sets its own text from the value is covered from the start
            if (this.delegate instanceof JComponent jc) plain(jc);
            // a custom renderer may cast the value to its own type, so only a plain toString renderer is ever handed a
            // substituted String; a custom one relies on the flag above
            this.showsToString = this.delegate.getClass() == javax.swing.DefaultListCellRenderer.class
                    || this.delegate instanceof javax.swing.plaf.UIResource;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Component getListCellRendererComponent(JList<? extends E> list, E value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            javax.swing.LookAndFeel now = javax.swing.UIManager.getLookAndFeel();
            if (now != updatedFor) {
                updatedFor = now;
                if (delegate instanceof Component c) javax.swing.SwingUtilities.updateComponentTreeUI(c);
            }
            Object shown = value;
            if (showsToString && value != null && !(value instanceof Icon) && !(value instanceof Component)) {
                String text = String.valueOf(value);
                String safe = inert(text);
                if (!safe.equals(text)) shown = safe;
            }
            Component c = ((ListCellRenderer) delegate).getListCellRendererComponent(list, shown, index, isSelected, cellHasFocus);
            if (c instanceof JComponent jc && !Boolean.TRUE.equals(jc.getClientProperty(HTML_DISABLE))) plain(jc);
            return c;
        }
    }
}
