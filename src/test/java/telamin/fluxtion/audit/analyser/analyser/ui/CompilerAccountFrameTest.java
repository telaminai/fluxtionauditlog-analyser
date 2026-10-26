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
        // deliberately changed (owner, 2026-10-26): the status line names the host only; the account id is the Account row
        await(() -> text().contains("Connected to 127.0.0.1") && !text().contains("as account"), "approval connects");
        await(() -> "free".equals(after("Plan")) && "Active".equals(after("Status")) && "120".equals(figure("Remaining")),
                "plan and credits are read");
        assertFalse(text().contains("Next reset"), "a plan with no reset shows no reset line");
        assertTrue(text().contains("One-time allowance (no reset)"), "it says the allowance is one-time");
        assertEquals(List.of(URI.create("https://compiler.example.com/device?user_code=" + StandInCompilerService.USER_CODE)), opened,
                "the system browser was asked to open the sign-in page once");
        assertNotNull(store.kept, "the credential was stored");
        // deliberately changed (2026-10-26): approval now remembers the address for the drop-down, which is one save;
        // the address itself was unchanged and is not re-saved
        assertEquals(1, saves.get(), "the approved address is remembered once");
        assertEquals(List.of(service.base()), config.recentCompilerServices);
        assertNoSecretOnScreen();

        service.usageBody = service.usageBody.replace("\"nextReset\":null", "\"nextReset\":\"2026-11-01T00:00:00Z\"")
                .replace("\"available\":120", "\"available\":119");
        onEdt(() -> button(CompilerAccountDialog.REFRESH).doClick());
        await(() -> "119".equals(figure("Remaining")) && text().contains("Next reset: 2026-11-01T00:00:00Z"),
                "refresh reads again, and a reset is shown when there is one");

        onEdt(() -> button(CompilerAccountDialog.REVOKE).doClick());
        await(() -> text().contains("Revoked.") && text().contains("Not connected."), "revoke disconnects");
        // owner, 2026-10-26: disconnected, the dialog keeps no empty connected-size space below its content
        onEdt(() -> assertEquals(dialog.getPreferredSize().height, dialog.getHeight(), "the dialog fits its disconnected content"));
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
        assertTrue(button(CompilerAccountDialog.CONNECT).isEnabled() && button(CompilerAccountDialog.CONNECT).isShowing(),
                "connect is offered again");
        assertFalse(button(CompilerAccountDialog.DISCONNECT).isVisible(), "nothing to disconnect");
    }

    @Test
    void aChangedAddressNeverRedirectsTheStoredCredential_andTheFieldNamesTheIssuingService() throws Exception {
        service.token("approved");
        AppConfig config = new AppConfig();
        config.compilerServiceUrl = service.base();
        open(config);
        onEdt(() -> button(CompilerAccountDialog.CONNECT).doClick());
        await(() -> "free".equals(after("Plan")), "connected");
        onEdt(dialog::dispose);

        config.compilerServiceUrl = "https://other.example.com";   // the setting changed after connecting
        long reads = service.count("/v1/usage");
        open(config);
        await(() -> service.count("/v1/usage") > reads && "free".equals(after("Plan")), "usage is read from the issuer");
        onEdt(() -> {
            assertEquals(service.base(), address().getText(), "the field names the service the credential belongs to");
            assertFalse(address().isEditable());
        });
    }

    @Test
    void thePanelShowsTheAccountAndDevices_revokesAnotherDevice_andCopiesDetailsWithoutTheCredential() throws Exception {
        service.token("approved");
        service.credentials.get("cred01")[1] = "Fluxtion analyser on Mac-1454.Home";   // a real machine's length of name
        AppConfig config = new AppConfig();
        config.compilerServiceUrl = service.base();
        open(config);
        List<String> copied = new CopyOnWriteArrayList<>();
        onEdt(() -> dialog.clipboard = copied::add);
        onEdt(() -> button(CompilerAccountDialog.CONNECT).doClick());
        await(() -> "acct-demo".equals(after("Account")) && "Personal".equals(after("Type"))
                && "administrator".equals(after("Your role")), "the account is read");
        await(() -> table().getRowCount() == 2, "the two devices the service still accepts are listed");
        onEdt(() -> {
            assertEquals("Fluxtion analyser on Mac-1454.Home (this machine)", table().getValueAt(0, 0));
            // owner, 2026-10-26: at the default size this machine's whole name and its tag fit, nothing elided
            Component own = table().prepareRenderer(table().getCellRenderer(0, 0), 0, 0);
            int column = table().getColumnModel().getColumn(0).getWidth();
            assertTrue(own.getPreferredSize().width <= column,
                    "the name is cut: needs " + own.getPreferredSize().width + "px, the column has " + column);
            assertEquals("Fluxtion analyser on other-host", table().getValueAt(1, 0));
            assertEquals(List.of("Name", "ID", "Kind", "Created (UTC)"), java.util.stream.IntStream.range(0, table().getColumnCount())
                    .mapToObj(table()::getColumnName).toList());
            assertEquals("cred02", table().getValueAt(1, 1));
            assertEquals("Device", table().getValueAt(1, 2));
            assertTrue(String.valueOf(table().getValueAt(1, 3)).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"));
            java.awt.Rectangle cell = table().getCellRect(1, 1, true);
            java.awt.event.MouseEvent at = new java.awt.event.MouseEvent(table(), java.awt.event.MouseEvent.MOUSE_MOVED, 0, 0,
                    cell.x + 2, cell.y + 2, 0, false);
            assertEquals("cred02", table().getToolTipText(at), "the ID cell's tooltip is the whole id");
            java.awt.Rectangle name = table().getCellRect(1, 0, true);
            assertNull(table().getToolTipText(new java.awt.event.MouseEvent(table(), java.awt.event.MouseEvent.MOUSE_MOVED, 0, 0,
                    name.x + 2, name.y + 2, 0, false)));
        });
        assertEquals("Fluxtion analyser on Mac-1454.Home", after("Name"), "this device is named");
        assertEquals("cred01", after("Credential"));
        // the layout's promises (owner polish, 2026-10-26): Close is the default, never a destructive action; Connect
        // is offered only when not connected; every action fits inside the dialog at its opening size
        onEdt(() -> assertEquals("Close", dialog.getRootPane().getDefaultButton().getText()));
        assertFalse(button(CompilerAccountDialog.CONNECT).isVisible(), "connected: Connect is not offered");
        for (String b : List.of(CompilerAccountDialog.REFRESH, CompilerAccountDialog.DISCONNECT, CompilerAccountDialog.REVOKE))
            assertInsideTheDialog(button(b));
        assertTrue(text().contains(CompilerAccountDialog.DELETION_NOTE));
        assertFalse(button(CompilerAccountDialog.REVOKE_SELECTED).isEnabled(), "nothing selected, nothing to revoke");
        for (String b : List.of(CompilerAccountDialog.REVOKE_SELECTED, CompilerAccountDialog.COPY_DETAILS, "Close"))
            assertInsideTheDialog(button(b));

        onEdt(() -> button(CompilerAccountDialog.COPY_DETAILS).doClick());
        assertEquals(1, copied.size());
        assertTrue(copied.get(0).contains("Remaining: 120") && copied.get(0).contains("This device: cred01"), copied.get(0));
        assertFalse(copied.get(0).contains(StandInCompilerService.BEARER), "Copy details carries the credential");

        List<String> asked = new CopyOnWriteArrayList<>();
        onEdt(() -> {
            dialog.confirm = (title, question) -> asked.add(question);
            table().setRowSelectionInterval(1, 1);
        });
        onEdt(() -> button(CompilerAccountDialog.REVOKE_SELECTED).doClick());
        await(() -> text().contains("Revoked \"Fluxtion analyser on other-host\".") && table().getRowCount() == 1,
                "the other device is revoked and the list read again");
        assertEquals(1, asked.size(), "the person confirmed first");
        assertTrue(service.requests().stream().anyMatch(r -> r.method().equals("DELETE") && r.path().equals("/v1/credentials/cred02")));
        assertTrue(service.requests().stream().noneMatch(r -> r.path().equals("/v1/credentials/cred01")), "this device untouched");
        assertNotNull(store.kept, "this machine stays connected");
        assertTrue(text().contains("Connected to 127.0.0.1"));

        onEdt(() -> table().setRowSelectionInterval(0, 0));   // this machine's own row: exactly Revoke this device
        onEdt(() -> button(CompilerAccountDialog.REVOKE_SELECTED).doClick());
        await(() -> text().contains("Revoked. The service no longer accepts this device") && text().contains("Not connected."),
                "revoking this machine's row disconnects");
        assertNull(store.kept);
        assertNoSecretOnScreen();
    }

    @Test
    void theAddressIsAnEditableDropDownOfAddressesThisMachineConnectedWith() throws Exception {
        AppConfig config = new AppConfig();
        config.recentCompilerServices.addAll(List.of(service.base(), "https://other.example.com", "http://plain.example.com"));
        open(config);
        await(() -> text().contains("Not connected."), "starts disconnected");
        onEdt(() -> {
            javax.swing.JComboBox<?> box = combo();
            assertTrue(box.isEditable() && box.isEnabled());
            assertEquals(List.of(service.base(), "https://other.example.com"),
                    java.util.stream.IntStream.range(0, box.getItemCount()).mapToObj(box::getItemAt).toList());
            assertEquals("", address().getText(), "nothing is chosen for the person");
            box.setSelectedIndex(1);
            assertEquals("https://other.example.com", address().getText(), "a suggestion fills the field");
            address().setText("https://typed.example.com");
            assertEquals("https://typed.example.com", address().getText(), "and any address can be typed");
        });
    }

    @Test
    void aDisconnectDeletesOnlyTheLocalCredential() throws Exception {
        service.token("approved");
        AppConfig config = new AppConfig();
        config.compilerServiceUrl = service.base();
        open(config);
        onEdt(() -> button(CompilerAccountDialog.CONNECT).doClick());
        await(() -> "free".equals(after("Plan")), "connected");
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

    /** The value shown beside a row's label (the line after its last occurrence). */
    private String after(String label) throws Exception {
        List<String> lines = List.of(text().split("\n", -1));
        int i = lines.lastIndexOf(label);
        return i < 0 || i + 1 >= lines.size() ? null : lines.get(i + 1);
    }

    /** A credit figure, shown above its caption (the line before it). */
    private String figure(String caption) throws Exception {
        List<String> lines = List.of(text().split("\n", -1));
        int i = lines.lastIndexOf(caption);
        return i < 1 ? null : lines.get(i - 1);
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

    private javax.swing.JTable table() {
        List<javax.swing.JTable> found = new ArrayList<>();
        collectTables(dialog.getContentPane(), found);
        assertEquals(1, found.size());
        return found.get(0);
    }

    private static void collectTables(Component c, List<javax.swing.JTable> out) {
        if (c instanceof javax.swing.JTable t) out.add(t);
        if (c instanceof Container k) for (Component child : k.getComponents()) collectTables(child, out);
    }

    private javax.swing.JComboBox<?> combo() {
        List<javax.swing.JComboBox<?>> found = new ArrayList<>();
        collectCombos(dialog.getContentPane(), found);
        assertEquals(1, found.size());
        return found.get(0);
    }

    private static void collectCombos(Component c, List<javax.swing.JComboBox<?>> out) {
        if (c instanceof javax.swing.JComboBox<?> b) out.add(b);
        if (c instanceof Container k) for (Component child : k.getComponents()) collectCombos(child, out);
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
