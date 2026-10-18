package telamin.fluxtion.audit.analyser.analyser.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * The folders whose contents the analyser must never be pointed at — ONE function, shared by every place that decides
 * whether a folder may be read through the analyser: Claude Code's working directory ({@code LocalAgentFolders}) and
 * every source root ({@link SourceRootPolicy}, owner decision 2026-10-18, P5).
 *
 * <p>Exposed: the home folder or any folder containing it (it holds every credential folder), and a credential folder —
 * {@code ~/.ssh}, {@code ~/.aws}, {@code ~/.m2}, {@code ~/.config}, {@code ~/.gnupg}, plus any a caller adds — or a folder
 * inside one, or a folder containing one. Paths are compared as they really are: symbolic links resolved (the longest
 * existing prefix made real, the rest appended), and on macOS, whose file system ignores case by default, without
 * regard to case — so {@code ~/.SSH} or a link to {@code ~/.ssh} is {@code ~/.ssh}.
 */
public final class SecretFolders {

    /** The credential folders under the home folder, by name. */
    public static final List<String> CREDENTIAL_FOLDERS = List.of(".ssh", ".aws", ".m2", ".config", ".gnupg");

    /** What a folder exposes. */
    public enum Exposure {
        /** Nothing this function knows of. */
        NONE,
        /** It is the home folder, or contains it. */
        HOME_OR_ANCESTOR,
        /** It is a credential folder, is inside one, or contains one. */
        CREDENTIAL_FOLDER
    }

    private SecretFolders() {
    }

    /** What {@code dir} exposes, with {@code home} the user's home folder and {@code alsoSecret} more folder names in it. */
    public static Exposure exposure(Path dir, Path home, Collection<String> alsoSecret) {
        if (dir == null || home == null) return Exposure.NONE;
        Path d = canonical(dir);
        Path h = canonical(home);
        if (within(h, d)) return Exposure.HOME_OR_ANCESTOR;
        List<String> names = new ArrayList<>(CREDENTIAL_FOLDERS);
        if (alsoSecret != null) names.addAll(alsoSecret);
        for (String name : names) {
            Path s = canonical(h.resolve(name));
            if (within(d, s) || within(s, d)) return Exposure.CREDENTIAL_FOLDER;
        }
        return Exposure.NONE;
    }

    /** {@link #exposure(Path, Path, Collection)} with the credential folders alone. */
    public static Exposure exposure(Path dir, Path home) {
        return exposure(dir, home, List.of());
    }

    /**
     * {@code p} as it really is: the longest existing prefix with its links resolved ({@code toRealPath}), and the names
     * that do not exist yet appended, normalised. A path that names nothing still resolves through a linked parent.
     */
    public static Path canonical(Path p) {
        Path abs = p.toAbsolutePath().normalize();
        Path existing = abs;
        List<Path> rest = new ArrayList<>();
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            if (existing.getFileName() != null) rest.add(0, existing.getFileName());
            existing = existing.getParent();
        }
        Path out;
        try {
            out = existing == null ? abs : existing.toRealPath();
        } catch (IOException e) {
            return abs;
        }
        for (Path name : rest) out = out.resolve(name);
        return out.normalize();
    }

    /** Whether {@code child} is {@code parent} or inside it — without regard to case where the file system ignores it. */
    public static boolean within(Path child, Path parent) {
        if (child == null || parent == null) return false;
        if (!CASE_INSENSITIVE) return child.startsWith(parent);
        return Path.of(child.toString().toLowerCase(Locale.ROOT)).startsWith(Path.of(parent.toString().toLowerCase(Locale.ROOT)));
    }

    /** macOS's default file system (APFS, HFS+) ignores case; elsewhere case is significant. */
    static final boolean CASE_INSENSITIVE = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
}
