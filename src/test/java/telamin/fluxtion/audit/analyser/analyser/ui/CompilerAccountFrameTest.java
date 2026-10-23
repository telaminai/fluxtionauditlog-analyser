package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerCredential;
import telamin.fluxtion.audit.analyser.analyser.account.CredentialStore;
import telamin.fluxtion.audit.analyser.analyser.account.StandInCompilerService;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.GraphicsEnvironment;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * CA-1 on a real dialog against a loopback stand-in service: connect shows the user code and opens the browser,
 * approval stores the credential and shows plan and credits (and no reset line when the plan has none), revoke deletes
 * at the service and here, and cancel stops the sign-in with nothing stored. At every step, no text on the dialog —
 * labels, fields, buttons, tooltips — carries the device code or the credential.
 */
class CompilerAccountFrameTest {

    private StandInCompilerService service;
    private final List<URI> opened = new CopyOnWriteArrayList<>();
    private final MemoryStore store = new MemoryStore();
    private final AtomicInteger saves = new AtomicInteger();
    private CompilerAccountDialog dialog;

    static final class MemoryStore implements CredentialStore {
        volatile CompilerCredential kept;
        @Override public String description() { return "a test store"; }
        @Override public Optional<CompilerCredential> load() { return Optional.ofNullable(kept); }
        @Override public void save(CompilerCredential c) { kept = c; }
        @Override public void delete() { kept = null; }
    }

    @BeforeEach
    void setUp() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        service = new StandInCompilerService();
        service.interval = 1;
    }

    @AfterEach
    void tearDown() throws Exception {
        if (dialog != null) onEdt(dialog::dispose);
        if (service != null) service.close();
    }

    private void open(AppConfig config) throws Exception {
        // no real waiting between polls: the interval is the client's job, tested headlessly
        CompilerAccountClient client = new CompilerAccountClient(HttpClient.newHttpClient(), d -> Thread.sleep(20), System::nanoTime);
        onEdt(() -> {
            dialog = new CompilerAccountDialog(null, config, saves::incrementAndGet, store, client, opened::add);
            dialog.setModalityType(Dialog.ModalityType.MODELESS);
            dialog.confirm = (title, question) -> true;
            dialog.setVisible(true);
        });
    }

    @Test
    void connectApproveReadUsageThenRevoke() throws Exception {
        service.token("pending", "pending", "approved");
        AppConfig config = new AppConfig();
        config.compilerServiceUrl = service.base();
        open(config);
        await(() -> text().contains("Not connected."), "starts disconnected");

        onEdt(() -> button(CompilerAccountDialog.CONNECT).doClick());
        await(() -> text().contains(StandInCompilerService.USER_CODE), "the user code is shown");
        assertNoSecretOnScreen();
        // found by looking (2026-10-23): the sign-in rows appeared after pack() and pushed Close and the message off
        for (String b : List.of(CompilerAccountDialog.CANCEL_SIGN_IN, "Close")) assertInsideTheDialog(button(b));
        await(() -> text().contains("Connected to " + service.base() + " as account acct-demo."), "approval connects");
        await(() -> text().contains("Plan: free · Available credits: 120"), "plan and credits are read");
        assertFalse(text().contains("Next reset"), "a plan with no reset shows no reset line");
        assertEquals(List.of(URI.create("https://compiler.example.com/device?user_code=" + StandInCompilerService.USER_CODE)), opened,
                "the system browser was asked to open the sign-in page once");
        assertNotNull(store.kept, "the credential was stored");
        assertEquals(0, saves.get(), "an unchanged address is not re-saved");
        assertNoSecretOnScreen();

        service.usageBody = service.usageBody.replace("\"nextReset\":null", "\"nextReset\":\"2026-11-01T00:00:00Z\"")
                .replace("\"available\":120", "\"available\":119");
        onEdt(() -> button(CompilerAccountDialog.REFRESH).doClick());
        await(() -> text().contains("Available credits: 119") && text().contains("Next reset: 2026-11-01T00:00:00Z"),
                "refresh reads again, and a reset is shown when there is one");

        onEdt(() -> button(CompilerAccountDialog.REVOKE).doClick());
        await(() -> text().contains("Revoked.") && text().contains("Not connected."), "revoke disconnects");
        assertNull(store.kept, "and deletes the local credential");
        assertTrue(service.requests().stream().anyMatch(r -> r.method().equals("DELETE")
                && r.path().equals("/v1/credentials/cred01")), "the service was asked to revoke this device");
        assertNoSecretOnScreen();
    }

    @Test
    void cancelStopsTheSignInAndStoresNothing() throws Exception {
        service.token("pending");
        AppConfig config = new AppConfig();
        open(config);
        onEdt(() -> address().setText(service.base() + "/"));
        onEdt(() -> button(CompilerAccountDialog.CONNECT).doClick());
        await(() -> text().contains(StandInCompilerService.USER_CODE), "signing in");
        assertEquals(service.base(), config.compilerServiceUrl, "the address used is the one saved");
        assertEquals(1, saves.get());
        await(() -> service.count("/auth/device/token") >= 1, "polling");
        onEdt(() -> button(CompilerAccountDialog.CANCEL_SIGN_IN).doClick());
        await(() -> text().contains("Sign-in cancelled. Nothing was stored."), "cancel is said");
        long polls = service.count("/auth/device/token");
        Thread.sleep(300);
        assertTrue(service.count("/auth/device/token") <= polls + 1, "polling stopped (one already in flight at most)");
        assertNull(store.kept);
        assertTrue(text().contains("Not connected."));
        assertTrue(button(CompilerAccountDialog.CONNECT).isEnabled(), "connect is offered again");
    }

    @Test
    void aDisconnectDeletesOnlyTheLocalCredential() throws Exception {
        service.token("approved");
        AppConfig config = new AppConfig();
        config.compilerServiceUrl = service.base();
        open(config);
        onEdt(() -> button(CompilerAccountDialog.CONNECT).doClick());
        await(() -> text().contains("Plan: free"), "connected");
        onEdt(() -> button(CompilerAccountDialog.DISCONNECT).doClick());
        await(() -> text().contains("Disconnected."), "disconnected");
        assertNull(store.kept);
        assertTrue(service.requests().stream().noneMatch(r -> r.method().equals("DELETE")), "disconnect calls nothing");
    }

    // ---- helpers -------------------------------------------------------------------------------------------------

    private void assertInsideTheDialog(Component c) throws Exception {
        onEdt(() -> {
            java.awt.Rectangle r = SwingUtilities.convertRectangle(c.getParent(), c.getBounds(), dialog.getContentPane());
            java.awt.Rectangle pane = new java.awt.Rectangle(dialog.getContentPane().getSize());
            assertTrue(c.isShowing() && pane.contains(r), "cut off: " + c + " at " + r + " in " + pane);
        });
    }

    private void assertNoSecretOnScreen() throws Exception {
        String all = text();
        assertFalse(all.contains(StandInCompilerService.DEVICE_CODE), "the device code is on screen");
        assertFalse(all.contains(StandInCompilerService.BEARER), "the credential is on screen");
    }

    /** Every string the dialog shows or offers: texts and tooltips of every component. */
    private String text() throws Exception {
        AtomicReference<String> out = new AtomicReference<>();
        onEdt(() -> {
            List<String> parts = new ArrayList<>();
            collect(dialog.getContentPane(), parts);
            parts.add(dialog.getTitle());
            out.set(String.join("\n", parts));
        });
        return out.get();
    }

    private static void collect(Component c, List<String> out) {
        if (c instanceof JLabel l && l.isShowing()) out.add(l.getText());
        if (c instanceof AbstractButton b && b.isShowing()) out.add(b.getText());
        if (c instanceof JTextComponent t) out.add(t.getText());
        if (c instanceof JComponent j && j.getToolTipText() != null) out.add(j.getToolTipText());
        if (c instanceof Container k) for (Component child : k.getComponents()) collect(child, out);
    }

    private AbstractButton button(String label) {
        List<AbstractButton> found = new ArrayList<>();
        find(dialog.getContentPane(), label, found);
        assertEquals(1, found.size(), label);
        return found.get(0);
    }

    private static void find(Component c, String label, List<AbstractButton> out) {
        if (c instanceof AbstractButton b && label.equals(b.getText())) out.add(b);
        if (c instanceof Container k) for (Component child : k.getComponents()) find(child, label, out);
    }

    private JTextComponent address() {
        List<JTextComponent> fields = new ArrayList<>();
        collectFields(dialog.getContentPane(), fields);
        assertEquals(1, fields.size());
        return fields.get(0);
    }

    private static void collectFields(Component c, List<JTextComponent> out) {
        if (c instanceof JTextComponent t) out.add(t);
        if (c instanceof Container k) for (Component child : k.getComponents()) collectFields(child, out);
    }

    private static void await(ThrowingSupplier<Boolean> condition, String what) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.get()) return;
            Thread.sleep(25);
            SwingUtilities.invokeAndWait(() -> { });
        }
        fail("timed out waiting: " + what);
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
