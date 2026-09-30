package telamin.fluxtion.audit.analyser.analyser.config;

import java.util.List;

/**
 * Which focus a topology should open at, and on whose authority.
 *
 * <p>Two sources, deliberately kept apart because they answer different questions:
 *
 * <ul>
 *   <li>{@link AppConfig#defaultFocus} — the PROJECT's advice, committed to its profile and travelling
 *       to a colleague: "this is where the topology is best entered".</li>
 *   <li>{@link AppConfig#lastFocusByProject} — YOUR history, machine tier, never shared: the focus you
 *       were last on in this project.</li>
 * </ul>
 *
 * <p>Yours wins when both exist: it is the more recent intent, and the project's advice is aimed at
 * somebody who has not formed one yet. A fresh checkout has no history, so it gets the advice —
 * which is the case the project-tier value exists for.
 *
 * <p>Neither is trusted blindly. A name that this topology's saved focuses do not contain is ignored
 * rather than reported: a profile can outlive a focus, and a rename must not raise an error on open.
 */
public record FocusOnOpen(String name, String source) {

    /** Your own history said so. */
    public static final String REMEMBERED = "remembered";
    /** The project's profile said so. */
    public static final String PROJECT_DEFAULT = "project default";

    /** Nothing to apply — open at the full graph, as before either value existed. */
    public static final FocusOnOpen NONE = new FocusOnOpen(null, null);

    public boolean any() {
        return name != null;
    }

    /**
     * @param remembered   the focus last applied in this project on this machine, or null
     * @param projectDefault the project's own "start here", or null
     * @param available    the focus names this topology actually has
     */
    public static FocusOnOpen choose(String remembered, String projectDefault, List<String> available) {
        if (available == null || available.isEmpty()) return NONE;
        if (usable(remembered, available)) return new FocusOnOpen(remembered.trim(), REMEMBERED);
        if (usable(projectDefault, available)) return new FocusOnOpen(projectDefault.trim(), PROJECT_DEFAULT);
        return NONE;
    }

    private static boolean usable(String name, List<String> available) {
        return name != null && !name.isBlank() && available.contains(name.trim());
    }
}
