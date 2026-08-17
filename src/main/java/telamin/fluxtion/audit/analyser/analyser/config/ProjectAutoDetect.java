package telamin.fluxtion.audit.analyser.analyser.config;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * Whether opening a log should offer to load the project it sits in (M20.3).
 *
 * <p>This is the M19 zero-setup hook. Download a playground bundle, open its audit log, and the profile
 * committed at the repo root configures the source roots, event processor and graphs — no manual setup,
 * and a real switchable project rather than a one-off import into your global settings.
 *
 * <h2>The rules are here because "when not to ask" is the hard part</h2>
 *
 * <p>An offer that appears when it should not is worse than no offer at all: a prompt you have already
 * declined, appearing every time you reopen the same file, is the kind of thing people disable the
 * feature over. So the policy is separated from the dialog and tested:
 *
 * <ul>
 *   <li>no profile above the log — nothing to offer;</li>
 *   <li>the profile found <em>is</em> the active project — already loaded, saying so would be noise;</li>
 *   <li>this log was declined earlier in the session — asked and answered;</li>
 *   <li>a log with no local path (an {@code s3://} object streamed to a temp file) has no project to
 *       sit in, and a temp directory must never be mistaken for one.</li>
 * </ul>
 *
 * <p>Declines are remembered <b>per session and per log</b>, matching the brief. Per session because a
 * later launch is a fair time to ask again — the profile may have appeared since. Per log because
 * declining one file says nothing about another.
 */
public final class ProjectAutoDetect {

    private final Set<String> declined = new HashSet<>();

    /**
     * The profile to offer for {@code localLog}, or {@code null} to stay quiet.
     *
     * @param localLog     the log's real path on disk, or {@code null} when it has none
     * @param activeFile   the project currently open, or {@code null}
     */
    public Path offerFor(Path localLog, Path activeFile) {
        if (localLog == null) {
            return null;
        }
        Path found = ProjectProfile.findNear(localLog);
        if (found == null) {
            return null;
        }
        if (activeFile != null && found.toAbsolutePath().normalize()
                .equals(activeFile.toAbsolutePath().normalize())) {
            return null;   // already the active project
        }
        if (declined.contains(key(localLog))) {
            return null;
        }
        return found;
    }

    /** Remember a "no" so the same log does not ask again this session. */
    public void decline(Path localLog) {
        if (localLog != null) {
            declined.add(key(localLog));
        }
    }

    /** Forget a decline — used when the user opens that project by hand after all. */
    public void clearDecline(Path localLog) {
        if (localLog != null) {
            declined.remove(key(localLog));
        }
    }

    private static String key(Path p) {
        return p.toAbsolutePath().normalize().toString();
    }
}
