package telamin.fluxtion.audit.analyser.analyser.source;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/** One read: rendered bytes and their origin travel together, including in archive caches. */
public record SourceDocument(String text, String root, String file, String archive, String entry,
                             String revision) {
    public static SourceDocument file(String text, Path root, Path file) {
        return new SourceDocument(text, absolute(root), absolute(file), null, null, digest(text));
    }
    public static SourceDocument archive(String text, Path jar, String entry) {
        return new SourceDocument(text, null, null, absolute(jar), entry, digest(text));
    }
    private static String absolute(Path path) { return path.toAbsolutePath().normalize().toString(); }
    public String identity() { return file != null ? file : archive + "!/" + entry; }
    public Map<String, Object> origin() {
        return file != null ? Map.of("root", root, "file", file) : Map.of("archive", archive, "entry", entry);
    }
    public static String digest(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
