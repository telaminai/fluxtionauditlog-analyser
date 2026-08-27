package telamin.fluxtion.audit.analyser.analyser.config;

import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * M38.6 (spec-portable-context D-C9) — the FORM a path is stored in, and the project's optional
 * <b>workspace anchor</b>.
 *
 * <p>Three forms already existed and are chosen automatically, most specific first: project-relative
 * (M35.11), {@code ~/…}, absolute. The gap was a missing anchor, not a missing option: a sibling checkout —
 * {@code ../shared-lib/src/main/java}, the monorepo neighbour — is outside the project root, so it fell to
 * {@code ~/work/shared-lib/…}: portable for <i>you</i> on another machine and silently wrong for a colleague
 * who checks out somewhere else. {@code workspaceRoot} is declared once, per project, by the person who
 * knows the layout ({@code ..}, {@code ../..}); a path under it that is not under the project root is then
 * written relative to the project root with {@code ..} steps, bounded by the anchor.
 *
 * <p>This does NOT weaken D-C2: runbook and vocabulary pointers stay project-relative with no {@code ..}
 * ({@link Runbooks#refusePointer}) — those are things an agent acts on. Source roots and Maven repos are
 * inert lists the analyser resolves, and may use the wider anchor.
 *
 * <p>And it is made visible (M37): the Project panel shows each root's stored form, so "this profile is not
 * portable" is a badge on a row before it is shared, not a failure on a colleague's machine.
 */
public final class PathForm {

    private PathForm() {
    }

    public enum Form {
        /** Under the project root — written relative to it. */
        PROJECT("project-relative"),
        /** Under the declared workspace anchor but outside the project — written with {@code ..} steps. */
        WORKSPACE("workspace-relative"),
        /** Under the user's home, outside project and workspace — written {@code ~/…}; portable for one person only. */
        HOME("~"),
        /** None of the above — written verbatim; correct on this machine and no other. */
        ABSOLUTE("absolute"),
        /** Already relative as configured (a bundle, a hand-written profile). */
        RELATIVE("relative");

        public final String label;

        Form(String label) {
            this.label = label;
        }
    }

    /** {@code .}, {@code ..}, {@code ../..} … — at or above the project root, at most six levels up. */
    private static final Pattern ANCHOR = Pattern.compile("\\.|\\.\\.(/\\.\\.){0,5}");

    /** Why this anchor may NOT be stored, or empty when it is a plain run of {@code ..} steps. */
    public static Optional<String> refuseWorkspaceRoot(String anchor) {
        if (anchor == null || anchor.isBlank()) return Optional.empty();          // absent is fine
        if (!ANCHOR.matcher(anchor.trim()).matches()) {
            return Optional.of("workspaceRoot '" + anchor + "' must be '.', '..', '../..' … (a directory at or above the "
                    + "project root, at most six levels up) — it is an anchor, not a path");
        }
        return Optional.empty();
    }

    /** The anchor as a directory on this machine, or null when unset or when there is no project root. */
    public static Path workspaceDir(Path projectRoot, String anchor) {
        if (projectRoot == null || anchor == null || anchor.isBlank() || refuseWorkspaceRoot(anchor).isPresent()) return null;
        return projectRoot.toAbsolutePath().normalize().resolve(anchor.trim()).normalize();
    }

    /** How {@code path} is (or would be) stored in this project's profile. */
    public static Form of(String path, Path projectRoot, String anchor, String home) {
        if (path == null || path.isBlank()) return Form.RELATIVE;
        Path p = Path.of(path);
        if (!p.isAbsolute()) return Form.RELATIVE;
        Path abs = p.normalize();
        if (projectRoot != null) {
            Path root = projectRoot.toAbsolutePath().normalize();
            if (abs.equals(root) || abs.startsWith(root)) return Form.PROJECT;
            Path ws = workspaceDir(projectRoot, anchor);
            if (ws != null && abs.startsWith(ws)) return Form.WORKSPACE;
        }
        if (home != null && !home.isBlank()) {
            Path h = Path.of(home).toAbsolutePath().normalize();
            if (abs.equals(h) || abs.startsWith(h)) return Form.HOME;
        }
        return Form.ABSOLUTE;
    }
}
