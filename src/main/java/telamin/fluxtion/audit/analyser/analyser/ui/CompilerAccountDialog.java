package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.DeviceStart;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.Outcome;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient.Usage;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerCredential;
import telamin.fluxtion.audit.analyser.analyser.account.CredentialStore;
import telamin.fluxtion.audit.analyser.analyser.account.CredentialStores;
import telamin.fluxtion.audit.analyser.analyser.account.ServiceAddress;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.core.Background;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * CA-1 — AI ▸ Compiler account…: connect this analyser to a compiler account by device sign-in, show the account's
 * plan and credits, disconnect, or revoke this device.
 *
 * <p><b>Reads, never operates.</b> The only calls are the device sign-in the person approves in their browser, a
 * usage read, and the revoke of this device's own credential ({@link CompilerAccountClient}).
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
    private final JLabel usageLine = UiText.label(" ");
    private final JLabel resetLine = UiText.label(" ");
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
    /** Asks the person to confirm (title, question); a frame test answers it without a nested modal. */
    java.util.function.BiPredicate<String, String> confirm = (title, question) -> JOptionPane.showConfirmDialog(this,
            UiText.inert(question), UiText.inert(title), JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION;

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

        add(root, usageLine);
        add(root, resetLine);
        root.add(Box.createVerticalStrut(6));
        add(root, message);
        root.add(Box.createVerticalStrut(12));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        connect.addActionListener(e -> startSignIn());
        refresh.setToolTipText("Read the plan and credits again; nothing is changed at the service");
        refresh.addActionListener(e -> refreshUsage());
        disconnect.setToolTipText("Delete the credential from this machine; the service still accepts it until revoked");
        disconnect.addActionListener(e -> disconnect());
        revoke.setToolTipText("Ask the service to stop accepting this device's credential, then delete it here");
        revoke.addActionListener(e -> revoke());
        JButton close = UiText.button("Close");
        close.addActionListener(e -> dispose());
        buttons.add(connect);
        buttons.add(refresh);
        buttons.add(disconnect);
        buttons.add(revoke);
        buttons.add(close);
        root.add(buttons);
        return root;
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
        if (connected == null) {
            usageLine.setText(" ");
            resetLine.setText(" ");
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
        CompilerCredential c = connected;
        if (c == null) return;
        busy = true;
        message.setText("Reading usage…");
        render();
        Background.run(() -> {
            try {
                return client.usage(c);
            } catch (CompilerAccountClient.AccountException e) {
                throw new IllegalStateException(e.getMessage());
            }
        }, (Usage u) -> {
            busy = false;
            usageLine.setText(UiText.inert(usageText(u)));
            String reset = resetText(u);
            resetLine.setText(reset == null ? " " : UiText.inert(reset));
            message.setText(" ");
            render();
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

    static String usageText(Usage u) {
        return "Plan: " + u.plan() + (u.active() ? "" : " (inactive)") + " · Available credits: " + u.available();
    }

    /** Null when the plan has no next reset: then no line is shown, rather than a guess. */
    static String resetText(Usage u) {
        return u.nextReset() == null ? null : "Next reset: " + u.nextReset();
    }

    static String revokeText(boolean revokedNow) {
        return revokedNow
                ? "Revoked. The service no longer accepts this device, and its credential was deleted here."
                : "The service already did not accept this device's credential; it was deleted here.";
    }
}
