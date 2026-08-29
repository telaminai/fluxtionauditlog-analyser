package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M19.12's credential boundary, asserted at every surface the spec names. */
class FluxtionKeySafetyTest {

    @TempDir Path temp;

    @Test
    void storeHasNoPublicValueRead_andConfigModelsOwnNoFluxtionKey() {
        for (Method method : FluxtionKeyStore.class.getMethods()) {
            assertFalse(method.getReturnType().equals(char[].class), method + " returns a credential buffer");
            assertFalse(method.getName().matches("(?i).*(get|read|load).*key.*"),
                    method + " exposes a key-reading API");
        }
        assertTrue(java.util.Arrays.stream(AppConfig.class.getFields())
                        .noneMatch(f -> f.getName().toLowerCase().contains("fluxtionkey")),
                "AppConfig must not hold the Fluxtion build key (its existing apiKey is the LLM key)");
        assertFalse(KnownKeys.PROFILE_FAMILIES.contains("fluxtionKey"));
        assertFalse(KnownKeys.CONFIG_FAMILIES.contains("fluxtionKey"));
    }

    @Test
    void valueCannotReachAProfileOrShareExport() throws Exception {
        String sentinel = "credential-that-must-stay-in-the-key-file";
        FluxtionKeyStore store = new FluxtionKeyStore(temp.resolve(".fluxtion"));
        store.save(sentinel.toCharArray());

        AppConfig config = new AppConfig();
        SettingsShare share = new SettingsShare(temp.toString());
        Path profile = ProjectProfile.pathFor(temp.resolve("project"));
        ProjectProfile.save(profile, config, share);

        assertFalse(Files.readString(profile).contains(sentinel));
        assertFalse(share.export(config, EnumSet.allOf(SettingsShare.Category.class)).contains(sentinel));
    }

    @Test
    void contextStatusAndDialogCanOnlyAskForPresence_neverTheValue() throws Exception {
        String main = Files.readString(Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        String dialog = Files.readString(Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/FluxtionKeyDialog.java"));
        String start = Files.readString(Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/StartPanel.java"));

        assertTrue(main.contains("fluxtionKeyStore.keyPresent()"));
        assertFalse(main.contains("getProperty(\"apiKey\")"),
                "context/echo/status owners must have no credential read path");
        assertFalse(dialog.contains("testConnection") || dialog.contains("HttpURLConnection")
                        || dialog.contains("setEchoChar((char) 0)"),
                "the dialog neither validates nor reveals a stored/entered key");
        assertTrue(dialog.contains("JPasswordField"));
        assertFalse(start.contains("JPasswordField"), "the screenshot-prone Start Page receives presence only");
        String store = Files.readString(Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/config/FluxtionKeyStore.java"));
        assertFalse(store.contains("System.out") || store.contains("System.err") || store.contains("Logger"),
                "the only class that can read the value has no console/status/logging exit");

        int firstRunStart = main.indexOf("public void showFirstRunSettingsIfNeeded()");
        int firstRunEnd = main.indexOf("\n    }", firstRunStart);
        assertTrue(firstRunStart > 0 && firstRunEnd > firstRunStart);
        String firstRun = main.substring(firstRunStart, firstRunEnd);
        assertFalse(firstRun.contains("JOptionPane") || firstRun.contains("FluxtionKeyDialog"),
                "M19.12a: key registration is an offer on the Start Page, never a first-run gate");
        assertTrue(start.contains("fluxtionKeyCard") && start.contains("openFluxtionKey"),
                "the non-modal Start Page must carry the registration remedy");
    }
}
