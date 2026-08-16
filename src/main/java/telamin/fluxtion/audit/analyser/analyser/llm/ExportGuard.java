package telamin.fluxtion.audit.analyser.analyser.llm;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The write-side gate for assistant verbs that produce files ({@code screenshot}, {@code report}).
 * Verb-initiated writes are <b>opt-in</b> (Settings ▸ Assistant ▸ "Allow file exports") and <b>confined</b>
 * to one user-chosen export directory; existing files are never overwritten. Human-driven exports
 * (File ▸ choosers) are not routed through here — a person picking a location in a dialog <i>is</i> the
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
    public static Resolved resolve(String requested, boolean exportsEnabled, String exportDir) {
        if (requested == null || requested.isBlank()) {
            return new Resolved(null, "'path' is required");
        }
        if (!exportsEnabled) {
            return new Resolved(null, "file exports are disabled — enable Settings ▸ Assistant ▸ "
                    + "'Allow file exports (screenshot / report)' and choose an export directory");
        }
        if (exportDir == null || exportDir.isBlank()) {
            return new Resolved(null, "no export directory is configured — set one in Settings ▸ Assistant");
        }
        Path dir = Path.of(exportDir).toAbsolutePath().normalize();
        Path candidate = Path.of(requested);
        Path resolved = (candidate.isAbsolute() ? candidate : dir.resolve(candidate)).toAbsolutePath().normalize();
        if (!resolved.startsWith(dir)) {
            return new Resolved(null, "path is outside the export directory (" + dir + ") — exports are "
                    + "confined to it; pass a relative name to write inside it");
        }
        if (Files.exists(resolved)) {
            return new Resolved(null, "file already exists: " + resolved + " — exports never overwrite; "
                    + "pick a new name");
        }
        return new Resolved(resolved, null);
    }
}
