package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import telamin.fluxtion.audit.analyser.analyser.config.SecretFolders;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.FolderVerdict;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Review M4/S10: which working directories may be given to Claude Code at all. {@code --restricted} confines Read, Grep
 * and Glob to the working directory — so the directory itself must not hold credentials. Refused: the home folder or
 * any folder containing it (it holds {@code ~/.ssh}, {@code ~/.claude} and the analyser's endpoint files, with the REST
 * token and the hosted credential), and anything inside or containing {@code ~/.fluxtion-analyser}, {@code ~/.claude},
 * {@code ~/.ssh}, {@code ~/.aws}, {@code ~/.m2}, {@code ~/.config} or {@code ~/.gnupg} ({@link SecretFolders}). No folder
 * at all (the scratch folder could not be made) is refused too. Pure: the frame observes, the session decides.
 */
public final class LocalAgentFolders {

    private LocalAgentFolders() {
    }

    public static FolderVerdict check(Path dir, Path home) {
        if (dir == null) return FolderVerdict.UNAVAILABLE;
        if (home == null) return FolderVerdict.OK;
        // the home folder or a folder containing it, and (review R4) the credential folders a confined Read or Grep must
        // never reach — the ONE function source roots are judged by too (P5, 2026-10-18), plus the analyser's own and
        // Claude Code's settings folders
        return SecretFolders.exposure(dir, home, CLAUDE_CODE_ALSO_SECRET) == SecretFolders.Exposure.NONE
                ? FolderVerdict.OK : FolderVerdict.EXPOSES_SECRETS;
    }

    /** Beyond {@link SecretFolders#CREDENTIAL_FOLDERS}: the analyser's endpoint files and Claude Code's own settings. */
    static final java.util.List<String> CLAUDE_CODE_ALSO_SECRET = java.util.List.of(".fluxtion-analyser", ".claude");

    /**
     * OD-74.22 (amends OD-74.5; review c7f2 F2): whether {@code projectRoot} is a folder the ANALYSER owns and never starts
     * Claude Code in — the sample project under {@code ~/.fluxtion-analyser/demo}, or an evidence bundle's working copy
     * (under {@code ~/.fluxtion-analyser/bundles}, or recognised by {@code bundleCopy}). Any other refused folder (home, a
     * folder holding credentials) is NOT one of these: it is still refused.
     */
    public static boolean analyserOwned(Path projectRoot, Path home, java.util.function.Predicate<Path> bundleCopy) {
        if (projectRoot == null) return false;
        Path d = real(projectRoot);
        if (bundleCopy != null && bundleCopy.test(projectRoot)) return true;
        if (home == null) return false;
        Path owned = real(home.resolve(".fluxtion-analyser"));
        return d.startsWith(real(owned.resolve("demo"))) || d.startsWith(real(owned.resolve("bundles")));
    }

    /**
     * Owner hands-on test (2026-10-18): the sample project's real root ({@code ~/.fluxtion-analyser/demo}) when
     * {@code projectRoot} is inside it, else null. Only the sample: an evidence bundle's working copy is someone else's
     * evidence, and is not widened.
     */
    public static Path sampleRoot(Path projectRoot, Path home) {
        if (projectRoot == null || home == null) return null;
        try {
            // Canonicalise the home first (normal platform/home aliases are fine), then keep the analyser-owned
            // anchor fixed beneath it. Resolving a moved demo or analyser directory as the authority would turn,
            // for example, a link to home into a home-wide grant for an ordinary project's hosted open calls.
            Path anchor = home.toRealPath().resolve(".fluxtion-analyser").resolve("demo");
            Path demo = anchor.toRealPath();
            if (!demo.equals(anchor) || !java.nio.file.Files.isDirectory(demo)) return null;
            return projectRoot.toRealPath().startsWith(demo) ? demo : null;
        } catch (IOException | SecurityException e) {
            return null; // unresolved ownership never widens the pre-allow
        }
    }

    /**
     * §4.3's working directory: the project root; for an analyser-owned root (OD-74.22) or no project, the private scratch
     * folder (which the session then works in, reading the evidence through the verbs; the working copy's
     * {@code CLAUDE.md} is never loaded).
     */
    public static Path workingDirectory(Path projectRoot, Path home, java.util.function.Predicate<Path> bundleCopy,
                                        java.util.function.Supplier<Path> scratch) {
        if (projectRoot != null && !analyserOwned(projectRoot, home, bundleCopy)) return projectRoot;
        return scratch.get();
    }

    private static Path real(Path p) {
        try {
            return p.toRealPath();
        } catch (IOException e) {
            return p.toAbsolutePath().normalize();
        }
    }
}
