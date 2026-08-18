package telamin.fluxtion.audit.analyser.analyser.llm;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The write-side gate for assistant verbs that produce files ({@code screenshot}, {@code report}).
 * Verb-initiated writes are <b>opt-in</b> (Settings ▸ Assistant ▸ "Allow assistant file exchange"
 * — one opt-in covers writes AND M29's external reads, deliberately) and <b>confined</b>
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
        if (!resolved.startsWith(dir)) {
            return new Resolved(null, "path is outside the exchange directory (" + dir + ") — external "
                    + "reads are confined to it (or to files the user picked in a chooser this session); "
                    + "place the file inside it or have the user open it by hand");
        }
        return new Resolved(resolved, null);
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
