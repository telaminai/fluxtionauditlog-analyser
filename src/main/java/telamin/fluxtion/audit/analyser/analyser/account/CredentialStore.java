package telamin.fluxtion.audit.analyser.analyser.account;

import java.io.IOException;
import java.util.Optional;

/**
 * CA-1: where this machine keeps the compiler-account device credential. One credential per user; nothing here is
 * ever written to a project, a profile, a settings export, a report, a walk, an evidence bundle or {@code context}.
 */
public interface CredentialStore {

    /** Where the credential lives, in words a person can check ("macOS Keychain", a file path). */
    String description();

    /** The stored credential, or empty when none is stored. A damaged entry is an exception, never a guess. */
    Optional<CompilerCredential> load() throws IOException;

    /** Replace any stored credential with this one, and confirm it reads back. */
    void save(CompilerCredential credential) throws IOException;

    /** Compare the observed sign-in before replacement; shared stores override with a process lock. */
    default boolean replace(CompilerCredential expected, CompilerCredential replacement) throws IOException {
        synchronized (this) {
            if (!java.util.Objects.equals(load().orElse(null), expected)) return false;
            save(replacement); return true;
        }
    }

    /** Delete only the observed sign-in, never a newer one. */
    default boolean delete(CompilerCredential expected) throws IOException {
        synchronized (this) {
            if (!java.util.Objects.equals(load().orElse(null), expected)) return false;
            delete(); return true;
        }
    }

    /** Remove the stored credential; removing nothing is not an error. */
    void delete() throws IOException;
}
