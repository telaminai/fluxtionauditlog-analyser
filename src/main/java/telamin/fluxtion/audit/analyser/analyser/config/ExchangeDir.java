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
 *
 * <h2>An open project with no value: {@code <project>/analyser-exchange/} (#182)</h2>
 *
 * <p>With a person's own project open and nothing declared, the machine directory used to answer — and that was
 * often ANOTHER project's folder, so this project's screenshots, bundles and reels landed in someone else's tree. The
 * open project now answers instead: {@value #PROJECT_DEFAULT_DIR} under its root. This is not a profile asking for a
 * location, so the "refused, not created" rule above does not apply: the directory is the analyser's own default,
 * created only when a write actually lands there ({@link #ensureCreated}), behind the same machine-tier opt-in. The
 * sample project and an evidence bundle's working copy are the analyser's, not the person's, so they keep the machine
 * directory; so does a session with no project, and a machine directory that already lies inside the open project. An existing {@value #PROJECT_DEFAULT_DIR} that is a link leaving the
 * project, or not a directory, falls back to the machine tier and says why, exactly as a declared value does.
 */
public record ExchangeDir(String dir, String source, String refusal, boolean projectDefault) {

    /** #182: the directory an open project's exchange defaults to when it declares none. */
    public static final String PROJECT_DEFAULT_DIR = "analyser-exchange";

    public ExchangeDir(String dir, String source, String refusal) {
        this(dir, source, refusal, false);
    }

    /**
     * #182: make the project default directory when a write is about to land in it, and nothing else — a declared
     * directory is never created (the profile rule above), and the machine tier is the person's own choice.
     */
    public void ensureCreated() throws java.io.IOException {
        if (!projectDefault || dir == null || dir.isBlank()) return;
        Path d = Path.of(dir);
        if (!Files.isDirectory(d)) Files.createDirectory(d);
    }

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
        if (!c.assistantExports) {
            return new ExchangeDir(machine, MACHINE, null);
        }
        if (wanted.isEmpty()) {
            return projectDefault(c, machine);
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
        // PR #33 review, R2: the checks above are LEXICAL, and isDirectory follows links. A directory inside the
        // project that is a symbolic link to somewhere else passed all of them, and git preserves links, so a
        // cloned repository could point the assistant's WRITES outside the project. Compare where it really is.
        try {
            Path real = resolved.toRealPath();
            Path realRoot = root.toRealPath();
            if (!real.startsWith(realRoot)) {
                return new ExchangeDir(machine, MACHINE, LABEL + ": '" + wanted + "' is a link that leaves the "
                        + "project (it resolves to " + real + ") — using the machine setting instead");
            }
            // Keep the checked location, not the alias that could be redirected before the write.
            return new ExchangeDir(real.toString(), PROJECT, null);
        } catch (java.io.IOException e) {
            return new ExchangeDir(machine, MACHINE, LABEL + ": '" + wanted + "' could not be resolved ("
                    + e.getMessage() + ") — using the machine setting instead");
        }
    }

    /**
     * #182: the directory a verb's WRITE uses — {@link #of}, with the project default made first when that is where the
     * write will land. Only for a write that is about to happen: reading the location never creates anything.
     */
    public static ExchangeDir forWrite(AppConfig c) {
        ExchangeDir d = of(c);
        try {
            d.ensureCreated();
        } catch (java.io.IOException | SecurityException e) {
            return new ExchangeDir(d.dir(), d.source(), "the project's default exchange directory could not be created ("
                    + e.getMessage() + ")", true);
        }
        return d;
    }

    /**
     * #182: an open, person-owned project that declares no exchange directory gets {@value #PROJECT_DEFAULT_DIR} under
     * its root; anything else (no project, the sample, a bundle's working copy) keeps the machine tier, unremarked.
     */
    private static ExchangeDir projectDefault(AppConfig c, String machine) {
        Path root = projectRoot(c);
        if (root == null || !Files.isDirectory(root) || analyserOwned(root)) {
            return new ExchangeDir(machine, MACHINE, null);
        }
        Path resolved = root.resolve(PROJECT_DEFAULT_DIR);
        try {
            Path realRoot = root.toRealPath();
            // the machine directory is already inside THIS project: it is this project's folder, so it stays
            if (!machine.isBlank() && Files.isDirectory(Path.of(machine))
                    && Path.of(machine).toRealPath().startsWith(realRoot)) {
                return new ExchangeDir(machine, MACHINE, null);
            }
            if (!Files.exists(resolved, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                // made when a write lands (ensureCreated); named by its canonical parent so the guard sees one spelling
                return new ExchangeDir(realRoot.resolve(PROJECT_DEFAULT_DIR).toString(), PROJECT, null, true);
            }
            if (!Files.isDirectory(resolved)) {
                return new ExchangeDir(machine, MACHINE, "the project's default exchange directory '" + PROJECT_DEFAULT_DIR
                        + "' exists and is not a directory (" + resolved + ") — using the machine setting instead");
            }
            Path realDefault = resolved.toRealPath();
            if (!realDefault.startsWith(realRoot)) {
                return new ExchangeDir(machine, MACHINE, "the project's default exchange directory '" + PROJECT_DEFAULT_DIR
                        + "' is a link that leaves the project (it resolves to " + realDefault + ") — using the machine setting instead");
            }
            return new ExchangeDir(realDefault.toString(), PROJECT, null, true);
        } catch (java.io.IOException | SecurityException e) {
            return new ExchangeDir(machine, MACHINE, "the project's default exchange directory could not be resolved ("
                    + e.getMessage() + ") — using the machine setting instead");
        }
    }

    /** The sample project and a bundle's working copy are the analyser's own folders (OD-74.22's one rule). */
    private static boolean analyserOwned(Path root) {
        String home = System.getProperty("user.home");
        return telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentFolders.analyserOwned(root,
                home == null ? null : Path.of(home), telamin.fluxtion.audit.analyser.bundle.EvidenceBundle::isWorkingCopy);
    }

    /**
     * The project's root directory, or null when no project is open.
     *
     * <p>Via {@link ProjectProfile#baseDirFor}, which is where the rule lives: a project profile in
     * {@code <root>/.analyser/} anchors on {@code <root>}, and a loose settings file anchors on its
     * own directory. A second copy of that rule here would be a second thing to get wrong when named
     * profiles changed it once already.
     */
    public static Path projectRoot(AppConfig c) {
        if (c == null || c.activeProjectPath == null || c.activeProjectPath.isBlank()) {
            return null;
        }
        return ProjectProfile.baseDirFor(Path.of(c.activeProjectPath));
    }
}
