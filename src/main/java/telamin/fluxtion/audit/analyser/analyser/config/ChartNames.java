package telamin.fluxtion.audit.analyser.analyser.config;

/**
 * M68.6 (spec-evidence-integrity D-E5; owner 2026-09-24, Q2: REFUSE at creation): which names a chart may be GIVEN.
 *
 * <p>The rule is the chart ADDRESS grammar's ({@code graph:<name>[:note:<n> | :series:<label>]}), so every name a chart
 * can be given is one every surface that points at charts can address. It lives here, in {@code config}, because charts
 * are named in places that are not the UI — the duplicate-name repair renames saved definitions directly — and a rule
 * only the UI's entrances applied would leave one open. The UI's address parser delegates here. (This package persists
 * settings, so it names no UI overlay, per D-SP4.)
 */
public final class ChartNames {

    private ChartNames() {
    }

    /** Why a chart may not be given {@code name}, or null when it may. A null or blank name is the caller's case. */
    public static String problem(String name) {
        if (name == null || name.isBlank()) return null;
        String n = name.trim();
        if (n.indexOf(':') >= 0) {
            return "a chart name cannot contain ':' — a chart's address uses ':' to separate the chart from its notes and "
                    + "series, so 'graph:" + n + "' could not be pointed at";
        }
        if (n.indexOf('"') >= 0) return "a chart name cannot contain '\"' — it quotes a chart name in a chart's address";
        if (n.equalsIgnoreCase("note") || n.equalsIgnoreCase("series")) {
            return "'" + n + "' is the word a chart's address uses for its parts, so this chart's notes and series "
                    + "could not be pointed at — choose another name";
        }
        return null;
    }
}
