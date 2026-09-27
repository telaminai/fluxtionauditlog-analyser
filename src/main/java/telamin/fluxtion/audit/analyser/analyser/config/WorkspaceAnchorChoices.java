package telamin.fluxtion.audit.analyser.analyser.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The anchors a project could declare, each with what it would actually buy (#20).
 *
 * <h2>Why this is a list and not a text field</h2>
 *
 * <p>{@code workspaceRoot} is a DEPTH, not a path: {@link PathForm} accepts {@code .}, {@code ..},
 * {@code ../..} and no more than six levels, because an anchor says "how far above the project my
 * checkouts live", not "where they live on this machine". A text field invites the absolute path
 * that {@code refuseWorkspaceRoot} rejects, and a directory chooser invites it twice over — the
 * person picks {@code /Users/someone/work} and the honest answer is "that is not what this is".
 * There are at most seven possible values, so they are offered as seven.
 *
 * <h2>Why each one states its consequence</h2>
 *
 * <p>The setting that this exists for is invisible in its own terms: {@code ../..} tells you nothing,
 * and the thing a person wants to know is which of THEIR roots stop being machine-specific. So each
 * choice carries the directory it resolves to and how many of the current roots it would make
 * portable. Picking the smallest anchor that covers the roots is then a comparison, not a guess —
 * and D-C9's rule stands: declared by the person who knows the layout, never inferred.
 */
public final class WorkspaceAnchorChoices {

    private WorkspaceAnchorChoices() {
    }

    /** The deepest anchor {@link PathForm} will accept: {@code ../../../../../..}. */
    public static final int MAX_LEVELS = 6;

    /**
     * One offered anchor.
     *
     * @param anchor    what would be stored ({@code ""} for none, {@code ".."}, {@code "../.."} …)
     * @param dir       where it resolves on this machine, or null when there is no project root
     * @param portable  how many of the current roots this anchor makes project- or workspace-relative
     * @param total     how many roots there are, so "3 of 4" can be read without a second lookup
     */
    public record Choice(String anchor, Path dir, int portable, int total) {

        /** No anchor — the state a project starts in. */
        public boolean isNone() {
            return anchor == null || anchor.isBlank();
        }

        /** One line for a combo box: what is stored, where it lands, and what it buys. */
        public String label() {
            String head = isNone() ? "(none)" : anchor;
            String where = dir == null ? "" : "  →  " + (dir.getFileName() == null
                    ? dir.toString() : dir.getFileName().toString());
            if (total == 0) {
                return head + where;
            }
            return head + where + "  ·  " + portable + " of " + total + " root"
                    + (total == 1 ? "" : "s") + " portable";
        }
    }

    /**
     * Every anchor worth offering for this project, shallowest first, starting with none.
     *
     * <p>{@code .} is accepted by {@link PathForm#refuseWorkspaceRoot} but is not offered: it resolves
     * to the project root, and a root under the project root is already project-relative, so it is an
     * option that changes nothing. A profile that already stores it is still shown — see
     * {@link #withCurrent} — because hiding a value someone is using is worse than offering one they
     * do not need.
     *
     * <p>The ladder stops BELOW the filesystem root. Offering {@code ../../../../../..} from two
     * levels down would offer a directory that does not exist, and offering {@code /} itself would be
     * worse than useless: every absolute path is under it, so it would report every root as portable
     * while writing the machine's whole layout into the profile as a run of {@code ..} steps. A
     * workspace is a directory somebody checks repositories out into; it is not the machine.
     */
    public static List<Choice> forProject(Path projectRoot, List<String> roots, String home) {
        List<Choice> out = new ArrayList<>();
        int total = roots == null ? 0 : roots.size();
        out.add(new Choice("", projectRoot, portableCount(projectRoot, roots, "", home), total));
        if (projectRoot == null) {
            return out;
        }
        Path root = projectRoot.toAbsolutePath().normalize();
        StringBuilder anchor = new StringBuilder();
        Path at = root;
        for (int level = 1; level <= MAX_LEVELS; level++) {
            at = at.getParent();
            if (at == null || at.getParent() == null) {
                break;                       // at the filesystem root, or would be — see above
            }
            anchor.append(level == 1 ? ".." : "/..");
            String value = anchor.toString();
            out.add(new Choice(value, at, portableCount(projectRoot, roots, value, home), total));
        }
        return out;
    }

    /**
     * The offered list, guaranteed to contain {@code current}.
     *
     * <p>A profile can hold an anchor this list would not offer — {@code .}, or one written by hand.
     * The control must still be able to show what is in force, or opening the dialog would silently
     * present a different setting from the one that is stored.
     */
    public static List<Choice> withCurrent(Path projectRoot, List<String> roots, String home, String current) {
        List<Choice> out = forProject(projectRoot, roots, home);
        String want = current == null ? "" : current.trim();
        for (Choice c : out) {
            if (c.anchor().equals(want)) {
                return out;
            }
        }
        out.add(1, new Choice(want, PathForm.workspaceDir(projectRoot, want),
                portableCount(projectRoot, roots, want, home), roots == null ? 0 : roots.size()));
        return out;
    }

    /** How many roots this anchor makes portable — project-relative ones included, since they already are. */
    static int portableCount(Path projectRoot, List<String> roots, String anchor, String home) {
        if (roots == null || roots.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (String r : roots) {
            PathForm.Form form = PathForm.of(r, projectRoot, anchor, home);
            if (form == PathForm.Form.PROJECT || form == PathForm.Form.WORKSPACE) {
                n++;
            }
        }
        return n;
    }
}
