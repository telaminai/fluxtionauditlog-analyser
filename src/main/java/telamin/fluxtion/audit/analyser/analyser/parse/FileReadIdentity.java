package telamin.fluxtion.audit.analyser.analyser.parse;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;

/** SHA-256 over the exact raw bytes consumed by the indexer, not a separate file traversal. */
public record FileReadIdentity(String path, String sha256, String problem) {
    public static Capture begin(Path path) throws IOException { return new Capture(path); }

    public static final class Capture {
        private final Path path;
        private final BasicFileAttributes before;
        private final MessageDigest digest;
        private long count;
        private boolean opened;
        private Capture(Path path) throws IOException {
            this.path = path.toRealPath();
            before = Files.readAttributes(this.path, BasicFileAttributes.class);
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        }
        public InputStream open() throws IOException {
            if (opened) throw new IllegalStateException("identity stream already opened");
            opened = true;
            return new FilterInputStream(Files.newInputStream(path)) {
                @Override public int read() throws IOException {
                    int b = in.read();
                    if (b >= 0) { digest.update((byte)b); count++; }
                    return b;
                }
                @Override public int read(byte[] b, int off, int len) throws IOException {
                    int n = in.read(b, off, len);
                    if (n > 0) { digest.update(b, off, n); count += n; }
                    return n;
                }
                @Override public long skip(long n) throws IOException {
                    // Skipped bytes still belong to the identity.
                    long remaining = n;
                    byte[] b = new byte[8192];
                    while (remaining > 0) {
                        int r = read(b, 0, (int)Math.min(b.length, remaining));
                        if (r < 0) break;
                        remaining -= r;
                    }
                    return n - remaining;
                }
            };
        }
        /** For an already-read, losslessly decoded UTF-8 heap buffer; never rereads the file. */
        public void accept(byte[] bytes) {
            if (opened) throw new IllegalStateException("identity input already supplied");
            opened = true;
            digest.update(bytes); count = bytes.length;
        }
        public FileReadIdentity finish() {
            try {
                var after = Files.readAttributes(path, BasicFileAttributes.class);
                if (!before.isRegularFile() || count != before.size() || before.size() != after.size()
                        || !before.lastModifiedTime().equals(after.lastModifiedTime())
                        || !Objects.equals(before.fileKey(), after.fileKey()))
                    return new FileReadIdentity(path.toString(), null, "changed or incomplete during indexed read");
                return new FileReadIdentity(path.toString(), HexFormat.of().formatHex(digest.digest()), null);
            } catch (IOException e) { return new FileReadIdentity(path.toString(), null, "unavailable after indexed read"); }
        }
    }
}
