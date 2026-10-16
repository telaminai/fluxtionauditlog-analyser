package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.FolderVerdict;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Review M4/S10: which working directories may be given to Claude Code at all. {@code --restricted} confines Read, Grep
 * and Glob to the working directory — so the directory itself must not hold credentials. Refused: the home folder or
 * any folder containing it (it holds {@code ~/.ssh}, {@code ~/.claude} and the analyser's endpoint files, with the REST
 * token and the hosted credential), and anything inside {@code ~/.fluxtion-analyser} or {@code ~/.claude}. No folder
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
        for (Path secret : new Path[]{h.resolve(".fluxtion-analyser"), h.resolve(".claude")}) {
            Path s = real(secret);
            if (d.startsWith(s) || s.startsWith(d)) return FolderVerdict.EXPOSES_SECRETS;
        }
        return FolderVerdict.OK;
    }

    private static Path real(Path p) {
        try {
            return p.toRealPath();
        } catch (IOException e) {
            return p.toAbsolutePath().normalize();
        }
    }
}
