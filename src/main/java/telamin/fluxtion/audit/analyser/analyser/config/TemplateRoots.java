package telamin.fluxtion.audit.analyser.analyser.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Edit-loop spec §I1: a template's own profile may only point inside the template it came with.
 *
 * <p>A profile's source roots are read grants, and a template profile is untrusted archive content. Profiles a
 * person writes may name roots outside their project on purpose (a monorepo neighbour, see {@link PathForm});
 * that stays supported and is not checked here. This check runs only when {@code TemplateArchive} installs a
 * downloaded template, against the STAGED project before the atomic move, so a refused profile never becomes an
 * installed one.
 *
 * <p>The rules, applied to each stored root before it is resolved: no {@code ~} or {@code ~/} (home-relative),
 * no root component ({@code /x}, and on Windows {@code C:x} or {@code \x}), not empty after normalisation (the
 * project root itself would grant the whole project), and no leading {@code ..}. Only a plain descendant path
 * keeps its containment when the staged directory is moved to a destination with a different name — a root such
 * as {@code ../<archive-root>/src} resolves inside staging and outside the installed project. The candidate is
 * then resolved against staging with both sides canonicalised; a directory that does not exist yet (a future
 * {@code target/} root) is judged by its nearest existing ancestor, which must be a directory. A template may
 * not declare a {@code workspaceRoot} at all: the loader does not resolve roots against it, but it shapes how
 * paths are written back, and a template has no business widening that. Maven repositories are outside this
 * rule: they are searched for source archives only, and {@code ~/.m2} is a legitimate default.
 */
public final class TemplateRoots {

    private TemplateRoots() {
    }

    /** Refuse the template if any source root in its staged profile could read outside the staged project. */
    public static void requireContained(Path stagedRoot, Path profile) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(profile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IllegalArgumentException malformed) {
            throw new IOException("template profile is not a readable settings file: " + malformed.getMessage());
        }
        String anchor = properties.getProperty("workspaceRoot");
        if (anchor != null && !anchor.isBlank()) {
            throw new IOException("template profile declares a workspace anchor ('" + anchor.trim()
                    + "'); a template cannot widen its own boundary");
        }
        List<String> roots = new ArrayList<>();
        ConfigStore.readList(properties, "sourceRoot", roots);
        Path boundary = stagedRoot.toRealPath();
        for (String root : roots) check(boundary, root);
    }

    static void check(Path boundary, String root) throws IOException {
        if (root == null || root.isBlank()) throw refuse(root, "is blank");
        if (root.equals("~") || root.startsWith("~/")) throw refuse(root, "is home-relative");
        Path path;
        try {
            path = Path.of(root);
        } catch (InvalidPathException e) {
            throw refuse(root, "is not a valid path");
        }
        if (path.getRoot() != null) throw refuse(root, "has a root component (an absolute, drive or rooted path)");
        Path normal = path.normalize();
        if (normal.toString().isEmpty()) throw refuse(root, "is the project root itself, which would grant the whole project");
        if (normal.getName(0).toString().equals("..")) throw refuse(root, "leaves the project");
        Path candidate = boundary.resolve(normal);
        Path existing = candidate;
        while (!Files.exists(existing)) existing = existing.getParent();   // boundary exists, so this ends
        if (!Files.isDirectory(existing)) throw refuse(root, "passes through a file that is not a directory");
        Path resolved = existing.toRealPath().resolve(existing.relativize(candidate));
        if (!resolved.startsWith(boundary)) throw refuse(root, "resolves outside the project");
    }

    private static IOException refuse(String root, String why) {
        return new IOException("template profile source root '" + root + "' " + why + "; the template was not installed");
    }
}
