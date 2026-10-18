package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M77-2 (spec-ai-setup-wizard.md §6.0, OD-77.1): the start page's AI block. On first run it is Screen 1 INLINE on the
 * page — a state of the page, never a window that opens by itself (M36 D-S1 stays). Every other time it is ONE line,
 * <i>"AI: … — Set up AI…"</i> or <i>"… — Change…"</i>. It renders {@link AiSetupState} and reports acts through
 * {@link Host}; the node composes every word ({@link AiSetupLines}) and decides everything.
 */
final class AiSetupBlock extends JPanel {

    interface Host {
        void choose(Choice choice);

        void proceed();

        void notNow();

        void open();

        /** OD-77.5 (M77-3): the box under No AI. */
        default void alsoTurnOff(boolean on) { }

        /** OD-77.11 (M77-3): the one-click Turn it off after a cancelled wizard turned access on. */
        default void turnOffAccess() { }
    }

    private final Host host;
    private final JPanel firstRun = new JPanel();
    private final JPanel lineRow = new JPanel(new WrapLayout(FlowLayout.LEFT, UiTheme.SPACE_SM, 0));
    private final JLabel line = UiText.label(" ");
    private final JButton link = UiText.button(AiSetupLines.SET_UP_LINK);
    /** OD-77.11 (M77-3): shown only while the node offers it — the start page hides the status bar. */
    private final JButton turnOff = UiText.button(AiSetupLines.TURN_IT_OFF);
    private final JPanel offerRow = new JPanel(new WrapLayout(FlowLayout.LEFT, UiTheme.SPACE_SM, 0));
    private final JLabel offerLine = UiText.label(AiSetupLines.ACCESS_STILL_ON_LEAD);
    private final Map<Choice, JRadioButton> radios = new LinkedHashMap<>();
    private final WrapLine sentence = new WrapLine(" ");
    private final JButton proceed = UiText.button("Continue");
    private final JButton notNow = UiText.button(AiSetupLines.NOT_NOW);
    private final ButtonGroup group = new ButtonGroup();
    /** OD-77.5 (M77-3): shown under No AI while external access is on; ticked by default. */
    private final javax.swing.JCheckBox alsoTurnOff = UiText.checkBox(AiSetupLines.ALSO_TURN_OFF_ACCESS);
    private JLabel footer;
    private boolean rendering;

    AiSetupBlock(Host host) {
        this.host = host;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        getAccessibleContext().setAccessibleName("AI set-up");

        firstRun.setOpaque(false);
        firstRun.setLayout(new BoxLayout(firstRun, BoxLayout.Y_AXIS));
        WrapLine question = new WrapLine(AiSetupLines.QUESTION).bold();
        firstRun.add(question);
        JPanel choices = new JPanel(new WrapLayout(FlowLayout.LEFT, UiTheme.SPACE_MD, UiTheme.SPACE_XS));
        choices.setOpaque(false);
        for (Choice c : List.of(Choice.NO_AI, Choice.CLIENT, Choice.INSIDE, Choice.BOTH)) {
            String title = switch (c) {
                case NO_AI -> AiSetupLines.NO_AI_TITLE;
                case CLIENT -> AiSetupLines.CLIENT_SHORT;
                case INSIDE -> AiSetupLines.INSIDE_TITLE;
                default -> AiSetupLines.BOTH_TITLE;
            };
            JRadioButton r = UiText.radio(title);
            r.getAccessibleContext().setAccessibleDescription(AiSetupLines.sentenceFor(c));
            r.setOpaque(false);
            r.addActionListener(e -> { if (!rendering) host.choose(c); });
            group.add(r);
            radios.put(c, r);
            choices.add(r);
        }
        firstRun.add(choices);
        sentence.setForeground(UiTheme.mutedForeground());
        firstRun.add(sentence);
        alsoTurnOff.setOpaque(false);
        alsoTurnOff.setVisible(false);
        alsoTurnOff.addActionListener(e -> { if (!rendering) host.alsoTurnOff(alsoTurnOff.isSelected()); });
        firstRun.add(alsoTurnOff);
        JPanel buttons = new JPanel(new WrapLayout(FlowLayout.LEFT, UiTheme.SPACE_SM, UiTheme.SPACE_XS));
        buttons.setOpaque(false);
        proceed.addActionListener(e -> host.proceed());
        notNow.addActionListener(e -> host.notNow());
        buttons.add(proceed);
        buttons.add(notNow);
        firstRun.add(buttons);
        footer = UiText.label(AiSetupLines.FIRST_RUN_FOOTER);
        footer.setForeground(UiTheme.mutedForeground());
        footer.setFont(footer.getFont().deriveFont(Font.ITALIC));
        firstRun.add(footer);
        for (java.awt.Component k : firstRun.getComponents()) ((JComponent) k).setAlignmentX(LEFT_ALIGNMENT);
        firstRun.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, UiTheme.accent()),
                BorderFactory.createEmptyBorder(UiTheme.SPACE_SM, UiTheme.SPACE_MD, UiTheme.SPACE_SM, UiTheme.SPACE_SM)));

        lineRow.setOpaque(false);
        line.getAccessibleContext().setAccessibleName("AI status");
        lineRow.add(line);
        link.addActionListener(e -> host.open());
        lineRow.add(link);
        turnOff.addActionListener(e -> host.turnOffAccess());
        offerRow.setOpaque(false);
        offerRow.add(offerLine);
        offerRow.add(turnOff);
        offerRow.setVisible(false);

        firstRun.setAlignmentX(LEFT_ALIGNMENT);
        lineRow.setAlignmentX(LEFT_ALIGNMENT);
        offerRow.setAlignmentX(LEFT_ALIGNMENT);
        add(firstRun);
        add(lineRow);
        add(offerRow);
        setVisible(false);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (sentence != null) {
            // the muted words follow the theme in force
            SwingUtilities.invokeLater(() -> {
                sentence.setForeground(UiTheme.mutedForeground());
                if (footer != null) footer.setForeground(UiTheme.mutedForeground());
                firstRun.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, UiTheme.accent()),
                        BorderFactory.createEmptyBorder(UiTheme.SPACE_SM, UiTheme.SPACE_MD, UiTheme.SPACE_SM,
                                UiTheme.SPACE_SM)));
            });
        }
    }

    /** Render the node's decision: the first-run block, the one line, or nothing (before the session has said). */
    void render(AiSetupState s) {
        AiSetupState st = s == null ? AiSetupState.IDLE : s;
        rendering = true;
        try {
            boolean any = st.wizardEntry() && (st.firstRun() || !st.aiLine().isBlank() || st.external().offerTurnOff());
            setVisible(any);
            firstRun.setVisible(st.firstRun());
            lineRow.setVisible(!st.firstRun() && !st.aiLine().isBlank());
            for (var e : radios.entrySet()) e.getValue().setSelected(st.choice() == e.getKey());
            if (radios.values().stream().noneMatch(JRadioButton::isSelected)) group.clearSelection();
            String words = AiSetupLines.sentenceFor(st.choice());
            sentence.setText(words.isEmpty() ? " " : words);
            proceed.setEnabled(st.choice() != Choice.NONE);
            alsoTurnOff.setVisible(st.firstRun() && st.external().offerAlsoTurnOff());
            alsoTurnOff.setSelected(st.external().alsoTurnOff());
            line.setText(st.aiLine().isBlank() ? " " : st.aiLine());
            link.setText(st.aiLineLink().isBlank() ? AiSetupLines.SET_UP_LINK : st.aiLineLink());
            link.getAccessibleContext().setAccessibleName(link.getText().replace("…", "") + " AI");
            offerRow.setVisible(st.external().offerTurnOff());
        } finally {
            rendering = false;
        }
        revalidate();
        repaint();
    }

    // ---- for the tests -----------------------------------------------------------------------------------------------

    JRadioButton radio(Choice c) {
        return radios.get(c);
    }

    JButton proceedButton() {
        return proceed;
    }

    javax.swing.JCheckBox alsoTurnOffBox() {
        return alsoTurnOff;
    }

    JButton turnOffButton() {
        return turnOff;
    }

    JButton notNowButton() {
        return notNow;
    }

    JButton linkButton() {
        return link;
    }

    JLabel lineLabel() {
        return line;
    }

    boolean showsFirstRun() {
        return isVisible() && firstRun.isVisible();
    }
}
