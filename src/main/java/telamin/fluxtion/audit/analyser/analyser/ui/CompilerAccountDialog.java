package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.account.AccountDetails;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.Device;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.Me;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.DeviceStart;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.Outcome;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.Usage;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerCredential;
import telamin.fluxtion.audit.analyser.analyser.account.CredentialStore;
import telamin.fluxtion.audit.analyser.analyser.account.CredentialStores;
import telamin.fluxtion.audit.analyser.analyser.account.ServiceAddress;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.core.Background;
import telamin.fluxtion.audit.analyser.analyser.core.ReleaseNotes;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * CA-1 — AI ▸ Compiler account…: connect this analyser to a compiler account by device sign-in; show the account (type,
 * role, plan), its credits, this device and the account's other devices; copy a support summary; disconnect, or revoke
 * this device or another one the person picks.
 *
 * <p><b>Reads, and revokes only on request.</b> The only calls are the device sign-in the person approves in their
 * browser, reads of the account, usage and device list, and the revoke of a credential the person chose
 * ({@link CompilerAccountClient}).
 *
 * <p><b>Secrets.</b> The device code and the credential are package-private to {@code account}; this dialog cannot
 * read either, so it can neither display nor log them. It shows the user code, which is meant to be read.
 *
 * <p><b>Rule 9.</b> Like {@link FluxtionKeyDialog}, this is dialog-local: the connection is a fact of the OS credential
 * store, read when the dialog opens, and the sign-in is an interaction that lives and dies with the dialog (closing it
 * cancels). No other surface renders the account and {@code context} does not carry it, so there is no second reader to
 * drift. Showing it anywhere else is the point at which it becomes session state: a node, an effect and a fact.
 */
public final class CompilerAccountDialog extends JDialog {

    static final String TITLE = "Compiler account";
    static final String CONNECT = "Connect compiler account";
    static final String REFRESH = "Refresh";
    static final String DISCONNECT = "Disconnect";
    static final String REVOKE = "Revoke this device";
    static final String CANCEL_SIGN_IN = "Cancel sign-in";
    static final String OPEN_AGAIN = "Open browser again";
    static final String COPY_DETAILS = "Copy details";
    static final String REVOKE_SELECTED = "Revoke selected";
    static final String THIS_MACHINE = " (this machine)";
    static final String DELETION_NOTE = "Deleting the account is not available in the app yet; ask the service's support.";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final AppConfig config;
    private final Runnable saveConfig;
    private final CredentialStore store;
    private final CompilerAccountClient client;
    private final Consumer<URI> browser;

    private final JLabel connection = UiText.label(" ");
    private final JLabel storage = UiText.label(" ");
    private final JTextField address = new JTextField(30);
    private final JLabel codeLabel = UiText.label(" ");
    private final JLabel codeHint = UiText.label(" ");
    private final JLabel codeExpiry = UiText.label(" ");
    private final JPanel signIn = new JPanel();
    private final JPanel accountPanel = new JPanel();
    private final JLabel accountLine = UiText.label(" ");
    private final JLabel planLine = UiText.label(" ");
    private final JLabel creditsLine = UiText.label(" ");
    private final JLabel resetLine = UiText.label(" ");
    private final JLabel deviceLine = UiText.label(" ");
    private final DefaultTableModel deviceModel = new DefaultTableModel(new Object[]{"Name", "Kind", "Created (UTC)"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable deviceTable = UiText.plainCells(new JTable(deviceModel));
    private final JButton revokeSelected = UiText.button(REVOKE_SELECTED);
    private final JButton copyDetails = UiText.button(COPY_DETAILS);
    private final JLabel message = UiText.label(" ");
    private final JButton connect = UiText.button(CONNECT);
    private final JButton refresh = UiText.button(REFRESH);
    private final JButton disconnect = UiText.button(DISCONNECT);
    private final JButton revoke = UiText.button(REVOKE);
    private final JButton cancelSignIn = UiText.button(CANCEL_SIGN_IN);
    private final JButton openAgain = UiText.button(OPEN_AGAIN);

    private CompilerCredential connected;           // public identifiers only reach this class; never the bearer
    private DeviceStart pending;
    private AtomicBoolean cancelled = new AtomicBoolean();
    private Future<?> signInTask;
    private boolean busy;
    private Usage usage;                            // the last reads, for Copy details; null until read
    private Me me;
    private final List<Device> shown = new ArrayList<>();
    /** Asks the person to confirm (title, question); a frame test answers it without a nested modal. */
    java.util.function.BiPredicate<String, String> confirm = (title, question) -> JOptionPane.showConfirmDialog(this,
            UiText.inert(question), UiText.inert(title), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION;
    /** Where Copy details puts its text; a frame test reads it without the system clipboard. */
    Consumer<String> clipboard = text -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);

    CompilerAccountDialog(Window owner, AppConfig config, Runnable saveConfig, CredentialStore store,
                          CompilerAccountClient client, Consumer<URI> browser) {
        super(owner, TITLE, ModalityType.APPLICATION_MODAL);
        this.config = config;
        this.saveConfig = saveConfig;
        this.store = store;
        this.client = client;
        this.browser = browser;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(content());
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                cancelSignIn();
            }
        });
        render();
        pack();
        setMinimumSize(new Dimension(600, getPreferredSize().height));
        setLocationRelativeTo(owner);
        loadStored();
    }

    /** Opens the one owner of the compiler-account connection, with this machine's credential store. */
    public static void show(Window owner, AppConfig config, Runnable saveConfig) {
        new CompilerAccountDialog(owner, config, saveConfig, CredentialStores.forThisMachine(),
                CompilerAccountClient.standard(), CompilerAccountDialog::browse).setVisible(true);
    }

    private static void browse(URI uri) {
        try {
            java.awt.Desktop.getDesktop().browse(uri);
        } catch (Exception ignored) {
            // the address and code stay on screen, so the person can open it themselves
        }
    }

    private JPanel content() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        add(root, connection);
        add(root, storage);
        root.add(Box.createVerticalStrut(10));

        JPanel addressRow = new JPanel(new BorderLayout(8, 0));
        addressRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        addressRow.add(UiText.label("Service address:"), BorderLayout.WEST);
        address.setText(config.compilerServiceUrl == null ? "" : config.compilerServiceUrl);
        address.setToolTipText("The compiler service's base address, for example https://compiler.example.com");
        addressRow.add(address, BorderLayout.CENTER);
        addressRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, address.getPreferredSize().height));
        root.add(addressRow);
        root.add(Box.createVerticalStrut(10));

        signIn.setLayout(new BoxLayout(signIn, BoxLayout.Y_AXIS));
        signIn.setAlignmentX(Component.LEFT_ALIGNMENT);
        codeLabel.setFont(codeLabel.getFont().deriveFont(Font.BOLD, codeLabel.getFont().getSize2D() * 1.6f));
        add(signIn, codeLabel);
        add(signIn, codeHint);
        add(signIn, codeExpiry);
        JPanel signInButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        signInButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        openAgain.addActionListener(e -> {
            if (pending != null) openBrowser(pending);
        });
        cancelSignIn.addActionListener(e -> {
            cancelSignIn();
            message.setText("Sign-in cancelled. Nothing was stored.");
            render();
        });
        signInButtons.add(openAgain);
        signInButtons.add(cancelSignIn);
        signIn.add(signInButtons);
        root.add(signIn);

        accountPanel.setLayout(new BoxLayout(accountPanel, BoxLayout.Y_AXIS));
        accountPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(accountPanel, heading("Account"));
        add(accountPanel, accountLine);
        add(accountPanel, planLine);
        accountPanel.add(Box.createVerticalStrut(6));
        add(accountPanel, heading("Credits"));
        add(accountPanel, creditsLine);
        add(accountPanel, resetLine);
        accountPanel.add(Box.createVerticalStrut(6));
        add(accountPanel, heading("This device"));
        add(accountPanel, deviceLine);
        accountPanel.add(Box.createVerticalStrut(6));
        add(accountPanel, heading("Your devices"));
        deviceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        deviceTable.setFillsViewportHeight(true);
        deviceTable.getSelectionModel().addListSelectionListener(e -> render());
        JScrollPane devicesScroll = new JScrollPane(deviceTable);
        devicesScroll.setPreferredSize(new Dimension(520, deviceTable.getRowHeight() * 4 + 28));
        devicesScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, devicesScroll.getPreferredSize().height));
        add(accountPanel, devicesScroll);
        JPanel deviceButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        deviceButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        revokeSelected.setToolTipText("Ask the service to stop accepting the selected device's credential");
        revokeSelected.addActionListener(e -> revokeSelected());
        deviceButtons.add(revokeSelected);
        accountPanel.add(deviceButtons);
        add(accountPanel, UiText.label(DELETION_NOTE));
        root.add(accountPanel);
        root.add(Box.createVerticalStrut(6));
        add(root, message);
        root.add(Box.createVerticalStrut(12));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        connect.addActionListener(e -> startSignIn());
        refresh.setToolTipText("Read the plan and credits again; nothing is changed at the service");
        refresh.addActionListener(e -> refreshUsage());
        copyDetails.setToolTipText("Copy a plain-text summary for a support request; it never includes the credential");
        copyDetails.addActionListener(e -> copyDetails());
        disconnect.setToolTipText("Delete the credential from this machine; the service still accepts it until revoked");
        disconnect.addActionListener(e -> disconnect());
        revoke.setToolTipText("Ask the service to stop accepting this device's credential, then delete it here");
        revoke.addActionListener(e -> revoke());
        JButton close = UiText.button("Close");
        close.addActionListener(e -> dispose());
        buttons.add(connect);
        buttons.add(refresh);
        buttons.add(copyDetails);
        buttons.add(disconnect);
        buttons.add(revoke);
        buttons.add(close);
        root.add(buttons);
        return root;
    }

    private static JLabel heading(String text) {
        JLabel l = UiText.label(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    private static void add(JComponent parent, JComponent child) {
        child.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(child);
    }

    // ---- state ---------------------------------------------------------------------------------------------------

    private void render() {
        boolean signingIn = pending != null;
        connection.setText(connectionLine(connected, signingIn));
        storage.setText("Credential kept in: " + store.description());
        // connected, the field names the service that ISSUED the credential (which usage and revoke go to), not the
        // configured address: the two differ when the setting was changed after connecting
        if (connected != null && !connected.service().equals(address.getText())) address.setText(connected.service());
        address.setEditable(connected == null && !signingIn && !busy);
        boolean grew = signingIn && !signIn.isVisible();
        signIn.setVisible(signingIn);
        if (signingIn) {
            codeLabel.setText(pending.userCode());
            codeHint.setText(codeHint(pending));
            codeExpiry.setText(expiryHint(pending));
        }
        connect.setEnabled(connected == null && !signingIn && !busy);
        refresh.setEnabled(connected != null && !busy);
        disconnect.setEnabled(connected != null && !busy);
        revoke.setEnabled(connected != null && !busy);
        copyDetails.setEnabled(connected != null && !busy);
        revokeSelected.setEnabled(connected != null && !busy && deviceTable.getSelectedRow() >= 0);
        if (connected == null) {
            usage = null;
            me = null;
            if (deviceModel.getRowCount() > 0) showDevices(List.of());
        }
        boolean shows = connected != null;
        grew |= shows && !accountPanel.isVisible();
        accountPanel.setVisible(shows);
        if (shows) {
            accountLine.setText(UiText.inert(accountText(me, usage, connected)));
            planLine.setText(usage == null ? " " : UiText.inert(planText(usage)));
            creditsLine.setText(usage == null ? " " : UiText.inert(creditsText(usage)));
            resetLine.setText(usage == null ? " " : UiText.inert(resetText(usage)));
            deviceLine.setText(UiText.inert(deviceText(connected.credentialId(), thisDeviceName())));
        }
        if (isDisplayable()) {
            revalidate();
            if (grew || getPreferredSize().height > getHeight()) {   // the sign-in rows must not push the buttons off
                Dimension pref = getPreferredSize();
                setSize(Math.max(getWidth(), pref.width), Math.max(getHeight(), pref.height));
            }
            repaint();
        }
    }

    private void loadStored() {
        busy = true;
        render();
        Background.run(() -> {
            try {
                return store.load();
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage());
            }
        }, (Optional<CompilerCredential> found) -> {
            busy = false;
            connected = found.orElse(null);
            render();
            if (connected != null) refreshUsage();
        }, err -> {
            busy = false;
            message.setText(UiText.inert("Could not read the stored credential: " + err.getMessage()));
            render();
        });
    }

    private void startSignIn() {
        String refusal = ServiceAddress.refusal(address.getText());
        if (refusal != null) {
            message.setText(refusal);
            return;
        }
        String base = ServiceAddress.require(address.getText());
        if (!base.equals(config.compilerServiceUrl)) {
            config.compilerServiceUrl = base;
            saveConfig.run();
        }
        message.setText("Asking the service for a sign-in code…");
        busy = true;
        render();
        AtomicBoolean flag = new AtomicBoolean();
        cancelled = flag;
        signInTask = Background.run(() -> {
            try {
                return client.start(base);
            } catch (CompilerAccountClient.AccountException e) {
                throw new IllegalStateException(e.getMessage());
            }
        }, started -> {
            busy = false;
            if (flag.get()) {
                render();
                return;
            }
            pending = started;
            message.setText("Waiting for you to approve the sign-in in the browser…");
            render();
            openBrowser(started);
            awaitApproval(started, flag);
        }, err -> {
            busy = false;
            message.setText(UiText.inert(err.getMessage()));
            render();
        });
    }

    private void awaitApproval(DeviceStart started, AtomicBoolean flag) {
        signInTask = Background.run(() -> client.await(started, flag::get), outcome -> {
            if (flag.get() || pending != started) return;   // cancelled or superseded: its answer is not news
            pending = null;
            if (outcome instanceof CompilerAccountClient.Approved a) {
                storeCredential(a.credential());
            } else {
                message.setText(outcomeText(outcome));
                render();
            }
        }, err -> {
            if (pending == started) pending = null;
            message.setText("The sign-in stopped unexpectedly.");
            render();
        });
    }

    private void storeCredential(CompilerCredential credential) {
        busy = true;
        message.setText("Approved. Storing the credential…");
        render();
        Background.run(() -> {
            try {
                store.save(credential);
                return credential;
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage());
            }
        }, saved -> {
            busy = false;
            connected = saved;
            message.setText("Connected.");
            render();
            refreshUsage();
        }, err -> {
            busy = false;
            message.setText(UiText.inert("Approved, but the credential could not be stored: " + err.getMessage()
                    + ". Revoke the new device from your account page."));
            render();
        });
    }

    private void refreshUsage() {
        refreshUsage(null);
    }

    /** Read the account, credits and devices again; {@code done}, when given, is the message once they are read. */
    private void refreshUsage(String done) {
        CompilerCredential c = connected;
        if (c == null) return;
        busy = true;
        message.setText("Reading the account…");
        render();
        Background.run(() -> {
            Usage u;
            try {
                u = client.usage(c);
            } catch (CompilerAccountClient.AccountException e) {
                throw new IllegalStateException(e.getMessage());
            }
            // the account and device reads are extras: either failing still shows the credits, and says what is missing
            List<String> missing = new ArrayList<>();
            Me m = null;
            List<Device> d = null;
            try {
                m = client.me(c);
            } catch (CompilerAccountClient.AccountException e) {
                missing.add(e.getMessage());
            }
            try {
                d = client.devices(c);
            } catch (CompilerAccountClient.AccountException e) {
                missing.add(e.getMessage());
            }
            return new Read(u, m, d, String.join(". ", missing));
        }, (Read r) -> {
            busy = false;
            if (connected != c) return;   // disconnected or revoked while reading: the answer is not news
            usage = r.usage;
            me = r.me;
            if (r.devices != null) showDevices(r.devices);
            message.setText(!r.missing.isEmpty() ? UiText.inert(r.missing) : done == null ? " " : UiText.inert(done));
            render();
        }, err -> {
            busy = false;
            message.setText(UiText.inert(err.getMessage()));
            render();
        });
    }

    private record Read(Usage usage, Me me, List<Device> devices, String missing) { }

    private void showDevices(List<Device> devices) {
        shown.clear();
        shown.addAll(devices);
        deviceModel.setRowCount(0);
        String own = connected == null ? null : connected.credentialId();
        for (Device d : devices) deviceModel.addRow(deviceRow(d, d.credentialId().equals(own)));
    }

    private String thisDeviceName() {
        return shown.stream().filter(d -> d.credentialId().equals(connected.credentialId())).map(Device::name).findFirst().orElse(null);
    }

    private void copyDetails() {
        CompilerCredential c = connected;
        if (c == null) return;
        clipboard.accept(AccountDetails.text(c, me, usage, thisDeviceName(), ReleaseNotes.version(), Instant.now()));
        message.setText("Details copied to the clipboard. They never include the credential.");
    }

    private void revokeSelected() {
        CompilerCredential c = connected;
        int row = deviceTable.getSelectedRow();
        if (c == null || row < 0 || row >= shown.size()) return;
        Device d = shown.get(row);
        if (d.credentialId().equals(c.credentialId())) {   // this machine's own: exactly Revoke this device
            revoke();
            return;
        }
        String label = d.name().isEmpty() ? d.credentialId() : d.name();
        if (!confirm.test(REVOKE_SELECTED, "Ask the service to stop accepting the device \"" + label + "\" ("
                + d.credentialId() + ")?\nIt stops working at once; this machine stays connected.")) return;
        busy = true;
        message.setText("Revoking…");
        render();
        Background.run(() -> {
            try {
                return client.revoke(c, d.credentialId());
            } catch (CompilerAccountClient.AccountException e) {
                throw new IllegalStateException(e.getMessage());
            }
        }, revokedNow -> {
            busy = false;
            refreshUsage(revokedNow ? "Revoked \"" + label + "\"." : "The service no longer had \"" + label + "\".");
        }, err -> {
            busy = false;
            message.setText(UiText.inert(err.getMessage()));
            render();
        });
    }

    private void disconnect() {
        if (!confirm.test(DISCONNECT, "Delete the credential from this machine?\n"
                + "The service keeps accepting it until you revoke it.")) return;
        deleteLocal("Disconnected. The credential was deleted from this machine.");
    }

    private void revoke() {
        CompilerCredential c = connected;
        if (c == null) return;
        if (!confirm.test(REVOKE, "Ask the service to stop accepting this device, then delete its credential here?")) return;
        busy = true;
        message.setText("Revoking…");
        render();
        Background.run(() -> {
            try {
                return client.revoke(c);
            } catch (CompilerAccountClient.AccountException e) {
                throw new IllegalStateException(e.getMessage());
            }
        }, revokedNow -> {
            busy = false;
            deleteLocal(revokeText(revokedNow));
        }, err -> {
            busy = false;
            message.setText(UiText.inert(err.getMessage() + " Still connected."));
            render();
        });
    }

    private void deleteLocal(String done) {
        busy = true;
        render();
        Background.run(() -> {
            try {
                store.delete();
                return Boolean.TRUE;
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage());
            }
        }, ok -> {
            busy = false;
            connected = null;
            message.setText(done);
            render();
        }, err -> {
            busy = false;
            message.setText(UiText.inert("The credential could not be deleted: " + err.getMessage()));
            render();
        });
    }

    private void cancelSignIn() {
        cancelled.set(true);
        pending = null;
        busy = false;
        Future<?> task = signInTask;
        if (task != null) task.cancel(true);
    }

    private void openBrowser(DeviceStart started) {
        if (ServiceAddress.browsable(started.browserUri())) browser.accept(URI.create(started.browserUri()));
    }

    // ---- words: pure, so they are tested without a screen ----------------------------------------------------------

    static String connectionLine(CompilerCredential connected, boolean signingIn) {
        if (connected != null) return "Connected to " + connected.service() + " as account " + connected.accountId() + ".";
        if (signingIn) return "Signing in…";
        return "Not connected.";
    }

    static String codeHint(DeviceStart s) {
        return "Enter this code at " + s.verificationUri();
    }

    static String expiryHint(DeviceStart s) {
        long minutes = Math.max(1, (s.expiresInSeconds() + 59) / 60);
        return "The browser was asked to open that page. The code expires in about " + minutes
                + (minutes == 1 ? " minute." : " minutes.");
    }

    static String outcomeText(Outcome outcome) {
        if (outcome instanceof CompilerAccountClient.Approved) return "Connected.";
        if (outcome instanceof CompilerAccountClient.Denied) return "The sign-in was denied in the browser. Nothing was stored.";
        if (outcome instanceof CompilerAccountClient.Expired) return "The code expired before it was approved. Connect again for a new code.";
        if (outcome instanceof CompilerAccountClient.Cancelled) return "Sign-in cancelled. Nothing was stored.";
        if (outcome instanceof CompilerAccountClient.Failed f) return f.reason() + ". Nothing was stored.";
        return "";
    }

    static String accountText(Me me, Usage u, CompilerCredential c) {
        String account = u != null ? u.accountId() : me != null ? me.selectedAccount() : c.accountId();
        if (me == null) return "Account " + account;
        return "Account " + account + " · " + me.type() + " · Your role: " + (me.role() == null ? "unknown" : me.role());
    }

    static String planText(Usage u) {
        return "Plan: " + u.plan() + " · " + (u.active() ? "Active" : "Inactive");
    }

    static String creditsText(Usage u) {
        return "Remaining: " + u.available() + " · Used: " + u.consumed() + " · In progress: " + u.reserved()
                + " · Generations: " + u.generations() + " · Failures: " + u.failures();
    }

    /** A plan without a next reset is a one-time allowance: said as such, never a guessed date. */
    static String resetText(Usage u) {
        return u.nextReset() == null ? "One-time allowance (no reset)" : "Next reset: " + u.nextReset();
    }

    static String deviceText(String credentialId, String name) {
        return (name == null || name.isEmpty() ? "Not in the device list" : name) + " · credential " + credentialId;
    }

    static Object[] deviceRow(Device d, boolean thisMachine) {
        String name = (d.name().isEmpty() ? d.credentialId() : d.name()) + (thisMachine ? THIS_MACHINE : "");
        return new Object[]{name, d.kind(), d.createdAt() > 0 ? DAY.format(Instant.ofEpochMilli(d.createdAt())) : ""};
    }

    static String revokeText(boolean revokedNow) {
        return revokedNow
                ? "Revoked. The service no longer accepts this device, and its credential was deleted here."
                : "The service already did not accept this device's credential; it was deleted here.";
    }
}
