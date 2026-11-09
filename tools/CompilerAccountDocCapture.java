package telamin.fluxtion.audit.analyser.analyser.ui;

import com.formdev.flatlaf.FlatLightLaf;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerAccountClient;
import telamin.fluxtion.audit.analyser.analyser.account.CompilerCredential;
import telamin.fluxtion.audit.analyser.analyser.account.CredentialStore;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;

import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import java.awt.Container;
import java.awt.Window;
import java.net.http.HttpClient;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Generated documentation capture of *AI ▸ Compiler account…* as a first-time user meets it: not connected, the
 * service-address list open on the hosted Production and Staging services ({@code compiler-account-services.png}).
 *
 * <p>Nothing here touches a real credential or the network. The credential store is an empty in-memory one — never the
 * OS keychain, which on the machine running this could hold the person's own credential — and the dialog is only
 * shown, never connected. It speaks {@link DocShot}'s protocol, so tools/capture-docs.py photographs the window by id.
 */
public final class CompilerAccountDocCapture {
    private CompilerAccountDocCapture() { }

    /** Holds nothing and keeps nothing: the dialog reads "not connected" from it. */
    private static final class EmptyStore implements CredentialStore {
        // the words CredentialStores.keychain shows on macOS (CredentialStores.java, description(), item SERVICE); keep in step
        @Override public String description() { return "macOS Keychain (item \"fluxtion-analyser-compiler-account\")"; }
        @Override public Optional<CompilerCredential> load() { return Optional.empty(); }
        @Override public void save(CompilerCredential credential) { }
        @Override public void delete() { }
    }

    public static void main(String[] args) throws Exception {
        DocShot.requireRealHome();
        FlatLightLaf.setup();
        AppConfig config = new AppConfig();   // fresh: no address set, nothing remembered
        CompilerAccountClient client = new CompilerAccountClient(HttpClient.newHttpClient(), d -> { }, System::nanoTime);
        CompilerAccountDialog[] dialog = new CompilerAccountDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            dialog[0] = new CompilerAccountDialog(null, config, () -> { }, new EmptyStore(), client, uri -> { });
            dialog[0].setModalityType(java.awt.Dialog.ModalityType.MODELESS);
            // never take the keyboard: keys typed elsewhere while this runs once landed in the address field and were
            // photographed, and losing focus to another app closes the list before its shot
            dialog[0].setFocusableWindowState(false);
            dialog[0].setVisible(true);
        });
        awaitShowing(dialog[0]);
        Thread.sleep(500);   // the stored-credential read is asynchronous; let "Not connected." settle

        JComboBox<?>[] box = new JComboBox<?>[1];
        SwingUtilities.invokeAndWait(() -> {
            box[0] = find(dialog[0], JComboBox.class);
            if (box[0] == null) throw new IllegalStateException("no service-address list in the dialog");
            clearAddress(dialog[0]);
            box[0].showPopup();
        });
        Thread.sleep(400);
        DocShot.withPopups(dialog[0], "compiler-account-services.png");
        SwingUtilities.invokeAndWait(() -> {
            box[0].hidePopup();
            dialog[0].dispose();
        });
        System.exit(0);
    }

    /** The address field empty, as on a fresh install, whatever reached it before the shot. */
    private static void clearAddress(Container root) {
        JComboBox<?> box = find(root, JComboBox.class);
        if (box != null && box.getEditor().getEditorComponent() instanceof javax.swing.text.JTextComponent field) {
            field.setText("");
        }
    }

    private static <T extends java.awt.Component> T find(Container root, Class<T> type) {
        for (java.awt.Component c : root.getComponents()) {
            if (type.isInstance(c)) return type.cast(c);
            if (c instanceof Container inner) {
                T hit = find(inner, type);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private static void awaitShowing(Window w) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < end && !w.isShowing()) Thread.sleep(50);
        if (!w.isShowing()) throw new IllegalStateException("the Compiler account dialog never showed");
    }
}
