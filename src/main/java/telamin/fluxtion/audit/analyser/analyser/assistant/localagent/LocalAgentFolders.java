package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.FolderVerdict;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Review M4/S10: which working directories may be given to Claude Code at all. {@code --restricted} confines Read, Grep
 * and Glob to the working directory — so the directory itself must not hold credentials. Refused: the home folder or
 * any folder containing it (it holds {@code ~/.ssh}, {@code ~/.claude} and the analyser's endpoint files, with the REST
 * token and the hosted credential), and anything inside {@code ~/.fluxtion-analyser}, {@code ~/.claude}, {@code ~/.ssh},
 * {@code ~/.aws}, {@code ~/.config} or {@code ~/.gnupg}. No folder
 * at all (the scratch folder could not be made) is refused too. Pure: the frame observes, the session decides.
 */
public final class LocalAgentFolders {

    private LocalAgentFolders() {
    }

    public static FolderVerdict check(Path dir, Path home) {
        if (dir == null) return FolderVerdict.UNAVAILABLE;
        Path d = real(dir);
        if (home == null) return FolderVerdict.OK;
        Path h = real(home);
        // the home folder, or a folder containing it (implied by the next check too; kept for when neither settings
        // folder can be resolved)
        if (h.startsWith(d)) return FolderVerdict.EXPOSES_SECRETS;
        // review R4: the credential folders a confined Read or Grep must never reach either
        for (Path secret : new Path[]{h.resolve(".fluxtion-analyser"), h.resolve(".claude"), h.resolve(".ssh"),
                h.resolve(".aws"), h.resolve(".config"), h.resolve(".gnupg")}) {
            Path s = real(secret);
            if (d.startsWith(s) || s.startsWith(d)) return FolderVerdict.EXPOSES_SECRETS;
        }
        return FolderVerdict.OK;
    }

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
