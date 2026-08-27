package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.core.Background;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpConnectionProbe;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpLaunchCommand;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpSetupState;
import telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The local, client-neutral MCP readiness surface (M42.2).
 *
 * <p>It never edits a third-party configuration. Opening it only reads the current local state and the
 * documented JBang launcher; enabling local transport has its own confirmation, and bridge probing is a
 * background, read-only {@code analyser_context} call through the actual child command.
 */
public final class McpSetupDialog extends JDialog {

    /** The requested client only tailors the explanation; client registration lands in later M42 slices. */
    public enum Target {
        CODEX("Codex"),
        CLAUDE("Claude"),
        GENERIC("another MCP client");

        private final String label;

        Target(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final AppConfig config;
    private final Runnable onTransportEnabled;
    private final RestEndpointFile endpointFile = RestEndpointFile.wellKnown();
    private final JLabel localStatus = new JLabel();
    private final JLabel commandStatus = new JLabel();
    private final JLabel bridgeStatus = new JLabel("Bridge: not checked in this session.");
    private final JLabel clientStatus = new JLabel();
    private final JButton enableTransport = new JButton("Enable local transport…");
    private final JButton checkBridge = new JButton("Check connection");
    private final JComboBox<Target> target;
    private McpLaunchCommand command;

    private McpSetupDialog(Window owner, AppConfig config, Runnable onTransportEnabled, Target initial,
                           Dialog.ModalityType modality) {
        super(owner, "Connect an AI client", modality);
        this.config = config;
        this.onTransportEnabled = onTransportEnabled == null ? () -> { } : onTransportEnabled;
        this.target = new JComboBox<>(Target.values());
        this.target.setSelectedItem(initial == null ? Target.GENERIC : initial);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        buildUi();
        refreshReadiness();
        pack();
        setMinimumSize(new Dimension(660, getHeight()));
        setLocationRelativeTo(owner);
    }

    /** Open from the Start Page (modeless) or Settings ▸ Assistant (document-modal over Settings only). */
    public static void show(Window owner, AppConfig config, Runnable onTransportEnabled, Target initial,
                            boolean modalOverOwner) {
        Dialog.ModalityType modality = modalOverOwner ? Dialog.ModalityType.DOCUMENT_MODAL : Dialog.ModalityType.MODELESS;
        new McpSetupDialog(owner, config, onTransportEnabled, initial, modality).setVisible(true);
    }

    private void buildUi() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));

        body.add(title("Let an AI client work in this analyser window"));
        body.add(Box.createVerticalStrut(6));
        body.add(note("The client launches a local stdio bridge; the bridge then reaches this already-open "
                + "window over its token-protected loopback transport. It never starts a second analyser."));
        body.add(Box.createVerticalStrut(12));

        JPanel targetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        targetRow.setAlignmentX(LEFT_ALIGNMENT);
        targetRow.setOpaque(false);
        targetRow.add(new JLabel("Set up:  "));
        targetRow.add(target);
        target.addActionListener(e -> refreshClientStatus());
        body.add(targetRow);
        body.add(Box.createVerticalStrut(12));

        body.add(section("1. This analyser window", localStatus));
        JPanel transportActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        transportActions.setOpaque(false);
        transportActions.setAlignmentX(LEFT_ALIGNMENT);
        enableTransport.addActionListener(e -> confirmEnableTransport());
        transportActions.add(enableTransport);
        body.add(transportActions);
        body.add(Box.createVerticalStrut(10));

        body.add(section("2. Bridge command", commandStatus));
        JPanel bridgeActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bridgeActions.setOpaque(false);
        bridgeActions.setAlignmentX(LEFT_ALIGNMENT);
        checkBridge.addActionListener(e -> checkBridge());
        bridgeActions.add(checkBridge);
        body.add(bridgeActions);
        body.add(Box.createVerticalStrut(4));
        body.add(noteLabel(bridgeStatus));
        body.add(Box.createVerticalStrut(10));

        body.add(section("3. Client registration", clientStatus));
        body.add(Box.createVerticalStrut(8));
        body.add(note("The registration label will be <b>fluxtion-analyser</b>. The bridge protocol identifies "
                + "itself as <b>fluxtion-audit-log-analyser</b>; those are the two existing names, not a third "
                + "identity. A successful local check proves this application and bridge only—it cannot prove "
                + "that a client has imported configuration, signed in, or approved a tool call."));
        body.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        buttons.add(close);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(close);
    }

    private static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setFont(label.getFont().deriveFont(label.getFont().getSize2D() + 3f));
        return label;
    }

    private static JPanel section(String heading, JLabel value) {
        JPanel panel = new JPanel(new BorderLayout(8, 2));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setOpaque(false);
        JLabel label = new JLabel(heading);
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
        panel.add(label, BorderLayout.NORTH);
        panel.add(noteLabel(value), BorderLayout.CENTER);
        return panel;
    }

    private static JLabel noteLabel(JLabel label) {
        label.setVerticalAlignment(SwingConstants.TOP);
        label.setForeground(UiTheme.mutedForeground());
        return label;
    }

    private static JTextArea note(String htmlFreeText) {
        // JTextArea deliberately gets plain text: it wraps reliably at the dialog width and cannot turn a
        // launcher path into an active link. The two small bold hints above are explanatory copy only.
        JTextArea text = new JTextArea(htmlFreeText.replace("<b>", "").replace("</b>", ""));
        text.setEditable(false);
        text.setOpaque(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFocusable(false);
        text.setAlignmentX(LEFT_ALIGNMENT);
        text.setForeground(UiTheme.mutedForeground());
        text.setMaximumSize(new Dimension(610, 90));
        return text;
    }

    private void refreshReadiness() {
        RestEndpointFile.Endpoint endpoint = endpointFile.read();
        boolean alive = endpoint != null && endpoint.alive();
        McpSetupState.LocalReadiness local = McpSetupState.classify(config.assistantActionsRest, endpoint, alive,
                ProcessHandle.current().pid());
        localStatus.setText(local.detail());
        enableTransport.setVisible(local.status() == McpSetupState.LocalStatus.OFF);

        Optional<McpLaunchCommand> jbang = McpLaunchCommand.installedJbang(Path.of(System.getProperty("user.home")));
        Optional<McpLaunchCommand> found = jbang.isPresent() ? jbang : McpLaunchCommand.runningJar();
        command = found.orElse(null);
        if (command == null) {
            commandStatus.setText("No supported local launcher was found. Install with JBang, then reopen this setup.");
        } else {
            commandStatus.setText((jbang.isPresent() ? "Ready to run installed JBang command: "
                    : "Ready to run this packaged application: ") + display(command.command()));
        }
        checkBridge.setEnabled(local.canProbe() && command != null);
        refreshClientStatus();
        revalidate();
        repaint();
    }

    private void refreshClientStatus() {
        Target selected = (Target) target.getSelectedItem();
        clientStatus.setText((selected == null ? "This client" : selected) + " is not registered by the analyser yet. "
                + "Registration or configuration is an explicit later step, never a side effect of opening this screen.");
    }

    private void confirmEnableTransport() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Enable local transport for this analyser?\n\n"
                        + "This saves the Assistant REST setting and starts a loopback-only endpoint with a fresh "
                        + "per-run token. The token is not copied into client configuration.",
                "Enable local transport", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return;
        config.assistantActionsRest = true;
        onTransportEnabled.run();
        bridgeStatus.setText("Bridge: local transport enabled; waiting for this analyser's endpoint.");
        refreshReadiness();
    }

    private void checkBridge() {
        if (command == null) return;
        checkBridge.setEnabled(false);
        bridgeStatus.setText("Bridge: checking the configured command…");
        McpLaunchCommand toCheck = command;
        Background.run(() -> new McpConnectionProbe(toCheck, endpointFile).probe(),
                this::showProbeResult,
                error -> {
                    bridgeStatus.setText("Bridge: check could not start. Retry after the analyser is ready.");
                    refreshReadiness();
                });
    }

    private void showProbeResult(McpConnectionProbe.Result result) {
        bridgeStatus.setText(switch (result.status()) {
            case VERIFIED -> "Bridge works now: the configured command reached this analyser.";
            case REST_OFF -> "Turn on local transport, then check again.";
            case OTHER_INSTANCE -> "Another analyser owns the MCP connection; check that window instead.";
            case LAUNCH_FAILED -> "Fix launcher path: the configured bridge command did not start.";
            case PROTOCOL_FAILED -> "Bridge needs attention: it did not complete the MCP exchange. A cold launcher may need a moment; retry once.";
            case ACTION_FAILED -> "App connection needs attention: the bridge reached the app but context failed.";
        });
        refreshReadiness();
    }

    private static String display(List<String> command) {
        return String.join("  ", command);
    }
}
