package telamin.fluxtion.audit.analyser.analyser.account;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/** Authoritative Maven-compatible single sign-in; the OS copy is consulted only for explicit migration. */
public final class SharedCredentialStore implements CredentialStore {
    private final SharedCredentialFile file;
    private final SharedCredentialFile migrated;
    private final CredentialStore legacy;
    SharedCredentialStore(SharedCredentialFile file, CredentialStore legacy) {
        this.file = file; this.legacy = legacy;
        migrated = new SharedCredentialFile(file.file().resolveSibling(file.file().getFileName() + ".os-migrated"));
    }
    @Override public String description() { return "Shared compiler sign-in: " + file.file(); }
    @Override public Optional<CompilerCredential> load() throws IOException { return Optional.ofNullable(from(file.read())); }
    @Override public void save(CompilerCredential c) throws IOException { file.write(to(c)); }
    @Override public void delete() throws IOException { file.delete(); }
    @Override public boolean replace(CompilerCredential expected, CompilerCredential next) throws IOException {
        return file.compareAndWrite(to(expected), to(next));
    }
    @Override public boolean delete(CompilerCredential expected) throws IOException { return file.compareAndDelete(to(expected)); }

    public record Migration(CompilerCredential shared, CompilerCredential previous) { }
    /** No fallback: a legacy credential is never silently used as the active sign-in. */
    public Optional<Migration> migration() throws IOException {
        if (migrated.read() != null) return Optional.empty();
        CompilerCredential old = legacy.load().orElse(null);
        return old == null ? Optional.empty() : Optional.of(new Migration(load().orElse(null), old));
    }
    /** Call only after the user chooses and the chosen credential is verified with its issuer. */
    public void migrate(Migration observed, boolean usePrevious) throws IOException {
        if (!Objects.equals(legacy.load().orElse(null), observed.previous()))
            throw new IOException("The previous sign-in changed. Review migration again.");
        CompilerCredential chosen = usePrevious ? observed.previous() : observed.shared();
        if (chosen == null) throw new IOException("No shared sign-in to keep.");
        if (!replace(observed.shared(), chosen)) throw new IOException("The shared sign-in changed. Review migration again.");
        if (!legacy.delete(observed.previous())) throw new IOException("The old sign-in changed; it was retained.");
        migrated.write(new SharedCredentialFile.Stored("complete", null, null, null));
    }
    private static SharedCredentialFile.Stored to(CompilerCredential c) {
        return c == null ? null : new SharedCredentialFile.Stored(c.bearer(), c.credentialId(), c.accountId(), c.service());
    }
    private static CompilerCredential from(SharedCredentialFile.Stored s) throws IOException {
        if (s == null) return null;
        try { return new CompilerCredential(s.url,s.credentialId,s.accountId,s.token); }
        catch (IllegalArgumentException e) { throw new IOException("The shared compiler sign-in is incomplete or damaged."); }
    }
}
