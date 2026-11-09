package telamin.fluxtion.audit.analyser.analyser.account;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/** Previous Analyser-only base64 format, retained for explicit migration using no-follow file operations. */
public final class ProtectedFileStore implements CredentialStore {
    public static final String FILE_NAME = "compiler-account.credential";
    private final SharedCredentialFile file;

    public ProtectedFileStore(Path file) { this.file = new SharedCredentialFile(file, true); }
    ProtectedFileStore(Path file, Path trustedRoot) { this.file = new SharedCredentialFile(file, true, trustedRoot); }
    @Override public String description() { return "an owner-only file, ~/.fluxtion-analyser/" + FILE_NAME; }
    @Override public Optional<CompilerCredential> load() throws IOException {
        SharedCredentialFile.Stored s = file.read();
        if (s == null) return Optional.empty();
        try { return Optional.of(new CompilerCredential(s.url, s.credentialId, s.accountId, s.token)); }
        catch (IllegalArgumentException e) { throw new IOException("The previous compiler sign-in is damaged."); }
    }
    @Override public void save(CompilerCredential credential) throws IOException { file.write(to(credential)); }
    @Override public void delete() throws IOException { file.delete(); }
    @Override public boolean delete(CompilerCredential expected) throws IOException { return file.compareAndDelete(to(expected)); }
    @Override public boolean replace(CompilerCredential expected, CompilerCredential next) throws IOException {
        return file.compareAndWrite(to(expected), to(next));
    }
    private static SharedCredentialFile.Stored to(CompilerCredential c) {
        return c == null ? null : new SharedCredentialFile.Stored(c.bearer(), c.credentialId(), c.accountId(), c.service());
    }
}
