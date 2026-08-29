package telamin.fluxtion.audit.analyser.analyser.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * The analyser's deliberately narrow Fluxtion build-key boundary (M19.12).
 *
 * <p>The value crosses this class in one direction only: from a caller-owned {@code char[]} to the
 * established {@code apiKey=...} file. Reads expose presence, profile names and paths, never the value.
 * This keeps the credential out of {@link AppConfig}, project profiles, settings sharing, action
 * context and verb echoes by construction.
 *
 * <p>The builder's precedence belongs to the builder JVM: a {@code -Dfluxtion.apiKey} passed to that
 * future process overrides this file. This process cannot observe that future argument, and the builder
 * does not read {@code FLUXTION_API_KEY}, so this class reports neither as an answering source.
 */
public final class FluxtionKeyStore {

    public static final String FILE_NAME = "fluxtion.apiKeyFile";
    public static final String API_KEY_PROPERTY = "apiKey";
    public static final String PROFILES_DIR = "profiles";
    public static final String ACTIVE_PROFILE_FILE = "active-profile";

    private final Path fluxtionDir;

    public FluxtionKeyStore() {
        this(Path.of(System.getProperty("user.home"), ".fluxtion"));
    }

    public FluxtionKeyStore(Path fluxtionDir) {
        this.fluxtionDir = fluxtionDir.toAbsolutePath().normalize();
    }

    public Path canonicalFile() {
        return fluxtionDir.resolve(FILE_NAME);
    }

    public Path profilesDir() {
        return fluxtionDir.resolve(PROFILES_DIR);
    }

    /** True only when the canonical file contains a non-blank {@code apiKey}; the value is discarded. */
    public boolean keyPresent() {
        return hasKey(canonicalFile());
    }

    /** Named local profiles, sorted; no file contents leave this class. */
    public List<String> profiles() {
        if (!Files.isDirectory(profilesDir())) return List.of();
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(profilesDir())) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".properties"))
                    .sorted()
                    .forEach(p -> {
                        String file = p.getFileName().toString();
                        names.add(file.substring(0, file.length() - ".properties".length()));
                    });
        } catch (IOException ignored) {
            return List.of();
        }
        return List.copyOf(names);
    }

    public String activeProfile() {
        Path file = fluxtionDir.resolve(ACTIVE_PROFILE_FILE);
        try {
            if (!Files.isRegularFile(file)) return "";
            String name = Files.readString(file).trim();
            return validProfileName(name) ? name : "";
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /** Save directly to the canonical builder file. The supplied array is wiped before returning. */
    public void save(char[] key) throws IOException {
        requireKey(key);
        try {
            Properties properties = read(canonicalFile());
            properties.setProperty(API_KEY_PROPERTY, new String(key));
            write(properties, canonicalFile());
            writeActiveProfile("");
        } finally {
            Arrays.fill(key, '\0');
        }
    }

    /** Save a named profile and make it the canonical active configuration. */
    public void saveProfileAndActivate(String name, char[] key) throws IOException {
        try {
            name = requireProfileName(name);
            requireKey(key);
            Properties profile = new Properties();
            profile.setProperty(API_KEY_PROPERTY, new String(key));
            write(profile, profileFile(name));
            copyKey(profile, canonicalFile());
            writeActiveProfile(name);
        } finally {
            if (key != null) Arrays.fill(key, '\0');
        }
    }

    /** Activate a profile without ever returning or displaying its stored value. */
    public void activate(String name) throws IOException {
        name = requireProfileName(name);
        Path profileFile = profileFile(name);
        Properties profile = read(profileFile);
        if (!hasKey(profile)) throw new IOException("profile has no API key: " + name);
        copyKey(profile, canonicalFile());
        writeActiveProfile(name);
    }

    public void deleteProfile(String name) throws IOException {
        name = requireProfileName(name);
        Files.deleteIfExists(profileFile(name));
        if (name.equals(activeProfile())) writeActiveProfile("");
    }

    private void copyKey(Properties from, Path target) throws IOException {
        Properties canonical = read(target);
        canonical.setProperty(API_KEY_PROPERTY, from.getProperty(API_KEY_PROPERTY));
        write(canonical, target); // preserve visualiser/builder properties this analyser does not own
    }

    private Path profileFile(String name) {
        return profilesDir().resolve(name + ".properties");
    }

    private static boolean hasKey(Path file) {
        try {
            return hasKey(read(file));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static boolean hasKey(Properties properties) {
        String ignoredAfterCheck = properties.getProperty(API_KEY_PROPERTY);
        return ignoredAfterCheck != null && !ignoredAfterCheck.isBlank()
                && !"MISSING_KEY".equals(ignoredAfterCheck.trim());
    }

    private static Properties read(Path file) throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            }
        }
        return properties;
    }

    private static void write(Properties properties, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            properties.store(out, "Fluxtion configuration");
        }
        ownerOnly(file);
    }

    private void writeActiveProfile(String name) throws IOException {
        Files.createDirectories(fluxtionDir);
        Path file = fluxtionDir.resolve(ACTIVE_PROFILE_FILE);
        Files.writeString(file, name);
        ownerOnly(file);
    }

    private static void ownerOnly(Path file) {
        try {
            if (file.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            }
        } catch (IOException | UnsupportedOperationException ignored) {
            // Best effort on non-POSIX filesystems; the value is still never surfaced by this process.
        }
    }

    private static void requireKey(char[] key) {
        if (key == null || key.length == 0 || new String(key).isBlank()) {
            if (key != null) Arrays.fill(key, '\0');
            throw new IllegalArgumentException("API key must not be blank");
        }
    }

    private static String requireProfileName(String name) {
        if (!validProfileName(name)) {
            throw new IllegalArgumentException("Profile name must use letters, numbers, dash or underscore");
        }
        return name;
    }

    private static boolean validProfileName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,60}");
    }
}
