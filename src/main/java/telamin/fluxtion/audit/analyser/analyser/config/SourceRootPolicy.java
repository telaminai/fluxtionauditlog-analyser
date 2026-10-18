package telamin.fluxtion.audit.analyser.analyser.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Owner decision 2026-10-18 (P5): which folders may become a source root, and for whom — decided HERE, once; every path
 * that registers a source root asks this, naming its caller.
 *
 * <ul>
 *   <li><b>Tier 1, every caller</b> — a person, an external MCP/REST client, the in-app assistant, Claude Code in the
 *       tab: never the home folder or a folder containing it, and never a credential folder ({@code ~/.ssh},
 *       {@code ~/.aws}, {@code ~/.m2}, {@code ~/.config}, {@code ~/.gnupg}), a folder inside one (below {@code ~/.m2}
 *       excepted: {@code ~/.m2/repository} is allowed), or one containing one
 *       ({@link SecretFolders}, the function Claude Code's working directory is judged by). A source root grants reading
 *       beneath it, so a root there would expose credentials to whoever reads source through the analyser. A project
 *       profile, a shared settings file or a proposal cannot carry one in either.</li>
 *   <li><b>Tier 2, assistants only</b> — the in-app assistant and Claude Code in the tab: a folder outside the open
 *       project's root. Claude Code's {@code source_root} is asked of the person, so for it the approval dialog says
 *       "outside this project" and the call stays the person's; the in-app assistant has no dialog, so it is refused
 *       ("add this folder yourself in Settings ▸ Sources"). External clients get tier 1 only.</li>
 * </ul>
 */
public final class SourceRootPolicy {

    /** Who is registering the root. */
    public enum Caller {
        /** The person, in the analyser's own UI — and what a profile or a settings file the person opened carries. */
        PERSON,
        /** An external MCP or REST client. */
        EXTERNAL,
        /** The in-app assistant (an action of its turn). */
        IN_APP_ASSISTANT,
        /** The analyser's own Claude Code session (the hosted bridge). */
        HOSTED_CLAUDE_CODE;

        public boolean assistant() {
            return this == IN_APP_ASSISTANT || this == HOSTED_CLAUDE_CODE;
        }
    }

    /** Why a folder is refused, by name. */
    public enum Reason {
        /** Tier 1: the home folder, or a folder containing it. */
        HOME_FOLDER,
        /** Tier 1: a credential folder, inside one, or containing one. */
        CREDENTIAL_FOLDER,
        /** Tier 2: outside the open project, for the in-app assistant. */
        OUTSIDE_PROJECT
    }

    /**
     * The decision. {@code reason} null means allowed; {@code outsideProject} says an assistant's allowed folder lies
     * outside the open project (what Claude Code's approval dialog says).
     */
    public record Verdict(Reason reason, String words, boolean outsideProject) {
        public boolean allowed() {
            return reason == null;
        }
    }

    private SourceRootPolicy() {
    }

    /** The decision for {@code dir}, registered by {@code caller}, with {@code projectRoot} the open project's (or null). */
    public static Verdict decide(Path dir, Path home, Path projectRoot, Caller caller) {
        Reason tier1 = tier1(dir, home);
        if (tier1 != null) return new Verdict(tier1, words(tier1, dir), false);
        boolean outside = caller != null && caller.assistant() && outside(dir, projectRoot);
        if (outside && caller == Caller.IN_APP_ASSISTANT) {
            return new Verdict(Reason.OUTSIDE_PROJECT, words(Reason.OUTSIDE_PROJECT, dir), true);
        }
        return new Verdict(null, null, outside);
    }

    /** Tier 1's reason for {@code dir}, or null when it is no secret folder. */
    public static Reason tier1(Path dir, Path home) {
        if (dir == null) return null;
        return switch (SecretFolders.exposure(dir, home)) {
            case HOME_OR_ANCESTOR -> Reason.HOME_FOLDER;
            case CREDENTIAL_FOLDER -> Reason.CREDENTIAL_FOLDER;
            case NONE -> null;
        };
    }

    /** Whether {@code dir} lies outside {@code projectRoot}; with no project open, every folder does. */
    public static boolean outside(Path dir, Path projectRoot) {
        if (dir == null) return true;
        if (projectRoot == null) return true;
        return !SecretFolders.within(SecretFolders.canonical(dir), SecretFolders.canonical(projectRoot));
    }

    /** The refusal in plain words. */
    public static String words(Reason reason, Path dir) {
        String d = dir == null ? "that folder" : "\"" + dir + "\"";
        return switch (reason) {
            case HOME_FOLDER -> "not added: " + d + " is your home folder or contains it — a source root lets the analyser "
                    + "read every file beneath it, your credentials included. Add the project's own source folder instead.";
            case CREDENTIAL_FOLDER -> "not added: " + d + " is, holds or is inside a credential folder (~/.ssh, ~/.aws, "
                    + "~/.config, ~/.gnupg; or is ~/.m2 itself) — a source root there would let the analyser read it.";
            case OUTSIDE_PROJECT -> "not added: " + d + " is outside this project, and the in-app assistant adds only "
                    + "folders inside it — add this folder yourself in Settings ▸ Sources.";
        };
    }

    /**
     * Tier 1 over a list a loader is about to take in (a profile, a settings file, the analyser's own settings, a
     * bundle's remembered roots): the roots it may keep, in order. {@code refused}, when given, receives the others.
     */
    public static List<String> keepAllowed(List<String> roots, Path home, List<String> refused) {
        List<String> out = new ArrayList<>();
        if (roots == null) return out;
        for (String r : roots) {
            if (r == null || r.isBlank()) continue;
            if (tier1(Path.of(r), home) == null) out.add(r);
            else if (refused != null) refused.add(r);
        }
        return out;
    }

    /** The user's home folder, as the analyser knows it. */
    public static Path home() {
        return Path.of(System.getProperty("user.home"));
    }
}
