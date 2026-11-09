package telamin.fluxtion.audit.analyser.analyser.ui;

/**
 * Owner decision 2026-11-07: every starter list the analyser shows — the template picker, the start page's template
 * names, {@code context.starters} (what the onboard assistant offers and proposes from) — shows only the recommended
 * starters unless the person ticks "Show all templates" in the picker. The tick is remembered for this run of the
 * analyser only (it starts unticked each launch): it is a view choice, not a setting.
 */
final class StarterListPreference {

    static final String LABEL = "Show all templates";

    private static volatile boolean showAll;

    private StarterListPreference() {
    }

    static boolean showAll() {
        return showAll;
    }

    static void setShowAll(boolean value) {
        showAll = value;
    }
}
