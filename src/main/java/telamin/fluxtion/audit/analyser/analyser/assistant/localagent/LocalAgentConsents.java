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
                           Double budgetUsd) {
        public Settings {
            trusted = List.copyOf(trusted == null ? List.of() : trusted);
            trustedFull = List.copyOf(trustedFull == null ? List.of() : trustedFull);
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
        SessionEvents.FolderVerdict folder = LocalAgentFolders.check(dir, home);
        long budget = s.budgetUsd() == null ? 0 : Math.round(s.budgetUsd() * 1_000_000d);
        return new SessionEvents.LocalAgentConsent(s.allowed(), confirmed,
                dir != null && bundleWorkingCopy != null && bundleWorkingCopy.test(dir), postureRef, budget, folder,
                s.full(), mode, fullConfirmed);
    }

    static String realPath(Path p) {
        if (p == null) return "";
        try {
            return p.toRealPath().toString();
        } catch (IOException e) {
            return p.toAbsolutePath().normalize().toString();
        }
    }
}
