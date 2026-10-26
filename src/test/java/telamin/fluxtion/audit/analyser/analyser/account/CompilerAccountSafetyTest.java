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
    void copyDetailsCarriesTheFiguresForSupport_butNeverTheCredential() {
        CompilerCredential c = new CompilerCredential("https://compiler.example.com", "cred01", "acct-demo",
                StandInCompilerService.BEARER);
        CompilerAccountClient.Usage u = new CompilerAccountClient.Usage("acct-demo", "free", true, 120, 30, 2, 3, 1, null);
        CompilerAccountClient.Me me = new CompilerAccountClient.Me("acct-demo", true, "administrator");
        String text = AccountDetails.text(c, me, u, "Fluxtion analyser on demo-host", "1.38.0",
                java.time.Instant.parse("2026-10-26T09:30:00Z"));
        for (String expected : List.of("Service: https://compiler.example.com", "Account: acct-demo", "Type: Personal",
                "Role: administrator", "Plan: free", "Active: yes", "Remaining: 120", "Used: 30", "In progress: 2",
                "Generations: 3", "Failures: 1", "Reset: one-time allowance (no reset)", "This device: cred01",
                "Device name: Fluxtion analyser on demo-host", "Analyser version: 1.38.0", "Time: 2026-10-26 09:30:00 UTC"))
            assertTrue(text.contains(expected), expected + " missing from:\n" + text);
        String secret = StandInCompilerService.BEARER.substring(StandInCompilerService.BEARER.indexOf('.') + 1);
        for (String partial : List.of(StandInCompilerService.BEARER, secret, "Bearer "))
            assertFalse(text.contains(partial), "Copy details carries the credential: " + partial);
        String unread = AccountDetails.text(c, null, null, null, "1.38.0", java.time.Instant.EPOCH);
        assertFalse(unread.contains(StandInCompilerService.BEARER));
        assertTrue(unread.contains("Usage: not read") && unread.contains("Device name: not listed"), unread);
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
        config.rememberCompilerService("https://compiler.example.com");
        SettingsShare share = new SettingsShare(temp.toString());
        Path profile = ProjectProfile.pathFor(temp.resolve("project"));
        ProjectProfile.save(profile, config, share);
        String profileText = Files.readString(profile);
        String exported = share.export(config, EnumSet.allOf(SettingsShare.Category.class));
        for (String text : List.of(profileText, exported)) {
            assertFalse(text.contains(CredentialStoreTest.BEARER));
            assertFalse(text.contains("compilerServiceUrl"), "the service address is this machine's, never a project's");
            assertFalse(text.contains("recentCompilerService"), "nor are the addresses it connected with");
        }
        assertFalse(KnownKeys.PROFILE_FAMILIES.contains("compilerServiceUrl"));
        assertTrue(KnownKeys.CONFIG_FAMILIES.contains("compilerServiceUrl"));
        assertFalse(KnownKeys.PROFILE_FAMILIES.contains("recentCompilerService"));
        assertTrue(KnownKeys.CONFIG_FAMILIES.contains("recentCompilerService"));
        telamin.fluxtion.audit.analyser.analyser.config.ConfigStore machine =
                new telamin.fluxtion.audit.analyser.analyser.config.ConfigStore(temp.resolve("config"));
        for (int i = 0; i < 7; i++) config.rememberCompilerService("https://c" + i + ".example.com");
        config.rememberCompilerService("https://c3.example.com");
        machine.save(config);
        assertEquals(List.of("https://c3.example.com", "https://c6.example.com", "https://c5.example.com",
                "https://c4.example.com", "https://c2.example.com"), machine.load().recentCompilerServices,
                "most recent first, each once, at most five, and kept on this machine");
        assertTrue(java.util.Arrays.stream(AppConfig.class.getFields()).map(f -> f.getName().toLowerCase())
                .noneMatch(n -> n.contains("compiler") && (n.contains("token") || n.contains("credential") || n.contains("bearer"))),
                "AppConfig is cleartext: it must hold no compiler credential");
    }
}
