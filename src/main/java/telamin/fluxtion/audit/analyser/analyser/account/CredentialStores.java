package telamin.fluxtion.audit.analyser.analyser.account;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * CA-1: the OS credential stores, and the protected-file fallback, behind {@link CredentialStore}.
 *
 * <ul>
 *   <li><b>macOS</b>: the login Keychain through {@code /usr/bin/security}. The write goes through the tool's
 *       interactive mode, so the value arrives on stdin and never on a command line.</li>
 *   <li><b>Linux</b>: the Secret Service through {@code secret-tool}, which reads the value from stdin.</li>
 *   <li><b>Elsewhere</b> (Windows, or Linux without {@code secret-tool}): a file only the owner can read, under the
 *       analyser's own home directory. Windows Credential Manager has no stock command that takes the secret off the
 *       command line, so it is not used.</li>
 * </ul>
 */
public final class CredentialStores {

    /** The Keychain service and Secret Service attribute: one entry per user, named for the analyser. */
    static final String SERVICE = "fluxtion-analyser-compiler-account";
    static final String ACCOUNT = "device-credential";
    static final String SECURITY = "/usr/bin/security";

    private CredentialStores() {
    }

    /** The store this machine should use. */
    public static CredentialStore forThisMachine() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path home = Path.of(System.getProperty("user.home"), ".fluxtion-analyser");
        if (os.contains("mac") && Files.isExecutable(Path.of(SECURITY))) return keychain(CommandRunner.system(), SERVICE);
        if (os.contains("linux")) {
            Optional<Path> tool = onPath("secret-tool");
            if (tool.isPresent()) return secretService(CommandRunner.system(), tool.get().toString(), SERVICE);
        }
        return new ProtectedFileStore(home.resolve(ProtectedFileStore.FILE_NAME));
    }

    private static Optional<Path> onPath(String name) {
        String path = System.getenv("PATH");
        if (path == null) return Optional.empty();
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) continue;
            Path candidate = Path.of(dir, name);
            if (Files.isExecutable(candidate)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /** macOS Keychain. {@code service} is a parameter so a test can use a throwaway entry. */
    public static CredentialStore keychain(CommandRunner runner, String service) {
        return new CredentialStore() {
            @Override public String description() {
                return "macOS Keychain (item \"" + service + "\")";
            }

            @Override public Optional<CompilerCredential> load() throws IOException {
                CommandRunner.Result r = runner.run(List.of(SECURITY, "find-generic-password", "-a", ACCOUNT, "-s", service, "-w"), null);
                if (r.exit() == 44) return Optional.empty();   // errSecItemNotFound, as the tool reports it
                if (r.exit() != 0) throw new IOException("the Keychain could not be read (security exited " + r.exit() + ")");
                return Optional.of(CompilerCredential.decode(r.stdout()));
            }

            @Override public void save(CompilerCredential credential) throws IOException {
                // interactive mode: the command, value included, is read from stdin. -U replaces an existing item.
                String line = "add-generic-password -U -a " + ACCOUNT + " -s " + service
                        + " -l \"Fluxtion analyser compiler account\" -w " + credential.encode() + "\n";
                CommandRunner.Result r = runner.run(List.of(SECURITY, "-i"), line);
                if (r.exit() != 0) throw new IOException("the Keychain refused the credential (security exited " + r.exit() + ")");
                confirm(this, credential);
            }

            @Override public void delete() throws IOException {
                CommandRunner.Result r = runner.run(List.of(SECURITY, "delete-generic-password", "-a", ACCOUNT, "-s", service), null);
                if (r.exit() != 0 && r.exit() != 44) throw new IOException("the Keychain entry could not be removed (security exited " + r.exit() + ")");
            }
        };
    }

    /** Linux Secret Service through {@code secret-tool}. */
    public static CredentialStore secretService(CommandRunner runner, String tool, String service) {
        return new CredentialStore() {
            @Override public String description() {
                return "Secret Service keyring (service \"" + service + "\")";
            }

            @Override public Optional<CompilerCredential> load() throws IOException {
                CommandRunner.Result r = runner.run(List.of(tool, "lookup", "service", service, "account", ACCOUNT), null);
                if (r.exit() != 0 || r.stdout().isBlank()) {
                    if (r.exit() == 1 && r.stdout().isBlank()) return Optional.empty();   // secret-tool: no match
                    throw new IOException("the keyring could not be read (secret-tool exited " + r.exit() + ")");
                }
                return Optional.of(CompilerCredential.decode(r.stdout()));
            }

            @Override public void save(CompilerCredential credential) throws IOException {
                CommandRunner.Result r = runner.run(List.of(tool, "store", "--label=Fluxtion analyser compiler account",
                        "service", service, "account", ACCOUNT), credential.encode());
                if (r.exit() != 0) throw new IOException("the keyring refused the credential (secret-tool exited " + r.exit() + ")");
                confirm(this, credential);
            }

            @Override public void delete() throws IOException {
                CommandRunner.Result r = runner.run(List.of(tool, "clear", "service", service, "account", ACCOUNT), null);
                if (r.exit() != 0 && r.exit() != 1) throw new IOException("the keyring entry could not be removed (secret-tool exited " + r.exit() + ")");
            }
        };
    }

    /** A store that answered success but kept something else has not stored the credential. */
    private static void confirm(CredentialStore store, CompilerCredential expected) throws IOException {
        Optional<CompilerCredential> back = store.load();
        if (back.isEmpty() || !back.get().equals(expected))
            throw new IOException(store.description() + " did not keep the credential");
    }
}
