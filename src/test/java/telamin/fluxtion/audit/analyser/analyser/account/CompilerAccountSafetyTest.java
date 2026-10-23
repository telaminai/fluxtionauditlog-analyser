package telamin.fluxtion.audit.analyser.analyser.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.KnownKeys;
import telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CA-1's credential boundary, asserted where it could leak: no public read of the bearer or device code; nothing in
 * the account package prints or logs; only the dialog and its menu item reach the package, so {@code context}, reports,
 * walks, bundles, profiles and settings exports cannot carry the credential; and the service address is machine-tier.
 */
class CompilerAccountSafetyTest {

    @TempDir Path temp;

    private static final Path MAIN = Path.of("src/main/java/telamin/fluxtion/audit/analyser");
    private static final String PACKAGE = "telamin.fluxtion.audit.analyser.analyser.account";

    @Test
    void noPublicMethodReturnsTheBearerOrTheDeviceCode() throws Exception {
        CompilerCredential c = CredentialStoreTest.credential();
        for (Method m : CompilerCredential.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers())) continue;
            Object v = m.invoke(c);
            assertFalse(String.valueOf(v).contains(CredentialStoreTest.BEARER), m + " exposes the credential");
        }
        CompilerAccountClient.DeviceStart s = new CompilerAccountClient.DeviceStart("https://compiler.example.com",
                StandInCompilerService.DEVICE_CODE, "WDJB-MJHT", "https://compiler.example.com/device", null, 600, 5);
        for (Method m : CompilerAccountClient.DeviceStart.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers()) || m.getParameterCount() != 0 || Modifier.isStatic(m.getModifiers())) continue;
            assertFalse(String.valueOf(m.invoke(s)).contains(StandInCompilerService.DEVICE_CODE), m + " exposes the device code");
        }
        assertFalse(Modifier.isPublic(CompilerCredential.class.getDeclaredMethod("bearer").getModifiers()));
        assertFalse(Modifier.isPublic(CompilerCredential.class.getDeclaredMethod("encode").getModifiers()));
    }

    @Test
    void nothingInTheAccountPackagePrintsOrLogs() throws Exception {
        try (Stream<Path> files = Files.list(MAIN.resolve("analyser/account"))) {
            for (Path f : files.toList()) {
                String src = Files.readString(f);
                for (String exit : List.of("System.out", "System.err", "Logger", "printStackTrace", "java.util.logging"))
                    assertFalse(src.contains(exit), f.getFileName() + " has a console/log exit: " + exit);
            }
        }
    }

    @Test
    void onlyTheDialogAndItsMenuItemReachTheAccountPackage() throws Exception {
        List<Path> users;
        try (Stream<Path> all = Files.walk(MAIN)) {
            users = all.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.startsWith(MAIN.resolve("analyser/account")))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains(PACKAGE) || Files.readString(p).contains("CompilerAccountDialog");
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }).map(p -> MAIN.relativize(p)).sorted().toList();
        }
        assertEquals(List.of(Path.of("analyser/ui/CompilerAccountDialog.java"), Path.of("analyser/ui/MainFrame.java")), users,
                "a new reader of the compiler account (context, a report, a bundle, a panel) is a decision: route it "
                        + "through the session (rule 9) and keep the credential out of it");
        String main = Files.readString(MAIN.resolve("analyser/ui/MainFrame.java"));
        assertEquals(1, main.split("CompilerAccountDialog", -1).length - 1, "MainFrame only opens the dialog");
        assertTrue(main.contains("CompilerAccountDialog.show(this, config, this::saveConfigQuietly)"));
        assertFalse(main.contains(PACKAGE), "MainFrame never holds a credential or a store");
    }

    @Test
    void theCredentialCannotReachAProfileOrAShareExport_andTheAddressIsMachineTier() throws Exception {
        ProtectedFileStore store = new ProtectedFileStore(temp.resolve(".fluxtion-analyser").resolve(ProtectedFileStore.FILE_NAME));
        store.save(CredentialStoreTest.credential());
        AppConfig config = new AppConfig();
        config.compilerServiceUrl = "https://compiler.example.com";
        SettingsShare share = new SettingsShare(temp.toString());
        Path profile = ProjectProfile.pathFor(temp.resolve("project"));
        ProjectProfile.save(profile, config, share);
        String profileText = Files.readString(profile);
        String exported = share.export(config, EnumSet.allOf(SettingsShare.Category.class));
        for (String text : List.of(profileText, exported)) {
            assertFalse(text.contains(CredentialStoreTest.BEARER));
            assertFalse(text.contains("compilerServiceUrl"), "the service address is this machine's, never a project's");
        }
        assertFalse(KnownKeys.PROFILE_FAMILIES.contains("compilerServiceUrl"));
        assertTrue(KnownKeys.CONFIG_FAMILIES.contains("compilerServiceUrl"));
        assertTrue(java.util.Arrays.stream(AppConfig.class.getFields()).map(f -> f.getName().toLowerCase())
                .noneMatch(n -> n.contains("compiler") && (n.contains("token") || n.contains("credential") || n.contains("bearer"))),
                "AppConfig is cleartext: it must hold no compiler credential");
    }
}
