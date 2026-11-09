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

    /** Remove the stored credential; removing nothing is not an error. */
    void delete() throws IOException;
}
