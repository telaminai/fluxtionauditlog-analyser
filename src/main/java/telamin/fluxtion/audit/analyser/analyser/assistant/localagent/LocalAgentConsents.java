package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

/**
 * What the machine says about starting Claude Code now, as the {@code LocalAgentConsent} fact (review finding 9: this
 * was the frame's private method, with no witness). Pure apart from reading the folder's three trust files: the frame
 * supplies its settings and folders, the session decides.
 */
public final class LocalAgentConsents {

    private LocalAgentConsents() {
    }

    /** The machine-tier settings the consent is read from. */
    public record Settings(boolean allowed, boolean full, String fullMode, List<String> trusted, List<String> trustedFull,
                           Double budgetUsd, String model) {
        public Settings {
            trusted = List.copyOf(trusted == null ? List.of() : trusted);
            trustedFull = List.copyOf(trustedFull == null ? List.of() : trustedFull);
        }

        /** The shape before the restart offer: the model not observed. */
        public Settings(boolean allowed, boolean full, String fullMode, List<String> trusted, List<String> trustedFull,
                        Double budgetUsd) {
            this(allowed, full, fullMode, trusted, trustedFull, budgetUsd, null);
        }
    }

    /**
     * @param dir               the working directory the session would use (the project root, or the scratch folder)
     * @param scratch           the analyser's own private scratch folder, or null
     * @param home              the user's home folder
     * @param bundleWorkingCopy whether a folder lies in an evidence bundle's working copy
     */
    public static SessionEvents.LocalAgentConsent of(Path dir, Path scratch, Path home, Settings s,
                                                     Predicate<Path> bundleWorkingCopy) {
        String real = realPath(dir);
        boolean isScratch = dir != null && dir.equals(scratch);
        boolean confirmed = isScratch || s.trusted().contains(real);
        LocalAgentPosture.FullMode mode = LocalAgentPosture.FullMode.parse(s.fullMode())
                .orElse(LocalAgentPosture.FullMode.DEFAULT);
        LocalAgentPosture posture = s.full() ? LocalAgentPosture.full(mode) : LocalAgentPosture.ANALYSER_ASSISTANT;
        boolean fullConfirmed = false;
        if (s.full() && dir != null) {
            FullFolderTrust.Scan scan = FullFolderTrust.scan(dir);
            // review finding 3: the scratch folder counts as confirmed only while it holds none of the three files —
            // a hook planted there is asked about like any other folder's
            fullConfirmed = isScratch && scan.allAbsent()
                    || FullFolderTrust.confirms(s.trustedFull(), real, scan.digest());
        }
        // the one posture function, the adapter's too: it re-digests the folder it is about to use (review M7)
        String postureRef = ClaudeCodeProvider.postureRef(dir, posture);
        SessionEvents.FolderVerdict folder = verdictOf(dir, home);
        long budget = s.budgetUsd() == null ? 0 : Math.round(s.budgetUsd() * 1_000_000d);
        // owner, 2026-10-18: the model (blank is the default) and the folder by digest, for the restart offer
        String model = s.model() == null ? null
                : s.model().isBlank() ? ClaudeCodeProvider.DEFAULT_MODEL : s.model().strip();
        return new SessionEvents.LocalAgentConsent(s.allowed(), confirmed,
                dir != null && bundleWorkingCopy != null && bundleWorkingCopy.test(dir), postureRef, budget, folder,
                s.full(), mode, fullConfirmed, model, dir == null ? null : LocalAgentPosture.digest(real));
    }

    static String realPath(Path p) {
        if (p == null) return "";
        try {
            return p.toRealPath().toString();
        } catch (IOException e) {
            return p.toAbsolutePath().normalize().toString();
        }
    }

    /**
     * M74 P2b (§5.6 step 1): the set-up folder's verdict — the same folder checks as Start, plus a bundle's working copy,
     * which is never a set-up folder. An observation for {@code localAgentSession}, which decides.
     */
    public static SessionEvents.FolderVerdict setupVerdict(java.nio.file.Path real, java.nio.file.Path home,
                                                          java.util.function.Predicate<java.nio.file.Path> bundleCopy) {
        SessionEvents.FolderVerdict v = verdictOf(real, home);
        if (v == SessionEvents.FolderVerdict.OK && bundleCopy != null && bundleCopy.test(real)) {
            return SessionEvents.FolderVerdict.EXPOSES_SECRETS;
        }
        return v;
    }

    /** The one folder check (OneDispatchModelTest: it happens here, once). */
    private static SessionEvents.FolderVerdict verdictOf(Path dir, Path home) {
        return LocalAgentFolders.check(dir, home);
    }
}
