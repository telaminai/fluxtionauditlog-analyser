package telamin.fluxtion.audit.analyser.analyser.llm;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The write-side gate for assistant verbs that produce files ({@code screenshot}, {@code report}).
 * Verb-initiated writes are <b>opt-in</b> (Settings ▸ Assistant ▸ "Allow assistant file exchange"
 * — one opt-in covers writes AND M29's external reads, deliberately) and <b>confined</b>
 * to one user-chosen exchange directory; existing files are never overwritten. Human-driven exports
 * (menu choosers) are not routed through here — a person picking a location in a dialog <i>is</i> the
 * authorisation; this guard exists for the path where no person is in the loop.
 *
 * <p>Pure and headless: resolution + policy only, no UI. The FAQ's security answer states this contract;
 * {@code FaqSecurityContractTest} keeps the two from drifting.
 */
public final class ExportGuard {

    /** Either a resolved, writable path ({@code error == null}) or the reason the write is refused. */
    public record Resolved(Path path, String error) {
        public boolean ok() {
            return error == null;
        }
    }

    private ExportGuard() {
    }

    /**
     * Resolve a verb-supplied path against the export policy. Relative paths land inside the export
     * directory (the friendly agent form: {@code "finding.pdf"}); absolute paths are accepted only if
     * they normalise to somewhere inside it.
     */
    /**
     * The READ counterpart (M29 D-F4): a verb-supplied path may be read only from the configured
     * exchange directory — the one writes are already confined to, behind the same opt-in — or when it
     * IS a file the user picked in a chooser this session (the chooser is the grant). The refusal names
     * the setting AND the directory (review F1): the first MCP user to hit it must learn which switch
     * was meant without leaving the error message.
     */
    public static Resolved resolveRead(String requested, boolean exchangeEnabled, String exportDir,
                                       java.util.Set<Path> sessionGrants) {
        if (requested == null || requested.isBlank()) {
            return new Resolved(null, "'path' is required");
        }
        Path candidate;
        try {
            candidate = Path.of(requested).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            return new Resolved(null, "'" + requested + "' is not a path");
        }
        if (sessionGrants != null && sessionGrants.contains(candidate)) {
            return new Resolved(candidate, null);   // picked by the user this session — the chooser IS the grant
        }
        if (!exchangeEnabled) {
            return new Resolved(null, "assistant file exchange is disabled — reads and writes share the "
                    + "one opt-in: enable Settings ▸ Assistant ▸ 'Allow assistant file exchange' and "
                    + "choose an exchange directory");
        }
        if (exportDir == null || exportDir.isBlank()) {
            return new Resolved(null, "no exchange directory is configured — set one in Settings ▸ Assistant");
        }
        Path dir = Path.of(exportDir).toAbsolutePath().normalize();
        Path resolved = (Path.of(requested).isAbsolute() ? candidate : dir.resolve(requested))
                .toAbsolutePath().normalize();
        // Compare actual locations: an absolute request may spell the same directory through an alias.
        return containedTarget(dir, resolved);
    }

    public static Resolved resolve(String requested, boolean exportsEnabled, String exportDir) {
        if (requested == null || requested.isBlank()) {
            return new Resolved(null, "'path' is required");
        }
        if (!exportsEnabled) {
            return new Resolved(null, "file exports are disabled — enable Settings ▸ Assistant ▸ "
                    + "'Allow assistant file exchange' and choose an exchange directory");
        }
        if (exportDir == null || exportDir.isBlank()) {
            return new Resolved(null, "no exchange directory is configured — set one in Settings ▸ Assistant");
        }
        Path dir = Path.of(exportDir).toAbsolutePath().normalize();
        Path candidate = Path.of(requested);
        Path resolved = (candidate.isAbsolute() ? candidate : dir.resolve(candidate)).toAbsolutePath().normalize();
        Resolved target = containedTarget(dir, resolved);
        if (!target.ok()) return target;
        if (Files.exists(target.path())) {
            return new Resolved(null, "file already exists: " + resolved + " — exports never overwrite; "
                    + "pick a new name");
        }
        return target;
    }

    private static Resolved containedTarget(Path dir, Path resolved) {
        try {
            Path realDir = canonicalPath(dir);
            Path realTarget = canonicalPath(resolved);
            if (!realTarget.startsWith(realDir)) {
                return new Resolved(null, "path resolves outside the exchange directory (" + realDir
                        + ") — links cannot widen assistant file exchange");
            }
            return new Resolved(realTarget, null);
        } catch (java.io.IOException e) {
            return new Resolved(null, "cannot resolve the exchange path: " + e.getMessage());
        }
    }

    /** Resolve links in every existing ancestor, retaining a missing descendant for a new output.
     * Returning the canonical target avoids a later swap of an alias already traversed here. This
     * does not lock directories against concurrent local filesystem changes after the check. */
    private static Path canonicalPath(Path path) throws java.io.IOException {
        Path ancestor = path;
        while (!Files.exists(ancestor, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            ancestor = ancestor.getParent();
            if (ancestor == null) throw new java.io.IOException("no existing ancestor");
        }
        try {
            return ancestor.toRealPath().resolve(ancestor.relativize(path)).normalize();
        } catch (java.io.IOException e) {
            if (Files.isSymbolicLink(ancestor)) {
                throw new java.io.IOException("symbolic link does not resolve: " + ancestor, e);
            }
            throw e;
        }
    }
}
