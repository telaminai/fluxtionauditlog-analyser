package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.core.Background;
import telamin.fluxtion.audit.analyser.analyser.mcp.CodexMcpClient;
import telamin.fluxtion.audit.analyser.analyser.mcp.ClaudeMcpClient;
import telamin.fluxtion.audit.analyser.analyser.mcp.GenericMcpConfiguration;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpConnectionProbe;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpLaunchCommand;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpSetupState;
import telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The local MCP readiness and confirmed-client-setup surface (M42.2–.6).
 *
 * <p>Opening it only reads the current local state and the documented JBang launcher. Third-party
 * configuration changes have their own confirmation; enabling local transport has another. Bridge
 * probing is a background, read-only {@code analyser_context} call through the actual child command.
 */
public final class McpSetupDialog extends JDialog {

    /** The requested client only tailors the explanation; client registration lands in later M42 slices. */
    public enum Target {
        CODEX("Codex"),
        CLAUDE_CODE("Claude Code"),
        CLAUDE_DESKTOP("Claude Desktop"),
        GENERIC("another MCP client");

        private final String label;

        Target(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }

        static Target fromPersisted(String value, Target fallback) {
            try {
                if ("CLAUDE".equals(value)) return CLAUDE_CODE; // M42.2's generic Claude choice keeps its useful route.
                return value == null || value.isBlank() ? fallback : Target.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }

    private final AppConfig config;
    /** Persists a deliberate local setup choice and applies REST if its checkbox was explicitly enabled. */
    private final Runnable onConfigurationChanged;
    private final RestEndpointFile endpointFile = RestEndpointFile.wellKnown();
    private final JLabel localStatus = new JLabel();
    /** A real field rather than a clipped label: absolute Java/jar paths are routinely wider than a dialog. */
    private final JTextArea commandStatus = commandBox();
    private final JLabel bridgeStatus = new JLabel("Bridge: not checked in this session.");
    private final JLabel clientStatus = new JLabel();
    private final JPanel clientActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    /** Generic JSON is shown as data the person can inspect/select, never placed in a hidden client configuration. */
    private final JTextArea genericConfiguration = configurationBox();
    private final JButton enableTransport = new JButton("Enable local transport…");
    private final JButton checkBridge = new JButton("Check connection");
    private final JComboBox<Target> target;
    private McpLaunchCommand command;
    private String launcherIdentity = "";
    private CodexMcpClient codex;
    private boolean codexChecked;
    /** The last explicit add/replace/remove result is more specific than a later passive re-render. */
    private boolean codexJustChanged;
    private CodexMcpClient.RegistrationStatus codexRegistration = CodexMcpClient.RegistrationStatus.INDETERMINATE;
    private String codexOutcome = "";
    private ClaudeMcpClient claude;
    private boolean claudeChecked;
    private boolean claudeJustChanged;
    private ClaudeMcpClient.RegistrationStatus claudeRegistration = ClaudeMcpClient.RegistrationStatus.INDETERMINATE;
    private String claudeOutcome = "";

    private McpSetupDialog(Window owner, AppConfig config, Runnable onConfigurationChanged, Target initial,
                           Dialog.ModalityType modality) {
        super(owner, "Connect an AI client", modality);
        this.config = config;
        this.onConfigurationChanged = onConfigurationChanged == null ? () -> { } : onConfigurationChanged;
        this.target = new JComboBox<>(Target.values());
        this.target.setSelectedItem(initial == null ? Target.GENERIC : initial);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        buildUi();
        refreshReadiness();
        pack();
        // Keep the registration/naming disclosure in the first view on ordinary displays. The body remains
        // scrollable when a short screen cannot accommodate this height.
        int screenCappedHeight = Math.max(getHeight(), Toolkit.getDefaultToolkit().getScreenSize().height - 80);
        int initialHeight = Math.min(screenCappedHeight, Math.max(680, getHeight()));
        setMinimumSize(new Dimension(660, initialHeight));
        setSize(Math.max(660, getWidth()), initialHeight);
        setLocationRelativeTo(owner);
    }

    /** Open from the Start Page (modeless) or Settings ▸ Assistant (document-modal over Settings only). */
    public static void show(Window owner, AppConfig config, Runnable onConfigurationChanged, Target initial,
                            boolean modalOverOwner) {
        Dialog.ModalityType modality = modalOverOwner ? Dialog.ModalityType.DOCUMENT_MODAL : Dialog.ModalityType.MODELESS;
        new McpSetupDialog(owner, config, onConfigurationChanged, initial, modality).setVisible(true);
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
        target.addActionListener(e -> {
            rememberSetup();
            refreshClientStatus();
        });
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
        clientActions.setOpaque(false);
        clientActions.setAlignmentX(LEFT_ALIGNMENT);
        body.add(clientActions);
        body.add(Box.createVerticalStrut(4));
        genericConfiguration.setVisible(false);
        body.add(genericConfiguration);
        body.add(Box.createVerticalStrut(4));
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

    private static JPanel section(String heading, JComponent value) {
        JPanel panel = new JPanel(new BorderLayout(8, 2));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setOpaque(false);
        JLabel label = new JLabel(heading);
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
        panel.add(label, BorderLayout.NORTH);
        panel.add(value instanceof JLabel text ? noteLabel(text) : value, BorderLayout.CENTER);
        return panel;
    }

    /** Long commands stay readable and copyable without a horizontal scroll or a shell reinterpretation. */
    private static JTextArea commandBox() {
        return borderedTextBox(3, 68);
    }

    /** The generic record is intentionally larger than a command: its argument vector must be inspectable. */
    private static JTextArea configurationBox() {
        return borderedTextBox(5, 68);
    }

    /** Confirmation is deliberately wider: a local Java path must be inspectable before a client CLI runs. */
    private static JTextArea confirmationCommandBox() {
        return borderedTextBox(4, 108);
    }

    private static JTextArea borderedTextBox(int rows, int columns) {
        JTextArea field = new JTextArea(rows, columns);
        field.setEditable(false);
        field.setLineWrap(true);
        field.setWrapStyleWord(true);
        java.awt.Font base = UIManager.getFont("TextField.font");
        if (base == null) base = field.getFont();
        field.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, base.getSize()));
        field.setBorder(BorderFactory.createCompoundBorder(UIManager.getBorder("TextField.border"),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        field.setAlignmentX(LEFT_ALIGNMENT);
        return field;
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
        launcherIdentity = command == null ? "" : jbang.isPresent() ? "installed JBang launcher" : "packaged application";
        if (command == null) {
            commandStatus.setText("No supported local launcher was found. Install with JBang, then reopen this setup.");
        } else {
            commandStatus.setText((jbang.isPresent() ? "Ready to run installed JBang command: "
                    : "Ready to run this packaged application: ") + display(command.command()));
        }
        commandStatus.setCaretPosition(0);
        checkBridge.setEnabled(local.canProbe() && command != null);
        refreshClientStatus();
        revalidate();
        repaint();
    }

    private void refreshClientStatus() {
        Target selected = (Target) target.getSelectedItem();
        clientActions.removeAll();
        genericConfiguration.setVisible(false);
        genericConfiguration.setText("");
        if (selected == Target.CODEX) {
            refreshCodexStatus();
        } else if (selected == Target.CLAUDE_CODE) {
            refreshClaudeStatus();
        } else if (selected == Target.CLAUDE_DESKTOP) {
            refreshClaudeDesktopStatus();
        } else {
            refreshGenericStatus();
        }
        clientActions.revalidate();
        clientActions.repaint();
    }

    private void refreshCodexStatus() {
        codex = CodexMcpClient.detect().orElse(null); // PATH inspection only: no Codex process starts on screen open.
        if (codex == null) {
            clientStatus.setText("Codex CLI was not found on PATH. Install Codex, then use the copyable registration "
                    + "command; this analyser has not changed Codex.");
            if (command != null) clientActions.add(action("Copy Codex command", this::copyCodexCommand));
            return;
        }

        if (codexChecked) {
            clientStatus.setText(codexJustChanged && !codexOutcome.isBlank() ? codexOutcome : switch (codexRegistration) {
                case PRESENT -> "Codex lists the named registration. This proves its configuration only—not a connected "
                        + "client session or an approved tool call; another scope may take precedence.";
                case ABSENT -> "Codex has no registration named fluxtion-analyser.";
                case INDETERMINATE -> codexOutcome.isBlank()
                        ? "Codex has not been checked in this setup session."
                        : codexOutcome;
            });
        } else if (config.mcpCodexRegistrationInstalled) {
            clientStatus.setText("This analyser last recorded a successful Codex registration. Check Codex to confirm "
                    + "what it has now; a past command cannot prove a current client session.");
        } else {
            clientStatus.setText("Codex CLI is available. Check its named registration or explicitly add one; opening this "
                    + "screen has not run Codex.");
        }

        clientActions.add(action("Check Codex registration", this::checkCodexRegistration));
        if (command != null) {
            boolean replace = codexChecked && codexRegistration == CodexMcpClient.RegistrationStatus.PRESENT;
            clientActions.add(Box.createHorizontalStrut(8));
            clientActions.add(action(replace ? "Replace Codex registration…" : "Register with Codex…",
                    () -> confirmCodexChange(replace ? CodexChange.REPLACE : CodexChange.ADD)));
            if (replace || config.mcpCodexRegistrationInstalled) {
                clientActions.add(Box.createHorizontalStrut(8));
                clientActions.add(action("Remove Codex registration…", () -> confirmCodexChange(CodexChange.REMOVE)));
            }
            if (codexChecked && codexRegistration == CodexMcpClient.RegistrationStatus.INDETERMINATE) {
                clientActions.add(Box.createHorizontalStrut(8));
                clientActions.add(action("Copy Codex command", this::copyCodexCommand));
            }
        }
    }

    private static JButton action(String text, Runnable run) {
        JButton button = new JButton(text);
        button.addActionListener(e -> run.run());
        return button;
    }

    private void checkCodexRegistration() {
        if (codex == null) return;
        rememberSetup();
        setClientActionsEnabled(false);
        clientStatus.setText("Checking only Codex's named registration…");
        CodexMcpClient toCheck = codex;
        Background.run(toCheck::registration, this::showCodexRegistration,
                error -> showCodexResult(new CodexMcpClient.Result(CodexMcpClient.Status.LAUNCH_FAILED,
                        "Codex could not be checked; its configuration was not changed."), false));
    }

    private void showCodexRegistration(CodexMcpClient.Registration registration) {
        codexChecked = true;
        codexJustChanged = false;
        codexRegistration = registration.status();
        codexOutcome = registration.detail();
        // A current CLI check can see a project-scoped entry. Only our successful add/replace/remove command
        // earns the persisted "last command succeeded" reminder; a check is useful evidence, not that claim.
        refreshClientStatus();
    }

    private void confirmCodexChange(CodexChange change) {
        if (codex == null || (command == null && change != CodexChange.REMOVE)) return;
        List<String> commands = new ArrayList<>();
        String verb;
        if (change == CodexChange.ADD) {
            commands.add(CodexMcpClient.shellDisplay(codex.addCommand(command)));
            verb = "add";
        } else if (change == CodexChange.REPLACE) {
            commands.add("1. " + CodexMcpClient.shellDisplay(codex.removeCommand()));
            commands.add("2. " + CodexMcpClient.shellDisplay(codex.addCommand(command)));
            verb = "replace";
        } else {
            commands.add(CodexMcpClient.shellDisplay(codex.removeCommand()));
            verb = "remove";
        }
        JTextArea exact = confirmationCommandBox();
        exact.setText(String.join("\n", commands));
        int choice = showWideRegistrationConfirmation("Confirm Codex registration", new Object[]{
                        "Codex will " + verb + " only the registration named fluxtion-analyser in its shared local "
                                + "configuration. The bridge discovers the per-run endpoint and token itself; neither is in this command.",
                        "Cancel means Codex is not started and no configuration is written.", exact});
        if (choice != JOptionPane.OK_OPTION) return;
        rememberSetup();
        setClientActionsEnabled(false);
        clientStatus.setText("Codex is applying the confirmed registration change…");
        CodexMcpClient client = codex;
        Background.run(() -> switch (change) {
                    case ADD -> client.add(command);
                    case REPLACE -> client.replace(command);
                    case REMOVE -> client.remove();
                }, result -> showCodexResult(result, change != CodexChange.REMOVE),
                error -> showCodexResult(new CodexMcpClient.Result(CodexMcpClient.Status.LAUNCH_FAILED,
                        "Codex could not start; its configuration was not changed."), false));
    }

    private void showCodexResult(CodexMcpClient.Result result, boolean installedOnSuccess) {
        codexChecked = true;
        codexJustChanged = true;
        if (result.successful()) {
            codexRegistration = installedOnSuccess ? CodexMcpClient.RegistrationStatus.PRESENT
                    : CodexMcpClient.RegistrationStatus.ABSENT;
            codexOutcome = installedOnSuccess
                    ? "Codex registration installed. This analyser has not observed a connected client or tool call."
                    : "Codex registration removed. This analyser has not changed any other Codex server.";
            if (config.mcpCodexRegistrationInstalled != installedOnSuccess) {
                config.mcpCodexRegistrationInstalled = installedOnSuccess;
                onConfigurationChanged.run();
            }
        } else {
            codexRegistration = CodexMcpClient.RegistrationStatus.INDETERMINATE;
            codexOutcome = result.detail() + " Copy the command and inspect it in a terminal if you need to diagnose it.";
        }
        refreshClientStatus();
    }

    private void copyCodexCommand() {
        if (command == null) return;
        List<String> copy = codex == null ? CodexMcpClient.addCommandForCopy(command) : codex.addCommand(command);
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new StringSelection(CodexMcpClient.shellDisplay(copy)), null);
            clientStatus.setText("Copied the Codex registration command. Running it is your explicit choice.");
        } catch (IllegalStateException | java.awt.HeadlessException e) {
            clientStatus.setText("Could not reach the clipboard. The exact command is shown when you choose Register with Codex.");
        }
    }

    private void setClientActionsEnabled(boolean enabled) {
        for (java.awt.Component component : clientActions.getComponents()) component.setEnabled(enabled);
    }

    /** Returns whether this explicit setup action changed the small, redacted local reminder. */
    private boolean rememberSetup() {
        Target selected = (Target) target.getSelectedItem();
        String chosen = selected == null ? "" : selected.name();
        if (!chosen.equals(config.mcpSetupTarget) || !launcherIdentity.equals(config.mcpLauncherIdentity)) {
            config.mcpSetupTarget = chosen;
            config.mcpLauncherIdentity = launcherIdentity;
            onConfigurationChanged.run();
            return true;
        }
        return false;
    }

    private enum CodexChange { ADD, REPLACE, REMOVE }

    private void refreshClaudeStatus() {
        claude = ClaudeMcpClient.detect().orElse(null); // PATH inspection only: opening setup never starts Claude Code.
        if (claude == null) {
            clientStatus.setText("Claude Code CLI was not found on PATH. Install Claude Code, then use one of the "
                    + "copyable commands; this analyser has not changed Claude Code or any project file.");
            addClaudeCopyActions();
            return;
        }

        if (claudeChecked) {
            clientStatus.setText(claudeJustChanged && !claudeOutcome.isBlank() ? claudeOutcome : switch (claudeRegistration) {
                case PRESENT -> "Claude Code lists the named registration. This proves its configuration only—not a "
                        + "connected session or an approved tool call; another scope may take precedence.";
                case ABSENT -> "Claude Code has no registration named fluxtion-analyser.";
                case INDETERMINATE -> claudeOutcome.isBlank()
                        ? "Claude Code has not been checked in this setup session."
                        : claudeOutcome;
            });
        } else if (config.mcpClaudeRegistrationInstalled) {
            clientStatus.setText("This analyser last recorded a successful Claude Code user registration. Check Claude "
                    + "Code to confirm what it has now; a past command cannot prove a current session.");
        } else {
            clientStatus.setText("Claude Code CLI is available. Check its named registration or explicitly add one; "
                    + "opening this screen has not run Claude Code.");
        }

        clientActions.add(action("Check Claude Code registration", this::checkClaudeRegistration));
        if (command != null) {
            boolean replace = claudeChecked && claudeRegistration == ClaudeMcpClient.RegistrationStatus.PRESENT;
            clientActions.add(Box.createHorizontalStrut(8));
            clientActions.add(action(replace ? "Replace Claude Code registration…" : "Register with Claude Code…",
                    () -> confirmClaudeChange(replace ? ClaudeChange.REPLACE : ClaudeChange.ADD)));
            if (replace || config.mcpClaudeRegistrationInstalled) {
                clientActions.add(Box.createHorizontalStrut(8));
                clientActions.add(action("Remove Claude Code registration…", () -> confirmClaudeChange(ClaudeChange.REMOVE)));
            }
            if (claudeChecked && claudeRegistration == ClaudeMcpClient.RegistrationStatus.INDETERMINATE) {
                clientActions.add(Box.createHorizontalStrut(8));
                clientActions.add(action("Copy Claude Code command", () -> copyClaudeCommand(false)));
            }
            clientActions.add(Box.createHorizontalStrut(8));
            clientActions.add(action("Copy project-scope command", () -> copyClaudeCommand(true)));
        }
    }

    private void addClaudeCopyActions() {
        if (command == null) return;
        clientActions.add(action("Copy Claude Code command", () -> copyClaudeCommand(false)));
        clientActions.add(Box.createHorizontalStrut(8));
        clientActions.add(action("Copy project-scope command", () -> copyClaudeCommand(true)));
    }

    private void checkClaudeRegistration() {
        if (claude == null) return;
        rememberSetup();
        setClientActionsEnabled(false);
        clientStatus.setText("Checking Claude Code's named registration (this explicit check may start its configured bridge)…");
        ClaudeMcpClient toCheck = claude;
        Background.run(toCheck::registration, this::showClaudeRegistration,
                error -> showClaudeResult(new ClaudeMcpClient.Result(ClaudeMcpClient.Status.LAUNCH_FAILED,
                        "Claude Code could not be checked; its configuration was not changed."), false));
    }

    private void showClaudeRegistration(ClaudeMcpClient.Registration registration) {
        claudeChecked = true;
        claudeJustChanged = false;
        claudeRegistration = registration.status();
        claudeOutcome = registration.detail();
        // `claude mcp get` reports the effective name and can see local/project entries. It must not turn
        // that observation into a claim that this app's user-scope command succeeded.
        refreshClientStatus();
    }

    private void confirmClaudeChange(ClaudeChange change) {
        if (claude == null || (command == null && change != ClaudeChange.REMOVE)) return;
        List<String> commands = new ArrayList<>();
        String verb;
        if (change == ClaudeChange.ADD) {
            commands.add(ClaudeMcpClient.shellDisplay(claude.addCommand(command)));
            verb = "add";
        } else if (change == ClaudeChange.REPLACE) {
            commands.add("1. " + ClaudeMcpClient.shellDisplay(claude.removeCommand()));
            commands.add("2. " + ClaudeMcpClient.shellDisplay(claude.addCommand(command)));
            verb = "replace";
        } else {
            commands.add(ClaudeMcpClient.shellDisplay(claude.removeCommand()));
            verb = "remove";
        }
        JTextArea exact = confirmationCommandBox();
        exact.setText(String.join("\n", commands));
        int choice = showWideRegistrationConfirmation("Confirm Claude Code registration", new Object[]{
                        "Claude Code will " + verb + " only the user-scoped registration named fluxtion-analyser. "
                                + "The bridge discovers the per-run endpoint and token itself; neither is in this command.",
                        "Cancel means Claude Code is not started and no configuration is written.", exact});
        if (choice != JOptionPane.OK_OPTION) return;
        rememberSetup();
        setClientActionsEnabled(false);
        clientStatus.setText("Claude Code is applying the confirmed user registration change…");
        ClaudeMcpClient client = claude;
        Background.run(() -> switch (change) {
                    case ADD -> client.add(command);
                    case REPLACE -> client.replace(command);
                    case REMOVE -> client.remove();
                }, result -> showClaudeResult(result, change != ClaudeChange.REMOVE),
                error -> showClaudeResult(new ClaudeMcpClient.Result(ClaudeMcpClient.Status.LAUNCH_FAILED,
                        "Claude Code could not start; its configuration was not changed."), false));
    }

    private void showClaudeResult(ClaudeMcpClient.Result result, boolean installedOnSuccess) {
        claudeChecked = true;
        claudeJustChanged = true;
        if (result.successful()) {
            claudeRegistration = installedOnSuccess ? ClaudeMcpClient.RegistrationStatus.PRESENT
                    : ClaudeMcpClient.RegistrationStatus.ABSENT;
            claudeOutcome = installedOnSuccess
                    ? "Claude Code user registration installed. This analyser has not observed a connected client or tool call; "
                            + "a local or project registration with this name can take precedence."
                    : "Claude Code user registration removed. This analyser has not changed any local or project registration.";
            if (config.mcpClaudeRegistrationInstalled != installedOnSuccess) {
                config.mcpClaudeRegistrationInstalled = installedOnSuccess;
                onConfigurationChanged.run();
            }
        } else {
            claudeRegistration = ClaudeMcpClient.RegistrationStatus.INDETERMINATE;
            claudeOutcome = result.detail() + " Copy the command and inspect it in a terminal if you need to diagnose it.";
        }
        refreshClientStatus();
    }

    private void copyClaudeCommand(boolean projectScope) {
        if (command == null) return;
        List<String> copy;
        if (projectScope) {
            copy = ClaudeMcpClient.projectCommandForCopy(command);
        } else {
            copy = claude == null ? ClaudeMcpClient.userCommandForCopy(command) : claude.addCommand(command);
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new StringSelection(ClaudeMcpClient.shellDisplay(copy)), null);
            clientStatus.setText(projectScope
                    ? "Copied the project-scope command. Run it yourself from the chosen project root; this analyser will not write .mcp.json."
                    : "Copied the Claude Code user-registration command. Running it is your explicit choice.");
        } catch (IllegalStateException | java.awt.HeadlessException e) {
            clientStatus.setText("Could not reach the clipboard. The exact user command is shown when you choose Register with Claude Code.");
        }
    }

    private enum ClaudeChange { ADD, REPLACE, REMOVE }

    private void refreshClaudeDesktopStatus() {
        clientStatus.setText("Claude Desktop extensions need a portable bundled server. This analyser's safe bridge is "
                + "the exact JBang or Java command on this machine, so no extension is offered that guesses or duplicates "
                + "that launcher. Use Generic MCP setup to copy the resolved no-token configuration instead.");
        clientActions.add(action("Use Generic MCP setup", () -> target.setSelectedItem(Target.GENERIC)));
    }

    private void refreshGenericStatus() {
        if (command == null) {
            clientStatus.setText("No generic configuration can be made until this app has a supported local launcher. "
                    + "Install with JBang or run the packaged application, then reopen setup.");
            return;
        }
        genericConfiguration.setText(GenericMcpConfiguration.render(command));
        genericConfiguration.setCaretPosition(0);
        genericConfiguration.setVisible(true);
        clientStatus.setText("This is the complete standard stdio record for another MCP client. It contains the local "
                + "launcher only—never this analyser's endpoint, token, log, or client approval choice.");
        clientActions.add(action("Copy configuration", this::copyGenericConfiguration));
        clientActions.add(Box.createHorizontalStrut(8));
        clientActions.add(action("Save snippet…", this::saveGenericConfiguration));
    }

    private void copyGenericConfiguration() {
        if (genericConfiguration.getText().isBlank()) return;
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new StringSelection(genericConfiguration.getText()), null);
            clientStatus.setText("Copied the generic MCP configuration. Choosing where or whether to install it remains your decision.");
        } catch (IllegalStateException | java.awt.HeadlessException e) {
            clientStatus.setText("Could not reach the clipboard. Select the configuration field and copy it manually.");
        }
    }

    private void saveGenericConfiguration() {
        if (genericConfiguration.getText().isBlank()) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save generic MCP configuration snippet");
        chooser.setSelectedFile(new java.io.File("fluxtion-analyser-mcp.json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path selected = chooser.getSelectedFile().toPath();
        if (Files.exists(selected)) {
            int replace = JOptionPane.showConfirmDialog(this,
                    "Replace the selected file?\n\n" + selected.getFileName() + "\n\n"
                            + "Only this generic MCP snippet will be written. No client configuration location is inferred.",
                    "Replace snippet", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (replace != JOptionPane.OK_OPTION) return;
        }
        try {
            Files.writeString(selected, genericConfiguration.getText() + System.lineSeparator());
            clientStatus.setText("Saved the generic MCP snippet to the file you chose. No client was configured.");
        } catch (IOException e) {
            clientStatus.setText("Could not save the generic MCP snippet. Choose another writable file and retry.");
        }
    }

    /**
     * A registration command is a consent surface, so give the person a desktop-width view rather than
     * relying on JOptionPane's compact default. The field itself is also wide enough to keep a Java/jar
     * vector legible; both Codex and Claude Code use this same disclosure.
     */
    private int showWideRegistrationConfirmation(String title, Object[] message) {
        JOptionPane pane = new JOptionPane(message, JOptionPane.WARNING_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        JDialog confirmation = pane.createDialog(this, title);
        int screenWidth = Toolkit.getDefaultToolkit().getScreenSize().width;
        int desiredWidth = Math.min(960, Math.max(640, screenWidth - 64));
        if (confirmation.getWidth() < desiredWidth) {
            confirmation.setSize(desiredWidth, confirmation.getHeight());
        }
        confirmation.setLocationRelativeTo(this);
        confirmation.setVisible(true);
        Object choice = pane.getValue();
        return choice instanceof Integer value ? value : JOptionPane.CLOSED_OPTION;
    }

    private void confirmEnableTransport() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Enable local transport for this analyser?\n\n"
                        + "This saves the Assistant REST setting and starts a loopback-only endpoint with a fresh "
                        + "per-run token. The token is not copied into client configuration.",
                "Enable local transport", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return;
        config.assistantActionsRest = true;
        if (!rememberSetup()) onConfigurationChanged.run();
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
