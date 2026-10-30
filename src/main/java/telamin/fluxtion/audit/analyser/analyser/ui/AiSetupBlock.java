package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * M77-2 (spec-ai-setup-wizard.md §6.0, OD-77.1): the start page's AI block. On first run it offers the three onboarding paths INLINE on the
 * page — a state of the page, never a window that opens by itself (M36 D-S1 stays). Every other time it is ONE line,
 * <i>"AI: … — Set up AI…"</i> or <i>"… — Change…"</i>. It renders {@link AiSetupState} and reports acts through
 * {@link Host}; the node composes every word ({@link AiSetupLines}) and decides everything.
 */
final class AiSetupBlock extends JPanel {

    interface Host {
        void build();

        void demo();

        void client();

        void notNow();

        void open();

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
    private final JButton proceed = UiText.button(AiSetupLines.CONTINUE);
    private final JButton notNow = UiText.button(AiSetupLines.NOT_NOW);
    private JLabel footer;

    AiSetupBlock(Host host) {
        this.host = host;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        getAccessibleContext().setAccessibleName("AI set-up");

        firstRun.setOpaque(false);
        firstRun.setLayout(new BoxLayout(firstRun, BoxLayout.Y_AXIS));
        firstRun.add(new WrapLine(AiSetupLines.ONBOARD_TITLE).bold());
        firstRun.add(new WrapLine(AiSetupLines.ONBOARD_NOTE));
        JPanel choices = new JPanel(new WrapLayout(FlowLayout.LEFT, UiTheme.SPACE_SM, UiTheme.SPACE_XS));
        choices.setOpaque(false);
        proceed.setText(AiSetupLines.ONBOARD_BUILD);
        proceed.addActionListener(e -> host.build());
        choices.add(proceed);
        JButton demo = UiText.button(AiSetupLines.ONBOARD_DEMO);
        demo.addActionListener(e -> host.demo());
        choices.add(demo);
        JButton client = UiText.button(AiSetupLines.ONBOARD_CLIENT);
        client.addActionListener(e -> host.client());
        choices.add(client);
        firstRun.add(choices);
        JPanel options = new JPanel(new WrapLayout(FlowLayout.LEFT, UiTheme.SPACE_SM, UiTheme.SPACE_XS));
        options.setOpaque(false);
        JButton more = UiText.button(AiSetupLines.ONBOARD_DETAILS);
        more.addActionListener(e -> host.open());
        options.add(more);
        notNow.addActionListener(e -> host.notNow());
        options.add(notNow);
        firstRun.add(options);
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
        if (firstRun != null) {
            // the muted words follow the theme in force
            SwingUtilities.invokeLater(() -> {
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
        boolean any = (st.firstRun() || !st.aiLine().isBlank() || st.external().offerTurnOff());
        setVisible(any);
        firstRun.setVisible(st.firstRun());
        lineRow.setVisible(!st.firstRun() && !st.aiLine().isBlank());
        line.setText(st.aiLine().isBlank() ? " " : st.aiLine());
        link.setText(st.aiLineLink().isBlank() ? AiSetupLines.SET_UP_LINK : st.aiLineLink());
        link.getAccessibleContext().setAccessibleName(link.getText().replace("…", "") + " AI");
        offerRow.setVisible(st.external().offerTurnOff());
        revalidate();
        repaint();
    }

    // ---- for the tests -----------------------------------------------------------------------------------------------

    JButton proceedButton() {
        return proceed;
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
