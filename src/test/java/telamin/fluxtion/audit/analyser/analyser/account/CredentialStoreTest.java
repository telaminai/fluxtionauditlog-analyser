package telamin.fluxtion.audit.analyser.analyser.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CA-1: the credential stores. Every platform call is recorded through an injected {@link CommandRunner}, and the test
 * asserts that the secret — raw or in its stored encoding — is on stdin only, never on a command line, which any local
 * user can read from the process table.
 */
class CredentialStoreTest {

    @TempDir Path temp;

    static final String BEARER = "fxk_cred01.secret-PLACEHOLDER-for-a-store-test";

    static CompilerCredential credential() {
        return new CompilerCredential("https://compiler.example.com", "cred01", "acct-demo", BEARER);
    }

    /** A fake platform tool: remembers what it was given, answers like the real one. */
    static final class FakeTool implements CommandRunner {
        final List<List<String>> argvs = new ArrayList<>();
        final List<String> stdins = new ArrayList<>();
        String kept;   // what the "keychain" holds

        @Override public Result run(List<String> argv, String stdin) {
            argvs.add(List.copyOf(argv));
            stdins.add(stdin);
            String verb = argv.size() > 1 ? argv.get(1) : "";
            if (argv.contains("-i")) {   // security -i: the command line arrives on stdin
                String[] words = stdin.strip().split(" ");
                kept = words[words.length - 1];
                return new Result(0, "");
            }
            return switch (verb) {
                case "find-generic-password" -> kept == null ? new Result(44, "") : new Result(0, kept + "\n");
                case "delete-generic-password" -> {
                    int exit = kept == null ? 44 : 0;
                    kept = null;
                    yield new Result(exit, "");
                }
                case "store" -> {
                    kept = stdin;
                    yield new Result(0, "");
                }
                case "lookup" -> kept == null ? new Result(1, "") : new Result(0, kept);
                case "clear" -> {
                    kept = null;
                    yield new Result(0, "");
                }
                default -> new Result(2, "");
            };
        }

        void assertNoSecretOnAnyCommandLine(CompilerCredential c) {
            assertFalse(argvs.isEmpty());
            for (List<String> argv : argvs) {
                String line = String.join(" ", argv);
                assertFalse(line.contains(c.bearer()), "the credential is on a command line: " + argv);
                assertFalse(line.contains(c.encode()), "the stored form is on a command line: " + argv);
            }
        }
    }

    @Test
    void keychain_roundTripsWithTheSecretOnStdinOnly() throws Exception {
        FakeTool tool = new FakeTool();
        CredentialStore store = CredentialStores.keychain(tool, "throwaway-service");
        assertEquals(Optional.empty(), store.load());
        store.save(credential());
        assertEquals(credential(), store.load().orElseThrow());
        assertTrue(tool.stdins.stream().anyMatch(s -> s != null && s.contains(credential().encode())), "written through stdin");
        store.delete();
        assertEquals(Optional.empty(), store.load());
        store.delete();   // deleting nothing is not an error
        tool.assertNoSecretOnAnyCommandLine(credential());
        assertTrue(tool.argvs.stream().allMatch(a -> a.get(0).equals("/usr/bin/security")), "the system tool, by absolute path");
    }

    @Test
    void secretService_roundTripsWithTheSecretOnStdinOnly() throws Exception {
        FakeTool tool = new FakeTool();
        CredentialStore store = CredentialStores.secretService(tool, "/usr/bin/secret-tool", "throwaway-service");
        assertEquals(Optional.empty(), store.load());
        store.save(credential());
        assertEquals(credential(), store.load().orElseThrow());
        store.delete();
        assertEquals(Optional.empty(), store.load());
        tool.assertNoSecretOnAnyCommandLine(credential());
    }

    @Test
    void aStoreThatAnswersSuccessButKeepsNothing_isNotConnected() {
        CommandRunner liar = (argv, stdin) -> argv.contains("find-generic-password")
                ? new CommandRunner.Result(44, "") : new CommandRunner.Result(0, "");
        IOException e = assertThrows(IOException.class, () -> CredentialStores.keychain(liar, "s").save(credential()));
        assertFalse(e.getMessage().contains(BEARER));
    }

    @Test
    void aStoreItemNameThatTheKeychainLineWouldTokeniseIsRefused() {
        FakeTool tool = new FakeTool();
        for (String name : List.of("a b", "x\" -w injected", "a\nb", "", "-U"))
            assertThrows(IllegalArgumentException.class, () -> CredentialStores.keychain(tool, name), name);
        assertThrows(IllegalArgumentException.class, () -> CredentialStores.secretService(tool, "/usr/bin/secret-tool", "a b"));
        assertTrue(tool.argvs.isEmpty(), "nothing ran");
        assertTrue(CredentialStores.ITEM_NAME.matcher(CredentialStores.SERVICE).matches(), "the real item name fits");
    }

    @Test
    void aDamagedEntryIsAnError_neverAGuess_andTheErrorQuotesNothing() {
        CommandRunner damaged = (argv, stdin) -> new CommandRunner.Result(0, "not-base64-" + BEARER);
        Exception e = assertThrows(IllegalArgumentException.class, () -> CredentialStores.keychain(damaged, "s").load());
        assertFalse(e.getMessage().contains(BEARER), e.getMessage());
    }

    @Test
    void protectedFile_isOwnerOnlyAndRoundTrips() throws Exception {
        Path file = temp.resolve(".fluxtion-analyser").resolve(ProtectedFileStore.FILE_NAME);
        ProtectedFileStore store = new ProtectedFileStore(file);
        assertEquals(Optional.empty(), store.load());
        store.save(credential());
        assertEquals(credential(), store.load().orElseThrow());
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix"))
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
        try (var siblings = Files.list(file.getParent())) {
            assertEquals(List.of(file), siblings.toList(), "no temporary file is left beside it");
        }
        store.delete();
        assertFalse(Files.exists(file));
    }

    @Test
    void theStoredFormIsNotThePlainSecretAndCarriesItsService() {
        CompilerCredential c = credential();
        assertFalse(c.encode().contains(BEARER));
        assertTrue(c.encode().matches("[A-Za-z0-9+/=]+"), "nothing a platform tool's tokenizer would have to quote");
        assertEquals("https://compiler.example.com", CompilerCredential.decode(c.encode()).service());
        assertFalse(c.toString().contains(BEARER));
    }

    /**
     * The real macOS Keychain, with a throwaway item that is deleted afterwards. Opt-in
     * ({@code -Dca1.liveKeychain=true}) because a test run should not write to a developer's login keychain unasked.
     */
    @Test
    @EnabledIfSystemProperty(named = "ca1.liveKeychain", matches = "true")
    void liveMacKeychain_roundTripsAndLeavesNothingBehind() throws Exception {
        assertTrue(Files.isExecutable(Path.of(CredentialStores.SECURITY)), "macOS only");
        String service = "fluxtion-analyser-ca1-test-" + UUID.randomUUID();
        CredentialStore store = CredentialStores.keychain(CommandRunner.system(), service);
        try {
            store.save(credential());
            assertEquals(credential(), store.load().orElseThrow());
        } finally {
            store.delete();
        }
        assertEquals(Optional.empty(), store.load(), "the throwaway item is gone");
    }
}
