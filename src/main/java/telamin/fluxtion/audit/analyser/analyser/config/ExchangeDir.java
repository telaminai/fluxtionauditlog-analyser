package telamin.fluxtion.audit.analyser.analyser.config;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Which directory the assistant's file exchange actually uses, and which tier said so (#21).
 *
 * <h2>A project says WHERE, never WHETHER</h2>
 *
 * <p>{@code assistantExports} — the "Allow assistant file exchange" opt-in — stays machine tier and is
 * not in this decision at all. Opening someone's project can therefore widen no permission: with the
 * opt-in off, a project-supplied location changes nothing, and {@link #of} returns the machine tier
 * so the value cannot even be read. What a project CAN say is where its own exports belong, which is
 * the thing a shared profile actually knows and a machine does not: {@code src/report/shared} is a
 * fact about the repository, not about the laptop.
 *
 * <h2>Anchored on the project root, NOT on {@code workspaceRoot}</h2>
 *
 * <p>The workspace anchor deliberately permits {@code ..} up to six levels ({@link PathForm}), which
 * is safe for source roots because those are inert lists the analyser only ever reads. <b>This
 * directory is written to, by the assistant.</b> A {@code ..}-capable anchor would let a profile
 * pulled from a repository place the assistant's write directory anywhere at or above the project, so
 * the value goes through {@link Runbooks#refusePointer}, the same gate every other pointer a profile
 * holds passes: relative, no {@code ..}, no {@code ~}, no URL, no shell metacharacters.
 *
 * <h2>A directory that is not there is refused, not created</h2>
 *
 * <p>Creating directories as a side effect of opening someone else's profile is exactly the kind of
 * quiet act that should be deliberate. A missing or non-directory value falls back to the machine
 * tier and says why, so the answer to "why did my screenshot land somewhere else" is on screen rather
 * than inferred.
 */
public record ExchangeDir(String dir, String source, String refusal) {

    /** The project tier supplied it. */
    public static final String PROJECT = "project";
    /** Machine settings supplied it (or nothing did). */
    public static final String MACHINE = "machine";

    private static final String LABEL = "assistant.exchangeDir";

    /** True when a project asked for this directory rather than the machine settings. */
    public boolean fromProject() {
        return PROJECT.equals(source);
    }

    /**
     * Resolve the effective exchange directory.
     *
     * <p>Order: the project's value when a project is open, the exchange is enabled and the value
     * passes; otherwise the machine's {@code assistantExportDir}; otherwise unset, as before projects
     * existed. {@link #refusal()} is non-null only when a project asked for something and did not get
     * it — so a caller can state the reason without having to detect the fallback itself.
     */
    public static ExchangeDir of(AppConfig c) {
        if (c == null) {
            return new ExchangeDir("", MACHINE, null);
        }
        String machine = c.assistantExportDir == null ? "" : c.assistantExportDir;
        String wanted = c.projectExchangeDir == null ? "" : c.projectExchangeDir.trim();
        if (wanted.isEmpty() || !c.assistantExports) {
            return new ExchangeDir(machine, MACHINE, null);
        }
        Path root = projectRoot(c);
        if (root == null) {
            // the value is only meaningful relative to a project; with none open it is not "refused",
            // it simply does not apply, and saying so would be noise on every machine-tier session
            return new ExchangeDir(machine, MACHINE, null);
        }
        String refused = Runbooks.refusePointer(LABEL, wanted).orElse(null);
        if (refused != null) {
            return new ExchangeDir(machine, MACHINE, refused + " — using the machine setting instead");
        }
        Path resolved = Runbooks.resolve(root, wanted);
        if (resolved == null) {
            // defence in depth: refusePointer already rejected `..`, and resolve() returns null for
            // anything that would still land outside the root
            return new ExchangeDir(machine, MACHINE,
                    LABEL + ": '" + wanted + "' resolves outside the project — using the machine setting instead");
        }
        if (!Files.isDirectory(resolved)) {
            return new ExchangeDir(machine, MACHINE, LABEL + ": '" + wanted + "' is not a directory in this "
                    + "project (" + resolved + ") — it is NOT created from a profile; make it, or change the "
                    + "value. Using the machine setting instead");
        }
        return new ExchangeDir(resolved.toString(), PROJECT, null);
    }

    /**
     * The project's root directory from the active profile path ({@code <root>/.analyser/project…}),
     * or null when no project is open.
     */
    static Path projectRoot(AppConfig c) {
        if (c.activeProjectPath == null || c.activeProjectPath.isBlank()) {
            return null;
        }
        Path dir = Path.of(c.activeProjectPath).toAbsolutePath().normalize().getParent();  // .analyser
        return dir == null ? null : dir.getParent();
    }
}
