package telamin.fluxtion.audit.analyser.analyser.account;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * CA-1's documented fallback when no OS credential store is usable: one file in the analyser's own home directory
 * ({@code ~/.fluxtion-analyser/compiler-account.credential}) that only its owner can read. On a POSIX file system it
 * is created mode 600 from the start (no moment at a wider mode); elsewhere its ACL is reduced to the owner alone.
 * It is protection from other accounts on the machine, not from the owner's own processes, and the docs say so.
 */
public final class ProtectedFileStore implements CredentialStore {

    public static final String FILE_NAME = "compiler-account.credential";
    private static final Set<PosixFilePermission> OWNER_ONLY = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final Path file;

    public ProtectedFileStore(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    @Override
    public String description() {
        return "an owner-only file, ~/.fluxtion-analyser/" + FILE_NAME;
    }

    @Override
    public Optional<CompilerCredential> load() throws IOException {
        if (!Files.isRegularFile(file)) return Optional.empty();
        return Optional.of(CompilerCredential.decode(Files.readString(file, StandardCharsets.UTF_8)));
    }

    @Override
    public void save(CompilerCredential credential) throws IOException {
        Files.createDirectories(file.getParent());
        boolean posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        Path tmp = posix
                ? Files.createTempFile(file.getParent(), ".compiler-account", ".tmp", PosixFilePermissions.asFileAttribute(OWNER_ONLY))
                : Files.createTempFile(file.getParent(), ".compiler-account", ".tmp");
        try {
            if (!posix) ownerOnlyAcl(tmp);
            Files.writeString(tmp, credential.encode(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public void delete() throws IOException {
        Files.deleteIfExists(file);
    }

    private static void ownerOnlyAcl(Path path) throws IOException {
        AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (view == null) throw new IOException("this file system cannot restrict the credential file to its owner");
        UserPrincipal owner = Files.getOwner(path);
        view.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
    }
}
