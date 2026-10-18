package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.ProfileState;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * M74 UX (owner, 2026-10-17): how the assistant works, as four cards in the assistant panel — not a chain of dialogs.
 * <i>Safe analysis</i> (the default), <i>Full Claude Code</i>, <i>In-app assistant</i> and <i>Off</i>. Choosing a card
 * shows only that profile's two or three details; Apply reports the person's choice.
 *
 * <p><b>Rule 9.</b> It RENDERS {@link ProfileState} — whether it is open, why, and why an Apply was refused — and it
 * REPORTS Apply and Close through its {@link Host}. Choosing a card is local to the overlay and reports nothing, as
 * choosing a start-page entry is (r8 F10). Whether an Apply may be applied now is {@code assistantProfile}'s; what it
 * writes is {@link AssistantProfiles#apply}'s, called by the frame with {@link #details()}. No API key or model the person
 * types ever leaves this component except through that effect's performer.
 *
 * <p><b>Layout</b> (owner hands-on feedback, 2026-10-18 — "lots of clipping and non-obvious expansion"): ONE scroll pane
 * for the whole chooser and none inside it; the cards are as tall as their text at their width, in one column below
 * {@link #TWO_COLUMNS_MIN} and two above it; the chosen card's details, the full warning included, show in full under
 * the cards. The assistant panel hides its composer and header lines while the chooser is open.
 */
public final class ProfileOverlay extends JPanel {

    /** What the overlay reports. */
    public interface Host {
        void apply(long request, AssistantProfile profile);

        void close();

        void openSettings();
    }

    /** What the cards start from when they open: the profile in force and this machine's current details. */
    public record Defaults(AssistantProfile inForce, String claudeModel, String fullMode, String provider, String model,
                           boolean keyStored, boolean externalAccess) {
        public static final Defaults NONE = new Defaults(AssistantProfile.NONE, "sonnet", "default", "anthropic", "", false,
                false);

        public Defaults(AssistantProfile inForce, String claudeModel, String fullMode, String provider, String model,
                        boolean keyStored) {
            this(inForce, claudeModel, fullMode, provider, model, keyStored, false);
        }
    }

    static final String TITLE = "How should the assistant work on this machine?";
    static final String FIRST_USE = "Choose once — you can change it at any time from the assistant's header. Nothing has "
            + "been sent.";
    static final String ASKED = "Each card sets the options it needs; Settings ▸ Assistant keeps every option.";

    /** The cards, in order, with the one line each shows under its title. */
    static final List<AssistantProfile> CARDS = List.of(AssistantProfile.SAFE, AssistantProfile.FULL,
            AssistantProfile.IN_APP, AssistantProfile.OFF);

    /**
     * Review P3 S3 (2026-10-17): what P3 made true. Safe analysis READS your evidence, and it can change what is open or
     * write a file — through the verbs that do — only after you approve each call; Full Claude Code's own tools run under
     * your rules, and the analyser asks you only when Claude Code asks. {@code FaqSecurityContractTest} pins these.
     */
    public static final String SAFE_CARD_LINE = "Claude Code reads your evidence; it changes what is open or writes a file only "
            + "after you approve each call. No shell — safe with logs you did not write. "
            + AssistantProfiles.CARD_SENDS + "Anthropic.";
    public static final String FULL_CARD_LINE = "Your own Claude Code set-up, skills and tools. When Claude Code asks for "
            + "approval, the analyser asks you. " + AssistantProfiles.CARD_SENDS + "Anthropic.";
    /** Review B6: Off is about the assistant IN the analyser; an external MCP or REST client is another matter. */
    public static final String OFF_CARD_LINE = "No assistant in the analyser. Everything else still works; external MCP and REST "
            + "clients are not affected.";

    /** Review B6: the one line under the cards — the external loop, on or off, and where it is set. */
    static String externalLine(boolean externalOn) {
        return externalOn
                ? "External MCP/REST access is ON: an MCP client or a REST caller on this machine can drive the analyser "
                        + "— Settings ▸ Assistant turns it off."
                : "External MCP/REST access is off — Settings ▸ Assistant turns it on for an MCP client or a REST caller.";
    }

    static String cardLine(AssistantProfile p) {
        return switch (p) {
            case SAFE -> SAFE_CARD_LINE;
            case FULL -> FULL_CARD_LINE;
            case IN_APP -> "The analyser's own assistant, with your API key and model. " + AssistantProfiles.CARD_SENDS
                    + "the provider you chose.";
            case OFF -> OFF_CARD_LINE;
            case NONE -> "";
        };
    }

    /** The two or three details a card shows when chosen, before its fields. */
    static List<String> detailLines(AssistantProfile p) {
        return switch (p) {
            case SAFE -> List.of(
                    "Claude Code reads with Read, Grep and Glob in the project folder, and uses the analyser's own views.",
                    "No Bash, no file edits, no web, and none of your own skills, hooks or MCP servers — so text in a "
                            + "log written to steer an AI reaches no shell.",
                    "Runs your installed claude on your own Claude plan; the analyser never reads your credentials.",
                    AssistantProfiles.CLAUDE_CODE_SENDS);
            case FULL -> List.of(
                    "Loads your settings, skills, plugins, hooks and MCP servers, and the folder's own — they run when "
                            + "you press Start.",
                    "When Claude Code asks for an approval, the analyser asks you; Deny is the default.",
                    AssistantProfiles.CLAUDE_CODE_SENDS);
            case IN_APP -> List.of(
                    AssistantProfiles.IN_APP_SENDS,
                    "The key is stored on this machine only (in cleartext), never in a project, a share or a bundle.");
            case OFF -> List.of(
                    "No question is sent anywhere from the analyser: the assistant panel and the start page say so instead.",
                    "An external MCP or REST client you connected is not affected — its access is set in Settings ▸ "
                            + "Assistant.",
                    "Every view, chart, report, walk and the topology work exactly as before.");
            case NONE -> List.of();
        };
    }

    private Host host;
    private Supplier<Defaults> defaults = () -> Defaults.NONE;

    private final WrapLine title = new WrapLine(TITLE);
    private final WrapLine why = new WrapLine(" ");
    /** Review B6: the external loop, under the cards. */
    final WrapLine external = new WrapLine(" ");
    private final Map<AssistantProfile, Card> cards = new EnumMap<>(AssistantProfile.class);
    private final ButtonGroup group = new ButtonGroup();
    /** Each card's details; only the chosen one is visible (a CardLayout would be as tall as the tallest). */
    private final Map<AssistantProfile, JComponent> detailPanels = new EnumMap<>(AssistantProfile.class);
    private final JPanel details = new JPanel();
    /** The one scroll pane: the whole chooser scrolls; nothing inside it does. */
    private final JScrollPane scroll;
    private final WrapLine refusal = new WrapLine(" ");
    private final JButton apply = UiText.button("Apply");
    private final JButton close = UiText.button("Close");
    private final JButton settings = UiText.button("All options: Settings ▸ Assistant…");

    // the details' own fields
    private final JTextField safeModel = new JTextField(12);
    private final JTextField fullModel = new JTextField(12);
    private final JComboBox<String> fullMode = UiText.plainCells(new JComboBox<>(
            telamin.fluxtion.audit.analyser.analyser.config.ConfigStore.FULL_MODES.toArray(new String[0])));
    private final WrapLine fullModeNote = new WrapLine(" ");
    private final JComboBox<String> provider = UiText.plainCells(new JComboBox<>(new String[]{"anthropic", "openai"}));
    private final JTextField model = new JTextField(16);
    private final JPasswordField apiKey = new JPasswordField(24);
    private final WrapLine keyNote = new WrapLine(" ");

    private AssistantProfile selected = AssistantProfile.SAFE;
    private boolean shown;
    private long request;
    private ProfileState last = ProfileState.NONE;
    private final java.util.List<Runnable> recolour = new java.util.ArrayList<>();

    /** Two columns of cards only when each can be at least this wide (the narrow pop-out has one). */
    static final int CARD_MIN_WIDTH = 260;
    static final int TWO_COLUMNS_MIN = 2 * CARD_MIN_WIDTH + UiTheme.SPACE_SM;

    public ProfileOverlay() {
        super(new BorderLayout(0, UiTheme.SPACE_SM));
        setBorder(BorderFactory.createEmptyBorder(UiTheme.SPACE_MD, UiTheme.SPACE_MD, UiTheme.SPACE_MD, UiTheme.SPACE_MD));
        getAccessibleContext().setAccessibleName("How the assistant works");

        Body body = new Body();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        Font base = UIManager.getFont("Label.font");
        if (base != null) title.setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 3f));
        JPanel row = new JPanel(new CardGrid());
        row.setOpaque(false);
        for (AssistantProfile p : CARDS) {
            Card c = new Card(p);
            cards.put(p, c);
            group.add(c);
            row.add(c);
            c.addActionListener(e -> choose(p));
        }
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setOpaque(false);
        for (AssistantProfile p : CARDS) {
            JComponent d = detailFor(p);
            d.setAlignmentX(LEFT_ALIGNMENT);
            detailPanels.put(p, d);
            details.add(d);
        }
        external.getAccessibleContext().setAccessibleName("External access");
        body.add(title);
        body.add(Box.createVerticalStrut(UiTheme.SPACE_XS));
        body.add(why);
        body.add(Box.createVerticalStrut(UiTheme.SPACE_MD));
        body.add(row);
        body.add(Box.createVerticalStrut(UiTheme.SPACE_MD));
        body.add(details);
        body.add(Box.createVerticalStrut(UiTheme.SPACE_MD));
        body.add(external);
        for (Component c : body.getComponents()) ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        scroll = new JScrollPane(body, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        JPanel foot = new JPanel();
        foot.setLayout(new BoxLayout(foot, BoxLayout.Y_AXIS));
        foot.setOpaque(false);
        refusal.setVisible(false);
        JPanel buttons = new JPanel(new WrapLayout(FlowLayout.RIGHT, UiTheme.SPACE_SM, UiTheme.SPACE_XS));
        buttons.setOpaque(false);
        buttons.add(close);
        buttons.add(apply);
        JPanel left = new JPanel(new WrapLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        settings.setBorderPainted(false);
        settings.setContentAreaFilled(false);
        settings.setMargin(new Insets(0, 0, 0, 0));
        left.add(settings);
        for (JComponent c : new JComponent[]{refusal, buttons, left}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            foot.add(c);
        }
        add(foot, BorderLayout.SOUTH);

        apply.addActionListener(e -> {
            if (host != null) host.apply(++request, selected);
        });
        close.addActionListener(e -> { if (host != null) host.close(); });
        settings.addActionListener(e -> { if (host != null) host.openSettings(); });
        fullMode.addActionListener(e -> fullModeNote.setText(ConfigPanel.modeNote(String.valueOf(fullMode.getSelectedItem()))));
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "profiles-close");
        getActionMap().put("profiles-close", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { if (host != null) host.close(); }
        });
        provider.getAccessibleContext().setAccessibleName("In-app provider");
        model.getAccessibleContext().setAccessibleName("In-app model");
        apiKey.getAccessibleContext().setAccessibleName("In-app API key");
        fullMode.getAccessibleContext().setAccessibleName("Full Claude Code mode");
        safeModel.getAccessibleContext().setAccessibleName("Claude Code model");
        fullModel.getAccessibleContext().setAccessibleName("Claude Code model");
        choose(AssistantProfile.SAFE);
        applyColours();
    }

    public void setHost(Host host) {
        this.host = host;
    }

    public void setDefaults(Supplier<Defaults> defaults) {
        this.defaults = defaults == null ? () -> Defaults.NONE : defaults;
    }

    /**
     * Render the session's decision. On opening, the cards start from the profile in force (Safe analysis when none is
     * chosen) and this machine's current details; while open, a refused Apply says why in place.
     */
    public void render(ProfileState s) {
        ProfileState st = s == null ? ProfileState.NONE : s;
        boolean opening = st.open() && !shown;
        shown = st.open();
        last = st;
        if (opening) load(defaults.get());
        why.setText(ProfileState.FIRST_USE.equals(st.why()) ? FIRST_USE : ASKED);
        boolean refused = st.open() && !st.refusal().isBlank() && st.request() == request;
        refusal.setText(refused ? st.refusal() : " ");
        refusal.setVisible(refused);
        // review PR151 F4: while Full Claude Code's warning is being read, a second Apply cannot be pressed
        apply.setEnabled(!st.confirming());
    }

    private void load(Defaults d) {
        AssistantProfile p = d.inForce() == null || d.inForce() == AssistantProfile.NONE ? AssistantProfile.SAFE : d.inForce();
        safeModel.setText(d.claudeModel() == null ? "" : d.claudeModel());
        fullModel.setText(d.claudeModel() == null ? "" : d.claudeModel());
        fullMode.setSelectedItem(d.fullMode() == null ? "default" : d.fullMode());
        fullModeNote.setText(ConfigPanel.modeNote(String.valueOf(fullMode.getSelectedItem())));
        provider.setSelectedItem(d.provider() == null || d.provider().isBlank() ? "anthropic" : d.provider());
        model.setText(d.model() == null ? "" : d.model());
        apiKey.setText("");
        keyNote.setText(d.keyStored() ? "A key is stored on this machine; leave this blank to keep it."
                : "No key is stored yet.");
        external.setText(externalLine(d.externalAccess()));
        choose(p);
    }

    /** Choosing a card: local to the overlay — its details show; nothing is reported until Apply. */
    void choose(AssistantProfile p) {
        selected = p;
        Card c = cards.get(p);
        if (c != null && !c.isSelected()) c.setSelected(true);
        for (var d : detailPanels.entrySet()) d.getValue().setVisible(d.getKey() == p);
        details.revalidate();
        apply.setText(p == AssistantProfile.FULL ? ConfigPanel.FULL_POSTURE_TURN_ON : "Apply — " + p.title());
        apply.getAccessibleContext().setAccessibleName("Apply " + p.title());
        for (Card k : cards.values()) k.repaint();
    }

    /** The chosen card's details, for the performer of the node's Apply effect. */
    public AssistantProfiles.Details details() {
        return switch (selected) {
            case SAFE -> new AssistantProfiles.Details(null, safeModel.getText().trim(), null, null, null);
            case FULL -> new AssistantProfiles.Details(String.valueOf(fullMode.getSelectedItem()),
                    fullModel.getText().trim(), null, null, null);
            case IN_APP -> new AssistantProfiles.Details(null, null, String.valueOf(provider.getSelectedItem()),
                    model.getText().trim(), new String(apiKey.getPassword()).trim());
            default -> AssistantProfiles.Details.NONE;
        };
    }

    AssistantProfile selected() {
        return selected;
    }

    AbstractButton card(AssistantProfile p) {
        return cards.get(p);
    }

    JButton applyButton() {
        return apply;
    }

    JButton closeButton() {
        return close;
    }

    WrapLine refusalLine() {
        return refusal;
    }

    JComboBox<String> fullModeCombo() {
        return fullMode;
    }

    JPasswordField apiKeyField() {
        return apiKey;
    }

    JTextField inAppModelField() {
        return model;
    }

    JComboBox<String> providerCombo() {
        return provider;
    }

    // ---- the details ----------------------------------------------------------------------------------------------

    private JComponent detailFor(AssistantProfile p) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        WrapLine heading = new WrapLine(p.title()).bold();
        box.add(heading);
        box.add(Box.createVerticalStrut(UiTheme.SPACE_XS));
        for (String line : detailLines(p)) box.add(bullet(line));
        switch (p) {
            case SAFE -> box.add(field("Claude Code model:", safeModel));
            case FULL -> {
                // the warning in full, as text — never a small scroll strip (owner, 2026-10-18); the confirmation that
                // Apply opens shows it again, with Cancel the default (review PR151 F4)
                box.add(Box.createVerticalStrut(UiTheme.SPACE_SM));
                WrapLine warning = new WrapLine(ConfigPanel.FULL_POSTURE_WARNING);
                warning.getAccessibleContext().setAccessibleName("Full Claude Code warning");
                box.add(warning);
                box.add(field("Mode:", fullMode));
                box.add(fullModeNote);
                box.add(field("Claude Code model:", fullModel));
            }
            case IN_APP -> {
                box.add(field("Provider:", provider));
                box.add(field("Model:", model));
                box.add(field("API key:", apiKey));
                box.add(keyNote);
            }
            default -> { }
        }
        for (Component c : box.getComponents()) ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        return box;
    }

    /** A label and its field on one row, as tall as they are. */
    private static JComponent field(String label, JComponent field) {
        JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, UiTheme.SPACE_SM, UiTheme.SPACE_XS)) {
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        row.setOpaque(false);
        row.add(UiText.label(label));
        row.add(field);
        return row;
    }

    private JComponent bullet(String text) {
        WrapLine a = new WrapLine("•  " + text);
        recolour.add(() -> a.setForeground(UIManager.getColor("Label.foreground")));
        return a;
    }

    /** For the frame tests: the one scroll pane. */
    JScrollPane scrollPane() {
        return scroll;
    }

    /** For the frame tests: the chosen card's details. */
    JComponent detailPanel(AssistantProfile p) {
        return detailPanels.get(p);
    }

    /** The chooser's content: as wide as the viewport, so every line wraps to it and nothing scrolls sideways. */
    private static final class Body extends JPanel implements Scrollable {
        Body() {
            setOpaque(false);
        }

        @Override public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
            return 16;
        }

        @Override public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
            return Math.max(16, r.height - 32);
        }

        @Override public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /**
     * The cards: one column below {@link #TWO_COLUMNS_MIN}, two above; each row as tall as its tallest card's text at the
     * column's width (owner, 2026-10-18: no fixed height, no text running to the edge).
     */
    static final class CardGrid implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component comp) { }

        @Override public void removeLayoutComponent(Component comp) { }

        static int columns(int width) {
            return width >= TWO_COLUMNS_MIN ? 2 : 1;
        }

        private static int width(Container parent) {
            Insets in = parent.getInsets();
            int w = parent.getWidth() - in.left - in.right;
            if (w <= 0 && parent.getParent() != null) {
                Insets pin = parent.getParent().getInsets();
                w = parent.getParent().getWidth() - pin.left - pin.right;
            }
            return w > 0 ? w : TWO_COLUMNS_MIN;
        }

        private static int[] rowHeights(Container parent, int cols, int colW) {
            int n = parent.getComponentCount();
            int[] rows = new int[(n + cols - 1) / cols];
            for (int i = 0; i < n; i++) {
                Component c = parent.getComponent(i);
                int h = c instanceof Card k ? k.heightFor(colW) : c.getPreferredSize().height;
                rows[i / cols] = Math.max(rows[i / cols], h);
            }
            return rows;
        }

        @Override public Dimension preferredLayoutSize(Container parent) {
            int w = width(parent);
            int cols = columns(w);
            int colW = (w - (cols - 1) * UiTheme.SPACE_SM) / cols;
            int h = 0;
            int[] rows = rowHeights(parent, cols, colW);
            for (int r : rows) h += r;
            h += Math.max(0, rows.length - 1) * UiTheme.SPACE_SM;
            Insets in = parent.getInsets();
            return new Dimension(w + in.left + in.right, h + in.top + in.bottom);
        }

        @Override public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(CARD_MIN_WIDTH, preferredLayoutSize(parent).height);
        }

        @Override public void layoutContainer(Container parent) {
            Insets in = parent.getInsets();
            int w = parent.getWidth() - in.left - in.right;
            int cols = columns(w);
            int colW = (w - (cols - 1) * UiTheme.SPACE_SM) / cols;
            int[] rows = rowHeights(parent, cols, colW);
            int y = in.top;
            for (int i = 0; i < parent.getComponentCount(); i++) {
                int col = i % cols;
                int row = i / cols;
                if (col == 0 && row > 0) y += rows[row - 1] + UiTheme.SPACE_SM;
                parent.getComponent(i).setBounds(in.left + col * (colW + UiTheme.SPACE_SM), y, colW, rows[row]);
            }
        }
    }

    private void applyColours() {
        refusal.setForeground(UiTheme.errorForeground());
        why.setForeground(UiTheme.mutedForeground());
        recolour.forEach(Runnable::run);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (recolour != null) SwingUtilities.invokeLater(this::applyColours);
    }

    /**
     * A card: a toggle in one group, painted (FlatLaf owns a button's look), still a real button — keyboard, focus,
     * tooltip and screen reader are the platform's. The chosen card is filled and ticked; colour is never the only cue.
     */
    private final class Card extends JToggleButton {
        private final AssistantProfile profile;

        Card(AssistantProfile profile) {
            this.profile = profile;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            String line = cardLine(profile);
            setToolTipText(UiText.tooltip(line));
            getAccessibleContext().setAccessibleName(profile.title() + (profile == AssistantProfile.SAFE
                    ? " (the default)" : ""));
            getAccessibleContext().setAccessibleDescription(line);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        /** Review M74 usability #1: the card is as tall as its text needs at its width — the line is never cut. */
        @Override
        public Dimension getPreferredSize() {
            int width = getWidth() > 0 ? getWidth() : 300;
            return new Dimension(CARD_MIN_WIDTH, heightFor(width));
        }

        /** The height this card's title and whole line need at {@code width} (owner, 2026-10-18: never a fixed one). */
        int heightFor(int width) {
            Font f = UIManager.getFont("Label.font");
            if (f == null) f = getFont();
            FontMetrics big = getFontMetrics(f.deriveFont(Font.BOLD, f.getSize2D() + 0.5f));
            FontMetrics sm = getFontMetrics(f.deriveFont(Font.PLAIN, f.getSize2D() - 1f));
            int lines = Math.max(1, wrap(sm, cardLine(profile), width - 2 * TEXT_INSET, 0).size());
            return 10 + big.getHeight() + 4 + lines * sm.getHeight() + 12;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean on = isSelected();
            boolean focused = isFocusOwner();
            boolean hot = getModel().isRollover() || focused;
            int w = getWidth(), h = getHeight();
            // review §4.2 (WCAG 2.4.7, 1.4.11): the focus ring is OUTSIDE the card, 2 px of the accent, as ActionChip's
            int in = 3;
            RoundRectangle2D shape = new RoundRectangle2D.Float(in, in, w - 2f * in, h - 2f * in, UiTheme.CONTROL_RADIUS,
                    UiTheme.CONTROL_RADIUS);
            g.setColor(on ? UiTheme.accentWash(0.20f) : hot ? UiTheme.accentWash(0.10f) : UiTheme.accentWash(0.04f));
            g.fill(shape);
            g.setColor(on || hot ? UiTheme.accent() : UiTheme.controlBorder());
            g.setStroke(new BasicStroke(on ? 2f : 1f));
            g.draw(shape);
            if (focused) {
                g.setColor(UiTheme.accent());
                g.setStroke(new BasicStroke(2f));
                g.draw(new RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, UiTheme.CONTROL_RADIUS + 3,
                        UiTheme.CONTROL_RADIUS + 3));
            }
            Font base = UIManager.getFont("Label.font");
            if (base == null) base = getFont();
            Font bold = base.deriveFont(Font.BOLD, base.getSize2D() + 0.5f);
            Font small = base.deriveFont(Font.PLAIN, base.getSize2D() - 1f);
            g.setFont(bold);
            FontMetrics fm = g.getFontMetrics();
            int x = TEXT_INSET, y = 10 + fm.getAscent();
            g.setColor(on ? UiTheme.accentText() : UIManager.getColor("Label.foreground"));
            // the tick is drawn on its own, in the logical Dialog font: in one string with the title, macOS's font
            // cascade dropped glyphs of the title ("afe anal sis" for "Safe analysis", seen 2026-10-18)
            if (on) {
                Font tickFont = new Font(Font.DIALOG, Font.BOLD, Math.round(bold.getSize2D()));
                g.setFont(tickFont);
                g.drawString("✓", x, y);
                x += g.getFontMetrics().stringWidth("✓ ");
                g.setFont(bold);
            }
            g.drawString(profile.title() + (profile == AssistantProfile.SAFE ? "  · default" : ""), x, y);
            x = TEXT_INSET;
            g.setFont(small);
            FontMetrics sm = g.getFontMetrics();
            g.setColor(UiTheme.mutedForeground());
            int yy = y + 4;
            // as many lines as the card has room for; if it is ever short of room the last line ends in "…" and the
            // whole line is the tooltip and the accessible description — never dropped silently
            int room = Math.max(1, (h - yy - 8) / sm.getHeight());
            for (String l : wrap(sm, cardLine(profile), w - 2 * TEXT_INSET, room)) {
                yy += sm.getHeight();
                g.drawString(l, x, yy);
            }
            g.dispose();
        }
    }

    /** The card's left and right text inset. */
    static final int TEXT_INSET = 14;

    /**
     * Word-wrap {@code text} to {@code width}. With {@code maxLines > 0} and more text than fits, the last line ends in
     * "…" — words are NEVER dropped silently (review M74 usability #1: the old loop broke at the limit and its ellipsis
     * branch could never fire, so the Safe analysis card's promise stopped mid-sentence). {@code maxLines <= 0}: every line.
     */
    static List<String> wrap(FontMetrics fm, String text, int width, int maxLines) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            String next = line.isEmpty() ? word : line + " " + word;
            if (line.isEmpty() || fm.stringWidth(next) <= width) {
                line.setLength(0);
                line.append(next);
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (!line.isEmpty()) out.add(line.toString());
        if (maxLines <= 0 || out.size() <= maxLines) return out;
        // more lines than room: keep maxLines, the last one ellipsised so the cut is visible
        java.util.List<String> kept = new java.util.ArrayList<>(out.subList(0, maxLines));
        String last = kept.get(maxLines - 1) + " " + out.get(maxLines);
        while (last.length() > 1 && fm.stringWidth(last + "…") > width) last = last.substring(0, last.length() - 1);
        kept.set(maxLines - 1, last.stripTrailing() + "…");
        return kept;
    }
}
