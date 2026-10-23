package telamin.fluxtion.audit.analyser.analyser.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

/** Pinned evidence under /demo; unlike M75's /teaching prose these are original run/source bytes, not lesson documents. */
public final class DiamondAssets {
    private DiamondAssets() { }
    public record Asset(String file, String source, String sha256) { }
    public static Path root() { return DemoAssets.root().resolve("diamond"); }
    public static Path log() { return root().resolve("diamond-spring-audit.yaml"); }
    public static Path graph() { return root().resolve("DiamondSpringProcessor.graphml"); }
    public static Path design() { return root().resolve("diamond.xml"); }
    public static Path sourceRoot() { return root().resolve("source"); }

    public static Properties manifest() throws IOException {
        Properties p = new Properties();
        try (InputStream in = DiamondAssets.class.getResourceAsStream("/demo/diamond/manifest.properties")) {
            if (in == null) throw new IOException("the bundled diamond manifest is missing");
            p.load(in);
        }
        return p;
    }

    public static List<Asset> files() throws IOException {
        Properties p = manifest();
        List<Asset> files = new ArrayList<>();
        int count;
        try { count = Integer.parseInt(p.getProperty("asset.count", "0")); }
        catch (NumberFormatException e) { throw new IOException("the bundled diamond manifest has no valid asset count", e); }
        if (count < 1 || count > 100) throw new IOException("the bundled diamond manifest has no valid asset count");
        for (int i = 0; i < count; i++) {
            String prefix = "asset." + i + ".";
            String file = p.getProperty(prefix + "file", "");
            String digest = p.getProperty(prefix + "sha256", "");
            if (file.isBlank() || Path.of(file).isAbsolute() || file.contains("\\")
                    || !Path.of(file).normalize().equals(Path.of(file)) || file.startsWith("..")
                    || !digest.matches("[a-f0-9]{64}")) throw new IOException("invalid bundled diamond manifest entry " + i);
            files.add(new Asset(file, p.getProperty(prefix + "source", ""), digest));
        }
        return List.copyOf(files);
    }

    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /** Called off the EDT. Modified/stale extracted members are replaced from verified jar bytes, never reused by name. */
    public static Path install() throws IOException { return install(root()); }

    public static Path install(Path destination) throws IOException {
        var assets = files();
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(destination))
            throw new IOException("the diamond extraction folder is not a directory");
        Files.createDirectories(destination);
        Path base = destination.toRealPath();
        for (Asset asset : assets) {
            byte[] original;
            try (InputStream in = DiamondAssets.class.getResourceAsStream("/demo/diamond/" + asset.file())) {
                if (in == null) throw new IOException("the bundled diamond is missing " + asset.file());
                original = in.readNBytes(2 * 1024 * 1024 + 1);
            }
            if (original.length > 2 * 1024 * 1024 || !asset.sha256().equals(sha256(original)))
                throw new IOException("bundled diamond manifest mismatch: " + asset.file());
            Path out = base.resolve(asset.file());
            Path ancestor = out.getParent();
            while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) ancestor = ancestor.getParent();
            if (!ancestor.toRealPath().startsWith(base)) throw new IOException("diamond extraction parent escapes its root");
            Files.createDirectories(out.getParent());
            if (!out.getParent().toRealPath().startsWith(base)) throw new IOException("diamond extraction parent escapes its root");
            if (Files.isRegularFile(out, LinkOption.NOFOLLOW_LINKS) && Files.size(out) == original.length
                    && asset.sha256().equals(sha256(Files.readAllBytes(out)))) continue;
            Path temp = Files.createTempFile(out.getParent(), ".diamond-", ".tmp");
            try {
                Files.write(temp, original);
                Files.move(temp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally { Files.deleteIfExists(temp); }
        }
        return base;
    }
}
